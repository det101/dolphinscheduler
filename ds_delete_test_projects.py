#!/usr/bin/env python3
import requests
import json
import warnings
warnings.filterwarnings("ignore")

BASE = "http://127.0.0.1:12345/dolphinscheduler"

# 1. Login
r = requests.post(f"{BASE}/login", data={"userName": "admin", "userPassword": "admin123"})
data = r.json()
print("Login:", data["msg"])
if data["code"] != 0:
    print("Login failed!")
    exit(1)

session_id = data["data"]["sessionId"]
headers = {"sessionId": session_id}
cookies = {"sessionId": session_id}

# 2. List all projects
r = requests.get(f"{BASE}/projects", headers=headers, cookies=cookies, params={"pageSize": 100, "pageNo": 1, "searchVal": ""})
proj_data = r.json()
print("List projects response code:", proj_data.get("code"))

if proj_data.get("code") != 0:
    # Try with token header
    headers2 = {"token": session_id}
    r = requests.get(f"{BASE}/projects", headers=headers2, params={"pageSize": 100, "pageNo": 1, "searchVal": ""})
    proj_data = r.json()
    print("List projects (token) response code:", proj_data.get("code"))

total_list = proj_data.get("data", {}).get("totalList", [])
print(f"Total projects found: {len(total_list)}")

# 3. Find test* projects
test_projects = [p for p in total_list if p.get("name", "").startswith("test")]
print(f"Projects starting with 'test': {len(test_projects)}")
for p in test_projects:
    print(f"  - name={p['name']}, code={p['code']}")

# 4. Delete them
for p in test_projects:
    code = p["code"]
    name = p["name"]
    r = requests.delete(f"{BASE}/projects/{code}", headers=headers, cookies=cookies)
    result = r.json()
    print(f"Delete '{name}' (code={code}): code={result.get('code')}, msg={result.get('msg')}")

if not test_projects:
    print("No test* projects to delete.")
else:
    print(f"Done. Deleted {len(test_projects)} project(s).")
