#!/usr/bin/env python3
"""
激活码签发工具。

只有你（开发者）需要用它。App 里只有公钥，无法签发。

    # 签发一个 10 年有效的码
    python3 tools/make_license.py issue --days 3650 --buyer "淘宝订单 20261015..."

    # 签发永久码
    python3 tools/make_license.py issue --forever --buyer "微信 张三"

    # 私钥不在工程里时，用 --key 指定（可放在子命令前，也可放在子命令后）
    python3 tools/make_license.py issue --forever --buyer "微信 张三" --key "D:\\私密\\激活码私钥.pem"

    # 校验一个码（排查买家说"激活不了"时用）
    python3 tools/make_license.py verify TCMF1XXXX...

    # 从订单表批量签发（CSV，一列 buyer 即可）
    python3 tools/make_license.py batch --input orders.csv --days 3650

私钥默认读同目录下的 `激活码私钥.pem`，用 --key 指定其他路径。
每次签发都会追加记录到 `签发记录.csv`，用于日后追溯泄露源。
"""

from __future__ import annotations

import argparse
import base64
import csv
import os
import struct
import sys
from datetime import datetime, timedelta
from pathlib import Path

try:
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec, utils
except ImportError:
    sys.exit(
        "缺少依赖：cryptography\n"
        "安装：pip install cryptography"
    )

# ---- 与 Kotlin 侧 LicenseFormat.kt 必须严格一致 ----
PREFIX = "TCMF1"
VERSION_CURRENT = 1
TIER_STANDARD = 1
VALID_FOREVER = 0xFFFF
PAYLOAD_BYTES = 11
SIGNATURE_BYTES = 64
TOTAL_BYTES = PAYLOAD_BYTES + SIGNATURE_BYTES   # 75，恰好是 5 的整数倍
CHARS = TOTAL_BYTES // 5 * 8                    # 120，无 Base32 填充位

# 签发日期的计时起点，与 Kotlin 侧 EPOCH_DAY_MS 一致（2026-01-01 UTC）
EPOCH_DAY = datetime(2026, 1, 1)


def load_private_key(path: Path):
    if not path.exists():
        sys.exit(
            f"找不到私钥：{path}\n"
            "私钥只在开发者手上，请从你的安全备份中取回。\n"
            "（如果确实丢失，只能重新生成密钥对，但**已售出的激活码会全部失效**）"
        )
    return serialization.load_pem_private_key(path.read_bytes(), password=None)


def build_payload(tier: int, valid_days: int, serial: int) -> bytes:
    """
    载荷布局（大端，共 11 字节）：
        [0]     版本          [1]     档位
        [2..3]  有效期天数    [4..7]  序号
        [8..9]  签发日期      [10]    保留位（恒 0）

    为什么是 11 字节这个不整齐的长度：11 + 64(签名) = 75，恰好是 5 的整数倍，
    Base32 编码后不产生填充位。若总长不是 5 的倍数，末尾会有几位死数据被解码器丢弃，
    导致**篡改激活码最后一个字符仍能通过验证**。这个坑是单元测试发现的。
    """
    if not 0 < serial <= 0xFFFFFFFF:
        sys.exit(f"序号必须在 1..4294967295 之间，当前 {serial}")
    if not 0 < valid_days <= VALID_FOREVER:
        sys.exit(f"有效期必须在 1..{VALID_FOREVER} 天之间，当前 {valid_days}")

    issued_days = (datetime.now() - EPOCH_DAY).days
    if not 0 <= issued_days <= 0xFFFF:
        sys.exit(f"签发日期 {issued_days} 超出可表示范围（0..65535 天）")

    return struct.pack(
        ">BBHIHB", VERSION_CURRENT, tier, valid_days, serial, issued_days, 0
    )


def sign_payload(private_key, payload: bytes) -> bytes:
    """
    签名后转成定长 r||s（各 32 字节）。

    cryptography 默认输出 DER，长度会在 70~72 之间浮动；
    定长 64 字节才能保证每个激活码长度完全一致（便于人工核对）。
    """
    der = private_key.sign(payload, ec.ECDSA(hashes.SHA256()))
    r, s = utils.decode_dss_signature(der)
    return r.to_bytes(32, "big") + s.to_bytes(32, "big")


def make_code(private_key, tier: int, valid_days: int, serial: int) -> str:
    payload = build_payload(tier, valid_days, serial)
    raw = payload + sign_payload(private_key, payload)
    body = base64.b32encode(raw).decode().rstrip("=")
    return PREFIX + body


def verify_code(code: str, private_key) -> tuple[bool, str]:
    """本地自检：确保刚生成的码能被正确解析（不含公钥，故用私钥反推公钥验签）。"""
    normalized = "".join(c for c in code if c.isalnum()).upper()
    if not normalized.startswith(PREFIX):
        return False, "前缀不正确"
    body = normalized[len(PREFIX):]
    if len(body) != CHARS:
        return False, f"长度应为 {CHARS}，实际 {len(body)}"
    try:
        raw = base64.b32decode(body + "=" * (-len(body) % 8))
    except Exception as exc:
        return False, f"Base32 解码失败：{exc}"
    if len(raw) != TOTAL_BYTES:
        return False, f"字节数应为 {TOTAL_BYTES}，实际 {len(raw)}"

    payload, raw_sig = raw[:PAYLOAD_BYTES], raw[PAYLOAD_BYTES:]
    r = int.from_bytes(raw_sig[:32], "big")
    s = int.from_bytes(raw_sig[32:], "big")
    der = utils.encode_dss_signature(r, s)
    from cryptography.exceptions import InvalidSignature
    from cryptography.hazmat.primitives.asymmetric import ec as _ec
    try:
        private_key.public_key().verify(der, payload, _ec.ECDSA(hashes.SHA256()))
    except InvalidSignature:
        return False, "签名校验失败"

    version, tier, days, serial, issued_days, _reserved = struct.unpack(">BBHIHB", payload)
    desc = "永久" if days == VALID_FOREVER else f"{days} 天"
    issued = (EPOCH_DAY + timedelta(days=issued_days)).strftime("%Y-%m-%d")
    return True, f"版本 v{version} · 档位 {tier} · 有效期 {desc} · 序号 {serial} · 签发于 {issued}"


def append_record(path: Path, serial: int, code: str, days: int, buyer: str) -> None:
    is_new = not path.exists()
    with path.open("a", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        if is_new:
            w.writerow(["签发时间", "序号", "有效期天数", "买家备注", "激活码"])
        w.writerow([
            datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            serial,
            "永久" if days == VALID_FOREVER else days,
            buyer,
            code,
        ])


def next_serial(record_path: Path) -> int:
    """从签发记录里取最大序号 +1，避免重号。"""
    if not record_path.exists():
        return 1001
    max_serial = 1000
    with record_path.open(newline="", encoding="utf-8-sig") as f:
        for row in csv.DictReader(f):
            try:
                max_serial = max(max_serial, int(row.get("序号", 0)))
            except (ValueError, TypeError):
                continue
    return max_serial + 1


def main() -> int:
    ap = argparse.ArgumentParser(description="中医工厂 激活码签发工具")
    ap.add_argument("--key", default=None, help="私钥路径（默认同目录 激活码私钥.pem）")
    ap.add_argument("--record", default=None, help="签发记录 CSV 路径")
    sub = ap.add_subparsers(dest="cmd", required=True)

    # 这两个参数也挂到每个子命令上，这样 `issue --key xxx` 和
    # `--key xxx issue` 两种写法都能用 —— 原先只挂顶层，用户在子命令后
    # 传 --key 会直接报 unrecognized arguments。
    # 子命令侧用 SUPPRESS 作默认值：不传时不往 namespace 写属性，
    # 从而不会把顶层已解析到的值覆盖成 None。
    common = argparse.ArgumentParser(add_help=False)
    common.add_argument("--key", default=argparse.SUPPRESS, help="私钥路径（默认同目录 激活码私钥.pem）")
    common.add_argument("--record", default=argparse.SUPPRESS, help="签发记录 CSV 路径")

    p_issue = sub.add_parser("issue", parents=[common], help="签发单个激活码")
    p_issue.add_argument("--days", type=int, default=3650, help="有效期天数（默认 3650）")
    p_issue.add_argument("--forever", action="store_true", help="永久授权")
    p_issue.add_argument("--serial", type=int, default=None, help="指定序号（默认自动递增）")
    p_issue.add_argument("--buyer", default="", help="买家备注，便于日后追溯")

    p_batch = sub.add_parser("batch", parents=[common], help="从 CSV 批量签发")
    p_batch.add_argument("--input", required=True, help="输入 CSV，需含 buyer 列")
    p_batch.add_argument("--days", type=int, default=3650)
    p_batch.add_argument("--forever", action="store_true")
    p_batch.add_argument("--out", default=None, help="输出 CSV（默认 licenses-out.csv）")

    p_ver = sub.add_parser("verify", parents=[common], help="校验激活码")
    p_ver.add_argument("code")

    args = ap.parse_args()

    here = Path(__file__).resolve().parent
    key_path = Path(args.key) if args.key else here / "激活码私钥.pem"
    record_path = Path(args.record) if args.record else here / "签发记录.csv"

    if args.cmd == "issue" and not Path(key_path).exists():
        # 私钥默认不在工程里（避免误提交），给出明确指引
        print("提示：默认路径下没有找到私钥。", file=sys.stderr)
        print("  请用 --key 指定私钥位置，例如：", file=sys.stderr)
        print("  --key 'D:\\私密\\激活码私钥.pem'", file=sys.stderr)

    key = load_private_key(key_path)

    if args.cmd == "issue":
        days = VALID_FOREVER if args.forever else args.days
        serial = args.serial if args.serial else next_serial(record_path)
        code = make_code(key, TIER_STANDARD, days, serial)
        ok, desc = verify_code(code, key)
        if not ok:
            print(f"✗ 自检失败：{desc}", file=sys.stderr)
            return 1
        append_record(record_path, serial, code, days, args.buyer)
        print()
        print("激活码：")
        print(code)
        print()
        print(f"  {desc}")
        print(f"  有效期    ：{'永久' if days == VALID_FOREVER else str(days) + ' 天（自首次激活起算）'}")
        print(f"  已记录到  ：{record_path}")
        print()
        return 0

    if args.cmd == "batch":
        src = Path(args.input)
        if not src.exists():
            sys.exit(f"找不到输入文件：{src}")
        days = VALID_FOREVER if args.forever else args.days
        out = Path(args.out) if args.out else Path("licenses-out.csv")
        serial = next_serial(record_path)
        count = 0
        with src.open(newline="", encoding="utf-8-sig") as fin, \
                out.open("w", newline="", encoding="utf-8-sig") as fout:
            reader = csv.DictReader(fin)
            if not reader.fieldnames:
                sys.exit("输入 CSV 为空")
            buyer_col = "buyer" if "buyer" in reader.fieldnames else reader.fieldnames[0]
            writer = csv.writer(fout)
            writer.writerow(["buyer", "serial", "code"])
            for row in reader:
                buyer = (row.get(buyer_col) or "").strip()
                if not buyer:
                    continue
                code = make_code(key, TIER_STANDARD, days, serial)
                ok, _ = verify_code(code, key)
                if not ok:
                    sys.exit(f"第 {count + 1} 条自检失败，已中止")
                append_record(record_path, serial, code, days, buyer)
                writer.writerow([buyer, serial, code])
                serial += 1
                count += 1
        print(f"✓ 已签发 {count} 个激活码 → {out}")
        print(f"  签发记录：{record_path}")
        return 0

    if args.cmd == "verify":
        ok, desc = verify_code(args.code, key)
        print(("✓ 有效：" if ok else "✗ 无效：") + desc)
        return 0 if ok else 1

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
