# -*- coding: utf-8 -*-
"""Release 说明里去掉「为什么做这个 App」（那段已移到 README 顶部），只留版本变化。"""
import os

OUT = r"D:\My Projects\_archive\2026-11-12"

for name in ("release-v0.28.0.md", "release-v0.27.0.md"):
    p = os.path.join(OUT, name)
    s = open(p, encoding="utf-8").read()
    # 砍掉开头到「## 这次改了什么」/「## 它是什么」之前的所有内容
    for marker in ("## 这次改了什么", "## 它是什么"):
        if marker in s:
            keep = s[s.index(marker):]
            # v0.28.0/v0.27.0 都补一行追溯说明
            keep = keep.replace(marker, marker, 1)
            open(p, "w", encoding="utf-8", newline="\n").write(keep)
            print(f"  ✓ {name} 已去掉初心段，剩 {len(keep)} 字符，开头：{keep.splitlines()[0]}")
            break
    else:
        print(f"  ✗ {name} 没找到版本段标记")
