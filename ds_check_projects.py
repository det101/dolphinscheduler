#!/usr/bin/env python3
import requests
BASE = "http://127.0.0.1:12345/dolphinscheduler"
r = requests.post(f"{BASE}/login", data={"userName":"admin","userPassword":"admin123"})
print("Login:", r.status_code, r.text[:200])
data = r.json()
if data.get('code') != 0:
    print("Login failed"); exit(1)
session_id = data['data']['sessionId']
headers = {"sessionId": session_id}
# List projects
r2 = requests.get(f"{BASE}/projects", headers=headers, params={"pageSize": 100, "pageNo": 1, "searchVal": ""})
print("List status:", r2.status_code)
print(r2.text[:1200])
