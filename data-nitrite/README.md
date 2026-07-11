# Micronaut Data Nitrite (Implementation Notes)

This module provides the Micronaut Data runtime + query builder implementation for **NitriteDB**.

## Scope (What This Module Aims to Support)

Nitrite is intended to be a practical embedded document store for **basic Micronaut Data usage**:

- `CrudRepository` operations (`save`, `update`, `deleteById`, `findById`, `existsById`, `count`, `findAll`)
- Common derived finders (`findByX`, `findByXAndY`, comparisons, basic `Like`/`Contains`)
- Paging and sorting (`Pageable`, method-name `OrderBy`)

Nitrite does not currently provide the broader JPA-style infrastructure (naming strategies, automatic association mapping/fetching, etc.) that some other persistence modules expose. Treat entities as flat documents and handle any related entity traversal manually or via custom helper methods.

## Transactions

Nitrite supports Micronaut `@Transactional` methods via `NitriteTransactionOperations` and the `NitriteTransactionHolder`. When a transaction is active, repository operations use the `Transaction` returned by the holder instead of the raw database so that all reads/writes participate in the same MVStore transaction and are committed or rolled back together. See `io.micronaut.data.nitrite.service.NitriteTransactionManagementService` and its spec (`data-nitrite/src/test/groovy/io/micronaut/data/nitrite/service/NitriteTransactionManagementSpec.groovy`) for examples that exercise the various propagation modes and rollback behavior.

Nitrite is **not** a feature-equivalent MongoDB replacement. Mongo-specific features such as BSON
filters, aggregation pipelines, and Mongo-only annotations are out of scope for this module.

At a high level, Micronaut Data works in two phases:

- **Compile time (annotation processor)**: repository methods are analyzed and encoded into a `PreparedQuery` (query string + bindings).
- **Runtime (repository operations)**: the encoded query is executed against the backing store.

Nitrite’s implementation is intentionally aligned with the **Micronaut Data 5.0.x direction** (criteria-first / QueryBuilder2-style encoding), while still remaining usable on 4.x.

## Building, Testing, and Publishing

Run Nitrite’s tests (the specs now live under `data-nitrite/src/test`):

- All Nitrite tests: `./gradlew :data-nitrite:test`
- Single test class: `./gradlew :data-nitrite:test --tests '*NitriteDocumentRepositorySpec'`
- Single test method: `./gradlew :data-nitrite:test --tests '*NitriteDocumentRepositorySpec.test update'`

Publish to Maven Local (so another project can consume it):

- All modules: `./gradlew publishToMavenLocal`
- Nitrite only (if supported by the build): `./gradlew :data-nitrite:publishToMavenLocal`

## Query Encoding

Nitrite supports two query shapes at runtime:

- **JSON filter** (criteria encoding) produced by `NitriteQueryBuilder`, e.g.:
  - `{"title":{"$eq":"$mn_qp:0"}}`
- **SQL-like SELECT/DELETE** (only where needed) produced by `micronaut-data-document-processor`.

### Parameter Binding

Two placeholder styles exist:

- `"$mn_qp:<index>"` – used by criteria encoding. The index maps to the `PreparedQuery` parameter array.
- `":name"` – used by user-authored JSON `@Query` methods. Names are bound using query bindings when available, otherwise by falling back to `PreparedQuery.getArguments()` names.

## Updates and `$set`

Nitrite updates are **partial updates** represented as a document of fields to change.

Some query encodings (and JSON `@Query` methods) use a Mongo-style wrapper:

```json
{"id": :id, "$set": {"title": :title}}
```

At runtime, Nitrite **must not** receive a document with a literal `"$set"` key. Doing so would create a `"$set"` field in the stored document and would not update the intended properties.

`DefaultNitriteRepositoryOperations` therefore unwraps `$set` into a plain update document before calling `collection.update(...)`.

## Numeric Equality and Type Coercion

Depending on how values flow through the mapping layer, Nitrite/Jackson may store a numeric field as different Java numeric types (for example `Long` vs `BigDecimal`).

To keep repository methods like `findById(Long)` stable, equality filters are tolerant to numeric representation differences by expanding `"$eq"` into an `OR` over common numeric shapes.

## Instant Storage

Nitrite is configured with Jackson’s `JavaTimeModule`. In practice, `Instant` values are stored as a numeric timestamp, so the runtime normalizes `Instant` query parameters to an **epoch-second double** (including fractional seconds) to keep equality and ordering comparisons consistent.

## Compile-Time Contract: `additionalRequiredParameters`

`QueryResult.getAdditionalRequiredParameters()` is consumed by the annotation processor to bind **repository method parameters** into the encoded query.

It must not be used as a generic “metadata” channel (for example markers like `"update" -> "true"`). Doing so can break compilation of implicit `CrudRepository` methods when `implicitQueries=true` because the processor will attempt to bind a method parameter named `"true"`.

## Troubleshooting

- **Compilation fails with** `A @Where(..) definition requires a parameter called [true] ...`:
  - This almost always means the query builder returned `"true"` (or `true`) inside `QueryResult.getAdditionalRequiredParameters()`.
  - Fix by ensuring `additionalRequiredParameters` is only used for **actual repository method parameters** that must be bound into the query.
- **Runtime fails with** `Unsupported query format. Expected JSON filter or SELECT/DELETE ... got: {"id": :id, "$set": ...}`:
  - The runtime is trying to parse an **update wrapper document** as a filter.
  - Fix by unwrapping `$set` before calling the filter parser and treating it as an update document.

## Versioning / Compatibility

Nitrite currently keeps a small delegating wrapper (`NitriteQueryBuilder2`) for compatibility with Micronaut Data versions that still try to load a `*QueryBuilder2` entry point.

In Micronaut Data **5.0.x**, where QueryBuilder2 is merged into QueryBuilder, this wrapper can be removed and `NitriteQueryBuilder` can remain as the sole entry point.
