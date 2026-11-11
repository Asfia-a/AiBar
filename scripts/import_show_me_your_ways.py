# -*- coding: utf-8 -*-
"""把本地「示我周行」归档导入为 AiBar 完整世界树数据（非占位简写）。"""
import json
import re
from collections import OrderedDict
from pathlib import Path

SRC = Path(r"E:\Ai\deepseekharness\workplace\matrees\示我周行")
OUT = Path(r"E:\Myproject\AiBar\ai-tavern-web\src\data\showMeYourWaysWorldTree.data.json")

INTRO = (
    "【埃尼阿克工作室旗下世界观】\n"
    "您好，朋友，战争结束了。我？我是来自邮政公司的信使，特地来通知您。\n"
    "是的，我不是军人，也不是医生，更不是雇佣兵或间谍，我们是不周山邮政公司的职员，是合法的武装信使。\n"
    "对，很遗憾，所多玛·新尼尼微女士去世了，尽管公司的创办者去世，我们仍会坚持她的理想——"
    "不、不，她的去世与国际政治无关，我们更不是间谍组织；我们已经得到和平安全理事会的认可，是国际上维护和平的中坚力量。\n"
    "是的，我们面对的是一个千疮百孔的世界，但是不用担心，我们会尽最大努力去遏制暴力的发生……为了什么？为了爱与和平，先生。\n"
    "不开玩笑了，我的名字是郁春，他叫做开白，有困难记得及时联系我们。"
)

DATA_VERSION = 3

seq = [0]


def nid(prefix="n"):
    seq[0] += 1
    return f"smyw-{prefix}-{seq[0]}"


def clean_title(t):
    t = re.sub(r"\*+", "", t or "").strip()
    t = re.sub(r"\s+", " ", t)
    return t


def summarize(text, n=160):
    s = re.sub(r"\s+", " ", (text or "").strip())
    s = re.sub(r"^#+\s*", "", s)
    if len(s) <= n:
        return s
    return s[: n - 1] + "…"


def scrub_md(text: str) -> str:
    """去掉抓取页里的平台噪声，保留正文。"""
    if not text:
        return ""
    text = re.sub(r"^>.*$", "", text, flags=re.M)
    text = re.sub(r"!\[.*?\]\(.*?\)", "", text)
    text = text.replace("登录后我更懂你～", "")
    # 封面元信息重复块
    for junk in (
        "世界类型",
        "公开状态",
        "世界状态",
        "内容保护",
        "原创",
        "已公开",
        "正常",
        "开启",
    ):
        text = re.sub(rf"(?m)^{re.escape(junk)}\s*$", "", text)
    text = re.sub(r"\n{3,}", "\n\n", text).strip()
    return text


def best_body(*parts: str) -> str:
    """取最长的一段非空正文，避免短 md 覆盖更长摘要。"""
    cleaned = [scrub_md(p) for p in parts if p and str(p).strip()]
    if not cleaned:
        return ""
    return max(cleaned, key=len)


def guess_type(title, parent_type=None, kind=None):
    if kind:
        return kind
    t = title or ""
    if any(
        k in t
        for k in (
            "种族",
            "奥斯提亚",
            "奥雷罗",
            "温戈里康",
            "提涅苏",
            "戈里米",
            "曼萨洛尔",
            "卢那",
        )
    ):
        return "race"
    if any(
        k in t
        for k in (
            "泰拉",
            "新科罗纳",
            "阿拉提莱",
            "埃兰沙赫尔",
            "哀律西昂",
            "因提苏由",
            "伊迪亚勒",
            "东天下",
            "伊比利亚",
            "维多利亚",
            "克洛维亚",
            "国家",
            "大地",
        )
    ):
        return "region"
    if "历法" in t:
        return "calendar"
    if any(k in t for k in ("时代", "近代", "古典", "历史", "战争", "会议")):
        return "era"
    if any(k in t for k in ("出身", "人物")) or parent_type == "figure":
        return "figure"
    if any(k in t for k in ("党", "会", "公司", "组织", "军")):
        return "faction"
    if any(k in t for k in ("水铅", "奇迹", "信仰", "巫术", "魂灵")):
        return "lore"
    if parent_type in ("chapter", "world"):
        return "chapter"
    return "lore"


def parse_background(md_text):
    lines = md_text.splitlines()
    root_children = []
    stack = []
    current_buf = []
    started = False

    def flush_content(node, buf):
        text = scrub_md("\n".join(buf))
        if text:
            node["content"] = text
            if not node.get("summary"):
                node["summary"] = summarize(text)

    for line in lines:
        m = re.match(r"^(#{1,3})\s+(.+)$", line)
        if m:
            level = len(m.group(1))
            title = clean_title(m.group(2))
            if title.startswith("示我周行 · 背景") or title in (
                "世界类型",
                "公开状态",
                "世界状态",
                "内容保护",
            ):
                continue
            if not started:
                if title.startswith("示我周行") and level == 2:
                    started = True
                    node = {
                        "id": nid("bg"),
                        "label": "设定集封面",
                        "type": "world",
                        "summary": "《一位博物学家的漫游》示我周行设定集入口。",
                        "content": "",
                        "children": [],
                    }
                    root_children.append(node)
                    stack = [(level, node)]
                    current_buf = []
                    continue
                if (
                    (level == 1 and title == "前言")
                    or title.startswith("魔鬼")
                    or title.startswith("种族")
                    or title.startswith("历史")
                    or title.startswith("大地")
                    or (level == 3 and title in ("世界历法", "世界概念"))
                ):
                    started = True
                else:
                    continue

            if stack:
                flush_content(stack[-1][1], current_buf)
            current_buf = []

            node = {
                "id": nid("bg"),
                "label": title,
                "type": guess_type(title),
                "summary": "",
                "content": "",
                "children": [],
            }
            while stack and stack[-1][0] >= level:
                stack.pop()
            if stack:
                parent = stack[-1][1]
                node["type"] = guess_type(title, parent.get("type"))
                parent["children"].append(node)
            else:
                root_children.append(node)
            stack.append((level, node))
        else:
            if started:
                current_buf.append(line)
    if stack:
        flush_content(stack[-1][1], current_buf)
    return root_children


def join_children_docs(nodes, heading_level=2):
    """把子节点正文拼成父节点可读长文。"""
    blocks = []
    hashes = "#" * heading_level
    for n in nodes or []:
        body = (n.get("content") or "").strip()
        if not body:
            continue
        label = n.get("label") or "未命名"
        # 若正文已自带同名标题则不再重复
        if re.match(rf"^#+\s*{re.escape(label)}\s*$", body.split("\n", 1)[0].strip()):
            blocks.append(body)
        else:
            blocks.append(f"{hashes} {label}\n\n{body}")
    return scrub_md("\n\n---\n\n".join(blocks))


def enrich_parents_with_children(nodes, heading_level=2):
    """自底向上：父节点若过短，用子节点全文填充。"""
    for n in nodes or []:
        kids = n.get("children") or []
        enrich_parents_with_children(kids, min(heading_level + 1, 4))
        joined = join_children_docs(kids, heading_level)
        own = (n.get("content") or "").strip()
        if joined and (len(own) < 80 or own.startswith("章节「") or "按原设定集" in own or "按年份" in own):
            n["content"] = joined if not own or len(own) < 80 else (own + "\n\n---\n\n" + joined)
            if not n.get("summary") or len(n["summary"]) < 40:
                n["summary"] = summarize(n["content"], 180)


def build_definitions(defs):
    groups = OrderedDict()
    for d in defs:
        ch = (d.get("chapter") or "未归类").strip() or "未归类"
        groups.setdefault(ch, []).append(d)

    children = []
    for ch, items in groups.items():
        ch_node = {
            "id": nid("ch"),
            "label": ch,
            "type": guess_type(ch, "chapter", "chapter"),
            "summary": f"{len(items)} 条设定",
            "content": "",
            "children": [],
        }
        for d in items:
            title = clean_title(d.get("title") or "未命名")
            md = (d.get("md") or "").strip()
            desc = (d.get("description") or "").strip()
            content = best_body(md, desc)
            content = re.sub(rf"^#\s*{re.escape(title)}\s*\n+", "", content)
            child_type = (
                "figure"
                if ch.startswith("出身") or "人物" in ch
                else guess_type(title, ch_node["type"])
            )
            if any(
                k in ch
                for k in (
                    "国家",
                    "TERRA",
                    "伊比利亚",
                    "因提苏由",
                    "哀律西昂",
                    "阿拉提莱",
                    "十三州",
                    "玻利维亚",
                    "自由市",
                    "先锋城",
                )
            ):
                if child_type == "lore":
                    child_type = "region"
            ch_node["children"].append(
                {
                    "id": nid("def"),
                    "label": title,
                    "type": child_type,
                    "summary": summarize(desc or content, 160),
                    "content": content or title,
                    "children": [],
                }
            )
        children.append(ch_node)
    return children


def event_year(ev):
    times = ev.get("time") or []
    years = []
    for t in times:
        du = (t or {}).get("dateUnit") or {}
        y = du.get("year")
        if isinstance(y, int) and y > 0:
            years.append(y)
    return min(years) if years else 10**9


def format_time(ev):
    times = ev.get("time") or []
    parts = []
    seen = set()
    for t in times:
        cal = (t or {}).get("calendarTitle") or "公元"
        du = (t or {}).get("dateUnit") or {}
        y, m, d = du.get("year"), du.get("month"), du.get("day")
        if not y:
            continue
        key = (cal, y, m, d)
        if key in seen:
            continue
        seen.add(key)
        if m and d:
            parts.append(f"{cal} {y}-{m:02d}-{d:02d}")
        elif m:
            parts.append(f"{cal} {y}-{m:02d}")
        else:
            parts.append(f"{cal} {y}")
    return "；".join(parts) if parts else "时间未标注"


def build_timeline(events):
    events = sorted(events, key=lambda e: (event_year(e), e.get("title") or ""))
    children = []
    for ev in events:
        title = clean_title(ev.get("title") or "未命名事件")
        md = (ev.get("md") or "").strip()
        desc = (ev.get("description") or "").strip()
        content = best_body(md, desc)
        time_s = format_time(ev)
        body = f"**时间**：{time_s}\n\n{content}".strip()
        children.append(
            {
                "id": nid("ev"),
                "label": title,
                "type": "era",
                "summary": summarize(desc or content, 160),
                "content": body,
                "children": [],
            }
        )
    return children


def strip_background_file(md_text: str) -> str:
    """整篇背景：去掉抓取页头，从正文标题起保留。"""
    text = scrub_md(md_text)
    # 丢掉文件级 H1 说明头
    text = re.sub(r"^#\s*示我周行 · 背景.*?\n+", "", text, count=1, flags=re.S)
    return text.strip()


def strip_definitions_file(md_text: str) -> str:
    """设定集 md：去掉章节索引，只留条目正文。"""
    text = scrub_md(md_text)
    # 索引在「## 章节索引」到第一个真正条目「## xxx」之间（条目通常无「（N 条）」）
    m = re.search(r"^##\s+章节索引\s*$", text, re.M)
    if m:
        rest = text[m.end() :]
        # 跳过索引列表，找到下一块不像「### 章名（N 条）」的 ## 标题
        m2 = re.search(r"^##\s+(?!.+（\d+\s*条）).+$", rest, re.M)
        if m2:
            text = rest[m2.start() :].strip()
        else:
            text = rest.strip()
    text = re.sub(r"^#\s*示我周行 · 设定集.*?\n+", "", text, count=1, flags=re.S)
    return text.strip()


def strip_timeline_file(md_text: str) -> str:
    text = scrub_md(md_text)
    text = re.sub(r"^#\s*示我周行 · 时间线与事件.*?\n+", "", text, count=1, flags=re.S)
    # 去掉事件索引列表，从第一个 ## 事件正文开始
    m = re.search(r"^##\s+事件索引\s*$", text, re.M)
    if m:
        rest = text[m.end() :]
        m2 = re.search(r"^##\s+.+$", rest, re.M)
        if m2:
            text = rest[m2.start() :].strip()
    # 清理 [object Object] 时间噪声
    text = text.replace("[object Object]", "")
    text = re.sub(r"\*时间：.*?·\s*", "*时间：", text)
    return scrub_md(text)


def count_nodes(nodes):
    n = 0
    for x in nodes:
        n += 1 + count_nodes(x.get("children") or [])
    return n


def main():
    defs = json.loads((SRC / "02-设定集.raw.json").read_text(encoding="utf-8"))
    events = json.loads((SRC / "03-时间线与事件.raw.json").read_text(encoding="utf-8"))
    bg_md = (SRC / "01-背景.md").read_text(encoding="utf-8")
    defs_md = (SRC / "02-设定集.md").read_text(encoding="utf-8")
    tl_md = (SRC / "03-时间线与事件.md").read_text(encoding="utf-8")

    bg_nodes = parse_background(bg_md)
    def_nodes = build_definitions(defs)
    tl_nodes = build_timeline(events)

    # 章节 / 背景父节点灌入子节点全文，避免 Overview 只看到一句话占位
    enrich_parents_with_children(bg_nodes, 2)
    enrich_parents_with_children(def_nodes, 2)
    enrich_parents_with_children(tl_nodes, 2)

    bg_full = strip_background_file(bg_md)
    defs_full = strip_definitions_file(defs_md)
    tl_full = strip_timeline_file(tl_md)

    # 若整篇解析失败则退回拼接
    if len(defs_full) < 1000:
        defs_full = join_children_docs(
            [
                {
                    "label": ch["label"],
                    "content": ch.get("content") or join_children_docs(ch.get("children") or []),
                }
                for ch in def_nodes
            ],
            2,
        )
    if len(tl_full) < 500:
        tl_full = join_children_docs(tl_nodes, 2)

    tree = [
        {
            "id": nid("root"),
            "label": "示我周行 Show Me Your Ways",
            "type": "world",
            "summary": "战后信使视角下的原创世界观：《一位博物学家的漫游》设定集。",
            "content": INTRO
            + f"\n\n设定条目 {len(defs)} 条；时间线事件 {len(events)} 条。\n\n"
            + "下方三个分册分别收录完整背景正文、全部设定条目与时间线事件。",
            "children": [
                {
                    "id": nid("sec"),
                    "label": "背景",
                    "type": "chapter",
                    "summary": summarize(bg_full, 180),
                    "content": bg_full,
                    "children": bg_nodes,
                },
                {
                    "id": nid("sec"),
                    "label": "设定集",
                    "type": "chapter",
                    "summary": f"{len(defs)} 条设定，按章节归类；本节点含全部条目正文。",
                    "content": defs_full,
                    "children": def_nodes,
                },
                {
                    "id": nid("sec"),
                    "label": "时间线与事件",
                    "type": "chapter",
                    "summary": f"{len(events)} 条历史事件；本节点含全部事件正文。",
                    "content": tl_full,
                    "children": tl_nodes,
                },
            ],
        }
    ]

    # 再补一层：三大分册若因清洗过短，用子树拼接兜底
    enrich_parents_with_children(tree, 2)

    payload = {
        "meta": {
            "title": "示我周行 Show Me Your Ways",
            "description": "战后信使视角下的原创世界观设定集（背景 + 设定集 + 时间线）。",
            "sourceHint": "完整世界树：背景 / 设定集 / 时间线。",
            "definitionCount": len(defs),
            "eventCount": len(events),
            "dataVersion": DATA_VERSION,
        },
        "tree": tree,
    }

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")
    print("nodes", count_nodes(tree))
    print("bytes", OUT.stat().st_size)
    bg = tree[0]["children"][0]
    st = tree[0]["children"][1]
    tl = tree[0]["children"][2]
    print("背景 content", len(bg["content"]))
    print("设定集 content", len(st["content"]))
    print("时间线 content", len(tl["content"]))
    print("out", OUT)


if __name__ == "__main__":
    main()
