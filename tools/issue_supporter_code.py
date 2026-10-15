#!/usr/bin/env python3
"""
支持者码签发 · 交互版
=====================

给不熟悉命令行的自己用。双击工程根目录下的「签发支持者码.bat」即可运行。

流程：
    1. 问你是谁打赏的（昵称）与备注（金额、渠道等，随便写）
    2. 生成一个支持者码
    3. 复制到剪贴板，你直接粘到聊天窗口发给他
    4. 顺带问一句：他是否同意公开留名？同意就写进内置名单包

和 make_license.py 的关系：本脚本底层调用的就是它，只是把命令行参数
换成了一问一答。两者生成的码完全等价，签发记录也写同一份 CSV。
"""

from __future__ import annotations

import argparse
import json
import subprocess
import sys
from datetime import datetime
from pathlib import Path

HERE = Path(__file__).resolve().parent          # .../TcmFactory/tools
PROJECT = HERE.parent                           # .../TcmFactory
ASSETS_PACK = PROJECT / "app/src/main/assets/knowledge/tcm.supporters.json"
KEY_HINT_FILE = HERE / ".keypath"               # 记住私钥位置，避免每次重输

sys.path.insert(0, str(HERE))


def find_private_key() -> Path | None:
    """按「越不可能被误提交的越优先」的顺序找私钥。"""
    import os

    env = os.environ.get("TCM_LICENSE_KEY")
    candidates = [Path(env)] if env else []
    candidates += [
        HERE / "激活码私钥.pem",
        PROJECT / "激活码私钥.pem",
        PROJECT / "5-私密-切勿上传/激活码私钥.pem",
        PROJECT.parent / "5-私密-切勿上传/激活码私钥.pem",
        PROJECT.parent / "私密/激活码私钥.pem",
    ]
    if KEY_HINT_FILE.exists():
        remembered = KEY_HINT_FILE.read_text(encoding="utf-8").strip()
        if remembered:
            candidates.insert(0, Path(remembered))

    for path in candidates:
        try:
            if path.is_file():
                return path
        except OSError:
            continue
    return None


def ask_key_path() -> Path | None:
    print("没有自动找到私钥（激活码私钥.pem）。")
    print("它通常和 tcm-release.jks 放在同一个私密文件夹里。")
    print()
    while True:
        raw = input("请把私钥文件的完整路径粘进来（直接回车放弃）：").strip().strip('"')
        if not raw:
            return None
        path = Path(raw)
        if path.is_file():
            KEY_HINT_FILE.write_text(str(path), encoding="utf-8")
            print(f"已记住这个位置，下次不用再输了。")
            return path
        print(f"这个路径下没有文件：{path}")
        print()


def copy_to_clipboard(text: str) -> bool:
    """失败也不影响主流程 —— 码还在屏幕上，手抄或手动选中复制都行。"""
    commands = [
        (["clip"], "utf-16le"),                              # Windows
        (["pbcopy"], "utf-8"),                               # macOS
        (["xclip", "-selection", "clipboard"], "utf-8"),     # Linux
        (["xsel", "--clipboard", "--input"], "utf-8"),
    ]
    for cmd, encoding in commands:
        try:
            proc = subprocess.run(
                cmd,
                input=text.encode(encoding),
                timeout=5,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,   # 缺少的剪贴板工具会往 stderr 抱怨，不必给用户看
            )
            if proc.returncode == 0:
                return True
        except (FileNotFoundError, OSError, subprocess.SubprocessError):
            continue
    return False


def add_to_list(name: str, message: str | None, serial: int, date: str) -> str:
    """
    把支持者写进内置名单包。

    返回一句人类可读的结果说明。写失败不抛异常 —— 码已经发出去了，
    名单是附加动作，不该因为一个 JSON 写不进去就中断。
    """
    try:
        pack = json.loads(ASSETS_PACK.read_text(encoding="utf-8"))
    except Exception as exc:
        return f"读取名单包失败，请手动添加：{exc}"

    items = pack.setdefault("items", [])

    # 同一序号重复添加时覆盖，避免手抖跑两遍写出两条一样的
    items = [it for it in items if str(it.get("serial")) != str(serial)]
    items.insert(0, {
        "externalId": f"supporter-{serial}",
        "name": name,
        "message": message or None,
        "serial": serial,
        "date": date,
    })
    pack["items"] = items

    # 只在内容真变了才抬版本号 —— 否则客户端不会下载
    old = str(pack.get("version", "1.0.0"))
    parts = old.split(".")
    try:
        parts[-1] = str(int(parts[-1]) + 1)
    except ValueError:
        parts.append("1")
    pack["version"] = ".".join(parts)

    try:
        ASSETS_PACK.write_text(
            json.dumps(pack, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )
    except Exception as exc:
        return f"写入名单包失败，请手动添加：{exc}"

    return (f"已写入名单包（版本 {old} → {pack['version']}，现有 {len(items)} 人）。")


def main() -> int:
    try:
        from make_license import (
            EPOCH_DAY, VALID_FOREVER, append_record, load_private_key,
            make_code, next_serial, verify_code, TIER_STANDARD,
        )
    except (ImportError, SystemExit) as exc:
        # make_license 在缺 cryptography 时会 sys.exit(提示语)，也一并接住
        detail = getattr(exc, "code", None) or exc
        print("无法载入签发模块 make_license.py。")
        print(f"（{detail}）")
        print()
        print("如果是缺少 cryptography，请在命令行执行：")
        print("    pip install cryptography")
        print()
        print("如果提示找不到 make_license.py，请确认它和本脚本在同一个 tools 目录下。")
        return 1

    print("=" * 58)
    print("  中医工厂 · 支持者码签发")
    print("=" * 58)
    print()

    key_path = find_private_key()
    if key_path is None:
        key_path = ask_key_path()
        if key_path is None:
            print("没有私钥就无法签发，已退出。")
            return 1
    print(f"私钥：{key_path}")
    print()

    try:
        key = load_private_key(key_path)
    except SystemExit as exc:
        # load_private_key 找不到文件时会 sys.exit(提示语)，这里接住它，
        # 免得整个窗口一闪而过 —— 双击运行时用户根本来不及看清。
        print(exc.code if exc.code else "私钥不可用。")
        return 1
    except Exception as exc:
        print(f"私钥读取失败：{exc}")
        return 1

    record_path = HERE / "签发记录.csv"

    while True:
        print("-" * 58)
        name = input("打赏者昵称（必填，直接回车则退出）：").strip()
        if not name:
            print("已退出。")
            break

        note = input("备注（金额 / 渠道 / 时间，随便写，可留空）：").strip()

        serial = next_serial(record_path)
        code = make_code(key, TIER_STANDARD, VALID_FOREVER, serial)
        ok, desc = verify_code(code, key)
        if not ok:
            print(f"生成失败，自检未通过：{desc}")
            print("这次没有签发成功，请不要把这个码发给对方。")
            continue

        append_record(record_path, serial, code, VALID_FOREVER, f"{name} {note}".strip())

        print()
        print("支持者码（把整段发给对方）：")
        print()
        print("    " + code)
        print()
        if copy_to_clipboard(code):
            print("↑ 已复制到剪贴板，直接粘贴发送即可。")
        else:
            print("↑ 未能自动复制，请选中上面这行手动复制。")
        print()
        print(f"  {desc}")
        print(f"  序号 {serial} · 已记入 {record_path.name}")
        print()
        print("对方的使用方法：设置 → 支持开发 → 输入支持者码 → 徽标点亮。")
        print("（徽标只是致谢，不解锁任何功能，这一点对谁都一样。）")
        print()

        answer = input("他是否同意公开留名在「支持者名单」里？(y/N)：").strip().lower()
        if answer == "y":
            message = input("想留一句话吗？（可留空）：").strip()
            date = datetime.now().strftime("%Y-%m-%d")
            print("  " + add_to_list(name, message, serial, date).replace("\n", "\n  "))
            # 名单变了就必须重新打包发布目录 —— 否则推上去的还是旧索引，
            # 用户点「检查更新」看不到新名字，还得再回来手动跑一次打包。
            print("  正在同步发布目录 kb/ ...")
            try:
                from sync_kb import sync
                ok, msg = sync(verbose=False)
                print("  " + ("✓ " if ok else "✗ ") + msg)
                if not ok:
                    print("    可稍后双击「发布知识库更新.bat」重试。")
            except Exception as exc:
                print(f"  ✗ 同步失败（{exc}），可稍后双击「发布知识库更新.bat」重试。")
        else:
            print("  未加入名单。他改主意了随时可以再来加。")

        print()
        again = input("还要再签发一个吗？(y/N)：").strip().lower()
        if again != "y":
            break
        print()

    print()
    print("全部完成。签发记录在 tools/签发记录.csv（这个文件不要上传）。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
