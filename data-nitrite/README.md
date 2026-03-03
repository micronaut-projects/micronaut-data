# Micronaut Data Nitrite (Implementation Notes)

This module provides the Micronaut Data runtime + query builder implementation for **NitriteDB**.

At a high level, Micronaut Data works in two phases:

- **Compile time (annotation processor)**: repository methods are analyzed and encoded into a `PreparedQuery` (query string + bindings).
- **Runtime (repository operations)**: the encoded query is executed against the backing store.

Nitrite’s implementation is intentionally aligned with the **Micronaut Data 5.0.x direction** (criteria-first / QueryBuilder2-style encoding), while still remaining usable on 4.x.

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

## Compile-Time Contract: `additionalRequiredParameters`

`QueryResult.getAdditionalRequiredParameters()` is consumed by the annotation processor to bind **repository method parameters** into the encoded query.

It must not be used as a generic “metadata” channel (for example markers like `"update" -> "true"`). Doing so can break compilation of implicit `CrudRepository` methods when `implicitQueries=true` because the processor will attempt to bind a method parameter named `"true"`.

## Versioning / Compatibility

Nitrite currently keeps a small delegating wrapper (`NitriteQueryBuilder2`) for compatibility with Micronaut Data versions that still try to load a `*QueryBuilder2` entry point.

In Micronaut Data **5.0.x**, where QueryBuilder2 is merged into QueryBuilder, this wrapper can be removed and `NitriteQueryBuilder` can remain as the sole entry point.

