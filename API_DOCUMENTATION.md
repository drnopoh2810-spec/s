# SMS Payment Gateway - API Documentation

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication

All requests require an API Key in the Authorization header:

```http
Authorization: Bearer YOUR_API_KEY
```

Get your API Key from the app's main screen.

---

## Endpoints

### 1. Health Check

**GET** `/health`

Check if the service is running.

**Response:**
```json
{
  "status": "ok",
  "timestamp": 1705329000000,
  "service": "SMS Payment Gateway"
}
```

---

### 2. Create Pending Transaction

**POST** `/transactions`

Add a new payment transaction to wait for SMS confirmation.

**Request Body:**
```json
{
  "id": "order-12345",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "expectedTxId": "VC123456789",
  "walletType": "VODAFONE_CASH",
  "expiresInMinutes": 30
}
```

**Parameters:**
- `id` (required): Unique transaction identifier
- `amount` (required): Expected payment amount
- `phoneNumber` (required): Phone number that will send the payment
- `expectedTxId` (optional): Expected wallet transaction ID
- `walletType` (optional): Expected wallet type (VODAFONE_CASH, ORANGE_MONEY, etc.)
- `expiresInMinutes` (optional): Expiration time in minutes (default: 30)

**Response:**
```json
{
  "success": true,
  "transaction": {
    "id": "order-12345",
    "amount": 500.00,
    "phoneNumber": "01012345678",
    "status": "PENDING",
    "createdAt": "2024-01-15T14:30:00Z",
    "expiresAt": "2024-01-15T15:00:00Z"
  }
}
```

---

### 3. Get Transaction Status

**GET** `/transactions/{id}`

Get the status of a specific transaction.

**Response:**
```json
{
  "id": "order-12345",
  "amount": 500.00,
  "phoneNumber": "01012345678",
  "status": "MATCHED",
  "createdAt": "2024-01-15T14:30:00Z",
  "matchedAt": "2024-01-15T14:30:05Z",
  "confidence": 0.98
}
```

**Status Values:**
- `PENDING`: Waiting for SMS
- `MATCHED`: SMS received and matched
- `EXPIRED`: Transaction expired
- `CANCELLED`: Transaction cancelled

---

### 4. List All Transactions

**GET** `/transactions`

Get all pending transactions.

**Response:**
```json
{
  "transactions": [
    {
      "id": "order-12345",
      "amount": 500.00,
      "status": "PENDING",
      "createdAt": "2024-01-15T14:30:00Z"
    }
  ]
}
```

---

### 5. Get SMS Logs

**GET** `/sms/logs`

Get all received and processed SMS messages.

**Response:**
```json
{
  "logs": [
    {
      "id": 1,
      "sender": "Vodafone",
      "message": "تم استلام 500 جنيه من 01012345678...",
      "receivedAt": "2024-01-15T14:30:00Z",
      "walletType": "VODAFONE_CASH",
      "amount": 500.00,
      "parsed": true,
      "matched": true,
      "confidence": 0.98
    }
  ]
}
```

---

## Webhook Callback

When a payment is confirmed, the app sends a POST request to your configured webhook URL.

**Webhook Payload:**
```json
{
  "event": "PAYMENT_CONFIRMED",
  "transactionId": "order-12345",
  "smsData": {
    "walletType": "VODAFONE_CASH",
    "walletTxId": "VC123456789",
    "amount": 500.00,
    "senderPhone": "01012345678",
    "timestamp": 1705329000000
  },
  "confidence": 0.98,
  "processedAt": 1705329005000
}
```

**Headers:**
```http
Content-Type: application/json
X-Signature: HMAC-SHA256 signature of the payload
```

**Verifying Webhook Signature:**

The `X-Signature` header contains an HMAC-SHA256 signature of the request body. Verify it using your HMAC secret (available in app settings).

```python
import hmac
import hashlib

def verify_signature(payload, signature, secret):
    computed = hmac.new(
        secret.encode(),
        payload.encode(),
        hashlib.sha256
    ).hexdigest()
    return hmac.compare_digest(computed, signature)
```

---

## Error Responses

All errors return a JSON object with an `error` field:

```json
{
  "error": "Error message description"
}
```

**HTTP Status Codes:**
- `200 OK`: Success
- `201 Created`: Resource created
- `400 Bad Request`: Invalid request
- `401 Unauthorized`: Invalid or missing API key
- `404 Not Found`: Resource not found
- `500 Internal Server Error`: Server error

---

## Rate Limiting

The API is limited to 100 requests per minute per IP address.

---

## Example Usage

### Python Example

```python
import requests

API_KEY = "your-api-key"
BASE_URL = "http://localhost:8080/api/v1"

headers = {
    "Authorization": f"Bearer {API_KEY}",
    "Content-Type": "application/json"
}

# Create transaction
response = requests.post(
    f"{BASE_URL}/transactions",
    headers=headers,
    json={
        "id": "order-12345",
        "amount": 500.00,
        "phoneNumber": "01012345678"
    }
)

print(response.json())

# Check status
response = requests.get(
    f"{BASE_URL}/transactions/order-12345",
    headers=headers
)

print(response.json())
```

### cURL Example

```bash
# Create transaction
curl -X POST http://localhost:8080/api/v1/transactions \
  -H "Authorization: Bearer YOUR_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "id": "order-12345",
    "amount": 500.00,
    "phoneNumber": "01012345678"
  }'

# Get transaction status
curl -X GET http://localhost:8080/api/v1/transactions/order-12345 \
  -H "Authorization: Bearer YOUR_API_KEY"
```

---

## Support

For issues or questions, please contact the development team.
