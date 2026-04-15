# Contributing to SMS Payment Gateway

Thank you for your interest in contributing! This document provides guidelines for contributing to the project.

---

## 📋 Table of Contents

1. [Code of Conduct](#code-of-conduct)
2. [Getting Started](#getting-started)
3. [Development Workflow](#development-workflow)
4. [Coding Standards](#coding-standards)
5. [Testing Guidelines](#testing-guidelines)
6. [Commit Messages](#commit-messages)
7. [Pull Request Process](#pull-request-process)
8. [Documentation](#documentation)

---

## Code of Conduct

### Our Standards

- Be respectful and inclusive
- Focus on constructive feedback
- Prioritize project goals
- Help others learn and grow

---

## Getting Started

### Prerequisites

```bash
# Required
- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Git

# Recommended
- Android device or emulator
- Postman for API testing
```

### Setup Development Environment

```bash
# 1. Clone the repository
git clone <repository-url>
cd sms-payment-gateway

# 2. Open in Android Studio
# File > Open > Select project folder

# 3. Sync Gradle
# Wait for Gradle sync to complete

# 4. Run tests
./gradlew test

# 5. Build and run
./gradlew installDebug
```

---

## Development Workflow

### Branch Strategy

```
main
  ├── develop
  │   ├── feature/add-new-wallet
  │   ├── feature/improve-matching
  │   ├── bugfix/fix-parser-issue
  │   └── hotfix/critical-security-fix
```

### Branch Naming

- **Features**: `feature/short-description`
- **Bug Fixes**: `bugfix/issue-number-description`
- **Hotfixes**: `hotfix/critical-issue`
- **Improvements**: `improve/what-is-improved`

### Workflow Steps

1. **Create a branch**
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

2. **Make changes**
   - Write code
   - Add tests
   - Update documentation

3. **Test locally**
   ```bash
   ./gradlew test
   ./gradlew lint
   ./test_all.sh
   ```

4. **Commit changes**
   ```bash
   git add .
   git commit -m "feat: add support for new wallet"
   ```

5. **Push and create PR**
   ```bash
   git push origin feature/your-feature-name
   # Create Pull Request on GitHub/GitLab
   ```

---

## Coding Standards

### Kotlin Style Guide

Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)

#### Key Points

```kotlin
// ✅ Good
class SmsParser {
    fun parse(message: String): ParsedSmsData? {
        return when {
            isVodafone(message) -> parseVodafone(message)
            else -> null
        }
    }
}

// ❌ Bad
class smsParser {
    fun Parse(message:String):ParsedSmsData?{
        if(isVodafone(message))return parseVodafone(message)
        else return null
    }
}
```

### Architecture Guidelines

- **Follow Clean Architecture**
  - Data layer: Entities, DAOs, Repositories
  - Domain layer: Models, Use Cases
  - Presentation layer: UI, ViewModels

- **Use MVVM for UI**
  - ViewModel for business logic
  - Compose for UI
  - StateFlow for state management

- **Dependency Injection**
  - Use Hilt for DI
  - Inject dependencies, don't create them
  - Use @Singleton for app-wide instances

### File Organization

```
app/src/main/java/com/sms/paymentgateway/
├── data/
│   ├── entities/       # Room entities
│   ├── dao/           # Data Access Objects
│   └── repository/    # Repository implementations
├── domain/
│   ├── models/        # Domain models
│   └── usecases/      # Business logic
├── presentation/
│   ├── ui/           # Compose screens
│   └── viewmodels/   # ViewModels
├── services/         # Background services
├── utils/           # Utilities
└── di/              # Hilt modules
```

### Naming Conventions

```kotlin
// Classes: PascalCase
class TransactionMatcher

// Functions: camelCase
fun findMatch()

// Variables: camelCase
val apiKey: String

// Constants: UPPER_SNAKE_CASE
const val MAX_RETRIES = 3

// Private properties: _camelCase (for backing properties)
private val _state = MutableStateFlow<State>()
val state = _state.asStateFlow()
```

---

## Testing Guidelines

### Test Coverage Requirements

- **Unit Tests**: 80%+ coverage
- **Critical Components**: 95%+ coverage
  - SmsParser
  - TransactionMatcher
  - SecurityManager

### Writing Tests

```kotlin
// ✅ Good test
@Test
fun `parse Vodafone Cash message with all fields`() {
    // Given
    val message = "تم استلام 500 جنيه من 01012345678 رقم VC123456"
    
    // When
    val result = smsParser.parse("Vodafone", message)
    
    // Then
    assertNotNull(result)
    assertEquals(500.0, result?.amount)
    assertEquals("VC123456", result?.transactionId)
    assertEquals("01012345678", result?.senderPhone)
}

// ❌ Bad test
@Test
fun test1() {
    val r = smsParser.parse("Vodafone", "msg")
    assertTrue(r != null)
}
```

### Test Organization

```
app/src/test/java/
├── SmsParserTest.kt
├── TransactionMatcherTest.kt
├── SecurityManagerTest.kt
└── RateLimiterTest.kt
```

### Running Tests

```bash
# All tests
./gradlew test

# Specific test class
./gradlew test --tests SmsParserTest

# With coverage
./gradlew testDebugUnitTest jacocoTestReport
```

---

## Commit Messages

### Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation changes
- **style**: Code style changes (formatting)
- **refactor**: Code refactoring
- **test**: Adding or updating tests
- **chore**: Maintenance tasks

### Examples

```bash
# Feature
feat(parser): add support for Etisalat Cash

Added regex patterns for Etisalat Cash SMS messages.
Includes support for both Arabic and English formats.

Closes #123

# Bug fix
fix(matcher): correct time window calculation

Fixed issue where time window was calculated incorrectly
for transactions created near midnight.

Fixes #456

# Documentation
docs(api): update webhook payload examples

Added more examples for webhook payloads including
error scenarios and edge cases.

# Refactor
refactor(security): extract HMAC logic to separate class

Moved HMAC signature generation and verification to
dedicated HmacHelper class for better reusability.
```

---

## Pull Request Process

### Before Creating PR

```bash
# 1. Update from develop
git checkout develop
git pull origin develop
git checkout your-branch
git rebase develop

# 2. Run all tests
./gradlew test
./gradlew lint
./test_all.sh

# 3. Update documentation
# - Update relevant .md files
# - Add/update code comments
# - Update CHANGELOG.md
```

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] All tests passing

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Tests added/updated
- [ ] CHANGELOG.md updated

## Screenshots (if applicable)
Add screenshots for UI changes

## Related Issues
Closes #123
```

### Review Process

1. **Automated Checks**
   - Tests must pass
   - Lint must pass
   - No merge conflicts

2. **Code Review**
   - At least 1 approval required
   - Address all comments
   - Update based on feedback

3. **Merge**
   - Squash and merge to develop
   - Delete branch after merge

---

## Documentation

### Code Documentation

```kotlin
/**
 * Parses SMS message and extracts payment information.
 *
 * @param sender The SMS sender identifier (e.g., "Vodafone")
 * @param message The SMS message content
 * @return ParsedSmsData if successful, null if parsing fails
 *
 * @throws IllegalArgumentException if sender is empty
 */
fun parse(sender: String, message: String): ParsedSmsData? {
    require(sender.isNotBlank()) { "Sender cannot be blank" }
    // Implementation
}
```

### Markdown Documentation

- Use clear headings
- Include code examples
- Add screenshots for UI
- Keep it up to date

### Documentation Files to Update

When making changes, update relevant files:

- **README.md**: Overview and quick start
- **API_DOCUMENTATION.md**: API changes
- **SETUP_GUIDE.md**: Setup process changes
- **TROUBLESHOOTING.md**: New issues/solutions
- **CHANGELOG.md**: All changes
- **Code comments**: Complex logic

---

## Adding New Features

### Checklist for New Features

```
□ Design document created
□ Architecture reviewed
□ Implementation completed
□ Unit tests added (80%+ coverage)
□ Integration tests added
□ Manual testing completed
□ Documentation updated
□ CHANGELOG.md updated
□ PR created and reviewed
```

### Example: Adding New Wallet Support

1. **Update WalletType enum**
   ```kotlin
   enum class WalletType {
       // ...
       NEW_WALLET("New Wallet", listOf("NewWallet", "NW"))
   }
   ```

2. **Add parsing logic**
   ```kotlin
   private fun parseNewWallet(message: String): ParsedSmsData? {
       // Implementation
   }
   ```

3. **Add tests**
   ```kotlin
   @Test
   fun `parse New Wallet message`() {
       // Test implementation
   }
   ```

4. **Update documentation**
   - Add to WALLET_SMS_PATTERNS.md
   - Update README.md
   - Update API_DOCUMENTATION.md

---

## Questions?

If you have questions:

1. Check existing documentation
2. Search closed issues
3. Ask in team chat
4. Create a discussion issue

---

## Recognition

Contributors will be recognized in:
- CHANGELOG.md
- Project README.md
- Release notes

Thank you for contributing! 🎉
