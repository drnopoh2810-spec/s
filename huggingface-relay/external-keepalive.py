#!/usr/bin/env python3
"""
═══════════════════════════════════════════════════════════
 External Keep Alive Script
 يعمل من جهازك لإبقاء Hugging Face Space نشط
═══════════════════════════════════════════════════════════
"""

import requests
import time
from datetime import datetime
import sys

# ضع رابط Space بتاعك هنا:
SPACE_URL = "https://YOUR_USERNAME-sms-gateway-relay.hf.space"

# كل كم دقيقة يرسل ping (الافتراضي: 5 دقائق)
PING_INTERVAL = 5 * 60  # seconds


def ping_space():
    """يرسل ping للـ Space"""
    try:
        response = requests.get(f"{SPACE_URL}/health", timeout=10)
        
        if response.status_code == 200:
            data = response.json()
            timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            print(f"[{timestamp}] ✅ Ping successful - Devices: {data.get('devices', 0)}")
            return True
        else:
            print(f"[{timestamp}] ⚠️ Ping failed - Status: {response.status_code}")
            return False
            
    except requests.exceptions.Timeout:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] ⏱️ Timeout")
        return False
        
    except Exception as e:
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        print(f"[{timestamp}] ❌ Error: {e}")
        return False


def main():
    """البرنامج الرئيسي"""
    print("=" * 60)
    print("🚀 External Keep Alive for Hugging Face Space")
    print("=" * 60)
    print(f"Space URL: {SPACE_URL}")
    print(f"Ping interval: {PING_INTERVAL // 60} minutes")
    print("Press Ctrl+C to stop")
    print("=" * 60)
    print()
    
    # التحقق من الاتصال الأولي
    print("Testing initial connection...")
    if ping_space():
        print("✅ Initial connection successful!")
    else:
        print("⚠️ Initial connection failed. Check the URL.")
        sys.exit(1)
    
    print()
    print("Starting keep alive loop...")
    print()
    
    # حلقة Keep Alive
    try:
        while True:
            time.sleep(PING_INTERVAL)
            ping_space()
            
    except KeyboardInterrupt:
        print()
        print("=" * 60)
        print("🛑 Keep alive stopped by user")
        print("=" * 60)
        sys.exit(0)


if __name__ == "__main__":
    main()
