#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
霞鹜文楷（LXGW WenKai）字体子集化。

背景：完整字库约 20MB，直接打进 APK 不可接受。
策略：按 GB2312 全集（6763 汉字 + 标点 + 符号）+ ASCII + 中医古籍专用字
裁剪，预计压到 6–8MB。子集外的生僻字会由 Android 系统字体 fallback 兜底，
不会显示豆腐块。

用法：
    python3 subset_font.py <输入.ttf> <输出.ttf>
"""
import sys
import os

OUT_CHARSET = "/tmp/fonts/charset.txt"


def build_charset() -> str:
    chars = set()

    # 1. GB2312 全集：汉字 + 标点 + 希腊字母 + 俄文字母 + 日文假名 + 制表符
    for hi in range(0xA1, 0xFA):
        for lo in range(0xA1, 0xFF):
            try:
                chars.add(bytes([hi, lo]).decode("gb2312"))
            except Exception:
                pass

    # 2. ASCII 可打印字符
    for c in range(0x20, 0x7F):
        chars.add(chr(c))

    # 3. GB2312 未覆盖但中文排印常用的标点与符号
    chars.update("　·—…“”‘’《》〈〉「」『』【】〔〕〖〗・※→←↑↓★☆○●◎◇◆□■△▲")

    # 4. 中医古籍常见字补充（GB2312 有部分覆盖，其余为生僻字）
    extra = (
        "癥瘕痎疟衄鼽瘛疭瞀膹郁掉眩痿躄喑痱鼾瞤擗淅啬翕濈趺髁骶髂髌腓腘跗跖"
        "髃髎臑腧窬頞颅颧颌颏髭髯髫髹黧皯皲皴瘼瘵痼瘳瘥痧疿疥癬疠痈疽疔疖"
        "瘰疬瘿瘤痞胀瞋嚏哕噫呃嗝呕涎唾浃溲溺溷舄舁臾舛舂斫斬斛斝甑甗瓿甏"
        "芪芎苈苡荠荸荜莪菝菖菥葶蒺蓖蔊蕤薤藁蘖虻蛭蛴蛞蝓蟅蠊蠡衃衄衊衖"
        "觳觫訾譫谵贲贳赭趼跛跗跖踒踠踡蹇蹂躏輈轑辒辘迕迤逡遑遘遽郄郗鄩"
        "酊酢酲酴醪醴醺釡鈹鉏銍鋈錞鍼鎊鏃鐺鑱闃闑阇阛阼陂陔陧隈隳雉雩霈"
        "靥鞲鞴鞅韂顱顴飧飨饕馘驺骠骝骰髑髖髟鬆鬲鬻魃魆魍魉鲠鲠鲭鲠鳜鳝"
    )
    chars.update(extra)

    text = "".join(sorted(chars))
    os.makedirs(os.path.dirname(OUT_CHARSET), exist_ok=True)
    with open(OUT_CHARSET, "w", encoding="utf-8") as f:
        f.write(text)
    return text


def main():
    if len(sys.argv) < 3:
        print("用法: python3 subset_font.py <输入.ttf> <输出.ttf>")
        sys.exit(1)

    src, dst = sys.argv[1], sys.argv[2]
    text = build_charset()
    print(f"[1/3] 字符集构建完成：{len(text)} 个字符 -> {OUT_CHARSET}")

    from fontTools import subset
    from fontTools.ttLib import TTFont

    src_size = os.path.getsize(src) / 1024 / 1024
    print(f"[2/3] 输入字体 {src} ({src_size:.1f} MB)，开始子集化…")

    options = subset.Options()
    options.text = text
    options.layout_features = []      # 中文单字排版不需要 GSUB/GPOS 特性
    options.drop_tables += ["DSIG", "GSUB", "GPOS", "GDEF", "BASE", "LTSH", "PCLT"]
    options.name_IDs = ["*"]
    options.name_legacy = False
    options.notdef_outline = True     # 保留 .notdef，缺字时画空心框而不是空白
    options.recalc_bounds = True
    options.recalc_timestamp = False
    options.desubroutinize = False
    options.hinting = True            # 保留 hinting，小字号下更清晰

    font = TTFont(src)
    subsetter = subset.Subsetter(options=options)
    subsetter.populate(text=text)
    subsetter.subset(font)

    font.flavor = None                # 输出未压缩 TTF（Android res/font 需要）
    font.save(dst)
    font.close()

    dst_size = os.path.getsize(dst) / 1024 / 1024
    print(f"[3/3] 子集化完成：{dst} ({dst_size:.1f} MB)，压缩率 "
          f"{(1 - dst_size / src_size) * 100:.1f}%")

    # 校验：确认关键字符都在
    check = TTFont(dst, lazy=True)
    cmap = check.getBestCmap()
    missing = [c for c in "伤寒论太阳病桂枝汤麻黄脉浮头项强痛而恶寒" if ord(c) not in cmap]
    print(f"      子集字符数 {len(cmap)}，关键字符缺失: {missing if missing else '无'}")


if __name__ == "__main__":
    main()
