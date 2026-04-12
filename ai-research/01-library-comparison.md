# Idempotency Library Comparison for Spring Boot REST Controllers

## IETF Idempotency-Key Standard (draft-ietf-httpapi-idempotency-key-header-07)

The IETF draft specifies:

- **Header**: `Idempotency-Key` — a string value (typically UUID v4) sent by the client
- **Server must**: store the response keyed by the idempotency key, return the cached response on duplicate requests
- **Payload fingerprinting**: server should detect if the same key is reused with a different request body
- **Required error responses**:
  - `400` — missing required `Idempotency-Key` header
  - `422` — key reused with a different request payload
  - `409` — concurrent duplicate request (original still in-flight)
- **Expiration**: server must publish and enforce TTL policies for stored results

---

## Libraries Evaluated

### 1. TransferWise idempotence4j

- **GitHub**: https://github.com/transferwise/idempotence4j (69 stars)
- **Status**: Active, production-proven at Wise (financial services)
- **Integration**: Programmatic — `IdempotenceService.execute(actionId, retryFn, execFn, serFn, deserFn)`
- **Storage**: PostgreSQL (native), MariaDB
- **REST controller support**: No — service-layer API only, no annotation/interceptor
- **IETF compliance**: None — no HTTP header support, no standard error codes
- **Response caching**: Yes — serializes and stores results in DB
- **Concurrency**: PostgreSQL row-level locking
- **Spring Boot starter**: Yes (`idempotence4j-spring-boot-starter`)

**Verdict**: Excellent storage/locking engine for PostgreSQL but cannot be used directly on controllers. Requires wrapping every method manually. Good candidate as a building block underneath a custom interceptor.

---

### 2. Trendyol Jdempotent

- **GitHub**: https://github.com/Trendyol/Jdempotent (111 stars)
- **Status**: Last release March 2022 — effectively stale
- **Integration**: Annotation: `@IdempotentResource` on methods
- **Storage**: Redis (with Sentinel), Couchbase — no PostgreSQL
- **REST controller support**: Yes — annotation-based, closest to desired UX
- **IETF compliance**: None — uses payload hash as key, not `Idempotency-Key` header
- **Response caching**: Yes
- **Concurrency**: Redis-based locking

**Verdict**: Right integration model (annotation on controller) but wrong storage backend (requires Redis/Couchbase). Stale project, unlikely to support Spring Boot 4.x / Java 25.

---

### 3. AWS Powertools for Java — Idempotency

- **Docs**: https://docs.aws.amazon.com/powertools/java/latest/utilities/idempotency/
- **Status**: Active, AWS-backed
- **Integration**: `@Idempotent` annotation with `@IdempotencyKey`
- **Storage**: DynamoDB only
- **REST controller support**: No — AWS Lambda specific
- **IETF compliance**: None — Lambda event model, not HTTP

**Verdict**: Disqualified — fundamentally incompatible with Spring Boot servlet model. Lambda-only, DynamoDB-only.

---

### 4. pig-mesh idempotent-spring-boot-starter

- **GitHub**: https://github.com/pig-mesh/idempotent-spring-boot-starter (165 stars)
- **Status**: Active
- **Integration**: `@Idempotent` annotation on controller methods
- **Storage**: Redis only (via Redisson)
- **REST controller support**: Yes
- **IETF compliance**: None — key = IP + URL + parameters, not `Idempotency-Key` header
- **Response caching**: No — only blocks duplicates, does not return cached responses
- **Concurrency**: Redis-based

**Verdict**: Only blocks duplicate requests — does not return cached responses. This violates the core IETF requirement. Redis-only.

---

### 5. arun0009/idempotent

- **GitHub**: https://github.com/arun0009/idempotent (24 stars)
- **Status**: Active — v2.4.0 released April 2026
- **Integration**: Dual: `@Idempotent` annotation OR programmatic `IdempotentService`
- **Storage**: Redis, DynamoDB, NATS, RDS (PostgreSQL)
- **REST controller support**: Yes — annotation-based
- **IETF compliance**: Partial — supports client-specified keys via configurable header
- **Response caching**: Yes
- **Concurrency**: Backend-dependent, exponential backoff for in-progress requests

**Verdict**: Most promising off-the-shelf candidate. Has annotation support, supports PostgreSQL via RDS backend, actively maintained, dual API.

---

### 6. dgrandemange/idempotence-receiver

- **GitHub**: https://github.com/dgrandemange/idempotence-receiver (0 stars)
- **Status**: Abandoned since February 2019
- **Integration**: `@Idempotent` annotation
- **Storage**: In-memory, Infinispan
- **IETF compliance**: Best of all libraries — uses `Idempotency-Key` header with body-hash fallback

**Verdict**: Disqualified — abandoned, zero community, no compatibility path for modern Spring Boot.

---

### 7. Spring Integration IdempotentReceiverInterceptor

- **Status**: Active (part of Spring Integration)
- **Integration**: AOP advice on message handlers
- **REST controller support**: No — designed for messaging channels

**Verdict**: Disqualified — messaging-oriented, not REST.

---

## Comparison Matrix

| Criterion                      | idempotence4j | Jdempotent | AWS Powertools | pig-mesh | arun0009   | dgrandemange | Spring Integration |
|--------------------------------|---------------|------------|----------------|----------|------------|--------------|--------------------|
| Annotation on controller       | —             | Yes        | Lambda only    | Yes      | **Yes**    | Yes          | —                  |
| PostgreSQL storage             | **Yes**       | —          | —              | —        | **Yes**    | —            | —                  |
| IETF Idempotency-Key header   | —             | —          | —              | —        | Partial    | **Yes**      | —                  |
| Cached response replay         | Yes           | Yes        | Yes            | **No**   | Yes        | Yes          | N/A                |
| Payload fingerprinting         | —             | Hash-based | JMESPath       | —        | —          | Body hash    | —                  |
| 400/422/409 error codes        | —             | —          | —              | —        | 409 only   | Partial      | —                  |
| Concurrent request handling    | DB locking    | Redis lock | DynamoDB cond  | Redis    | Backoff    | —            | —                  |
| Active maintenance             | Yes           | Stale      | Yes            | Yes      | **Yes**    | Dead         | Yes                |
| Spring Boot 4.x compatible     | Unknown       | Unlikely   | N/A            | Unknown  | **Yes**    | No           | Possible           |

---

## Conclusion

No existing library fully implements the IETF Idempotency-Key standard for Spring Boot REST controllers. **arun0009/idempotent** is the strongest candidate — it provides annotation-based REST controller support, PostgreSQL storage, active maintenance targeting Spring Boot 4.x, and configurable HTTP header extraction. See `02-gate-evaluation.md` for the detailed gate assessment.
