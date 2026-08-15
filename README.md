# Limited Drop reservation service

A Spring Boot 3 / Java 21 backend for contention-heavy, limited inventory drops. It provides JWT-authenticated holds, confirmation, cancellation, expiry recovery, live availability, Redis-backed static metadata caching, and RabbitMQ events.

## Run it

The only prerequisite is Docker with Compose:

```bash
docker-compose up --build
```

The API is at `http://localhost:8080`. RabbitMQ's management UI is at `http://localhost:15672` (guest/guest). The compose profile uses development JWT signing solely to make the demo self-contained.

Get a development token, inspect seed data, then make a hold:

```bash
TOKEN=$(curl -sS -X POST http://localhost:8080/api/v1/dev/tokens \
  -H 'Content-Type: application/json' \
  -d '{"customerId":"alice"}' | jq -r .accessToken)

curl -sS http://localhost:8080/api/v1/drops \
  -H "Authorization: Bearer $TOKEN"

curl -sS -X POST http://localhost:8080/api/v1/drops/<DROP_ID>/holds \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: 7a1d0f0b-7cf0-4df5-b2a6-0ca3b966cb79' \
  -H 'Content-Type: application/json' \
  -d '{"quantity":2}'

curl -sS -X POST http://localhost:8080/api/v1/holds/<HOLD_ID>/confirm \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Idempotency-Key: e4fec5c0-9f31-42e7-a291-d698d5517b91'
```

Cancel an active hold with `DELETE /api/v1/holds/{holdId}`. Health is available without authentication at `/actuator/health`.

To remove local volumes after the demo, run `docker-compose down -v`.

## API

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/v1/drops` | List drops with live remaining inventory |
| `GET` | `/api/v1/drops/{dropId}` | Get a drop with live remaining inventory |
| `POST` | `/api/v1/drops/{dropId}/holds` | Create/replay a hold; requires `Idempotency-Key` |
| `GET` | `/api/v1/holds/{holdId}` | Fetch the authenticated customer's hold |
| `POST` | `/api/v1/holds/{holdId}/confirm` | Confirm an active hold; requires `Idempotency-Key` |
| `DELETE` | `/api/v1/holds/{holdId}` | Cancel an active hold |

The JWT `sub` claim is the customer ID. A customer cannot read or change another customer's hold. In production configure either `APP_SECURITY_ISSUER_URI` (OIDC discovery) or `APP_SECURITY_JWK_SET_URI`; never enable the development token endpoint or use the shared HMAC secret in production.

## Correctness design

### Inventory invariant

`available_units` is the authoritative, bounded inventory counter in MySQL. The invariant is:

```text
available_units + units in active holds + confirmed units = total_units
0 <= available_units <= total_units
```

Hold creation runs this conditional update and inserts the hold in the same database transaction:

```sql
UPDATE drops
SET available_units = available_units - :quantity
WHERE id = :dropId
  AND available_units >= :quantity
  AND opens_at <= CURRENT_TIMESTAMP;
```

An affected-row count of zero means the drop does not exist, has not opened, or lacks capacity. This single statement is the oversell guard: concurrent transactions cannot drive the counter negative. The hold insert is in the same transaction, so an insert failure rolls the decrement back.

Confirming a hold only changes `ACTIVE -> CONFIRMED`; it never decrements inventory again. Cancellation and expiry lock the hold row, verify it remains active, change state, and add its quantity back in that same transaction. That makes retries and concurrent cancel/expiry/confirm races safe: only the first valid transition releases or claims the units.

The create idempotency key is unique per `(drop, customer, key)`. A replay with the same quantity returns the original hold; a changed quantity returns `409`. The confirmation endpoint also requires an idempotency key, while confirmation itself is state-idempotent: retrying after success returns the confirmed hold.

### Holds and expiry

The default hold period is `PT10M`, configurable with `APP_RESERVATIONS_DEFAULT_HOLD_DURATION`. A drop may override this at creation/seed time. Each hold stores its actual expiry timestamp, so changing configuration does not alter existing promises.

The scheduled expiry job scans expired active IDs in bounded batches and locks/validates each hold again before expiring it. Multiple application replicas may process the same candidate, but the lock and state check allow only one to release inventory. Confirm/cancel performs the same expiry check lazily, so capacity is reclaimed even if the background worker is delayed.

### Redis

Redis caches static drop metadata (title, opening time, total capacity, and configured hold period). Remaining inventory is deliberately read from MySQL on every public drop response, satisfying the real-time availability requirement. Redis cache errors are swallowed and fall back to MySQL; it has no role in admission, locking, or inventory accounting.

### RabbitMQ and the outbox

Each committed hold state change writes an `outbox_events` row in the same transaction. A background publisher sends unpublished events to the durable `drop.events` topic exchange and marks an event published only after a RabbitMQ publisher confirmation. Events are at-least-once: consumers must de-duplicate by the supplied `eventId`. RabbitMQ outage delays delivery but never blocks or reverses valid reservations.

Routing keys are `hold.created`, `hold.confirmed`, `hold.cancelled`, and `hold.expired`. The included durable `drop.events.audit` queue binds `hold.#` so the demo exposes the topology immediately.

## Configuration

All connection details are environment configurable:

| Setting | Default outside Compose |
|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:mysql://localhost:3306/limited_drop` |
| `SPRING_DATA_REDIS_HOST` | `localhost` |
| `SPRING_RABBITMQ_HOST` | `localhost` |
| `APP_SECURITY_ISSUER_URI` | none |
| `APP_SECURITY_JWK_SET_URI` | none |
| `APP_SECURITY_HMAC_SECRET` | none outside `dev` |
| `APP_RESERVATIONS_DEFAULT_HOLD_DURATION` | `PT10M` |
| `APP_RESERVATIONS_EXPIRY_SCAN_DELAY` | `PT5S` |

OIDC issuer configuration takes precedence over direct JWKS configuration, which takes precedence over HMAC. Application startup fails if none is configured (except under the explicit `dev` profile).

## Tests

```bash
mvn test
```

No test needs MySQL, Redis, or RabbitMQ. Mockito tests cover service state transitions, idempotency replay, and exact inventory release. An embedded H2 MySQL-mode test launches 20 concurrent conditional reservations against five units and proves exactly five succeed.

## Trade-offs and next work

- MySQL availability is intentionally strongly consistent. At very high write contention, a single popular drop becomes a hot row. This is the correct baseline; the next scaling step would be a carefully proven allocation/sharding strategy, not a cache-based counter.
- The worker currently discovers candidates before locking one at a time. It is safe across replicas but can repeat work. A MySQL `FOR UPDATE SKIP LOCKED` claim query would reduce repeated scans when profiling proves it worthwhile.
- Outbox delivery is at-least-once. Exactly-once delivery is not realistic across a database and broker without consumer idempotency; event IDs make consumer deduplication practical.
- Inventory display is a current MySQL read. This favors correctness and transparency over catalog-read throughput. A separately labeled, short-lived availability projection could be added for read-heavy browsing, but it must never decide admissions.
- Production would add authorization scopes, request tracing propagated into events, rate limiting at the edge, metrics/alerts for expired-outbox age and inventory anomalies, and MySQL/RabbitMQ integration smoke tests in CI.
