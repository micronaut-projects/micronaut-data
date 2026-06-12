# Oracle BOOLEAN Support Design for Micronaut Data

## Goal

Support Oracle SQL `BOOLEAN` for Oracle Database 23.1 and later while preserving existing Oracle 19c/21c behavior.

The implementation should not add a new dialect if a cleaner, extensible option model can solve the problem. `Dialect.ORACLE` should remain the Oracle database family dialect, and version-gated behavior should be represented by resolved dialect options/capabilities.

## Background

Oracle SQL `BOOLEAN` is available starting with Oracle Database 23.1. Older Oracle versions, including 19c and 21c, do not support native SQL boolean columns or boolean SQL predicates in the same way.

Micronaut Data currently treats Oracle boolean values as legacy numeric boolean values:

- DDL maps `DataType.BOOLEAN` to `NUMBER(1)`.
- Boolean literals for Oracle render as `1` / `0`.
- JDBC boolean null binding for Oracle uses `Types.BIT`.
- Generated queries are stored at build time in Micronaut annotation metadata.

Because repository SQL can be generated into a jar and used by another application, runtime detection cannot be the primary source of truth for query generation. Runtime cannot safely rewrite already generated SQL.

## Design Direction

Do not add version-specific Oracle dialects as the first choice.

Instead, introduce generic SQL dialect options that are resolved during query generation and carried by the SQL query builder/configuration path. Runtime SQL binding then reads the same resolved options from `SqlStoredQuery` / `SqlPreparedQuery` through the SQL query builder.

This avoids growing `Dialect` for every version-gated feature.

## Configuration Model

Use `Dialect.ORACLE` plus generic dialect options.

Datasource schema-generation configuration shape:

```properties
datasources.default.dialect-options.version=23.1
```

Reasoning:

- Future Oracle version-gated features can reuse the same dot-separated version model.
- Users can express intent as a SQL target version, not isolated feature toggles.
- Avoid exposing or persisting individual feature toggles because unsafe combinations such as Oracle legacy target version plus native boolean would be possible.
- Oracle native boolean starts at the official 23.1.0 version, so the first Oracle native boolean target is `23.1`.

For R2DBC schema generation the same shape should apply under the R2DBC datasource prefix:

```properties
r2dbc.datasources.default.dialect-options.version=23.1
```

For repository query generation, the user-facing repository annotation can set the target version for one repository:

```java
@JdbcRepository(dialect = Dialect.ORACLE, version = "23.1")
interface ProductRepository {
}
```

The same member should exist on `@R2dbcRepository`. The annotation processor materializes this value into the internal SQL query configuration metadata:

```java
@SqlQueryConfiguration(@SqlQueryConfiguration.DialectConfiguration(dialect = Dialect.ORACLE, version = "23.1"))
```

`SqlQueryConfiguration` remains an internal/generated metadata path for SQL query builders, not the primary user-facing configuration.

Build-wide repository SQL generation uses dialect-scoped annotation processor options:

```properties
micronaut.data.sql.dialect-options.oracle.version=23.1
```

This follows the same option shape as datasource schema generation, but it is a build-time annotation processor option and includes the repository dialect in the property path. It can also be supplied as a JVM system property for test/build environments.

Future dialect-specific options can use the same namespace:

```properties
micronaut.data.sql.dialect-options.mysql.version=9.0
micronaut.data.sql.dialect-options.sql-server.version=16.0
```

Datasource schema configuration remains:

```properties
datasources.default.dialect-options.version=23.1
```

because the datasource already has a configured dialect. Repository compiler options need the extra dialect segment because one compilation unit can contain repositories for more than one SQL dialect.

Repository-level configuration takes precedence over the dialect-scoped compiler option.

The configured version is a SQL generation target. It tells Micronaut Data which SQL feature set it may use when it builds repository SQL, criteria SQL, bind mappings, and schema-generation SQL. It is not runtime database version detection, and Micronaut Data should not inspect JDBC or R2DBC connection metadata for the first implementation.

Accepted user input should be dot-separated numeric notation only:

```text
23
23.1
23.1.0
```

Underscore and dialect-prefixed forms such as `23_1`, `ORACLE_23_1`, or `ORACLE_23_1_0` should not be supported. Internally, `23.1` can be normalized to `23.1.0` before comparison with `SemanticVersion`.

## Resolved Options

Introduce an internal resolved options object used by query builders and runtime SQL operations.

Example shape:

```java
record SqlDialectOptions(
    Dialect dialect,
    Optional<String> version
) {
    boolean isVersionAtLeast(String requiredVersion) {
        // normalize dot-separated versions, then compare with SemanticVersion
    }
}
```

For default Oracle behavior:

```text
dialect = ORACLE
version = empty
isVersionAtLeast("23.1") = false
```

For Oracle 23.1+ behavior:

```text
dialect = ORACLE
version = 23.1
isVersionAtLeast("23.1") = true
```

For a later configured Oracle version:

```text
dialect = ORACLE
version = 23.4
isVersionAtLeast("23.1") = true
```

The options should be immutable after resolution.

Oracle boolean decisions should remain explicit at the call site:

```java
dialect == Dialect.ORACLE
    && dialectOptions.isVersionAtLeast(SqlDialectOptions.ORACLE_23_1_VERSION)
```

## Build-Time Flow

At annotation-processing/query-generation time:

1. Check whether the repository uses `SqlQueryBuilder`.
2. If the repository already has a non-empty `@SqlQueryConfiguration(@SqlQueryConfiguration.DialectConfiguration(dialect = ..., version = ...))`, keep it.
3. Resolve the repository dialect and derive the dialect-scoped build-time option, for example `micronaut.data.sql.dialect-options.oracle.version`.
4. If present, materialize it into repository annotation metadata as `@SqlQueryConfiguration(@SqlQueryConfiguration.DialectConfiguration(dialect = ..., version = "..."))`.
5. Let `SqlQueryBuilder(AnnotationMetadata)` resolve `SqlDialectOptions` from the repository metadata as today.
6. Generate SQL according to resolved options.
7. Store generated SQL as today.

The processor step is intentionally dialect-scoped but not Oracle-hardcoded. It can record a target version option for any SQL repository when that repository's dialect has a matching compiler option. Dialect-specific behavior remains explicit at the SQL rendering/binding call sites, for example:

```java
dialect == Dialect.ORACLE
    && dialectOptions.isVersionAtLeast(SqlDialectOptions.ORACLE_23_1_VERSION)
```

This keeps the option model extensible and avoids giving unsupported version strings cross-dialect behavior by accident. For example, `micronaut.data.sql.dialect-options.oracle.version=23.1` should not annotate MySQL repositories.

This avoids adding Oracle boolean state to `DefaultStoredQuery` and avoids adding a separate `DataMethodQuery` metadata member. The repository metadata already carries the generic SQL target version option, so runtime `SqlQueryBuilder` reconstruction sees the same setting used during query generation.

## Schema Generation Flow

Schema generation does not operate through repository methods and does not have access to `DataMethodQuery`, `SqlStoredQuery`, or `SqlPreparedQuery`.

JDBC schema generation currently reads `DataJdbcConfiguration`, including the datasource dialect, and creates SQL directly from mapped entities.

R2DBC schema generation does the same through `DataR2dbcConfiguration`.

Therefore schema generation must read the same option shape from datasource configuration:

```text
datasources.<name>.dialect
datasources.<name>.dialect-options.version

r2dbc.datasources.<name>.dialect
r2dbc.datasources.<name>.dialect-options.version
```

The implementation binds this nested datasource property through `@ConfigurationBuilder(prefixes = "set", configurationPrefix = "dialect-options")` on the JDBC and R2DBC datasource configuration classes.

The datasource builder targets should remain small module-local holders. They do not need to be standalone configuration beans, and sharing one holder type across JDBC and R2DBC can produce duplicate generated configuration-reference files. The public property shape should remain the same for both modules even if the backing holder classes are module-specific.

Repository query generation and schema generation can have different configuration sources, but they must resolve to the same internal model:

```text
Dialect.ORACLE + SqlDialectOptions{version=23.1}
```

Users who enable Micronaut Data schema generation should keep datasource `dialect-options` aligned with repository query-generation options. This mirrors the existing dialect split: repository methods use repository dialect metadata, while schema generation uses datasource dialect configuration.

## Runtime Flow

At runtime:

1. `DefaultSqlStoredQuery` exposes the SQL query builder through `SqlStoredQuery`.
2. `SqlStoredQuery#getDialectOptions()` resolves to `getQueryBuilder().getDialectOptions()`.
3. `DefaultSqlPreparedQuery` delegates to the same SQL stored query path.
4. JDBC binding uses these query options, not live environment configuration.

Runtime environment configuration must not override the mode for precompiled queries. If a jar was compiled with native Oracle boolean SQL, the runtime app cannot make those queries legacy unless Micronaut Data generates multiple query variants, which is out of scope.

## Runtime Metadata API

Use a SQL-specific runtime accessor.

Example:

```java
interface SqlStoredQuery<E, R> {
    default SqlDialectOptions getDialectOptions() {
        return getQueryBuilder().getDialectOptions();
    }
}
```

The SQL-specific option is cleaner because these options only matter to SQL repositories. `DefaultStoredQuery` does not need to carry the Oracle boolean mode.

Existing generated metadata defaults to legacy behavior when the member is absent.

For builds configured with dialect-scoped compiler options, the annotation processor materializes the version value into repository annotation metadata. This is important because runtime binding reconstructs the SQL query builder from repository metadata, not from the annotation processor environment.

## Query Generation Changes

Update Oracle boolean generation to depend on resolved options.

### DDL

Current Oracle behavior:

```sql
NUMBER(1)
```

Oracle with `23.1` target version:

```sql
BOOLEAN
```

Affected area:

- `SqlColumnMapping#getSqlType(...)`

This requires passing `SqlDialectOptions` into SQL type resolution, not only `Dialect`.

### Literals

Current Oracle behavior:

```sql
1
0
```

Oracle with `23.1` target version:

```sql
TRUE
FALSE
```

Affected area:

- `SqlQueryBuilder#asLiteral(...)`

### Predicates

Current legacy Oracle behavior must remain compatible with numeric boolean storage.

Oracle with `23.1` target version may use native boolean predicates:

```sql
active IS TRUE
active IS FALSE
WHERE active
WHERE NOT active
```

Use the native syntax only when the query is generated with Oracle 23.1 target version.

Affected area:

- `AbstractSqlLikeQueryBuilder#visitIsTrue(...)`
- `AbstractSqlLikeQueryBuilder#visitIsFalse(...)`
- Any boolean predicate rendering that can produce bare boolean expressions.

Legacy Oracle predicates remain numeric:

```sql
active = 1
active = 0
```

Native predicates are used only with Oracle 23.1 target version:

```sql
active IS TRUE
active IS FALSE
```

## Binding Changes

Binding should use the resolved query options from `SqlStoredQuery` / `SqlPreparedQuery`.

Current JDBC lookup:

```java
JdbcQueryStatement.findSqlType(DataType dataType, Dialect dialect)
```

Expected shape:

```java
JdbcQueryStatement.findSqlType(
    DataType dataType,
    Dialect dialect,
    SqlDialectOptions dialectOptions
)
```

Oracle behavior:

```java
if (dialect == Dialect.ORACLE
    && dataType == DataType.BOOLEAN
    && dialectOptions.isVersionAtLeast(SqlDialectOptions.ORACLE_23_1_VERSION)) {
    return Types.BOOLEAN;
}

if (dialect == Dialect.ORACLE && dataType == DataType.BOOLEAN) {
    return Types.BIT;
}
```

R2DBC behavior should follow the same resolved options, even if the final type mapping differs by driver.

R2DBC area:

- `DefaultR2dbcRepositoryOperations#findR2dbcType(DataType)`

Expected shape:

```java
findR2dbcType(DataType dataType, Dialect dialect, SqlDialectOptions dialectOptions)
```

## Runtime Version Validation

Do not require runtime database-version validation for the first implementation.

The selected Oracle target version is a build-time query-generation input. It is the user's responsibility to target a database version compatible with the generated SQL.

Reasons to avoid mandatory runtime validation:

- A repository may be built with Oracle 23 target version but contain no generated SQL that uses Oracle 23-only features. Rejecting that repository on Oracle 19c/21c would be stricter than the actual SQL requires.
- JDBC and R2DBC expose database version metadata differently. JDBC has structured major/minor access, but R2DBC SPI only exposes a vendor-formatted version string.
- R2DBC version strings are not reliably parseable across drivers and database branding.
- Mandatory validation adds lifecycle and multi-datasource complexity without being necessary for query execution.
- The generated SQL already acts as the real target version contract.

Optional diagnostics can be considered later, but should not be part of the core behavior.

Possible optional future property:

```properties
datasources.default.dialect-options.validate-version=true
```

If such diagnostics are added, JDBC can validate more reliably than R2DBC. R2DBC should remain best-effort only.

## Precompiled Jar Behavior

The resolved dialect options are part of the generated query artifact.

If a library jar is built with Oracle 23.1 target version options, it may contain SQL such as:

```sql
BOOLEAN
TRUE
FALSE
IS TRUE
WHERE active
TO_BOOLEAN(...)
```

That jar is not valid on Oracle 19c/21c for those repositories. This is expected and should be documented.

The portable default remains legacy Oracle behavior.

## Schema Migration and Mixed Schemas

This Micronaut Data design controls which Oracle SQL feature set generated queries target.

For the common cases:

- All legacy Oracle boolean columns use `NUMBER(1)`: use default legacy options.
- All native Oracle boolean columns use `BOOLEAN`: target Oracle 23.1.

Mixed schemas are harder:

```text
same Oracle 23.1+ database
some boolean columns are native BOOLEAN
some old boolean columns remain NUMBER(1)
```

A repository-level or dialect-scoped compiler option cannot fully model that. Native boolean predicates could be wrong for legacy `NUMBER(1)` columns.

Implementation should not attempt to solve mixed schemas unless there is a concrete requirement. Document that mixed schemas require either:

- keeping the repository in legacy mode until migration is complete; or
- separating repositories by version where possible; or
- explicit queries for columns that do not match the selected repository version.

## Target Version

Default behavior remains unchanged:

- `Dialect.ORACLE`
- no Oracle 23.1 target version option
- `NUMBER(1)` DDL
- legacy boolean literals/binding

This is backward-compatible for Oracle 19c/21c users.

Enabling Oracle 23.1 target version options is opt-in and should be treated as a minor feature addition.

## Implementation Areas

Affected areas:

- `data-model`
  - `SqlQueryBuilder`
  - `AbstractSqlLikeQueryBuilder`
  - `SqlColumnMapping`
  - new dialect options/capability model
  - `SqlStoredQuery` option accessor through the query builder
- `data-processor`
  - repository configuration resolution
  - materialize dialect-scoped SQL target version into repository metadata for matching SQL repositories
- `data-runtime`
  - `DefaultSqlPreparedQuery`
  - SQL runtime option access
- `data-jdbc`
  - boolean SQL type binding
- `data-r2dbc`
  - boolean R2DBC type binding
- tests
  - query builder unit tests for legacy/native options
  - JDBC Oracle 21c legacy integration test
  - JDBC Oracle 23.1+ native integration test
  - R2DBC Oracle 23.1+ native integration test if test infrastructure allows it

## Test Plan

Compile-time/unit tests:

- Oracle default options generate `NUMBER(1)`.
- Oracle 23.1 target version options generate `BOOLEAN`.
- Oracle default options render boolean literals as `1` / `0`.
- Oracle 23.1 target version options render boolean literals as `TRUE` / `FALSE`.
- Oracle default options avoid native-only boolean predicates.
- Oracle 23.1 target version options allow native boolean predicates.
- Generated metadata contains resolved dialect options.
- Missing generated options defaults to legacy behavior.
- A build-wide dialect-scoped compiler option is recorded only for matching SQL repositories.
- A dialect-scoped Oracle compiler option is not recorded for MySQL/Postgres repositories.
- Table generation tests cover Oracle legacy and Oracle 23.1 boolean DDL.

JDBC integration tests:

- Oracle 21c with default options persists `true`, `false`, and `null` using legacy storage.
- Oracle 23.1+ with native options persists `true`, `false`, and `null` using native `BOOLEAN`.

R2DBC integration tests:

- Oracle 23.1+ with native options persists `true`, `false`, and `null` using native `BOOLEAN`.
- Oracle 23.1+ with native options reads native boolean values back and uses generated boolean predicates.

## Open Questions

- Whether optional diagnostics should ever validate database target version at startup, and if so whether JDBC-only reliable validation is enough.

## Final Recommendation

Keep `Dialect.ORACLE` and introduce generic SQL dialect options.

Resolve those options at build time, materialize dialect-scoped compiler configuration into repository `@SqlQueryConfiguration` metadata, expose resolved options through `SqlStoredQuery` / `SqlPreparedQuery`, and use them for both SQL generation and binding.

Do not require runtime database version validation. The generated SQL and stored dialect options are the target version contract; optional diagnostics can be considered separately.

Do not attempt to solve mixed native and legacy boolean storage in this implementation.
