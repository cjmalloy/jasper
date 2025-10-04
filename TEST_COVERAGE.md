# Test Coverage for Better Error Messages (PR #200)

## Overview
This document describes the test coverage added for the "Better Error Messages" functionality introduced in PR #200.

## Functionality Being Tested

The PR #200 added three key methods to `ExceptionTranslator.java`:

1. **`getCustomizedTitle(Throwable err)`** - Returns custom titles for specific exception types
   - `MethodArgumentNotValidException` → "Method argument not valid"

2. **`getCustomizedErrorDetails(Throwable err)`** - Returns environment-aware error details
   - **Production profile**: Sanitizes sensitive internal error messages
   - **Development profile**: Returns detailed error messages for debugging

3. **`containsPackageName(String message)`** - Helper method that detects package names in error messages
   - Checks for: "org.", "java.", "net.", "jakarta.", "javax.", "com.", "io.", "de.", "jasper."

## Test Structure

### Test Controller Endpoints (ExceptionTranslatorTestController.java)

Added 3 new endpoints to simulate different error scenarios:

| Endpoint | Exception Thrown | Purpose |
|----------|------------------|---------|
| `/http-message-conversion` | `HttpMessageConversionException` | Test sanitization of HTTP conversion errors |
| `/data-access` | `DataAccessException` | Test sanitization of database access errors |
| `/internal-server-error-with-package` | `RuntimeException` with package name | Test sanitization of stack traces with package names |

### Development Profile Tests (ExceptionTranslatorIT.java)

Added 4 new test cases:

1. **`testHttpMessageConversionExceptionInDev()`**
   - Verifies: Detailed error message "Failed to convert http message" is shown
   - Expected: No sanitization in dev mode

2. **`testDataAccessExceptionInDev()`**
   - Verifies: Detailed error message "Database access failed" is shown
   - Expected: No sanitization in dev mode

3. **`testInternalServerErrorWithPackageNameInDev()`**
   - Verifies: Error message with "org.springframework.web package" is shown as-is
   - Expected: Package names are NOT sanitized in dev mode

4. **`testMethodArgumentNotValidHasCustomTitle()`**
   - Verifies: Custom title "Method argument not valid" is returned
   - Tests: `getCustomizedTitle()` method functionality

### Production Profile Tests (ExceptionTranslatorProdIT.java)

Created new test file with 3 test cases:

1. **`testHttpMessageConversionExceptionInProd()`**
   - Verifies: Generic message "Unable to convert http message" is returned
   - Expected: Internal error details are hidden

2. **`testDataAccessExceptionInProd()`**
   - Verifies: Generic message "Failure during data access" is returned
   - Expected: Database error details are hidden

3. **`testInternalServerErrorWithPackageNameInProd()`**
   - Verifies: Generic message "Unexpected runtime exception" is returned
   - Expected: Package names and stack traces are sanitized
   - Tests: `containsPackageName()` method functionality

## Test Coverage Summary

| Method | Test Coverage | Pass/Fail Criteria |
|--------|---------------|-------------------|
| `getCustomizedTitle()` | ✅ Covered | Custom title for MethodArgumentNotValidException |
| `getCustomizedErrorDetails()` (dev) | ✅ Covered | Detailed messages for HttpMessageConversionException, DataAccessException, and package names |
| `getCustomizedErrorDetails()` (prod) | ✅ Covered | Sanitized messages for all three scenarios |
| `containsPackageName()` | ✅ Covered | Detects "org." prefix and sanitizes in prod |

## Files Modified

1. **ExceptionTranslatorTestController.java** - Added 3 new test endpoints (+17 lines)
2. **ExceptionTranslatorIT.java** - Added 4 new test methods (+47 lines)
3. **ExceptionTranslatorProdIT.java** - New file with 3 test methods (+61 lines)

**Total**: 125 lines of new test code

## Running the Tests

### Using Docker (Recommended)
```bash
docker build --target test -t jasper-test .
docker run --rm jasper-test
```

### Using Maven Locally (requires Java 25)
```bash
# All tests
./mvnw test

# Development profile tests only
./mvnw test -Dtest=ExceptionTranslatorIT

# Production profile tests only
./mvnw test -Dtest=ExceptionTranslatorProdIT
```

## Expected Results

All tests should pass, demonstrating that:
- Custom error titles are applied correctly
- Error messages are detailed in development environments
- Error messages are sanitized in production environments
- Package names are properly detected and sanitized
- The error handling follows RFC7807 Problem Details format
