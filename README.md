# Idempotency Test Harness — `idempotent-rds`

[![Watch the video](https://img.youtube.com/vi/IP-rGJKSZ3s/maxresdefault.jpg)](https://www.youtube.com/watch?v=IP-rGJKSZ3s)

A Spring Boot app demonstrating the [idempotent-rds](https://github.com/arun0009/idempotent) library
for making REST endpoints idempotent using PostgreSQL.

## How Idempotency Works in the example:

1. The client sends an `Idempotency-Key` header with a unique value (e.g. a UUID) on the `POST /api/orders` request.
2. The **first** request with that key executes normally — the order is created and the response is cached in the `idempotent` table.
3. Any **subsequent** request with the **same key** returns the cached response immediately, without re-executing the endpoint logic.

This prevents duplicate order creation when a client retries (e.g. network timeout, double-click).

### Annotated endpoint

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@Idempotent(key = "#request", duration = "PT1H", hashKey = true)
public OrderResponse createOrder(@RequestBody OrderRequest request,
																@RequestParam(required = false, defaultValue = "1") int delay) {
		// ...
}
```

| Parameter | Purpose |
|-----------|---------|
| `key = "#request"` | Uses the request body as the idempotency scope |
| `duration = "PT1H"` | Cached response expires after 1 hour |
| `hashKey = true` | Hashes the key for storage efficiency |

## Quick Start

```bash
./mvnw spring-boot:run
```

Then open **Swagger UI** at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) —
the request body is pre-filled so you can test immediately.

## Testing Idempotency via Swagger UI

1. Open [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
2. Expand **POST /api/orders** → click **Try it out**
3. Copy a UUID (e.g. `550e8400-e29b-41d4-a716-446655440000`) into the `Idempotency-Key` header
4. Click **Execute** — note the `orderReferenceNumber` in the response
5. Click **Execute** again with the **same** key — you get the **identical** response (cached)
6. Change the key to a different UUID and execute — a **new** order is created

### Simulating slow requests

Add `?delay=10` to make the endpoint sleep for 10 seconds. This lets you fire a second request
with the same key while the first is still processing, to verify the library handles concurrent retries.

## Running Tests

```bash
./mvnw verify
```

Tests use Testcontainers to spin up PostgreSQL and verify:
- Order creation with correct price/VAT calculations
- Idempotent behaviour: duplicate keys return the cached response
- Distinct keys create separate orders
- GET requests work without an idempotency key

## Tech Stack

| Component | Version |
|-----------|---------|
| Spring Boot | 4.0.5 |
| idempotent-rds | 2.4.1 |
| PostgreSQL | 17 |
| Liquibase | managed by Spring Boot |
| Testcontainers | managed by Spring Boot |
| springdoc-openapi | 2.8.6 |
| REST-assured | 6.0.0 |

## Other Spring Boot Idempotency Tools

This test harness uses **arun0009/idempotent**, but several other libraries exist for adding idempotency to Spring Boot applications. 

| Document                                                | Summary |
|---------------------------------------------------------|---------|
| [01-library-comparison.md](ai-research/01-library-comparison.md) | Evaluates 7 libraries against the IETF Idempotency-Key standard. Covers TransferWise idempotence4j (PostgreSQL, no annotation support), Trendyol Jdempotent (annotation-based but Redis-only, stale), AWS Powertools (Lambda-only), pig-mesh starter (Redis-only, no response caching), arun0009/idempotent (best fit), dgrandemange/idempotence-receiver (abandoned), and Spring Integration (messaging-only). |
| [02-gate-evaluation.md](ai-research/02-gate-evaluation.md)         | Deep-dives into arun0009/idempotent v2.4.0 against 6 gates: Spring Boot 4.x compatibility, PostgreSQL support, configurable header, cached response replay, payload fingerprinting, and concurrent request handling. Result: 5/6 pass — only payload fingerprinting (IETF 422) is missing. |
| [03-implementation.md](ai-research/docs/03-implementation.md)      | Documents the integration: Maven dependency, Liquibase schema, configuration, controller annotation, AOP execution flow, auto-configuration, and known gaps (no payload fingerprinting, no mandatory key enforcement). |

**Key takeaway**: No existing library fully implements the IETF standard. arun0009/idempotent was selected for its annotation-based API, PostgreSQL storage, active maintenance on Spring Boot 4.x, and robust concurrent request handling.
