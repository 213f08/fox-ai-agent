# -*- coding: utf-8 -*-
"""
统计本周客服咨询量
数据源: tmp/chat-memory/*.kryo （每个文件 = 一次会话/咨询，文件名 = conversationId）
判定依据: 文件的最后修改时间(LastWriteTime)落在"本周(周一~周日)"内即计入
"""
import os
import glob
from datetime import datetime, timedelta

DATA_DIR = os.path.join("tmp", "chat-memory")


def week_range(today=None):
    """返回本周的 (周一 00:00, 下周一 00:00) 区间"""
    today = today or datetime.now()
    monday = (today - timedelta(days=today.weekday())).replace(
        hour=0, minute=0, second=0, microsecond=0
    )
    next_monday = monday + timedelta(days=7)
    return monday, next_monday


def main():
    files = glob.glob(os.path.join(DATA_DIR, "*.kryo"))
    if not files:
        print("[!] 未找到任何会话文件,目录:", DATA_DIR)
        return

    start, end = week_range()
    total_all = len(files)
    in_week = []      # (mtime, filename)
    out_week = []

    for f in files:
        mtime = datetime.fromtimestamp(os.path.getmtime(f))
        rec = (mtime, os.path.basename(f))
        if start <= mtime < end:
            in_week.append(rec)
        else:
            out_week.append(rec)

    in_week.sort(reverse=True)   # 最近的排前面

    print("=" * 56)
    print("本周客服咨询量统计")
    print("统计周期 : {} ~ {}".format(
        start.strftime("%Y-%m-%d %a"),
        (end - timedelta(seconds=1)).strftime("%Y-%m-%d %a")))
    print("生成时间 :", datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    print("-" * 56)
    print("本周咨询量 : {} 次".format(len(in_week)))
    print("历史总咨询 : {} 次".format(total_all))
    print("-" * 56)
    print("本周明细:")
    if in_week:
        for i, (mt, name) in enumerate(in_week, 1):
            print("  {:>2}. {:<42} {}".format(i, name, mt.strftime("%m-%d %H:%M")))
    else:
        print("  (无)")
    print("=" * 56)

    # 顺便给个按天的分布
    by_day = {}
    for mt, _ in in_week:
        d = mt.strftime("%Y-%m-%d")
        by_day[d] = by_day.get(d, 0) + 1
    if by_day:
        print("按天分布:")
        for d in sorted(by_day):
            print("  {} : {} 次".format(d, by_day[d]))


if __name__ == "__main__":
    main()
