#!/usr/bin/env python3
"""
把内置知识包重新打包到 `kb/` 发布目录。
=============================================

`kb/` 是数据源**直接读取**的目录（仓库里提交着，jsDelivr / GitHub Pages
都从它取文件）。所以：

    改动了 app/src/main/assets/knowledge/ 里的内容
        ↓  必须重新生成 kb/，否则用户拉到的还是旧索引
    kb/ 更新 → git push → 用户点「检查更新」看到新内容

只改名单的话不必手动跑这个脚本 —— 签发支持者码时会自动调用它。

用法：
    python3 tools/sync_kb.py
"""

from __future__ import annotations

import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PROJECT = HERE.parent
SRC = PROJECT / "app/src/main/assets/knowledge"
OUT = PROJECT / "kb"


def sync(verbose: bool = True) -> tuple[bool, str]:
    """
    重新打包。返回 (是否成功, 给用户看的一句话)。

    做成函数是为了能被签发脚本直接调用 —— 那边需要在「用户同意留名」
    之后顺手把 kb/ 也刷新掉，而不是让用户记得再跑一个脚本。
    """
    if not SRC.is_dir():
        return False, f"找不到知识包源目录：{SRC}"

    proc = subprocess.run(
        [sys.executable, str(HERE / "build_knowledge_packs.py"),
         "--src", str(SRC), "--out", str(OUT)],
        capture_output=True, text=True,
        encoding="utf-8", errors="replace",
    )

    if proc.returncode != 0:
        detail = (proc.stderr or proc.stdout or "").strip().splitlines()
        tail = detail[-1] if detail else "未知错误"
        return False, f"打包失败：{tail}"

    # 从脚本输出里抓「完成：8 个知识包，共 456 条内容」这一行
    summary = next(
        (ln.strip() for ln in (proc.stdout or "").splitlines() if ln.startswith("完成：")),
        "打包完成",
    )
    if verbose:
        print(f"  {summary}")
        print(f"  产物目录：{OUT}")
    return True, summary


def main() -> int:
    print("=" * 58)
    print("  中医工厂 · 重新生成知识库发布目录")
    print("=" * 58)
    print()
    print(f"源目录：{SRC}")
    print(f"输出到：{OUT}")
    print()

    ok, message = sync()
    print(("✓ " if ok else "✗ ") + message)

    if not ok:
        print()
        print("没有生成成功，请照上面的提示排查。")
        return 1

    print()
    print("接下来：把 kb/ 目录连同其他改动一起推送到 GitHub。")
    print("推到 main 分支后，用户打开 App → 知识库 → 检查更新 即可看到。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
