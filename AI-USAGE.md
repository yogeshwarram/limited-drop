# AI usage

## Tools and context

I used Codex (GPT-5) as a design and implementation partner in the workspace. I supplied the full assignment constraints: Java/Spring Boot, MySQL as the database, Redis, RabbitMQ, Docker Compose startup, infrastructure-free JUnit/Mockito tests, and the non-negotiable no-oversell rule. I also explicitly asked the user to approve material design choices before implementation. The user selected JWT authentication, configurable hold duration, required idempotency keys, transactional-outbox delivery, and real-time availability; they then approved OIDC/JWKS production validation plus a development HMAC JWT profile.

I used workspace inspection to establish that this was an empty repository and checked the locally installed runtime before planning verification. The system Java launcher was unavailable, so I used the bundled IDE Java runtime for local tests. Docker was initially absent; after the user explicitly authorized installation and completed Docker Desktop's privileged first-run prompt, I installed it and performed the Compose smoke test below.

## Suggestions accepted

1. **Accepted: atomic conditional MySQL inventory update.** The suggestion was to use one `UPDATE ... WHERE available_units >= :quantity` as the admission control mechanism, rather than a read-then-write sequence. I accepted it because the affected-row result is atomic under concurrent transactions and directly protects the inventory invariant.

2. **Accepted: transactional outbox with publisher confirms.** The suggestion was to persist an event in the same transaction as the hold transition, publish it asynchronously, and mark it delivered only after a RabbitMQ broker confirmation. I accepted it because a synchronous broker call would either make reservation availability depend on RabbitMQ or risk losing a committed event.

3. **Accepted: keep Redis outside the consistency boundary.** The suggestion was to cache only static drop metadata and fetch `available_units` from MySQL. I accepted it because the user requested real-time remaining units and Redis counters/locks introduce split-brain and recovery complexity into the exact place that must not oversell.

4. **Accepted: production OIDC/JWKS validation plus a development-only HMAC token path.** I accepted it because OIDC integrates with a real identity provider, while the HMAC profile and explicitly enabled local token endpoint make `docker-compose up --build` usable without deploying an identity provider.

## Suggestions rejected

1. **Rejected: Redis `DECRBY` as the stock reservation authority.** Although fast, a Redis counter plus durable hold rows requires reconciliation after Redis persistence/failover errors and creates a second source of truth. MySQL already owns the transaction that creates the hold, so the counter must remain there.

2. **Rejected: an application-level `synchronized` block or a distributed Redis lock around each drop.** JVM locks only protect one process; distributed locks add lease-expiry and split-brain failure modes. The database conditional update works across every service replica without a lock service.

3. **Rejected: confirm by decrementing stock.** A hold already removed units from availability, so decrementing again on confirmation would silently undercount remaining units. Confirmation is only an `ACTIVE -> CONFIRMED` state change.

4. **Rejected: publishing RabbitMQ messages directly from the request transaction.** If MySQL commits after a broker failure, the event is lost; if the broker succeeds before a database rollback, consumers observe a state that never existed. The outbox resolves both failure windows.

5. **Rejected: caching the live availability response.** Even short TTLs can misrepresent a scarce drop and, worse, tempt the system to use stale data for admission. Static catalog metadata is a safe cache target; the atomic MySQL update remains the only availability decision.

6. **Accepted: scope-protected additive admin capacity operations.** The user approved standard `drops:manage` OAuth2 scopes, all-or-nothing batches, required admin idempotency keys, and additive capacity only. This keeps operator workflows useful without permitting arbitrary writes to the inventory counter.

7. **Accepted: database audit plus outbox event for admin writes.** Admin changes retain actor, reason, and before/after values locally while also using the existing at-least-once event pipeline. This supports both operator investigation and downstream consumers without coupling the request to RabbitMQ availability.

8. **Rejected: direct absolute replacement of available units.** It is convenient for an admin UI but cannot distinguish active holds from sellable stock; additive total/available updates preserve the same invariant as reservation and expiry.

## How I checked the result

- I traced each transition against the invariant: create subtracts once; confirm subtracts zero; cancel and expiry add once; terminal states cannot transition again.
- The service tests assert those transitions, including an expired confirmation that returns inventory before reporting a conflict and a replay that never reserves again.
- The repository test runs 20 simultaneous one-unit attempts against five units using H2's MySQL mode and asserts that exactly five updates succeed and no availability remains.
- I inspected transaction boundaries: hold creation rolls its inventory update back if the hold insert/outbox write fails; cancellation/expiry inventory return and state mutation share a transaction; event delivery is deliberately separate from admission.
- The full Java 21 Maven suite passes, including the 20-way concurrent conditional-update test. The project itself targets Java 21.
- The Dockerfile built successfully and the Compose stack reached healthy status for MySQL, Redis, RabbitMQ, and the application. I issued a development JWT, created and confirmed a one-unit hold, and observed live availability move `11 -> 10 -> 10`, proving confirmation did not decrement stock twice.
- I checked RabbitMQ's durable audit queue through its local management API after the flow; it contained the published hold events. This also found two production-only issues (a Flyway/JPA identifier-column mismatch and Redis cache type-erasure), both of which were fixed with a forward-only migration and a typed-safe cache serializer before repeating the successful flow.
- For admin management, focused tests prove ordinary JWTs receive `403`, `drops:manage` succeeds, idempotent create replay does not write twice, duplicate/unknown bulk targets fail, and capacity updates change total and available units together. The full Java 21 suite and a live Compose admin smoke flow are the final checks for this extension.

## Review follow-up

A subsequent repository-wide review found four production edge cases. I accepted fixes that bind JWTs to the configured audience and issuer, isolate expiry work from RabbitMQ confirmation delays, evict changed drop metadata only after a capacity transaction commits, and reject integer capacity overflow without partially mutating the entity. Focused regression tests cover each behavior. After these changes, the Docker image compiled successfully and the full Java 21 suite passed with 85 tests.

A later soak test showed the original outbox publisher could not keep up: it polled 100 rows every two seconds, waited for each confirmation serially, and let replicas select the same rows. I replaced that path with leased `SKIP LOCKED` claims, configurable 1,000-event batches, concurrent publisher confirms, bulk database outcome updates, and delayed retries during broker failures. I also made the unconsumed demo audit queue opt-in so successful publication does not merely move an unbounded backlog into RabbitMQ. The exact competing-claim query was checked on MySQL 8.4, the production image built successfully, and the full Java 21 suite passed with 92 tests.
