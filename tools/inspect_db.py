# -*- coding: utf-8 -*-
"""看看拉下来的两个库里到底有什么表。"""
import os
import sqlite3

D = r"D:\My Projects\_archive\2026-11-23"
for f in sorted(os.listdir(D)):
    p = os.path.join(D, f)
    size = os.path.getsize(p) // 1024
    head = open(p, "rb").read(16)
    print(f"{f}  {size} KB  头部={head!r}")
    if f.endswith(".db"):
        try:
            c = sqlite3.connect(p)
            names = [r[0] for r in c.execute("SELECT name FROM sqlite_master WHERE type='table'")]
            uv = c.execute("PRAGMA user_version").fetchone()[0]
            print("   user_version =", uv)
            print("   tables =", names)
            for n in names:
                cnt = c.execute(f"SELECT COUNT(*) FROM {n}").fetchone()[0]
                print(f"      {n}: {cnt} 行")
            c.close()
        except Exception as e:
            print("   ERR", e)
