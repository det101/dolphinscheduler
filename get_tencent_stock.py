#!/usr/bin/env python3
import requests
import warnings
from datetime import datetime

warnings.filterwarnings("ignore")

def get_price():
    try:
        url = "https://query1.finance.yahoo.com/v8/finance/chart/0700.HK?interval=1m&range=1d"
        headers = {'User-Agent': 'Mozilla/5.0'}
        r = requests.get(url, headers=headers, timeout=10)
        data = r.json()
        meta = data['chart']['result'][0]['meta']
        price = meta['regularMarketPrice']
        currency = meta['currency']
        now = datetime.now().strftime("%Y-%m-%d %H:%M")
        return f"0700.HK  - {price} {currency} | {now}"
    except Exception as e:
        return f"获取腾讯股价失败: {e}"

if __name__ == "__main__":
    print(get_price())
