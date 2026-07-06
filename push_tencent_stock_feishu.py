#!/usr/bin/env python3
import requests
import warnings
from datetime import datetime

warnings.filterwarnings("ignore")

FEISHU_WEBHOOK = ""

def get_price_line():
    url = "https://query1.finance.yahoo.com/v8/finance/chart/0700.HK?interval=1m&range=1d"
    headers = {"User-Agent": "Mozilla/5.0"}
    r = requests.get(url, headers=headers, timeout=10)
    data = r.json()
    meta = data["chart"]["result"][0]["meta"]
    price = meta["regularMarketPrice"]
    currency = meta["currency"]
    now = datetime.now().strftime("%Y-%m-%d %H:%M")
    return f"0700.HK  - {price} {currency} | {now}"

def push_to_feishu(text: str):
    if not FEISHU_WEBHOOK:
        raise RuntimeError("FEISHU_WEBHOOK is empty. Please set it.")
    payload = {"msg_type": "text", "content": {"text": text}}
    r = requests.post(FEISHU_WEBHOOK, json=payload, timeout=10)
    r.raise_for_status()
    return r.text

if __name__ == "__main__":
    line = get_price_line()
    # Print for logs
    print(line)
    # Push
    try:
        resp = push_to_feishu(line)
        print("pushed")
    except Exception as e:
        print(f"push failed: {e}")
