# Implementation: arun0009/idempotent v2.4.0 with Spring Boot

## Overview

The `idempotency-test-harness` module has been integrated with the `arun0009/idempotent` library (v2.4.0) using the RDS (PostgreSQL) storage backend. The `POST /api/invoices` endpoint is now idempotent — duplicate requests with the same `Idempotency-Key` header return the cached response without re-executing the business logic.

---

## What Was Changed

### 1. Maven Dependency (`idempotency-test-harness/pom.xml`)

Added:
```xml
<dependency>
    <groupId>io.github.arun0009</groupId>
    <artifactId>idempotent-rds</artifactId>
    <version>2.4.0</version>
</dependency>
```

This pulls in `idempotent-core` (AOP aspect, annotation, serialization) and `idempotent-rds` (PostgreSQL storage via JdbcTemplate).

### 2. Database Table (`002-create-idempotent-table.yaml`)

Liquibase changeset creates the `idempotent` table required by the RDS module:

| Column | Type | Description |
|--------|------|-------------|
| `key_id` | VARCHAR(255) | Idempotency key (from header or SpEL) |
| `process_name` | VARCHAR(255) | Auto-generated: `__ControllerName.methodName()` |
| `status` | VARCHAR(50) | `INPROGRESS` or `COMPLETED` |
| `expiration_time_millis` | BIGINT | TTL timestamp (epoch millis) |
| `response` | TEXT | Jackson-serialized response payload |

Primary key: `(key_id, process_name)` — composite key allows the same idempotency key to be used across different endpoints.

Index: `idx_idempotent_expiration` on `expiration_time_millis` — supports the cleanup task's batch deletion of expired records.

### 3. Configuration (`application.yaml`)

```yaml
idempotent:
  key:
    header: Idempotency-Key    # IETF standard header name (default was X-Idempotency-Key)
  rds:
    table-name: idempotent
    cleanup:
      fixed-delay: 60000        # Cleanup expired records every 60 seconds
      batch-size: 1000           # Delete up to 1000 expired records per cycle
```

### 4. Controller Annotation (`InvoiceController.java`)

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@Idempotent(key = "#request", duration = "PT1H", hashKey = true)
public InvoiceResponse createInvoice(@RequestBody InvoiceRequest request) {
    return invoiceService.createInvoice(request);
}
```

- `key = "#request"` — SpEL expression used as fallback when no header is provided; evaluates the entire request body
- `duration = "PT1H"` — cached responses expire after 1 hour (ISO-8601 duration)
- `hashKey = true` — SHA-256 hashes the key before storage (since the key may be a serialized object)

### 5. Integration Tests (`InvoiceControllerIT.java`)

Added 4 new idempotency tests + updated existing tests to include the `Idempotency-Key` header:

| Test | What It Verifies |
|------|------------------|
| `shouldReturnCachedResponseForDuplicateIdempotencyKey` | Same key + same body = returns cached 201 with same ID |
| `shouldCreateSeparateInvoicesWithDifferentIdempotencyKeys` | Different keys = separate invoices created |
| `shouldNotRequireIdempotencyKeyForGetRequests` | GET endpoints work without any idempotency header |
| `shouldReturnCachedResponseEvenWhenDuplicateWouldFailUniqueConstraint` | Proves idempotency prevents unique constraint violations |

---

## How It Works (Execution Flow)

1. Client sends `POST /api/invoices` with `Idempotency-Key: <uuid>` header
2. Spring AOP intercepts the `@Idempotent`-annotated method via `IdempotentAspect`
3. Key is extracted from the `Idempotency-Key` header (first priority) or SpEL expression (fallback)
4. If `hashKey = true`, key is SHA-256 hashed
5. Process name is computed as `__InvoiceController.createInvoice()`
6. Database is queried for existing entry with `(key_id, process_name)`:
   - **No entry**: Insert with status `INPROGRESS`, execute controller method, update to `COMPLETED` with serialized response
   - **COMPLETED entry** (not expired): Return cached response immediately — method body is NOT executed
   - **INPROGRESS entry**: Retry with exponential backoff (100ms initial, 2x multiplier, max 5 retries); if still in progress, throw `IdempotentKeyConflictException`
7. On exception from the business method, the `INPROGRESS` entry is removed (errors are not cached)
8. Non-2xx `ResponseEntity` responses are also removed (not cached)
9. `RdsCleanupTask` runs every 60 seconds to purge expired records in batches of 1000

---

## Auto-Configuration

The library uses Spring Boot auto-configuration (no explicit `@Bean` definitions needed):

- `IdempotentCoreAutoConfiguration` — registers `IdempotentAspect` and `IdempotentService` beans
- `RdsAutoConfiguration` — registers `RdsIdempotentStore` (backed by the existing `DataSource`), `RdsCleanupTask`, and a `TaskScheduler` for cleanup
- `IdempotentJsonMapperAutoConfiguration` — configures the `JacksonIdempotentPayloadCodec` for response serialization

All auto-configuration is conditional — the RDS module activates because `DataSource` and `JdbcTemplate` beans are present.

---

## IETF Standard Compliance Status

| IETF Requirement | Status | Notes |
|------------------|--------|-------|
| `Idempotency-Key` header | Implemented | Configured via `idempotent.key.header` property |
| Cached response replay | Implemented | Returns same status + body for duplicate keys |
| TTL expiration | Implemented | Configurable via `duration` annotation attribute |
| Concurrent request handling | Implemented | Exponential backoff + conflict exception |
| 400 for missing key | Not implemented | Library proceeds without idempotency if no key |
| 422 for payload mismatch | Not implemented | No payload fingerprinting (Gate 5 failure) |
| 409 for in-flight duplicates | Partially implemented | Throws exception after retry exhaustion |

---

## Known Gaps and Future Considerations

### 1. No Payload Fingerprinting (IETF 422)
The library does not verify that the request body matches when the same idempotency key is reused. A different payload with the same key returns the first request's cached response. To address this:
- Add a custom `OncePerRequestFilter` that computes a SHA-256 hash of the request body and stores it alongside the idempotency record
- Compare the hash on subsequent requests and return 422 if they differ

### 2. Missing Key Not Enforced (IETF 400)
If no `Idempotency-Key` header is sent, the library falls back to the SpEL key expression rather than rejecting the request. To enforce the header:
- Add a custom `HandlerInterceptor` that checks for the header on `@Idempotent`-annotated methods and returns 400 if missing

### 3. Serialization Security Warning
The library uses Jackson polymorphic type serialization (`DefaultTyping`) which logs a warning at startup about untrusted sources. This is acceptable for response caching since we control the serialized data, but a custom `PolymorphicTypeValidator` can be provided via `IdempotentJsonMapperCustomizer` bean.

### 4. @ResponseStatus Compatibility
The `@ResponseStatus(HttpStatus.CREATED)` annotation on the controller method is applied by Spring MVC regardless of whether the AOP aspect returns a cached response or the actual method result. This means cached responses correctly return 201.

### 5. Error Handling
Exceptions thrown by the business method (e.g., database constraint violations) cause the idempotency entry to be removed. This means retrying after an error will re-execute the method — which is the correct behavior for transient failures.
