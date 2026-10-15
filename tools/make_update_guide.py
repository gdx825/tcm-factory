#!/usr/bin/env python3
"""
为每次版本更新生成一份「操作单」。

为什么要有这个脚本
------------------
操作单里有大量**手写容易错、而且错了很难发现**的数字：

  · 压缩包大小（字节 ↔ Windows 的 1024 进制显示，曾经混淆过一次）
  · SHA-256 校验值（32 字节的十六进制，抄错一个字符就失去校验意义）
  · 本次要推送几个提交、推送后仓库总共几个提交
  · APK 与工程的版本号是否一致

这些全部可以从工程与压缩包本身**算出来**，没有必要手写。脚本只负责
把这些事实提取并填进模板；「本次改了什么」这类需要人来概括的部分，
由 --notes 传入（不给则用 git log 自动生成草稿）。

用法
----
    python3 tools/make_update_guide.py \
        --zip ../2-上传用-压缩包/TcmFactory-上传包-v1.5.3.zip \
        --out ../4-文档/更新到v1.5.3-操作单.md \
        --since 89b7f8b \
        --notes ../4-文档/v1.5.3-发布说明.md

参数
----
--project   工程根目录，默认为脚本所在目录的上一级
--zip       上传用压缩包的路径（用于计算大小与校验值）
--out       操作单输出路径，默认为 <文档目录>/更新到v<版本>-操作单.md
--since     上次发布时的提交号（用于统计本次要推送的提交）；给 latest-tag 则自动取最近 tag
--notes     发布说明文件（Markdown）；不给则用 git log 生成草稿
--apk       APK 相对工程的路径，默认自动探测 apk/ 目录下唯一的 .apk

设计约束：只用 Python 标准库，任何人 clone 下来都能直接跑。
"""

from __future__ import annotations

import argparse
import hashlib
import os
import re
import subprocess
import sys
from datetime import datetime
from pathlib import Path

MB = 1024 * 1024


# ----------------------------------------------------------------------
#  基础工具
# ----------------------------------------------------------------------

def run_git(project: Path, *args: str) -> str:
    """执行 git 命令，失败时返回空串而不是抛异常（缺 git 时脚本仍能出骨架）。"""
    try:
        out = subprocess.run(
            ["git", "-C", str(project), *args],
            capture_output=True, text=True, timeout=30, check=False,
        )
        return out.stdout.strip() if out.returncode == 0 else ""
    except (OSError, subprocess.SubprocessError):
        return ""


def sha256_of(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def human_size(n: int) -> str:
    """按 Windows 资源管理器的口径（1024 进制）显示，避免与十进制混淆。"""
    return f"{n / MB:.1f} MB"


# ----------------------------------------------------------------------
#  从工程里读事实
# ----------------------------------------------------------------------

def read_version(project: Path) -> tuple[int, str]:
    gradle = project / "app" / "build.gradle.kts"
    if not gradle.exists():
        return 0, "?"
    text = gradle.read_text(encoding="utf-8")
    code = re.search(r"versionCode\s*=\s*(\d+)", text)
    name = re.search(r'versionName\s*=\s*"([^"]+)"', text)
    return (int(code.group(1)) if code else 0,
            name.group(1) if name else "?")


def read_package_name(project: Path) -> str:
    gradle = project / "app" / "build.gradle.kts"
    if not gradle.exists():
        return "?"
    m = re.search(r'applicationId\s*=\s*"([^"]+)"', gradle.read_text(encoding="utf-8"))
    return m.group(1) if m else "?"


def find_apk(project: Path, explicit: str | None) -> Path | None:
    if explicit:
        p = (project / explicit) if not os.path.isabs(explicit) else Path(explicit)
        return p if p.exists() else None
    apk_dir = project / "apk"
    if not apk_dir.is_dir():
        return None
    apks = sorted(apk_dir.glob("*.apk"))
    # 排除 debug 包，优先正式版
    release = [p for p in apks if "debug" not in p.name.lower()]
    return (release or apks or [None])[0]


def commits_since(project: Path, since: str | None) -> tuple[list[str], int]:
    """返回 (本次要推送的提交列表, 数量)。"""
    if not since:
        # 没给基准点时，退化为「最近 3 条」，并提示需要人工确认
        log = run_git(project, "log", "--oneline", "-3")
        lines = log.splitlines()
        return lines, len(lines)

    if since == "latest-tag":
        since = run_git(project, "describe", "--tags", "--abbrev=0") or ""
        if not since:
            return [], 0

    log = run_git(project, "log", f"{since}..HEAD", "--oneline")
    lines = [ln for ln in log.splitlines() if ln.strip()]
    return lines, len(lines)


def remote_url(project: Path) -> str:
    return run_git(project, "remote", "get-url", "origin") or "(未配置)"


def repo_slug(url: str) -> str:
    """从 git 地址提取 owner/repo。"""
    m = re.search(r"github\.com[:/]+([^/]+)/([^/\s]+?)(?:\.git)?$", url.strip())
    return f"{m.group(1)}/{m.group(2)}" if m else "owner/repo"


def load_notes(path: str | None, commits: list[str], version: str) -> str:
    if path:
        p = Path(path)
        if p.exists():
            return p.read_text(encoding="utf-8").rstrip()
        print(f"  ⚠️  --notes 指定的文件不存在：{path}，改用 git log 草稿", file=sys.stderr)

    # 草稿：把提交信息整理成列表，交由人工润色
    if not commits:
        return f"## 中医工厂 v{version}\n\n（本次改动说明待补充）\n"
    body = "\n".join(f"- {_strip_hash(ln)}" for ln in commits)
    return f"## 中医工厂 v{version}\n\n### 本次改动\n\n{body}\n\n> 以上为 git 提交信息草稿，请润色为面向用户的表述后再发布。\n"


def _strip_hash(line: str) -> str:
    """去掉 `abc1234 ` 前缀。"""
    return re.sub(r"^[0-9a-f]{7,}\s+", "", line).strip()


# ----------------------------------------------------------------------
#  生成操作单
# ----------------------------------------------------------------------

def build_guide(
    *,
    version: str,
    version_code: int,
    pkg: str,
    zip_path: Path | None,
    apk_path: Path | None,
    commits: list[str],
    since: str | None,
    remote: str,
    notes: str,
    head: str,
) -> str:
    slug = repo_slug(remote)

    # 注意：这段嵌在有序列表项里，每一行都要带同样的缩进，
    # 否则 Markdown 会把表格拆成「首行在列表内、其余行在外面」的碎片。
    IND = "   "
    zip_line = f"{IND}（未找到压缩包）"
    if zip_path and zip_path.exists():
        size = zip_path.stat().st_size
        zip_line = (
            f"{IND}| 文件大小 | **{human_size(size)}**（{size:,} 字节）|\n"
            f"{IND}| SHA-256 | `{sha256_of(zip_path)}` |"
        )

    apk_lines = "（未找到 APK）"
    if apk_path and apk_path.exists():
        size = apk_path.stat().st_size
        apk_lines = (
            f"| 文件名 | `{apk_path.name}` |\n"
            f"| 大小 | {human_size(size)} |\n"
            f"| SHA-256 | `{sha256_of(apk_path)}` |"
        )

    n_new = len(commits)
    commit_list = "\n".join(f"| `{_hash_of(c)}` | {_strip_hash(c)} |" for c in commits) \
        or "| — | （未能自动识别，请人工确认）|"

    since_hint = since or "（未指定基准点 —— 请填入上次发布时的提交号，以便准确统计）"

    return f"""# 更新到 v{version} · 操作单

> 本文档由 `tools/make_update_guide.py` 生成于 {datetime.now():%Y-%m-%d %H:%M}。
> 其中大小、校验值、提交数均为**实际计算所得**，可直接引用。
> 全程约 10 分钟。

---

## 本次更新概览

| 项 | 值 |
|---|---|
| 版本号 | **{version}**（versionCode {version_code}）|
| 包名 | `{pkg}` |
| 需推送提交数 | **{n_new} 个** |
| 基准提交 | `{since_hint}` |
| 推送后 HEAD | `{head}` |
| 仓库地址 | {remote} |

### 本次要推送的提交

| 提交 | 说明 |
|---|---|
{commit_list}

---

## 第一步：下载并替换工程目录

1. 下载 **`{zip_path.name if zip_path else "上传包"}`**

{zip_line}

2. ⚠️ **把上次解压出来的 `TcmFactory` 文件夹整个删掉，再解压本包**

   > **为什么必须删、不能覆盖解压**：若本次新增或删除了文件，覆盖解压不会删除旧文件，
   > 残留下来会让推送时多出（或少掉）文件。

3. 解压新的压缩包到同一个位置

---

## 第二步：推送到 GitHub

1. 打开 **GitHub Desktop**
2. 菜单 **File → Add local repository…** → 选中新解压的 `TcmFactory` 文件夹

   > 若提示 "This directory does not appear to be a Git repository"，说明选错了层级 ——
   > 要选**里面直接能看到 `.git`、`app`、`docs` 的那一层**。

3. 右上角应显示 **`Push origin`**，旁边有 **`{n_new} commits ahead`** → **点它**
4. 等进度条走完（约 1–3 分钟）

**验证**：打开 https://github.com/{slug} ，
提交数应比推送前多 **{n_new} 个**。

### 如果右上角显示的是 `Publish branch` 而不是 `Push origin`

先点一次 **`Fetch origin`**，等它跑完再看。

仍然显示 `Publish branch` 也可以**直接点** —— 远程仓库和 `main` 分支都是现成的，
它只会把你本地多出的提交推上去，不会新建仓库、也不会覆盖任何东西。

---

## 第三步：发布新 Release

打开（复制到浏览器）：

```
https://github.com/{slug}/releases/new
```

### ① 顶部三个输入框

| 位置 | 填什么 |
|---|---|
| **Choose a tag** | 输入 `v{version}` → 下方弹出 **Create new tag: v{version} on publish** → **点它** |
| **Target** | 保持 `main` 不动 |
| **Release title** | `中医工厂 v{version}` |

### ② Describe this release

下面这段**整段复制**（如需修改请直接改这一段，不要改上面的命令）：

```markdown
{notes}
```

### ③ 拖入 APK

页面往下拉，找到 **Attach binaries by dropping them here**，把本地这个文件拖进去：

```
TcmFactory\\apk\\{apk_path.name if apk_path else "tcm-factory-vX.Y.Z.apk"}
```

{apk_lines}

### ④ 点最下面的 **Publish release**

---

## 发布后验证

| 检查 | 期望结果 |
|---|---|
| 仓库提交数 | 比推送前多 {n_new} 个 |
| README 的加速链接 | 应指向 `{apk_path.name if apk_path else "本次 APK 文件名"}` |
| Releases 页面 | 有 `v{version}`，附件大小与上表一致 |
| 手机安装 | 覆盖安装后功能正常 |

> jsDelivr 对新内容有缓存延迟，加速链接可能要十几分钟到几小时才生效，
> 属正常现象。Release 里的 APK 下载不受影响，随时可用。

---

## 以后每次更新的标准流程

1. **改代码** → 构建 APK、更新文档、打包上传包
2. **生成操作单**：
   ```bash
   python3 tools/make_update_guide.py \\
       --zip <压缩包路径> \\
       --out <操作单输出路径> \\
       --since <上次发布时的提交号>
   ```
3. **下载新包** → 删旧的工程目录 → 解压
4. **GitHub Desktop** → Add local repository → **Push origin**
5. **发新 Release** → tag 用新版本号 → 拖入新 APK

> ⚠️ **签名密钥永远不变。** 每次发版都用同一个 `.jks`，
> 否则老用户无法覆盖安装（只能卸载重装，学习进度会丢）。
"""


def _hash_of(line: str) -> str:
    m = re.match(r"^([0-9a-f]{7,})", line)
    return m.group(1) if m else "—"


# ----------------------------------------------------------------------
#  入口
# ----------------------------------------------------------------------

def main() -> int:
    default_project = Path(__file__).resolve().parent.parent
    ap = argparse.ArgumentParser(description="生成版本更新操作单")
    ap.add_argument("--project", default=str(default_project), help="工程根目录")
    ap.add_argument("--zip", dest="zip_path", default=None, help="上传用压缩包路径")
    ap.add_argument("--out", default=None, help="操作单输出路径")
    ap.add_argument("--since", default=None,
                    help="上次发布时的提交号；或填 latest-tag 自动取最近 tag")
    ap.add_argument("--notes", default=None, help="发布说明 Markdown 文件")
    ap.add_argument("--apk", default=None, help="APK 相对工程路径（默认自动探测 apk/ 目录）")
    args = ap.parse_args()

    project = Path(args.project).resolve()
    if not (project / "app" / "build.gradle.kts").exists():
        print(f"✗ 未在 {project} 找到 app/build.gradle.kts，请用 --project 指定工程根目录",
              file=sys.stderr)
        return 1

    version_code, version = read_version(project)
    pkg = read_package_name(project)
    apk = find_apk(project, args.apk)
    zip_path = Path(args.zip_path).resolve() if args.zip_path else None
    commits, _ = commits_since(project, args.since)
    remote = remote_url(project)
    head = run_git(project, "rev-parse", "--short", "HEAD") or "?"
    notes = load_notes(args.notes, commits, version)

    text = build_guide(
        version=version, version_code=version_code, pkg=pkg,
        zip_path=zip_path, apk_path=apk, commits=commits, since=args.since,
        remote=remote, notes=notes, head=head,
    )

    out = Path(args.out) if args.out else (project.parent / f"更新到v{version}-操作单.md")
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(text, encoding="utf-8")

    print(f"✓ 操作单已生成：{out}")
    print(f"  版本      ：{version}（versionCode {version_code}）")
    print(f"  待推送提交：{len(commits)} 个")
    if zip_path and zip_path.exists():
        print(f"  压缩包    ：{human_size(zip_path.stat().st_size)}")
    if apk and apk.exists():
        print(f"  APK       ：{apk.name}  {human_size(apk.stat().st_size)}")
    print()
    print("  请人工复核：「发布说明」一段是否为面向用户的表述（若是自动草稿，需润色）")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
