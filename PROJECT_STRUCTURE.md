# SMS Payment Gateway - Project Structure

## Overview

This project follows **Clean Architecture** principles with **MVVM** pattern for the presentation layer.

```
sms-payment-gateway/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/sms/paymentgateway/
│   │   │   │   ├── data/              # Data Layer
│   │   │   │   │   ├── entities/      # Room entities
│   │   │   │   │   │   ├── SmsLog.kt
│   │   │   │   │   │   └── PendingTransaction.kt
│   │   │   │   │   ├── dao/           # Data Access Objects
│   │   │   │   │   │   ├── SmsLogDao.kt
│   │   │   │   │   │   └── PendingTransactionDao.kt
│   │   │   │   │   ├── AppDatabase.kt
│   │   │   │   │   └── Converters.kt
│   │   │   │   │
│   │   │   │   ├── domain/            # Domain Layer
│   │   │   │   │   └── models/        # Domain models
│   │   │   │   │       ├── WalletType.kt
│   │   │   │   │       ├── ParsedSmsData.kt
│   │   │   │   │       └── TransactionType.kt
│   │   │   │   │
│   │   │   │   ├── presentation/      # Presentation Layer
│   │   │   │   │   └── ui/
│   │   │   │   │       └── MainActivity.kt
│   │   │   │   │
│   │   │   │   ├── services/          # Background Services
│   │   │   │   │   ├── SmsReceiver.kt
│   │   │   │   │   ├── SmsProcessor.kt
│   │   │   │   │   ├── PaymentGatewayService.kt
│   │   │   │   │   ├── BootReceiver.kt
│   │   │   │   │   ├── ApiServer.kt
│   │   │   │   │   └── WebhookClient.kt
│   │   │   │   │
│   │   │   │   ├── utils/             # Utilities
│   │   │   │   │   ├── parser/
│   │   │   │   │   │   └── SmsParser.kt
│   │   │   │   │   ├── matcher/
│   │   │   │   │   │   └── TransactionMatcher.kt
│   │   │   │   │   └── security/
│   │   │   │   │       └── SecurityManager.kt
│   │   │   │   │
│   │   │   │   ├── di/                # Dependency Injection
│   │   │   │   │   └── AppModule.kt
│   │   │   │   │
│   │   │   │   └── PaymentGatewayApp.kt
│   │   │   │
│   │   │   ├── res/
│   │   │   │   └── values/
│   │   │   │       └── strings.xml
│   │   │   │
│   │   │   └── AndroidManifest.xml
│   │   │
│   │   └── test/                      # Unit Tests
│   │       └── java/com/sms/paymentgateway/
│   │           ├── SmsParserTest.kt
│   │           └── TransactionMatcherTest.kt
│   │
│   ├── build.gradle.kts
│   └── proguard-rules.pro
│
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── .gitignore
├── README.md
├── API_DOCUMENTATION.md
├── SETUP_GUIDE.md
└── PROJECT_STRUCTURE.md
```

---

## Layer Descriptions

### 1. Data Layer (`data/`)

Responsible for data management and persistence.

**Components:**
- **Entities**: Room database entities representing tables
  - `SmsLog`: Stores received SMS messages
  - `PendingTransaction`: Stores transactions waiting for confirmation

- **DAOs**: Data Access Objects for database operations
  - `SmsLogDao`: CRUD operations for SMS logs
  - `PendingTransactionDao`: CRUD operations for transactions

- **Database**: Room database configuration
  - `AppDatabase`: Main database class
  - `Converters`: Type converters for Room (Date, etc.)

---

### 2. Domain Layer (`domain/`)

Contains business logic and domain models.

**Components:**
- **Models**: Pure Kotlin data classes
  - `WalletType`: Enum for supported wallets
  - `ParsedSmsData`: Parsed SMS information
  - `TransactionType`: Enum for transaction types

---

### 3. Presentation Layer (`presentation/`)

UI components and user interaction.

**Components:**
- **UI**: Jetpack Compose screens
  - `MainActivity`: Main app screen with API key display

---

### 4. Services Layer (`services/`)

Background services and receivers.

**Components:**
- `SmsReceiver`: BroadcastReceiver for incoming SMS
- `SmsProcessor`: Processes and matches SMS
- `PaymentGatewayService`: Foreground service keeping app alive
- `BootReceiver`: Starts service on device boot
- `ApiServer`: Local HTTP server (NanoHTTPD)
- `WebhookClient`: Sends notifications to external webhook

---

### 5. Utils Layer (`utils/`)

Utility classes and helpers.

**Components:**
- **Parser**: SMS parsing logic
  - `SmsParser`: Extracts data from SMS using regex

- **Matcher**: Transaction matching logic
  - `TransactionMatcher`: Matches SMS with pending transactions

- **Security**: Security utilities
  - `SecurityManager`: API key, HMAC, encryption

---

### 6. Dependency Injection (`di/`)

Hilt modules for dependency injection.

**Components:**
- `AppModule`: Provides singletons (Database, Parser, etc.)

---

## Key Technologies

| Component | Technology |
|-----------|-----------|
| Language | Kotlin |
| Architecture | Clean Architecture + MVVM |
| DI | Hilt (Dagger) |
| Database | Room (SQLite) |
| HTTP Server | NanoHTTPD |
| HTTP Client | Retrofit + OkHttp |
| UI | Jetpack Compose |
| Async | Kotlin Coroutines + Flow |
| Logging | Timber |
| Testing | JUnit + MockK |

---

## Data Flow

### SMS Reception Flow

```
SMS Received
    ↓
SmsReceiver (BroadcastReceiver)
    ↓
SmsProcessor
    ↓
SmsParser (Extract data)
    ↓
Save to SmsLog (Database)
    ↓
TransactionMatcher (Find match)
    ↓
Update PendingTransaction (if matched)
    ↓
WebhookClient (Send notification)
```

### API Request Flow

```
External Request
    ↓
ApiServer (NanoHTTPD)
    ↓
SecurityManager (Validate API key)
    ↓
PendingTransactionDao (Database operation)
    ↓
Response (JSON)
```

---

## Database Schema

### sms_logs

| Column | Type | Description |
|--------|------|-------------|
| id | Long | Primary key |
| sender | String | SMS sender |
| message | String | SMS content |
| receivedAt | Date | Timestamp |
| walletType | String | Wallet type |
| transactionId | String? | Extracted TX ID |
| amount | Double? | Extracted amount |
| phoneNumber | String? | Extracted phone |
| transactionType | String | Transaction type |
| parsed | Boolean | Successfully parsed |
| matched | Boolean | Matched with transaction |
| matchedTransactionId | String? | Matched TX ID |
| confidence | Double | Parsing confidence |

### pending_transactions

| Column | Type | Description |
|--------|------|-------------|
| id | String | Primary key |
| amount | Double | Expected amount |
| phoneNumber | String | Expected phone |
| expectedTxId | String? | Expected TX ID |
| walletType | String? | Expected wallet |
| status | Enum | PENDING/MATCHED/EXPIRED |
| createdAt | Date | Creation time |
| expiresAt | Date | Expiration time |
| matchedAt | Date? | Match time |
| matchedSmsId | Long? | Matched SMS ID |
| confidence | Double? | Match confidence |

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/health` | Health check |
| POST | `/api/v1/transactions` | Create transaction |
| GET | `/api/v1/transactions/{id}` | Get transaction |
| GET | `/api/v1/transactions` | List transactions |
| GET | `/api/v1/sms/logs` | Get SMS logs |

---

## Security Features

1. **API Key Authentication**: All requests require Bearer token
2. **HMAC Signatures**: Webhook payloads signed with HMAC-SHA256
3. **Encrypted Storage**: Sensitive data encrypted in database
4. **Secure Communication**: HTTPS support (optional)

---

## Testing Strategy

### Unit Tests
- `SmsParserTest`: Tests SMS parsing logic
- `TransactionMatcherTest`: Tests matching algorithm

### Integration Tests
- End-to-end SMS processing
- API server functionality
- Database operations

### Manual Tests
- Real SMS reception
- API calls from external clients
- Webhook delivery

---

## Build Configuration

### Gradle Files

- `build.gradle.kts` (root): Plugin versions
- `app/build.gradle.kts`: App dependencies and config
- `settings.gradle.kts`: Project settings
- `gradle.properties`: Gradle properties

### Dependencies

See `app/build.gradle.kts` for complete list:
- Room 2.6.1
- Retrofit 2.9.0
- Hilt 2.48
- Compose BOM 2024.01.00
- NanoHTTPD 2.3.1

---

## Future Enhancements

1. **Remote Config**: Update regex patterns remotely
2. **Analytics**: Track success rates and performance
3. **Multi-device**: Support multiple phones
4. **Web Dashboard**: Remote monitoring interface
5. **Machine Learning**: Improve parsing with ML
6. **Backup/Restore**: Cloud backup for database

---

## Contributing

When adding new features:

1. Follow Clean Architecture principles
2. Add unit tests for new logic
3. Update documentation
4. Follow Kotlin coding conventions
5. Use Hilt for dependency injection

---

## License

Internal use only.
