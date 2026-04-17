# SMS Gateway Relay Server

## النشر على Huggingface Spaces

1. اذهب إلى https://huggingface.co/spaces
2. اضغط "Create new Space"
3. اختر:
   - **SDK**: Docker أو Gradio
   - **Space name**: sms-gateway-relay
4. ارفع الملفات: `app.py`, `requirements.txt`
5. انتظر حتى يعمل الـ Space

## الاستخدام

### التطبيق (Android) يتصل بـ:
```
wss://YOUR_USERNAME-sms-gateway-relay.hf.space/device
```

### الرابط العام لأي موقع:
```
https://YOUR_USERNAME-sms-gateway-relay.hf.space/gateway/{deviceId}/api/v1/transactions
```

## مثال

```bash
# إنشاء معاملة من أي مكان في العالم
curl -X POST "https://myuser-sms-gateway-relay.hf.space/gateway/gw-abc123/api/v1/transactions" \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"TX001","amount":500,"phoneNumber":"01012345678","walletType":"VODAFONE_CASH"}'
```
