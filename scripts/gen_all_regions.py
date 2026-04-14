#!/usr/bin/env python3
"""
gen_all_regions.py
从 EchoTik API 拉取全球统一品类树（L1/L2/L3），
为所有 10 个 TikTok 地区生成 SQL 文件并直接导入 PostgreSQL。

用法：
    python scripts/gen_all_regions.py
"""

import os
import time
import subprocess
import requests
from requests.auth import HTTPBasicAuth

# ── 配置 ──────────────────────────────────────────────────────────────────────

BASE_URL   = "https://open.echotik.live/api/v3/echotik"
APP_KEY    = os.environ.get("ECHOTIK_APP_KEY",    "260107955437997637")
APP_SECRET = os.environ.get("ECHOTIK_APP_SECRET", "302ad86dd9bd407a9cae1f19b42bc6e7")

CONTAINER = "tiktok-postgres"
DB_USER   = "tiktok_app"
DB_NAME   = "tiktok_selection"

SQL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "sql")

# 全部 10 个地区
REGIONS = [
    ("TH", "泰国",       "02"),
    ("US", "美国",       "04"),
    ("UK", "英国",       "05"),
    ("ID", "印度尼西亚", "03"),
    ("MY", "马来西亚",   "06"),
    ("PH", "菲律宾",     "07"),
    ("VN", "越南",       "08"),
    ("SG", "新加坡",     "09"),
    ("SA", "沙特阿拉伯", "10"),
    ("AE", "阿联酋",     "11"),
]

# ── EchoTik API ───────────────────────────────────────────────────────────────

def fetch(endpoint, params, auth, retry=3):
    url = f"{BASE_URL}/{endpoint}"
    for attempt in range(1, retry + 1):
        try:
            resp = requests.get(url, params=params, auth=auth, timeout=30)
            resp.raise_for_status()
            body = resp.json()
            if body.get("code") != 0:
                print(f"  [WARN] 非0响应: code={body.get('code')}, msg={body.get('message')}")
                return []
            data = body.get("data", [])
            return data if isinstance(data, list) else []
        except Exception as e:
            print(f"  [WARN] 请求失败(attempt {attempt}/{retry}): {e}")
            if attempt < retry:
                time.sleep(1)
    return []


def build_name_map(items):
    return {
        str(item.get("category_id") or item.get("id")): (item.get("category_name") or item.get("name"))
        for item in items
        if (item.get("category_id") or item.get("id")) and (item.get("category_name") or item.get("name"))
    }


def fetch_all_categories(auth):
    """拉取全球统一品类树，返回 [(cid, parent_id, level, name_zh, name_en), ...]"""
    categories = []
    api_calls = 0

    print("[L1] 拉取一级品类...")
    l1_zh = fetch("category/l1", {"language": "zh-CN"}, auth); api_calls += 1
    l1_en = fetch("category/l1", {"language": "en-US"}, auth); api_calls += 1
    en_map = build_name_map(l1_en)
    l1_ids = []
    for item in l1_zh:
        cid = str(item.get("category_id") or item.get("id") or "")
        if cid:
            categories.append((cid, None, 1, item.get("category_name") or "", en_map.get(cid)))
            l1_ids.append(cid)
    print(f"  L1: {len(l1_ids)} 条")

    print("[L2] 拉取二级品类...")
    l2_zh = fetch("category/l2", {"language": "zh-CN"}, auth); api_calls += 1
    l2_en = fetch("category/l2", {"language": "en-US"}, auth); api_calls += 1
    if not l2_zh and l1_ids:
        print("  全量L2为空，按L1逐个拉取...")
        l2_en_list = []
        for l1id in l1_ids:
            l2_zh.extend(fetch("category/l2", {"language": "zh-CN", "parent_id": l1id}, auth)); api_calls += 1
            l2_en_list.extend(fetch("category/l2", {"language": "en-US", "parent_id": l1id}, auth)); api_calls += 1
        l2_en = l2_en_list
    en_map = build_name_map(l2_en)
    l2_ids = []
    for item in l2_zh:
        cid    = str(item.get("category_id") or item.get("id") or "")
        parent = str(item.get("parent_id") or item.get("parent_category_id") or item.get("l1_category_id") or "")
        if cid:
            categories.append((cid, parent or None, 2, item.get("category_name") or "", en_map.get(cid)))
            l2_ids.append(cid)
    print(f"  L2: {len(l2_ids)} 条")

    print("[L3] 拉取三级品类...")
    l3_zh = fetch("category/l3", {"language": "zh-CN"}, auth); api_calls += 1
    l3_en = fetch("category/l3", {"language": "en-US"}, auth); api_calls += 1
    if not l3_zh and l2_ids:
        print("  全量L3为空，按L2逐个拉取...")
        l3_en_list = []
        for l2id in l2_ids:
            l3_zh.extend(fetch("category/l3", {"language": "zh-CN", "parent_id": l2id}, auth)); api_calls += 1
            l3_en_list.extend(fetch("category/l3", {"language": "en-US", "parent_id": l2id}, auth)); api_calls += 1
        l3_en = l3_en_list
    en_map = build_name_map(l3_en)
    l3_count = 0
    for item in l3_zh:
        cid    = str(item.get("category_id") or item.get("id") or "")
        parent = str(item.get("parent_id") or item.get("parent_category_id") or item.get("l2_category_id") or "")
        if cid:
            categories.append((cid, parent or None, 3, item.get("category_name") or "", en_map.get(cid)))
            l3_count += 1
    print(f"  L3: {l3_count} 条")
    print(f"  API 调用共 {api_calls} 次\n")

    return categories

# ── SQL 生成 ──────────────────────────────────────────────────────────────────

def esc(s):
    if s is None:
        return "NULL"
    return "'" + str(s).replace("'", "''") + "'"


def generate_sql(categories, region_code, region_name, seq):
    out_file = os.path.join(SQL_DIR, f"{seq}-echotik_category_{region_code.lower()}.sql")
    with open(out_file, "w", encoding="utf-8") as f:
        f.write(f"-- {region_name}({region_code})商品类目数据 (L1/L2/L3)\n")
        f.write("-- 来源: EchoTik API, 自动生成\n\n")
        f.write(f"DELETE FROM db_cache.echotik_category WHERE region = '{region_code}';\n\n")
        for cid, parent, level, name_zh, name_en in categories:
            pid = "NULL" if not parent or parent == "0" else f"'{parent}'"
            f.write(
                f"INSERT INTO db_cache.echotik_category "
                f"(category_id, region, parent_id, level, name_en, name_zh) VALUES "
                f"({esc(cid)}, '{region_code}', {pid}, {level}, {esc(name_en)}, {esc(name_zh)});\n"
            )
    return out_file

# ── 导入数据库 ────────────────────────────────────────────────────────────────

def import_sql(sql_file):
    with open(sql_file, "r", encoding="utf-8") as f:
        sql_content = f.read()
    result = subprocess.run(
        ["docker", "exec", "-i", CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME],
        input=sql_content,
        capture_output=True,
        text=True,
        encoding="utf-8"
    )
    return result.returncode == 0, result.stderr.strip()


def verify():
    result = subprocess.run(
        ["docker", "exec", CONTAINER, "psql", "-U", DB_USER, "-d", DB_NAME,
         "-c", "SELECT region, COUNT(*) as cnt FROM db_cache.echotik_category GROUP BY region ORDER BY region;"],
        capture_output=True, text=True, encoding="utf-8"
    )
    return result.stdout

# ── 主流程 ────────────────────────────────────────────────────────────────────

def main():
    auth = HTTPBasicAuth(APP_KEY, APP_SECRET)

    print("=" * 50)
    print("步骤 1/3：从 EchoTik API 拉取品类数据")
    print("=" * 50)
    categories = fetch_all_categories(auth)
    print(f"共拉取 {len(categories)} 条品类数据\n")

    print("=" * 50)
    print("步骤 2/3：生成 SQL 文件")
    print("=" * 50)
    sql_files = []
    for region_code, region_name, seq in REGIONS:
        sql_file = generate_sql(categories, region_code, region_name, seq)
        sql_files.append((region_code, region_name, sql_file))
        print(f"✓ 生成 {os.path.basename(sql_file)}")

    print(f"\n共生成 {len(sql_files)} 个文件\n")

    print("=" * 50)
    print("步骤 3/3：导入 PostgreSQL")
    print("=" * 50)
    success = 0
    for region_code, region_name, sql_file in sql_files:
        ok, err = import_sql(sql_file)
        if ok:
            print(f"✓ {region_name}（{region_code}）导入成功")
            success += 1
        else:
            print(f"✗ {region_name}（{region_code}）导入失败")
            if err:
                print(f"  错误：{err[:200]}")

    print(f"\n完成！{success}/{len(sql_files)} 个地区导入成功")
    print("\n── 数据库验证 ──────────────────────────")
    print(verify())


if __name__ == "__main__":
    main()
