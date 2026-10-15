#!/usr/bin/env python3
"""
中医工厂 · 知识库打包发布工具
=================================

把知识包源文件打包成可以直接上传到任意静态托管的发布目录：
    dist/
      manifest.json                        <- 客户端拉取的索引
      packs/<packId>-<version>.json        <- 各知识包

用法：
    python3 build_knowledge_packs.py --src ./kb --out ./dist
    python3 build_knowledge_packs.py --src ./kb --out ./dist --min-app-version 1.3.0

源目录要求：
    kb/
      tcm.materia.core.json      <- 每个文件是一个知识包（KnowledgePack 结构）
      tcm.formula.core.json

知识包 JSON 结构（kind 四选一）：
{
  "schemaVersion": 1,
  "packId": "tcm.materia.core",
  "packName": "中药学 · 核心药材",
  "kind": "cards",                      # cards | questions | book | supporters
  "version": "1.1.0",
  "subject": "中药学",
  "license": "公有领域 / 国家标准事实数据 / 自编条目",
  "sourceNote": "依据《中国药典》2025 年版一部与规划教材考点整理",
  "items": [
    { "externalId": "materia-guizhi-01",
      "deck": "中药学",
      "front": "桂枝的功效",
      "back": "发汗解肌，温通经脉，助阳化气，平冲降气",
      "sourceRef": "《中国药典》2025年版一部" }
  ]
}

要点：
  * items[].externalId 可以省略，脚本会按「内容派生」规则自动生成。
    该规则与 App 端一致：sha256(字段以 \\u0001 连接) 取前 16 位十六进制。
    externalId 必须在同一包的版本之间保持稳定 —— 它是 App 匹配
    「已有条目」并保留用户背诵进度的唯一依据。因此**不要**用序号，
    也不要在前面插入条目后重新编号。
  * version 只在内容真正变化时递增，否则客户端不会下载。
  * 发布后客户端只下载「版本高于本地」的包，增量更新。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
from datetime import datetime, timezone
from pathlib import Path

SCHEMA_VERSION = 1
SUPPORTED_KINDS = ("cards", "questions", "book", "supporters")

# 与 App 端 KnowledgePack 的字段保持一致的必填校验
REQUIRED_ITEM_FIELDS = {
    "cards": ("front", "back"),
    "questions": ("stem",),
    "book": ("title", "content"),
    # 名单只有昵称必填：留言、日期、序号都可以省略
    "supporters": ("name",),
}

# externalId 自动派生时使用的字段（顺序固定，改顺序会让所有 ID 变化）
ID_FIELDS = {
    "cards": ("deck", "front"),
    "questions": ("subject", "stem"),
    "book": ("title",),
}

# 与 App 端 PackValidator.MAX_SUPPORTER_NAME_CHARS 保持一致
MAX_SUPPORTER_NAME_CHARS = 24


def sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def derive_external_id(kind: str, item: dict, pack: dict) -> str:
    """
    内容派生的稳定 ID。

    ⚠️ 必须与 App 端 core/knowledge/ExternalId.kt 完全一致：
        sha256("\u0001".join(parts).strip each) -> hex -> 前 16 位
    不一致的话，App 会认为这是一条全新内容，导致更新后出现重复条目。
    """
    if kind == "supporters":
        # 名单重名很常见（多个"匿名""小王"），所以把序号或日期一并纳入派生键。
        # ⚠️ 这段逻辑必须与 App 端 PackValidator.deriveExternalId 的分支逐字对应。
        discriminator = item.get("serial")
        if discriminator is None:
            discriminator = (item.get("date") or "").strip()
        if not discriminator:
            discriminator = pack.get("subject") or pack.get("packName") or ""
        raw = "\u0001".join([
            str(item.get("name", "")).strip(),
            str(discriminator).strip(),
        ]).encode("utf-8")
        return sha256_hex(raw)[:16]

    fields = ID_FIELDS.get(kind, ())
    parts = []
    for field in fields:
        value = item.get(field)
        if value is None:
            # 回退到包级字段，保证总能算出东西
            value = pack.get("subject") or pack.get("packName") or ""
        parts.append(str(value).strip())
    raw = "\u0001".join(parts).encode("utf-8")
    return sha256_hex(raw)[:16]


def validate_pack(pack: dict, path: Path) -> list[str]:
    """返回问题列表；空列表表示通过。"""
    problems: list[str] = []

    pack_id = pack.get("packId")
    if not pack_id:
        problems.append("缺少 packId")
    elif not _valid_pack_id(pack_id):
        problems.append(f"packId 含非法字符：{pack_id}（只允许小写字母、数字、点、下划线、连字符）")

    if not pack.get("version"):
        problems.append("缺少 version")

    kind = pack.get("kind")
    if kind not in SUPPORTED_KINDS:
        problems.append(f"kind 必须是 {SUPPORTED_KINDS} 之一，当前为 {kind!r}")

    items = pack.get("items")
    if not isinstance(items, list):
        problems.append("items 不是数组")
    elif not items and kind != "supporters":
        # 名单允许为空 —— 「还没人打赏」是正常状态，不是错误。
        # 其他类型空包没有任何意义，仍然拒绝。
        problems.append("items 为空")

    return problems


def _valid_pack_id(pack_id: str) -> bool:
    if not (2 <= len(pack_id) <= 64):
        return False
    allowed = set("abcdefghijklmnopqrstuvwxyz0123456789._-")
    return pack_id[0] in allowed and all(c in allowed for c in pack_id)


def normalize_items(pack: dict) -> tuple[list[dict], list[str]]:
    """补全 externalId，并做条目级校验。返回 (条目列表, 警告列表)。"""
    kind = pack.get("kind")
    warnings: list[str] = []
    result: list[dict] = []
    seen: set[str] = set()

    for index, item in enumerate(pack.get("items", [])):
        if not isinstance(item, dict):
            warnings.append(f"第 {index + 1} 条不是对象，已跳过")
            continue

        for field in REQUIRED_ITEM_FIELDS.get(kind, ()):
            if not str(item.get(field, "")).strip():
                warnings.append(f"第 {index + 1} 条缺少必填字段 {field!r}，已跳过")
                break
        else:
            external_id = str(item.get("externalId") or "").strip()
            if not external_id:
                external_id = derive_external_id(kind, item, pack)
            if external_id in seen:
                warnings.append(f"第 {index + 1} 条 externalId 重复（{external_id}），已跳过")
                continue
            seen.add(external_id)

            normalized = dict(item)
            normalized["externalId"] = external_id
            result.append(normalized)

    return result, warnings


def build(src_dir: Path, out_dir: Path, min_app_version: str | None) -> int:
    if not src_dir.is_dir():
        print(f"[错误] 源目录不存在：{src_dir}", file=sys.stderr)
        return 1

    pack_files = sorted(p for p in src_dir.glob("*.json"))
    if not pack_files:
        print(f"[错误] {src_dir} 下没有任何 .json 知识包", file=sys.stderr)
        return 1

    packs_out = out_dir / "packs"
    packs_out.mkdir(parents=True, exist_ok=True)

    entries: list[dict] = []
    total_items = 0
    total_warnings: list[str] = []

    for file in pack_files:
        try:
            pack = json.loads(file.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            print(f"[跳过] {file.name}：JSON 解析失败（{exc}）", file=sys.stderr)
            continue

        problems = validate_pack(pack, file)
        if problems:
            print(f"[跳过] {file.name}：", file=sys.stderr)
            for p in problems:
                print(f"        · {p}", file=sys.stderr)
            continue

        items, warnings = normalize_items(pack)
        if not items and pack.get("kind") != "supporters":
            print(f"[跳过] {file.name}：没有任何条目通过校验", file=sys.stderr)
            continue
        for w in warnings:
            total_warnings.append(f"{file.name}: {w}")

        pack_id = pack["packId"]
        version = str(pack["version"])
        kind = pack["kind"]

        # 输出文件名带上版本，便于 CDN 缓存失效时自然切换
        payload = {
            "schemaVersion": pack.get("schemaVersion", SCHEMA_VERSION),
            "packId": pack_id,
            "packName": pack.get("packName", pack_id),
            "kind": kind,
            "version": version,
            "subject": pack.get("subject"),
            "description": pack.get("description"),
            "license": pack.get("license"),
            "sourceNote": pack.get("sourceNote"),
            "items": items,
        }
        # 去掉 None 值，让输出更干净（App 端有默认值，不影响解析）
        payload = {k: v for k, v in payload.items() if v is not None}

        raw = json.dumps(payload, ensure_ascii=False, indent=2).encode("utf-8")
        out_name = f"{pack_id}-{version}.json"
        (packs_out / out_name).write_bytes(raw)

        entries.append({
            "packId": pack_id,
            "packName": payload["packName"],
            "kind": kind,
            "version": version,
            "subject": payload.get("subject"),
            "description": payload.get("description"),
            "license": payload.get("license"),
            "itemCount": len(items),
            "bytes": len(raw),
            "sha256": sha256_hex(raw),
            "url": f"packs/{out_name}",
        })

        total_items += len(items)
        print(f"[打包] {pack_id:<28} v{version:<8} {len(items):>5} 条  {len(raw) / 1024:>7.1f} KB")

    if not entries:
        print("[错误] 没有任何知识包被打包", file=sys.stderr)
        return 1

    entries.sort(key=lambda e: e["packId"])
    manifest = {
        "schemaVersion": SCHEMA_VERSION,
        "generatedAt": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    }
    if min_app_version:
        manifest["minAppVersion"] = min_app_version
    manifest["packs"] = entries

    manifest_raw = json.dumps(manifest, ensure_ascii=False, indent=2).encode("utf-8")
    (out_dir / "manifest.json").write_bytes(manifest_raw)

    print()
    print(f"完成：{len(entries)} 个知识包，共 {total_items} 条内容")
    print(f"索引：{out_dir / 'manifest.json'}（{len(manifest_raw) / 1024:.1f} KB，"
          f"sha256 {sha256_hex(manifest_raw)[:16]}…）")
    if total_warnings:
        print(f"警告 {len(total_warnings)} 条：")
        for w in total_warnings[:20]:
            print(f"  · {w}")
        if len(total_warnings) > 20:
            print(f"  · 其余 {len(total_warnings) - 20} 条已省略")
    print()
    print("下一步：把整个输出目录上传到任意静态托管，"
          "然后在 App 的「知识库 → 数据源设置」里填入该目录地址。")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(
        description="把知识包源文件打包为可发布的静态目录",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("--src", required=True, type=Path, help="知识包源目录")
    parser.add_argument("--out", required=True, type=Path, help="输出目录")
    parser.add_argument("--min-app-version", default=None,
                        help="要求的最低 App 版本（如 1.3.0）")
    args = parser.parse_args()
    return build(args.src, args.out, args.min_app_version)


if __name__ == "__main__":
    raise SystemExit(main())
