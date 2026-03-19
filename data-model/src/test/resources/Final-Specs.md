# Micronaut JSON Schema Registry

---

## Project Overview

Micronaut JSON Schema Registry adds startup-time schema reconciliation across application models, Confluent Schema Registry, and Oracle Database JSON Domains. For each logical schema, the module resolves an authoritative JSON Schema according to configuration and reconciles configured targets using that schema:

  - Confluent Schema Registry (if configured)
  - Oracle Database 23ai as JSON Domains (if configured)

This provides a configurable authoritative source for JSON validation across layers (serialization, eventing, persistence), with reconciliation to configured targets to minimize schema drift.

---

## Concepts

### Glossary

  - SR: Schema Registry (Confluent Schema Registry)
  - FQCN: Fully Qualified Class Name (e.g., com.acme.OrderCreated)
- Logical schema: a reconciliation unit identified by the selected authority object together with its resolved cross-target pairing and, in `application` authority mode, by discovered `@JsonSchema` types.
  - Annotation-driven discovery: in `application` authority mode, `@JsonSchema` types identify logical schemas to reconcile.
  - Authoritative schema resolution: Obtain the candidate schema from the configured authority (`application`, `sr`, or `oracle`).
  - Oracle drift detection: Compare the current domain validation schema to the candidate using normalized schema text equivalence.
  - Reconciliation: Sync non-authoritative targets (SR/Oracle) to match the candidate schema, governed by per-target policy and target capabilities.
  - Idempotency: Equivalent → no-op. Different → perform the target’s `manage` behavior (SR: register new version; Oracle: create-if-missing only) or report drift (`observe_only`). (For existing Oracle domains, drift is report-only in this version.)

### Authoritative schema source (normative)

For each reconciliation run, the implementation MUST use exactly one authoritative schema source per logical schema.

Supported values for `json-schema.registry.authority` are:

- `application`: generate schema from application `@JsonSchema` types and treat the generated schema as authoritative
- `sr`: read the latest schema for the resolved subject from Confluent Schema Registry and treat it as authoritative
- `oracle`: read the current Oracle domain validation schema for the resolved domain and treat it as authoritative

The authoritative schema for a run is called the `candidate` schema in this specification.

Non-authoritative systems are reconciliation targets. Their configured policy mode (`manage` or `observe_only`) determines whether the implementation writes changes or only reports drift.

The authority source MUST NOT be treated as a reconciliation target for the same logical schema in the same run.

If `json-schema.registry.authority=sr`, SR MUST be configured and reachable as the authority source. If `json-schema.registry.authority=oracle`, Oracle MUST be configured and reachable as the authority source.

When `json-schema.registry.authority` is `sr` or `oracle`, application-generated schemas MAY be computed for diagnostics, but they are not reconciliation targets and do not define correctness.

Default: `application`.

Outcome vocabulary (normative)

- `missing_authority`: the configured authority does not provide a schema for the selected logical schema
- `unreadable_authority`: the configured authority object exists, but its schema cannot be extracted or parsed
- `missing_target`: the non-authoritative target does not exist
- `equivalent`: target schema and candidate are equivalent after required normalization/projection
- `drift`: target schema differs from the candidate after required normalization/projection
- `projection_incompatibility`: the candidate cannot be represented in the target system's supported schema model
- `failed`: reconciliation could not complete due to target I/O, authorization, compatibility rejection, or other operational failure

Projection policy (normative)

- Projection from the candidate to a target representation MUST be classified as either `exact` or `incompatible` in this version.
- The implementation MUST NOT silently perform lossy projection.
- If a target representation would require dropping, rewriting, or ignoring candidate semantics beyond the target's defined schema model, the implementation MUST report `projection_incompatibility`.
- Future versions MAY add configurable lossy projection policies, but they are out of scope for this specification.

Authority mode decision table (normative)

| Authority | Candidate source | Reconciliation targets | Application-generated schema role |
| --- | --- | --- | --- |
| `application` | JSON Schema generated from discovered `@JsonSchema` types | `sr`, `oracle` (if enabled) | authoritative |
| `sr` | latest SR schema for the resolved subject | `oracle` (if enabled) | optional diagnostic comparison only |
| `oracle` | current Oracle domain validation schema for the resolved domain | `sr` (if enabled) | optional diagnostic comparison only |

For a given logical schema, the implementation MUST apply exactly one row of this table in a reconciliation run.

### Normalized schema text (normative)

This specification relies on a deterministic textual form of a JSON Schema document called **normalized schema text**. Normalized schema text is used to decide whether a remote schema/domain definition is **equivalent** to the candidate.

**Normalization rules**:

- Input is any JSON value representing a JSON Schema document (including boolean schemas).
- The normalized schema text is produced by serializing the JSON value with:
  - **Stable object key ordering**: object keys are ordered lexicographically (Unicode code point order) at all nesting levels.
  - **Minified output**: no insignificant whitespace.
  - **UTF-8 encoding**.
  - **Array order preserved** exactly as in the input.
- Normalization **does not** dereference `$ref` (local or remote). `$ref` is treated as ordinary JSON data.
- Normalization **does not** mutate schema content (no dropping of keywords, no default injection). It is purely a formatting/ordering transform.

Note (normative)

- Normalization SHOULD be implemented by parsing schemas into a JSON tree model and re-serializing. Within a single reconciliation run, the same JSON library and serialization settings MUST be used for both the current and candidate schema normalization.

**Equivalence**:

- Two schemas are considered **equivalent** if and only if their normalized schema text is byte-for-byte identical.

Note (normative)

- “Byte-for-byte identical” refers to the UTF-8 encoded bytes of the normalized schema text serialization.
- No Unicode normalization (for example, NFC/NFD) is performed beyond what the JSON parser produces.

---

## Policy Model

Each target has an independent policy mode.

Authority selection and target policy mode are independent:

- Authority selection determines where the candidate schema comes from.
- Per-target policy mode determines how each non-authoritative target is handled (`manage` or `observe_only`).

### Per-target behaviors

**SR**
  - `manage`: read latest; register new versions when needed (SR compatibility rules apply)
  - `observe_only`: read-only drift detection (missing/equivalent/drift); no HTTP writes

**Oracle**
  - `manage`: create domain if missing; if domain exists, do not change validation schema (report drift)
  - `observe_only`: read-only drift detection (missing vs exists; if exists: equivalent vs drift); no DDL

### Per-target policy mode

If a target is enabled and its policy mode is not specified, the default is `manage`.

Note

- Defaulting to `manage` enables writes/DDL (SR registrations; Oracle `CREATE DOMAIN`). For audit-first rollouts, use `observe_only`.

---

## Requirements

  - Micronaut 4.x+; Java 17+.
  - Reuse Micronaut JSON Schema runtime APIs for schema generation in `application` authority mode; new json-schema-registry module orchestrates startup reconciliation.
  - Non-breaking by default: feature activates only when configured.

---

## Feature Descriptions

### Feature 1: Schema Resolution

Schema resolution identifies the logical schemas the registry must reconcile and obtains the candidate schema for each one.

  - A logical schema is a reconciliation unit for which the implementation can resolve subject and/or domain names using the naming rules in this specification.
  - The authoritative schema is resolved according to `json-schema.registry.authority`:
    - `application`: generate a JSON Schema from the discovered type using Micronaut JSON Schema runtime APIs
    - `sr`: retrieve the latest schema for the resolved subject from Schema Registry
    - `oracle`: retrieve the current domain validation schema for the resolved domain from Oracle metadata
  - If the configured authoritative source does not contain the schema for a logical schema selected for reconciliation, the implementation MUST report `missing_authority` and proceed according to global fail-fast vs best-effort semantics.

Notes:
  - In `application` authority mode, the registry module performs runtime discovery of `@JsonSchema` types and uses Micronaut JSON Schema runtime APIs (e.g., `io.micronaut.jsonschema.JsonSchemaMapper.generateSchemaFor(Class<?>)`) to produce candidate schemas for reconciliation.
  - In `sr` and `oracle` authority modes, subject/domain names determine which logical schemas are selected for reconciliation.
  - Schema generation settings (library version and configuration) SHOULD be consistent across environments/runs to avoid reporting drift due to generator changes.
  - In `sr` and `oracle` authority modes, application-generated schemas MAY still be computed for comparison/reporting, but they are not authoritative unless `json-schema.registry.authority=application`.

#### Logical schema selection (normative)

Logical schema selection determines which reconciliation units are processed in a run.

- In `application` authority mode:
  - The implementation MUST discover application types annotated with `@JsonSchema`.
  - Each discovered type defines one logical schema.
- In `sr` authority mode:
  - The implementation MUST select logical schemas from SR subjects discovered from the authoritative SR source and/or from configured SR subject names.
  - Subject selection MAY use `json-schema.registry.sr.subjects` to explicitly include or narrow the selected subject set.
  - Subject discovery/selection MUST be explicit and deterministic.
  - Subject selection MUST NOT require discovering `@JsonSchema` types.
- In `oracle` authority mode:
  - The implementation MUST select logical schemas from Oracle domains discovered from the authoritative Oracle source and/or from configured Oracle domain names.
  - Domain selection MAY use `json-schema.registry.oracle.domains` to explicitly include or narrow the selected domain set.
  - Domain discovery/selection MUST be explicit and deterministic.
  - Domain selection MUST NOT require discovering `@JsonSchema` types.

If no logical schemas are selected for the configured authority mode, the implementation MUST log the condition and complete the run as a no-op unless global fail-fast policy treats empty selection as an error.

Selection precedence (normative)

- `application` authority: logical schemas are discovered from `@JsonSchema` types.
- `sr` authority:
  1) Start from SR discovery using configured discovery rules.
  2) If `json-schema.registry.sr.subjects` is provided, narrow the selected set to those subjects.
- `oracle` authority:
  1) Start from Oracle discovery using configured discovery rules.
  2) If `json-schema.registry.oracle.domains` is provided, narrow the selected set to those domains.

If explicit narrowing produces an empty set, the run is a no-op unless global fail-fast policy treats empty selection as an error.

#### Logical schema mapping (normative)

Each selected logical schema MUST be pairable to both an SR subject and an Oracle domain name.

- By default, the implementation MUST derive cross-target pairing automatically using the naming rules in this specification.
- In `sr` authority mode, the implementation MUST derive the Oracle domain name from the resolved SR subject by first deriving the logical FQCN and then applying Oracle domain naming rules.
- In `oracle` authority mode, the implementation MUST derive the SR subject name from the resolved Oracle domain by first deriving the logical FQCN and then applying SR subject naming rules.
- If the missing side cannot be derived deterministically, the implementation MUST report `failed` for that logical schema and include `missing_mapping` in logs.
- Explicit pairing configuration MAY be provided only to override or disambiguate derived naming.
- Explicit pairing configuration, when present, MUST take precedence over derived naming.
- Explicit pairing configuration defines name pairing only; it does not make mappings a logical schema selection source in this version.

Explicit mapping configuration uses `json-schema.registry.mappings`, where each entry defines one logical schema with:

- `subject`: SR subject name
- `domain`: Oracle domain name

Example:

```properties
json-schema.registry.mappings[0].subject=com.acme.OrderCreated
json-schema.registry.mappings[0].domain=APP_COM_ACME_ORDERCREATED
json-schema.registry.mappings[1].subject=com.acme.InvoiceIssued
json-schema.registry.mappings[1].domain=APP_COM_ACME_INVOICEISSUED
```

If `json-schema.registry.mappings` is provided:

- The implementation MUST use it only as an override/disambiguation source for the specified entries.
- `json-schema.registry.sr.subjects` and `json-schema.registry.oracle.domains` MAY be omitted if all selected logical schemas are otherwise discoverable and pairable.
- If a configured subject or domain is selected but has no explicit override and deterministic derivation is not possible, the implementation MUST report `failed` for that logical schema.

#### Authority discovery rules (normative)

- `sr` authority discovery:
  - The implementation MUST support subject discovery constrained by `json-schema.registry.naming.subject.prefix`.
  - The implementation MAY support additional include/exclude filtering, but prefix-based discovery is the minimum required behavior in this version.
- `oracle` authority discovery:
  - The implementation MUST support domain discovery constrained by `json-schema.registry.naming.domain.prefix`.
  - The implementation MAY support owner/schema scoping and additional include/exclude filtering, but prefix-based discovery is the minimum required behavior in this version.

Implementations MUST document the exact discovery query/API behavior used for SR subject enumeration and Oracle domain enumeration.

#### Authority modes (normative)

The implementation MUST support the following reconciliation modes:

- `application` authority:
  - Discover `@JsonSchema` types.
  - Generate the candidate schema from the discovered type.
  - Reconcile enabled non-authoritative targets (SR and/or Oracle) against that candidate.
- `sr` authority:
  - Resolve the logical schema to an SR subject.
  - Read the latest schema for that subject and treat it as the candidate.
  - Reconcile enabled non-authoritative targets (Oracle) against that candidate.
- `oracle` authority:
  - Resolve the logical schema to an Oracle domain.
  - Read the current Oracle domain validation schema and treat it as the candidate.
  - Reconcile enabled non-authoritative targets (SR) against that candidate.

This specification does not define a three-way merge or multi-master model. Reconciliation is always pairwise from the configured authority to each enabled non-authoritative target.

#### `authority=sr` reconciliation procedure (normative)

For each logical schema selected by SR authority discovery and optional `json-schema.registry.sr.subjects` narrowing, the implementation MUST:

1) Resolve the SR subject for the logical schema.
2) Retrieve the latest SR schema via `GET /subjects/{subject}/versions/latest`.
3) If SR returns 404 for the authoritative subject, report `missing_authority` and proceed according to global fail-fast vs best-effort semantics.
4) Parse and normalize the SR schema; this is the candidate.
5) Resolve the Oracle domain name for the same logical schema.
6) Project the candidate to the Oracle-supported schema model.
7) If projection is not exact, report `projection_incompatibility` and proceed according to global fail-fast vs best-effort semantics.
8) If Oracle target is disabled, complete reconciliation for this logical schema.
9) If Oracle target is enabled:
   - Check whether the Oracle domain exists.
   - If missing:
     - In `json-schema.registry.oracle.policy.mode=manage`, create the domain using the projected candidate.
     - In `json-schema.registry.oracle.policy.mode=observe_only`, report `missing_target`.
   - If present:
     - Retrieve the current Oracle domain validation schema using the Oracle extraction order defined in Feature 3.
     - If the current Oracle schema cannot be extracted or parsed, report `unreadable_authority` only when `json-schema.registry.authority=oracle`; otherwise report `drift` for the Oracle target and include the extraction failure reason in logs. In this version, unreadable non-authoritative Oracle target state is classified as target drift.
     - Compare the projected candidate to the current Oracle domain schema using normalized schema text equivalence.
     - If equivalent: report `equivalent` / no-op.
     - If different: report `drift`.
       - In `json-schema.registry.oracle.policy.mode=manage`, no in-place update is performed in this version; drift is report-only.
       - In `json-schema.registry.oracle.policy.mode=observe_only`, report drift with no DDL.

Implementation checklist (`authority=sr`)

1) Enumerate candidate SR subjects using discovery rules and optional explicit narrowing
2) Resolve subject/domain pairing using naming rules by default, and mappings only when override/disambiguation is required
3) Read latest SR schema; if missing, report `missing_authority`
4) Normalize the candidate and project it to Oracle-supported form
5) If projection is not exact, report `projection_incompatibility`
6) Read Oracle state and compare projected candidate to current domain schema
7) Apply Oracle target policy (`manage` create-if-missing, otherwise observe/report)

#### `authority=oracle` reconciliation procedure (normative)

For each logical schema selected by Oracle authority discovery and optional `json-schema.registry.oracle.domains` narrowing, the implementation MUST:

1) Resolve the Oracle domain for the logical schema.
2) Retrieve the current Oracle domain validation schema using the Oracle extraction order defined in Feature 3.
3) If the authoritative Oracle domain is missing, report `missing_authority` and proceed according to global fail-fast vs best-effort semantics.
4) If the authoritative Oracle domain exists but its schema cannot be extracted or parsed, report `unreadable_authority` and proceed according to global fail-fast vs best-effort semantics.
5) Parse and normalize the Oracle schema; this is the candidate.
6) Resolve the SR subject for the same logical schema.
7) If SR target is disabled, complete reconciliation for this logical schema.
8) If SR target is enabled:
   - Retrieve the latest SR schema via `GET /subjects/{subject}/versions/latest`.
   - If SR returns 404:
     - In `json-schema.registry.sr.policy.mode=manage`, register the candidate.
     - In `json-schema.registry.sr.policy.mode=observe_only`, report `missing_target`.
   - If SR latest exists:
     - Compare the latest SR schema to the candidate using normalized schema text equivalence.
     - If equivalent: report `equivalent` / no-op.
     - If different:
       - In `json-schema.registry.sr.policy.mode=manage`, register the candidate as a new version.
       - In `json-schema.registry.sr.policy.mode=observe_only`, report `drift`.
   - If SR rejects registration due to compatibility, mode, authorization, or other write failure, report `failed` and follow SR failure semantics.

Implementation checklist (`authority=oracle`)

1) Enumerate candidate Oracle domains using discovery rules and optional explicit narrowing
2) Resolve subject/domain pairing using naming rules by default, and mappings only when override/disambiguation is required
3) Read Oracle schema; if missing, report `missing_authority`; if unreadable, report `unreadable_authority`
4) Normalize the candidate
5) Read latest SR schema and compare to the candidate
6) Apply SR target policy (`manage` register missing/new version, otherwise observe/report)

### Feature 2: Confluent Schema Registry (SR)

  - **Subject naming**: subject is computed as `<subjectPrefix><FQCN>` (case preserved), where `subjectPrefix` is `json-schema.registry.naming.subject.prefix` (may be empty).
  - **Existence**: `GET /subjects/{subject}/versions/latest` (404 = missing).
  - **Register**: `POST /subjects/{subject}/versions` with `{ schemaType: "JSON", schema: "<schema text>" }` (references optional).
  - **Compatibility**: `/config/{subject}` supports `NONE`, `BACKWARD`, `BACKWARD_TRANSITIVE`, `FORWARD`, `FORWARD_TRANSITIVE`, `FULL`, `FULL_TRANSITIVE`. Recommend `BACKWARD`.
  - **Server mode**: `/mode` and `/mode/{subject}` (`READWRITE`, `READONLY`, `IMPORT`). Writes occur only in `READWRITE`.
  - Refer to the Confluent Schema Registry API documentation for full server mode semantics (especially `IMPORT`).
  - **Compatibility levels**: configured via `/config` (globally or per subject); do not confuse this with Schema Registry server mode.

#### SR manage behavior (normative)

In `json-schema.registry.sr.policy.mode=manage`, the implementation MUST:

- Retrieve the latest registered schema (404 = missing).
- If missing: register the candidate schema via `POST /subjects/{subject}/versions`.
- If present: perform normalized schema text comparison:
  - If equivalent: no-op (do not POST).
  - If drift: register the candidate schema as a new version via `POST /subjects/{subject}/versions`.

Schema compatibility is enforced by Schema Registry on `POST` according to `/config/{subject}` (or global `/config`). If SR rejects a schema as incompatible, follow SR failure semantics.

If `json-schema.registry.authority=sr`, the latest schema retrieved for the resolved subject is the candidate for the reconciliation run.

#### SR drift detection (observe_only) (normative)

In `json-schema.registry.sr.policy.mode=observe_only`, the implementation MUST perform read-only drift detection per subject:

- Retrieve the latest registered schema via `GET /subjects/{subject}/versions/latest` (404 = missing).
- If missing: report `missing_target`.
- If present:
  - Extract the schema text from the response.
  - Compare the normalized schema text of the remote latest schema to the normalized schema text of the candidate:
    - If equivalent: report `equivalent`.
    - Otherwise: report `drift`.

Optionally (read-only), the implementation MAY also retrieve and report:
- SR mode (`GET /mode` and/or `GET /mode/{subject}`)
- SR compatibility configuration (`GET /config` and/or `GET /config/{subject}`)

#### SR failure semantics

- If SR rejects a registration due to incompatibility, the behavior follows global `fail-fast` vs best-effort:
  - **fail-fast**: gate readiness (service remains NOT_READY)
  - **best-effort**: log and continue (SR target marked failed for this run)

- For failures other than incompatibility (HTTP 409)—for example, authentication/authorization errors, rate limiting, or server errors—the implementation MUST manage the failure according to the global fail-fast vs best-effort semantics, applying the SR retry policy only to transient failures.

Note

- Incompatible schema registration attempts are rejected with HTTP 409 (Conflict). Treat as non-retryable and manage via fail-fast vs best-effort.


#### Errors & reliability

  - Handle 4xx/5xx; retry transient failures with backoff; structured logs/metrics.
  - If Schema Registry is in `READONLY` mode, mutation operations (e.g., schema registration) will be rejected by SR and MUST be managed according to fail-fast vs best-effort.

Retry policy for SR HTTP calls (normative)

  - Retries MUST be bounded (attempt count and/or total time budget).
  - Retries SHOULD apply only to transient failures such as network errors, HTTP 5xx, and HTTP 429.
  - Retries SHOULD NOT be performed for other HTTP 4xx errors.

Note

- Implementations MAY skip explicit `/mode` checks and rely on SR response codes from `POST /subjects/{subject}/versions`.

### Feature 3: Oracle 23ai JSON Domains

- **Domain naming** (normative): domain name is computed as `<domainPrefix><FQCN>` where:
  - `domainPrefix` is `json-schema.registry.naming.domain.prefix` (may be empty)
  - `FQCN` is transformed by replacing `.` with `_`
  - the final result is uppercased

Example:

- `com.acme.OrderCreated` with `domainPrefix=APP_` → `APP_COM_ACME_ORDERCREATED`

Identifier length + truncation (normative)

Note (informative)

- This specification targets Oracle 23ai deployments that support 128-byte identifiers; implementations assume a 128-byte identifier budget.

- Implementations MUST produce identifiers that fit within **128 bytes** when encoded as **UTF-8**. Example how it could be done:
- If the computed domain name exceeds 128 bytes, the implementation MUST deterministically truncate and append a suffix to avoid collisions:
  1) Let `MAX_LEN = 128`.
  2) Let `H` be the first 8 lowercase hex characters of `SHA-256(UTF-8(<full computed domain name>))`.
  3) Let `SUFFIX = "_" + H`.
  4) Let `PREFIX` be the longest prefix of the computed name whose UTF-8 byte length is `<= MAX_LEN - byte_length(SUFFIX)`.
  5) Output `PREFIX + SUFFIX`.

Note (normative)

- Truncation MUST be performed by UTF-8 byte length, not by Java `char` count. Implementations MUST NOT split a multi-byte UTF-8 sequence.

- **Existence**: `USER_DOMAINS` (owner) or `ALL_DOMAINS` (owner + name).

#### Create (if missing)

`CREATE DOMAIN <NAME> AS JSON VALIDATE USING '<JSON schema text>'`

- Build literal safely: escape `'` → `''`.
- For large payloads, split and concatenate with `to_clob('chunk') || ...`.

Failure handling

- If `CREATE DOMAIN` fails in `json-schema.registry.oracle.policy.mode=manage`, the failure MUST be managed according to global fail-fast vs best-effort semantics.

#### Drift awareness / drift detection

- In `json-schema.registry.oracle.policy.mode in {manage, observe_only}`, the implementation MUST retrieve the current domain validation schema.
- Retrieve the current domain schema using the following order:
   1) Prefer `DBMS_METADATA.GET_DDL('SQL_DOMAIN', :NAME, :OWNER)` and extract the JSON schema literal from the `JSON VALIDATE USING '<...>'` clause in application code (JDBC retrieves the DDL as CLOB). Then unescape doubled single quotes and parse as JSON.
      - (Informative) Cross-schema `GET_DDL` may require catalog/dictionary privileges. If privilege-blocked or `GET_DDL` fails, proceed to step 2 and log which path was used.
   2) Otherwise, retrieve `SEARCH_CONDITION` from `USER_DOMAIN_CONSTRAINTS` / `ALL_DOMAIN_CONSTRAINTS`, extract the JSON schema literal after `VALIDATE USING`, and unescape doubled single quotes in application code.
      - (Informative) `SEARCH_CONDITION` datatype is version-dependent: in Oracle 23ai it is `VARCHAR2(4000)` (risk of truncation); in Oracle 26ai it is `CLOB` (no 4000-byte limit). Regardless of datatype, treat extraction/parse failures as a fallback condition (report and proceed according to policy).

Extraction rules for `VALIDATE USING` (normative)

- The implementation MUST extract the JSON schema text from a `VALIDATE USING` clause that uses a single-quoted SQL string literal.
- The extracted literal MUST be unescaped according to SQL string literal rules for single quotes (replace doubled `''` with `'`).
- The resulting text MUST be parsed as JSON. If parsing fails, consider this an extraction failure.
- If extraction fails for step (1), the implementation MUST proceed to step (2).
- If extraction fails for step (2), the implementation MUST consider the current schema unavailable/unparseable and proceed according to policy (this counts as drift for the purposes of drift detection, and MUST be reported).

Observability and error reporting for extraction paths (normative)

- For each domain examined, the implementation MUST log which extraction path was used: `get_ddl` or `search_condition`.
- If an extraction path fails, the implementation MUST log the failure reason and include whether the failure is likely privilege-related vs parse-related.

Example SQL (informative)

```sql
-- Domain existence
SELECT domain_name FROM user_domains WHERE domain_name = :domain_name;

-- Preferred: reconstructed DDL (parse VALIDATE USING)
SELECT dbms_metadata.get_ddl('SQL_DOMAIN', :domain_name, :owner) AS ddl FROM dual;

-- Alternative: retrieve SEARCH_CONDITION and parse JSON Schema in application code
SELECT search_condition
FROM   user_domain_constraints
WHERE  domain_name = :domain_name;
```
- Compare the **normalized schema text** of the current domain schema to the **normalized schema text** of the candidate:
  - If equivalent: no-op.
- If different: consider as drift and report via logs/metrics (no remediation in this version).

If `json-schema.registry.authority=oracle`, the current domain validation schema retrieved from Oracle metadata is the candidate for the reconciliation run.

#### Drift mode behavior (normative)

`json-schema.registry.oracle.drift.mode` controls what happens when an existing domain is present and drift is detected (current differs from the candidate):

- `report`:
  - Always log drift and emit metrics.
  - Never gate readiness.
- `fail`:
  - Treat drift as an error.
  - In global fail-fast mode: gate readiness (service remains NOT_READY).
  - In global best-effort mode: log as an error and continue (Oracle target marked failed for this run).

#### Domain evolution

Oracle does not support altering the JSON validation schema attached to a domain created with `VALIDATE USING` via `ALTER DOMAIN`. Domain evolution (creating a new domain and migrating table columns) is out of scope for this specification/implementation.

### Feature 4: Configuration & Conditional Activation

- Activate only if `json-schema.registry.enabled=true` and at least one target is configured.
- Fine-grained toggles enable/disable individual targets.
- Authority selection determines which system provides the candidate schema for each run.

Authority-specific logical schema selection:

- `application` authority: select logical schemas via discovery of `@JsonSchema` types
- `sr` authority: discover logical schemas from SR subjects, optionally narrowed by configured SR subjects
- `oracle` authority: discover logical schemas from Oracle domains, optionally narrowed by configured Oracle domains

Targets are enabled via:

- `json-schema.registry.sr.enabled`
- `json-schema.registry.oracle.enabled`

### Feature 5: Observability

  - Structured logs with correlation IDs per schema subject/domain and per reconciliation run.
  - Micrometer metrics: counters for discovered schemas and per-target outcomes (e.g., created, no-op, drift, failed), plus latencies per target operation (SR HTTP calls; Oracle introspection/DDL).

Metrics SHOULD include consistent dimensions/tags to support cross-target dashboards:
  - target: `sr` | `oracle`
  - authority: `application` | `sr` | `oracle`
  - mode: `manage` | `observe_only`
  - result: `created` | `noop` | `missing_target` | `equivalent` | `drift` | `missing_authority` | `unreadable_authority` | `projection_incompatibility` | `failed`

Note

- Avoid high-cardinality tags (for example, SR `subject` or Oracle `domain`) in metrics by default. Prefer including identifiers in logs and keeping metrics aggregated.

### Feature 6: Safe rollout and dry-run

  - Dry-run previews `manage` actions: it computes and prints intended write operations (SR POST / Oracle CREATE DOMAIN) without executing them.
  - “Fail-fast” vs “best-effort” modes when multiple targets are enabled (fail-fast gates readiness on error; best-effort continues and aggregates results).

Dry-run semantics (normative)

- Dry-run MUST NOT perform any write operations (no SR POST/PUT; no Oracle DDL).
  - Dry-run MAY resolve the candidate and perform read-only probes (e.g., SR GET latest/mode/config; Oracle introspection) to produce an accurate plan.
  - Dry-run output MUST clearly label actions as “would do”.
  - Dry-run output is emitted via structured logs (e.g., INFO) and SHOULD be prefixed with `[DRY-RUN]`.

Note

  - If a target is configured with `json-schema.registry.sr.policy.mode=observe_only` or `json-schema.registry.oracle.policy.mode=observe_only`, dry-run does not change target behavior beyond labeling output as “would do”.

Multi-target best-effort (normative)

  - In best-effort mode, when multiple targets are enabled, each target MUST be attempted independently and a summary MUST be logged.

Fail-fast readiness behavior (normative)

  - In fail-fast mode, if reconciliation fails for any enabled target, the application MUST report itself as not ready until the failure is resolved.
  - In Micronaut terms, this means the service readiness indicator MUST be DOWN/NOT_READY while reconciliation is failing.
  - Implementations SHOULD surface this via a Micronaut `@Readiness` `HealthIndicator`. If Micronaut management endpoints are enabled, the aggregated `/health/readiness` endpoint returns HTTP 503 if any readiness indicator reports DOWN (by default).

---

## Performance

- O(N) logical schema discovery plus authority resolution and per-target existence/drift checks.
- SR writes only when missing or compatible update allowed.
- Oracle DDL is limited to `CREATE DOMAIN` when missing.
- Limited parallelism; avoid aggressive batching.

---

## Unified Configuration (authoritative)

Properties (equivalents; abbreviated)

  - `json-schema.registry.enabled=true`
  - `json-schema.registry.authority=application`
    - Allowed values: `application` | `sr` | `oracle`
  - `json-schema.registry.mappings=[]`
    - Optional explicit subject/domain pairing overrides for non-standard or ambiguous cases
  - `json-schema.registry.sr.subjects=[]`
    - Optional narrowing/include list when `json-schema.registry.authority=sr`
  - `json-schema.registry.oracle.domains=[]`
    - Optional narrowing/include list when `json-schema.registry.authority=oracle`
  - `json-schema.registry.dry-run=false`
  - `json-schema.registry.fail-fast=true`

SR

  - `json-schema.registry.sr.enabled=true`
  - `json-schema.registry.sr.url=http://localhost:8081`
  - `json-schema.registry.sr.policy.mode=manage`
  - `json-schema.registry.sr.policy.compatibility.default=BACKWARD`
  - `json-schema.registry.naming.subject.prefix=`

Oracle

  - `json-schema.registry.oracle.enabled=true`
  - `json-schema.registry.oracle.datasource=default`
  - `json-schema.registry.oracle.policy.mode=manage`
  - `json-schema.registry.oracle.drift.mode=report`
    - Allowed values: `report` | `fail`
  - `json-schema.registry.naming.domain.prefix=APP_`

Authority-mode examples

```properties
# Application-authoritative: discover @JsonSchema types, reconcile SR + Oracle
json-schema.registry.enabled=true
json-schema.registry.authority=application
json-schema.registry.sr.enabled=true
json-schema.registry.sr.policy.mode=manage
json-schema.registry.oracle.enabled=true
json-schema.registry.oracle.datasource=default
json-schema.registry.oracle.policy.mode=manage
json-schema.registry.oracle.drift.mode=report
```

```properties
# SR-authoritative: discover by subject prefix, optionally narrow to selected subjects, derive Oracle domains automatically
json-schema.registry.enabled=true
json-schema.registry.authority=sr
json-schema.registry.sr.enabled=true
json-schema.registry.naming.subject.prefix=com.acme.
json-schema.registry.sr.subjects[0]=com.acme.OrderCreated
json-schema.registry.sr.subjects[1]=com.acme.InvoiceIssued
json-schema.registry.oracle.enabled=true
json-schema.registry.oracle.datasource=default
json-schema.registry.oracle.policy.mode=manage
json-schema.registry.naming.domain.prefix=APP_
```

```properties
# Oracle-authoritative: discover by domain prefix, optionally narrow to selected domains, derive SR subjects automatically
json-schema.registry.enabled=true
json-schema.registry.authority=oracle
json-schema.registry.oracle.enabled=true
json-schema.registry.oracle.datasource=default
json-schema.registry.naming.domain.prefix=APP_
json-schema.registry.oracle.domains[0]=APP_COM_ACME_ORDERCREATED
json-schema.registry.oracle.domains[1]=APP_COM_ACME_INVOICEISSUED
json-schema.registry.sr.enabled=true
json-schema.registry.sr.policy.mode=manage
json-schema.registry.naming.subject.prefix=
```

```properties
# Optional override example: use mappings only when derived pairing is non-standard or ambiguous
json-schema.registry.mappings[0].subject=custom.order.subject
json-schema.registry.mappings[0].domain=APP_CUSTOM_ORDER
```

---

## Availability & Concurrency

  - Fail-fast gates readiness on first error; best-effort logs and continues.
- Optional async background sync after startup (implementation-defined). If enabled and `fail-fast=true`, readiness MUST remain NOT_READY until reconciliation succeeds.
- This spec does not require distributed locking. Implementations must be safe under concurrent startup runs via idempotent reads and conditional writes based on normalized schema text equivalence.

Cross-target projection limits (normative)

- Oracle-targeted schemas MUST be self-contained.
- Implementations MUST NOT assume that an authoritative schema can be represented in every target without loss.
- If the candidate cannot be projected to a target’s supported schema model, the implementation MUST report projection incompatibility and proceed according to global fail-fast vs best-effort semantics.

---

## Design Constraints

  - Use Micronaut bean metadata for logical schema discovery and Micronaut JSON Schema runtime APIs when `json-schema.registry.authority=application`.
  - Oracle domain existence detection via `USER_DOMAINS`/`ALL_DOMAINS`; no `CREATE IF NOT EXISTS`.
  - No automatic SR subject deletion; no app-driven Oracle domain replacement by default.
  - No remote `$ref` dereferencing (drift detection operates on provided documents only).

---

## Reliability

  - SR retries follow the "Retry policy for SR HTTP calls" section.
- Oracle DDL is create-only; create failures are operational failures managed according to global fail-fast vs best-effort semantics.
  - Dry-run to preview changes; plan logs with predicted actions per subject/domain.

---

## Maintainability

  - Clear separation: discovery/generation vs SR client vs Oracle DDL executor.
  - Extensible to future targets.

---

## Security

  - Credentials via Micronaut config; support Vault/env vars.
  - Redact secrets in logs.

---

## Compatibility

  - Oracle: Requires 23ai with JSON domain support.
  - SR: Confirm JSON Schema draft support if using 2020-12.

---

## Testing

  - Unit tests: schema resolution, subject/domain naming, SR client behavior, Oracle drift detection (normalized text equivalence) and drift mode behavior (report/fail).
  - Authority-mode tests: `application`, `sr`, and `oracle`; missing authority source; projection incompatibility.
  - SR tests: WireMock (contracts); Testcontainers (Kafka + SR) optional for end-to-end.
  - Oracle tests: Testcontainers (if feasible) or gated integration; otherwise manual SQL verification.

---

## Functional Testing & Acceptance Criteria

  - SR: schemaType:"JSON" registration works; GET latest returns; SR compatibility enforced on POST (rejects incompatible schemas).
  - `authority=application`: generated schema is used as the candidate and reconciled to configured targets.
  - `authority=sr`: SR latest schema is used as the candidate and Oracle is reconciled or observed according to policy.
  - `authority=oracle`: current Oracle domain schema is used as the candidate and SR is reconciled or observed according to policy.
  - Missing authoritative schema is reported and managed according to fail-fast vs best-effort semantics.
  - Oracle: domain created when missing; visible in `USER_DOMAINS`.
  - Oracle: when domain exists and candidate differs, drift is detected and handled according to `json-schema.registry.oracle.drift.mode` (report vs fail). No change is made to the existing domain.
  - Dry-run prints a clear plan; real run is idempotent on second startup (no-op when normalized schema text is equivalent).
  - Logs/metrics include subject/domain, action, duration, result.

---

## Client Interfaces

  - Admin resync endpoint/command.

Admin resync endpoint/command behavior (normative)

  - Triggers the same reconciliation logic as startup.
  - MUST respect `dry-run` and per-target policy modes.
  - MUST apply fail-fast readiness gating semantics for reconciliation failures.

SR Client endpoints (non-exhaustive)
  - `/subjects`
  - `/subjects/{subject}/versions`
  - `/subjects/{subject}/versions/latest`
  - `/config`
  - `/config/{subject}`
  - `/mode`
  - `/mode/{subject}`
  - `/schemas/ids/{id}`

Oracle DDL
  - `CREATE DOMAIN <NAME> AS JSON VALIDATE USING '<JSON schema text>'`

---

## Installation and First Use

  - Add dependency: `:micronaut-json-schema-registry` (new module) to application.
  - Configure as per “Unified Configuration”.
  - Start the application. Observe logs/metrics for results (dry-run plans are emitted to logs when enabled).

---

## Dependencies and Effects – Software Dependencies

  - Depends on micronaut-http-client, micronaut-sql (Hikari), Oracle JDBC driver, Micrometer.
  - Reuses Micronaut JSON Schema runtime library for schema generation.

### External Impacts

  - Confluent Schema Registry must be reachable; network failures delay or skip sync based on policy. When `json-schema.registry.sr.policy.mode=manage` is enabled, ensure the Schema Registry server mode (`/mode`) is `READWRITE`, otherwise registration attempts will be rejected.
  - Oracle DDL requires privileges to `CREATE DOMAIN`; coordinate with DBAs in production.

---

## Appendix A – References

  - JSON Schema: https://json-schema.org/
  - Draft 2020-12: https://json-schema.org/specification-links.html
  - Confluent Schema Registry API: https://docs.confluent.io/platform/current/schema-registry/develop/api.html
  - Micronaut Framework: https://micronaut.io/
  - Oracle Database 23ai JSON Schema: https://docs.oracle.com/en/database/oracle/oracle-database/23/adjsn/json-schema.html
  - Oracle CREATE DOMAIN (23c/23ai): https://docs.oracle.com/en/database/oracle/oracle-database/23/sqlrf/CREATE-DOMAIN.html
  - Oracle CREATE DOMAIN (latest): https://docs.oracle.com/en/database/oracle/oracle-database/26/sqlrf/create-domain.html
  - DBMS_METADATA: https://docs.oracle.com/en/database/oracle/oracle-database/23/arpls/DBMS_METADATA.html

---

## Appendix B – Oracle Implementation Notes

Scope and assumptions

  - Authoritative schema source: configured via `json-schema.registry.authority`; the candidate MAY be application-generated, read from Schema Registry, or read from Oracle metadata
  - DDL is create-only: under `manage`, create a domain if missing; existing domains are not modified. `observe_only` does not perform DDL.

### Appendix B.1 – Oracle drift detection (normative)

Oracle drift detection determines whether an existing domain’s validation schema is equivalent to the candidate schema.

Oracle drift detection in this version is strict equivalence only; no compatibility classification is performed.

Inputs

- `current`: the current domain validation schema as a JSON value (prefer extracted from `DBMS_METADATA.GET_DDL('SQL_DOMAIN', ...)`; otherwise parsed from `USER_DOMAIN_CONSTRAINTS`/`ALL_DOMAIN_CONSTRAINTS` `SEARCH_CONDITION`)
- `candidate`: the candidate schema as a JSON value

Both inputs MUST be normalized (see “Normalized schema text”) and parsed as JSON prior to comparison.

Note (normative)

- Appendix B.1 expands Feature 3 (“Oracle 23ai JSON Domains”) into a step-by-step procedure. If there is any conflict, Feature 3 is authoritative.

#### Drift evaluation procedure (normative)

For a given `(current, candidate)` the implementation MUST apply the following steps:

1) Parse `current` and `candidate` as JSON. If either cannot be parsed, consider this drift.
2) Compute normalized schema text for both inputs.
3) If normalized schema texts are byte-identical: no drift.
4) Otherwise: drift.

##### Result usage

- In `observe_only`: always compute drift; report via logs/metrics.
- In `manage`: if drift is detected for an existing domain, report drift via logs/metrics (no remediation in this version).

Checklist

1) Resolve or confirm the domain name for the logical schema using Feature 3 naming rules (prefix + `.`→`_` + uppercase + truncation/suffix if needed) when derivation is required
2) Existence: `USER_DOMAINS`/`ALL_DOMAINS`
3) If NOT EXISTS and `json-schema.registry.oracle.policy.mode=manage` → `CREATE DOMAIN <NAME> AS JSON VALIDATE USING '<candidate schema>'`
4) If EXISTS:
    - Drift awareness: retrieve current schema using the same order as Feature 3 (`DBMS_METADATA.GET_DDL('SQL_DOMAIN', ...)` → `USER_DOMAIN_CONSTRAINTS`/`ALL_DOMAIN_CONSTRAINTS` `SEARCH_CONDITION` fallback)
    - If `json-schema.registry.oracle.policy.mode=manage`:
        - No in-place schema update (domain validation schema cannot be altered). Treat differences as drift and report via logs/metrics.
    - If `json-schema.registry.oracle.policy.mode=observe_only` → no DDL; log/metric findings

Building the JSON literal

- Escape single quotes (replace `'` with `''`)
- For large schemas exceeding literal limits, split and concatenate: `to_clob('chunk1') || 'chunk2' || ...`

Operational Safety

- Coordinate a maintenance window (DDL takes metadata locks)
- Ensure privileges: `CREATE DOMAIN`
- Log/metric every attempted and completed create operation

Note on domain evolution

- Updating a domain’s JSON validation schema requires creating a new domain and migrating dependent columns. This is explicitly out of scope for this version.

---

## Appendix C – SR Implementation Notes

Checklist

1) Resolve subject name as `<subjectPrefix><FQCN>` (case preserved)
2) If `json-schema.registry.authority=sr`, GET latest (`GET /subjects/{subject}/versions/latest`; 404 = missing) and use it as the candidate
3) If SR is a non-authoritative target and `json-schema.registry.sr.policy.mode=manage`:
   - Ensure SR mode is `READWRITE`; otherwise follow fail-fast vs best-effort.
   - If missing: optionally set `/config/{subject}` then POST the candidate.
   - If latest exists and differs: POST new version; SR enforces compatibility.
4) If SR is a non-authoritative target and `json-schema.registry.sr.policy.mode=observe_only`:
   - Never POST.
   - Perform read-only drift detection against the candidate:
      - missing (404)
      - equivalent (normalized schema text matches)
      - drift (normalized schema text differs)

Acceptance

- Register succeeds with `schemaType: "JSON"`.
- GET latest reflects the registered schema.
- SR compatibility honored (POST rejected if incompatible).
