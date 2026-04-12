# Gate Evaluation: arun0009/idempotent v2.4.0

**Library**: https://github.com/arun0009/idempotent  
**Version**: 2.4.0 (released April 3, 2026)  
**Maven coordinates**: `io.github.arun0009:idempotent-rds:2.4.0`

---

## Gate Results Summary

| Gate | Requirement | Result | Notes |
|------|-------------|--------|-------|
| 1 | Spring Boot 4.0.5 / Java 25 compatible | **PASS** | Library targets Spring Boot 4.0.5, Java 17+ |
| 2 | RDS backend works with PostgreSQL | **PASS** | Supports PostgreSQL, MySQL, H2 via JDBC |
| 3 | Configurable `Idempotency-Key` header | **PASS** | Default `X-Idempotency-Key`, configurable via `idempotent.key.header` |
| 4 | Returns cached responses | **PASS** | Serializes response (Jackson JSON), returns on cache hit |
| 5 | Payload fingerprinting (422 on mismatch) | **FAIL** | No payload fingerprinting; same key + different body returns cached response |
| 6 | Concurrent request handling (409) | **PASS** | Returns 409 via `IdempotentKeyConflictException`; exponential backoff retry |

**Overall: 5/6 PASS**

---

## Gate 1: Spring Boot 4.0.5 / Java 25 Compatibility — PASS

- Library parent POM targets Spring Boot 4.0.5
- Java baseline is 17+; Java 25 is compatible (forward-compatible)
- Uses Spring Framework 7.x APIs (matching Spring Boot 4.x)
- v2.4.0 was released April 2026, same timeframe as our Spring Boot 4.0.5

**Evidence**: The library's own POM uses `spring-boot-starter-parent:4.0.5` and its integration tests run on Spring Boot 4.x with TestContainers.

---

## Gate 2: RDS Backend Works with PostgreSQL — PASS

- The `idempotent-rds` module provides `RdsIdempotentStore` implementation
- Supports PostgreSQL, MySQL, and H2 databases
- Uses Spring's `JdbcTemplate` for database operations
- Table schema:
  - `key_id` (VARCHAR 255) — primary key part 1
  - `process_name` (VARCHAR 255) — primary key part 2
  - `status` (VARCHAR 50) — INPROGRESS / COMPLETED
  - `expiration_time_millis` (BIGINT) — TTL timestamp
  - `response` (TEXT) — serialized response payload
- Composite primary key: `(key_id, process_name)`
- Includes `RdsCleanupTask` for expired record cleanup

**Configuration**:
- `idempotent.rds.table-name`: table name (default: `idempotent`)
- `idempotent.rds.cleanup.fixed-delay`: cleanup interval in ms (default: 60000)
- `idempotent.rds.cleanup.batch-size`: batch delete size (default: 1000)

---

## Gate 3: Configurable `Idempotency-Key` Header — PASS

- Default header name: `X-Idempotency-Key`
- Configurable via property: `idempotent.key.header`
- Can be set to `Idempotency-Key` to align with the IETF standard
- The `IdempotentAspect` reads the header from the `HttpServletRequest`
- Falls back to SpEL expression evaluation if header is not present

**Configuration needed**:
```yaml
idempotent:
  key:
    header: Idempotency-Key
```

---

## Gate 4: Returns Cached Responses — PASS

- On cache hit (same key, status COMPLETED), the stored response is deserialized and returned
- Response serialization uses `IdempotentPayloadCodec`:
  - Default: Jackson JSON with polymorphic type support
  - Alternative: JDK native Java serialization
  - Configurable via `idempotent.serialization.strategy`
- The AOP aspect intercepts the method, returns the cached result directly without executing the method body
- HTTP status code is preserved from the original response

---

## Gate 5: Payload Fingerprinting — FAIL

- The library does **not** compute or store a fingerprint of the request body
- If the same idempotency key is sent with a different request body, the cached response from the first request is returned
- The IETF standard recommends returning HTTP 422 in this case
- This is the primary gap for full IETF compliance

**Mitigation options**:
1. Accept this limitation — many real-world APIs don't enforce payload fingerprinting
2. Add a custom wrapper/filter that computes a body hash and validates before the AOP aspect runs
3. Use `hashKey = true` with a SpEL key that includes request body fields (partial workaround)

---

## Gate 6: Concurrent Request Handling — PASS

- v2.2.0 introduced `IdempotentKeyConflictException` for strict duplicate detection
- When a concurrent request arrives while the first is still INPROGRESS:
  - The library retries with exponential backoff
  - Max retries: `idempotent.inprogress.max.retries` (default: 5)
  - Initial interval: `idempotent.inprogress.retry.initial.intervalMillis` (default: 100ms)
  - Multiplier: `idempotent.inprogress.retry.multiplier` (default: 2)
- If retries are exhausted and the original is still in progress, a conflict exception is thrown

---

## Library Architecture Summary

### Core Components

| Component | Purpose |
|-----------|---------|
| `@Idempotent` | Annotation for controller/service methods |
| `IdempotentAspect` | Spring AOP `@Around` advice — the main interceptor |
| `IdempotentService` | Programmatic API for explicit control |
| `IdempotentStore` | Pluggable storage interface |
| `RdsIdempotentStore` | PostgreSQL/MySQL/H2 implementation via JdbcTemplate |
| `IdempotentPayloadCodec` | Response serialization/deserialization |
| `RdsCleanupTask` | Scheduled task to purge expired records |
| `IdempotentProperties` | Spring Boot configuration binding |

### Annotation API

```java
@Idempotent(
    key = "#request",         // SpEL expression for key generation
    duration = "PT1H",        // ISO-8601 TTL (default: PT5M)
    hashKey = true            // SHA-256 hash the key (default: false)
)
```

### Execution Flow

1. Client sends request with `Idempotency-Key: <uuid>` header
2. `IdempotentAspect` intercepts the `@Idempotent`-annotated method
3. Key is extracted from header (or computed via SpEL expression)
4. Store is queried for existing entry:
   - **No entry**: Insert with status INPROGRESS, execute method, update to COMPLETED with serialized response
   - **COMPLETED entry**: Return cached response immediately (method not executed)
   - **INPROGRESS entry**: Retry with exponential backoff; return cached response when available or throw conflict
5. Non-2xx responses or null results cause the entry to be removed (not cached)

### Version History

| Version | Date | Key Changes |
|---------|------|-------------|
| 2.4.0 | Apr 2026 | Pluggable serialization strategy (Jackson/JDK) |
| 2.3.0 | Mar 2026 | JSpecify null-safety annotations |
| 2.2.0 | Mar 2026 | Strict insert with `IdempotentKeyConflictException` |
| 2.1.0 | Feb 2026 | RDS module, NATS support, Spring Boot 4.0.0 upgrade |

---

## Decision: Proceed with Implementation

Despite the Gate 5 failure (no payload fingerprinting), the library covers 5 of 6 requirements and provides significant value:

- Clean annotation-based integration matching the desired developer experience
- PostgreSQL storage matching the existing infrastructure
- Active maintenance on the same Spring Boot 4.x version
- Robust concurrent request handling
- Configurable header name for IETF alignment

The payload fingerprinting gap can be addressed later via a custom filter if needed.
