/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.nitrite.runtime;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.QueryModel;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.EntityOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import jakarta.inject.Singleton;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.common.mapper.NitriteMapper;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.dizitart.no2.repository.ObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Default Nitrite repository operations. Implements core CRUD using Nitrite's Document codec. */
@Singleton
@SuppressWarnings("removal")
public final class DefaultNitriteRepositoryOperations extends AbstractRepositoryOperations
    implements NitriteRepositoryOperations {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultNitriteRepositoryOperations.class);

  // Patterns for parsing SQL WHERE clauses from document processor
  private static final Pattern SQL_COMPARISON =
      Pattern.compile("\\w+\\.(\\w+)\\s*(=|!=|<>|>|<|>=|<=)\\s*:(\\w+)");
  private static final Pattern SQL_IS_NOT_NULL =
      Pattern.compile("\\w+\\.(\\w+)\\s+IS\\s+NOT\\s+NULL");
  private static final Pattern SQL_IS_NULL =
      Pattern.compile("\\w+\\.(\\w+)\\s+IS\\s+(?!NOT\\s+)NULL");
  private static final Pattern SQL_LIKE_CONCAT =
      Pattern.compile("\\w+\\.(\\w+)\\s+LIKE\\s+CONCAT\\(([^)]+)\\)");
  private static final Pattern SQL_SET_ASSIGNMENT =
      Pattern.compile("\\w+\\.(\\w+)\\s*=\\s*:(\\w+)");
  private static final Pattern SQL_LITERAL_BOOL =
      Pattern.compile("\\w+\\.(\\w+)\\s*(=|!=|<>)\\s*(true|false)", Pattern.CASE_INSENSITIVE);
  private static final Pattern SQL_LITERAL_EMPTY_STR = Pattern.compile("\\w+\\.(\\w+)\\s*=\\s*''");

  private final Nitrite database;
  private final NitriteMapper nitriteMapper;
  private final NitriteTransactionHolder transactionHolder;

  public DefaultNitriteRepositoryOperations(
      final Nitrite database,
      final DateTimeProvider dateTimeProvider,
      final RuntimeEntityRegistry runtimeEntityRegistry,
      final DataConversionService conversionService,
      final NitriteTransactionHolder transactionHolder) {
    super(dateTimeProvider, runtimeEntityRegistry, conversionService, null);
    this.database = database;
    this.nitriteMapper = database.getConfig().nitriteMapper();
    this.transactionHolder = transactionHolder;
  }

  @Override
  public Nitrite getDatabase() {
    return database;
  }

  /**
   * Get an object repository for the given entity type.
   *
   * @param <T> the entity type
   * @param <ID> the ID type
   * @param entityType the entity type class
   * @return the repository
   */
  @SuppressWarnings("unchecked")
  public <T, ID extends Serializable> ObjectRepository<T> getRepository(final Class<T> entityType) {
    return (ObjectRepository<T>) database.getRepository(entityType);
  }

  /**
   * Get an object repository for the given entity type and discriminator.
   *
   * @param <T> the entity type
   * @param entityType the entity type class
   * @param discriminator the discriminator
   * @return the repository
   */
  @SuppressWarnings("unchecked")
  public <T> ObjectRepository<T> getRepository(
      final Class<T> entityType, final String discriminator) {
    return (ObjectRepository<T>) database.getRepository(entityType, discriminator);
  }

  /** Get collection name from entity class. */
  private String getCollectionName(final Class<?> type) {
    MappedEntity mappedEntity = type.getAnnotation(MappedEntity.class);
    if (mappedEntity != null && !mappedEntity.value().isEmpty()) {
      return mappedEntity.value();
    }
    return type.getSimpleName();
  }

  /** Get Nitrite collection for entity type. */
  private NitriteCollection getCollection(final Class<?> type) {
    String name = getCollectionName(type);
    if (transactionHolder.isActive()) {
      // Nitrite transactions require the collection to pre-exist before the transaction started.
      // Touch the collection on the database first (idempotent: creates if absent).
      database.getCollection(name);
      return transactionHolder.get().getCollection(name);
    }
    return database.getCollection(name);
  }

  /** Convert entity to Nitrite Document. */
  private <T> Document toDocument(final T entity) {
    Document doc = (Document) nitriteMapper.tryConvert(entity, Document.class);
    // Entities with @JsonProperty("_id") cause Jackson to serialize the id as "_id".
    // Nitrite reserves "_id" for NitriteId — rename user's id to "id" to avoid InvalidIdException.
    Object reservedId = doc.get("_id");
    if (reservedId != null && !(reservedId instanceof NitriteId)) {
      doc.remove("_id");
      doc.put("id", toFilterValue(reservedId));
    }
    return doc;
  }

  /** Convert Nitrite Document to entity. */
  @SuppressWarnings("unchecked")
  private <T> T fromDocument(final Document doc, final Class<T> type) {
    // Create a copy of the document using only user-visible fields (doc.getFields() excludes
    // Nitrite internals: "_id"/NitriteId, "_revision", "_modified", "_source").
    // This prevents entities with @JsonProperty("_id") from getting the NitriteId (a numeric long)
    // where they expect a UUID, which would cause ObjectMappingException.
    Document docCopy = Document.createDocument();
    for (String field : doc.getFields()) {
      docCopy.put(field, doc.get(field));
    }
    T entity = (T) nitriteMapper.tryConvert(docCopy, type);
    // Post-process: entities with @JsonProperty("_id") have their id field mapped to "_id" key.
    // Since we stripped "_id" (NitriteId) from docCopy, Jackson left id=null. Restore from "id".
    var idProp = getEntity(type).getIdentity();
    if (idProp != null && idProp.getProperty().get(entity) == null) {
      Object storedId = doc.get("id");
      if (storedId != null) {
        idProp.getProperty().set(entity, convertIdValue(storedId, idProp.getType()));
      }
    }
    return entity;
  }

  /** Convert a stored id value (e.g. String from Jackson) to the entity's id type. */
  private static Object convertIdValue(final Object stored, final Class<?> targetType) {
    if (targetType == UUID.class && stored instanceof String) {
      return UUID.fromString((String) stored);
    }
    return stored;
  }

  /**
   * Normalize a field name from SQL WHERE clause to Nitrite storage field name. Entities
   * with @MappedProperty("_id") generate SQL with "_id" but we store as "id".
   */
  private static String normalizeFieldName(final String field) {
    return "_id".equals(field) ? "id" : field;
  }

  /**
   * Normalize a value for use in a Nitrite filter. Jackson serializes UUID fields as strings, so
   * UUID values must be converted to String before comparison.
   */
  private static Object toFilterValue(final Object val) {
    if (val instanceof UUID) {
      return val.toString();
    }
    if (val instanceof Instant instant) {
      return instant.getEpochSecond() + instant.getNano() / 1_000_000_000.0;
    }
    return val;
  }

  /**
   * Generate and set ID on entity if @GeneratedValue is present. Supports String (UUID), Long
   * (timestamp-based), and Integer IDs.
   */
  private <T> void generateIdIfNecessary(final T entity, final Class<T> type) {
    RuntimePersistentEntity<T> persistentEntity = getEntity(type);
    var idProperty = persistentEntity.getIdentity();
    if (idProperty != null && idProperty.isAnnotationPresent(GeneratedValue.class)) {
      Class<?> idType = idProperty.getType();
      Object generatedId;

      if (idType == String.class) {
        generatedId = UUID.randomUUID().toString();
      } else if (idType == UUID.class) {
        generatedId = UUID.randomUUID();
      } else if (idType == Long.class || idType == long.class) {
        generatedId = System.currentTimeMillis();
      } else if (idType == Integer.class || idType == int.class) {
        generatedId = (int) (System.currentTimeMillis() % Integer.MAX_VALUE);
      } else {
        // Default to UUID string for unknown types
        generatedId = UUID.randomUUID().toString();
      }

      idProperty.getProperty().set(entity, generatedId);
    }
  }

  // ========== Core CRUD Operations ==========

  @Override
  public <T> T findOne(final Class<T> type, final Object id) {
    NitriteCollection collection = getCollection(type);
    // Use "id" field filter instead of NitriteId (which is for internal Nitrite doc IDs)
    Document doc = collection.find(FluentFilter.where("id").eq(toFilterValue(id))).firstOrNull();
    if (doc == null) {
      return null;
    }
    return fromDocument(doc, type);
  }

  @Override
  public <T> T persist(@NonNull final InsertOperation<T> operation) {
    T entity = operation.getEntity();
    Class<T> type = operation.getRootEntity();
    generateIdIfNecessary(entity, type);
    NitriteCollection collection = getCollection(type);
    Document doc = toDocument(entity);
    collection.insert(doc);
    return entity;
  }

  @Override
  public <T> Iterable<T> persistAll(@NonNull final InsertBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    for (T entity : operation) {
      generateIdIfNecessary(entity, type);
      collection.insert(toDocument(entity));
    }
    return operation;
  }

  @Override
  public <T> T update(@NonNull final UpdateOperation<T> operation) {
    T entity = operation.getEntity();
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    Document doc = toDocument(entity);
    Object idValue = doc.get("id");
    if (idValue != null) {
      collection.update(FluentFilter.where("id").eq(idValue), doc);
    }
    // Nitrite doesn't modify documents server-side, so return entity directly
    return entity;
  }

  @Override
  public <T> Iterable<T> updateAll(@NonNull final UpdateBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    for (T entity : operation) {
      Document doc = toDocument(entity);
      Object idValue = doc.get("id");
      if (idValue != null) {
        collection.update(FluentFilter.where("id").eq(idValue), doc);
      }
    }
    return operation;
  }

  @Override
  public <T> int delete(@NonNull final DeleteOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    T entity = operation.getEntity();
    Document doc = toDocument(entity);
    Object idValue = doc.get("id");
    if (idValue == null) {
      return 0;
    }
    var result = collection.remove(FluentFilter.where("id").eq(idValue), false);
    return result.getAffectedCount();
  }

  @Override
  public <T> Optional<Number> deleteAll(@NonNull final DeleteBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);

    // Check if this is a "delete all" operation (no specific entities)
    if (operation.all()) {
      var result = collection.remove(Filter.ALL, false);
      return Optional.of(result.getAffectedCount());
    }

    // Delete specific entities by ID
    // Move getEntity() outside the loop (registry lookup returns same object)
    RuntimePersistentEntity<T> persistentEntity =
        (RuntimePersistentEntity<T>) runtimeEntityRegistry.getEntity(type);
    RuntimePersistentProperty idProperty = persistentEntity.getIdentity();
    int count = 0;
    for (T entity : operation) {
      if (idProperty != null) {
        Object idValue = idProperty.getProperty().get(entity);
        if (idValue != null) {
          var result =
              collection.remove(FluentFilter.where("id").eq(toFilterValue(idValue)), false);
          if (result.getAffectedCount() > 0) {
            count++;
          }
        }
      }
    }
    return Optional.of(count);
  }

  /** Build FindOptions with pagination and sorting. */
  private FindOptions buildFindOptions(final Pageable pageable) {
    return buildFindOptions(pageable, null);
  }

  /** Build FindOptions with pagination and sorting, merging additional sort from QueryModel. */
  private FindOptions buildFindOptions(final Pageable pageable, final Sort additionalSort) {
    FindOptions options = new FindOptions();

    // Pagination
    long offset = pageable.getOffset();
    if (offset > 0) {
      options.skip(offset);
    }
    int size = pageable.getSize();
    if (size > 0) {
      options.limit(size);
    }

    // Sorting - pageable sort takes precedence over method-name OrderBy.
    // Micronaut Data merges them into pageable.getSort(), but the order matters.
    // We want the most specific sort (from Pageable argument) to win.
    Map<String, Sort.Order> mergedOrders = new LinkedHashMap<>();

    // 1. Apply additionalSort (from method name OrderBy)
    if (additionalSort != null && additionalSort.isSorted()) {
      for (var order : additionalSort.getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }

    // 2. Apply pageable sort (overwrites same fields from method name)
    if (pageable.getSort() != null && pageable.getSort().isSorted()) {
      for (var order : pageable.getSort().getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }

    if (!mergedOrders.isEmpty()) {
      for (var order : mergedOrders.values()) {
        String field = normalizeFieldName(order.getProperty());
        SortOrder sortOrder =
            order.getDirection() == Sort.Order.Direction.ASC
                ? SortOrder.Ascending
                : SortOrder.Descending;
        options.thenOrderBy(field, sortOrder);
      }
    }

    return options;
  }

  /** Parses sort from SQL ORDER BY clause. Format: alias.field1 ASC, alias.field2 DESC */
  private Sort parseSortFromSqlQuery(final String sql) {
    if (sql == null) {
      return null;
    }
    int idx = sql.toUpperCase().indexOf(" ORDER BY ");
    if (idx < 0) {
      return null;
    }
    String clause = sql.substring(idx + 10).trim();
    List<Sort.Order> orders = new ArrayList<>();
    for (String part : clause.split(",")) {
      String[] tokens = part.trim().split("\\s+");
      if (tokens.length == 0 || tokens[0].isBlank()) {
        continue;
      }
      String fieldExpr = tokens[0];
      String field =
          fieldExpr.contains(".") ? fieldExpr.substring(fieldExpr.lastIndexOf('.') + 1) : fieldExpr;
      boolean desc = tokens.length > 1 && "DESC".equalsIgnoreCase(tokens[1]);
      orders.add(desc ? Sort.Order.desc(field) : Sort.Order.asc(field));
    }
    return orders.isEmpty() ? null : Sort.of(orders);
  }

  /** Parses sort from a JSON query string's $sort key (embedded by NitriteQueryBuilder). */
  @SuppressWarnings("unchecked")
  private Sort parseSortFromJsonQuery(final String queryString) {
    if (queryString == null || !queryString.contains("\"$sort\"")) {
      return null;
    }
    try {
      Object parsed = parseJson(queryString);
      if (parsed instanceof Map) {
        Object sortObj = ((Map<String, Object>) parsed).get("$sort");
        if (sortObj instanceof Map) {
          List<Sort.Order> orders = new ArrayList<>();
          for (Map.Entry<?, ?> e : ((Map<?, ?>) sortObj).entrySet()) {
            String field = e.getKey().toString();
            int dir = e.getValue() instanceof Number ? ((Number) e.getValue()).intValue() : 1;
            orders.add(dir >= 1 ? Sort.Order.asc(field) : Sort.Order.desc(field));
          }
          return orders.isEmpty() ? null : Sort.of(orders);
        }
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  /** Parse sort string from query hints (format: "field1:ASC,field2:DESC"). */
  private Sort parseSortFromHints(final Map<String, Object> hints) {
    if (hints == null || hints.isEmpty()) {
      return null;
    }

    Object sortObj = hints.get("sort");
    if (sortObj instanceof String sortStr && !sortStr.isEmpty()) {
      List<Sort.Order> orders = new ArrayList<>();
      for (String part : sortStr.split(",")) {
        String[] parts = part.trim().split(":");
        if (parts.length == 2) {
          String field = parts[0];
          Sort.Order.Direction direction = Sort.Order.Direction.valueOf(parts[1]);
          if (direction == Sort.Order.Direction.ASC) {
            orders.add(Sort.Order.asc(field));
          } else {
            orders.add(Sort.Order.desc(field));
          }
        }
      }
      if (!orders.isEmpty()) {
        return Sort.of(orders);
      }
    }
    return null;
  }

  @Override
  public <T> Iterable<T> findAll(@NonNull final PagedQuery<T> query) {
    Class<T> type = (Class<T>) query.getRootEntity();
    NitriteCollection collection = getCollection(type);

    // Build options with pagination and sorting
    FindOptions options = buildFindOptions(query.getPageable());

    var cursor = collection.find(Filter.ALL, options);
    List<T> results = new ArrayList<>();
    for (Document doc : cursor) {
      results.add(fromDocument(doc, type));
    }
    return results;
  }

  @Override
  public <T> long count(@NonNull final PagedQuery<T> query) {
    Class<T> type = (Class<T>) query.getRootEntity();
    NitriteCollection collection = getCollection(type);
    return collection.size();
  }

  // ========== Filter construction from PreparedQuery ==========

  /**
   * Build a Nitrite Filter from a PreparedQuery. The query is stored as a JSON-like filter
   * representation by NitriteQueryBuilder. Format: {"$and":[{"age":{"$gt":"$mn_qp:0"}}]} Parameters
   * are bound via $mn_qp:N placeholders.
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter buildFilterFromPreparedQuery(final PreparedQuery<?, ?> q) {
    String queryString = q.getQuery().trim();
    if (queryString.isEmpty()) {
      return Filter.ALL;
    }

    Object[] params = q.getParameterArray();

    // Check for JSON filter generated by NitriteQueryBuilder (usually starts with { )
    if (queryString.startsWith("{")) {
      try {
        Object filterObj = parseJson(queryString);
        if (filterObj instanceof Map) {
          return buildFilterFromJson((Map<String, Object>) filterObj, params);
        }
      } catch (Exception ignored) {
        // Fall through to SQL parsing
      }
    }

    // Fallback to SQL-like statement from document processor
    String upper = queryString.toUpperCase();
    if (upper.startsWith("DELETE")) {
      return parseFilterFromDeleteStatement(queryString, params);
    }
    if (upper.startsWith("SELECT")) {
      return parseFilterFromSelectStatement(queryString, params);
    }

    throw new IllegalStateException(
        "Unsupported query format. Expected JSON filter or SELECT/DELETE statement, got: "
            + queryString);
  }

  /**
   * Parse filter from DELETE statement generated by micronaut-data-document-processor. Format:
   * DELETE entity AS alias WHERE condition
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter parseFilterFromDeleteStatement(final String sql, final Object[] params) {
    int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String where = sql.substring(whereIdx + 7);
    // Trim ORDER BY if present (consistent with parseFilterFromSelectStatement)
    int orderByIdx = where.toUpperCase().indexOf(" ORDER BY");
    if (orderByIdx >= 0) {
      where = where.substring(0, orderByIdx);
    }
    return parseWhereClause(where, params);
  }

  /**
   * Parse filter from SELECT statement generated by micronaut-data-document-processor. Format:
   * SELECT alias FROM entity AS alias WHERE (condition)
   */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter parseFilterFromSelectStatement(final String sql, final Object[] params) {
    int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String where = sql.substring(whereIdx + 7);
    // Remove ORDER BY if present
    int orderByIdx = where.toUpperCase().indexOf(" ORDER BY");
    if (orderByIdx >= 0) {
      where = where.substring(0, orderByIdx);
    }
    // Remove trailing parenthesis
    where = where.trim();
    if (where.startsWith("(") && where.endsWith(")")) {
      where = where.substring(1, where.length() - 1);
    }
    return parseWhereClause(where, params);
  }

  /** Parse WHERE clause conditions into Filter. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter parseWhereClause(String where, final Object[] params) {
    List<Filter> filters = new ArrayList<>();

    // Special case: isEmpty pattern generated by document processor for findByXIsEmpty()
    // Pattern: (event_.payload IS NULL OR event_.payload = '')
    // We detect this and convert to $empty: true
    Matcher mEmpty =
        Pattern.compile(
                "\\(\\w+\\.(\\w+)\\s+IS\\s+NULL\\s+OR\\s+\\w+\\.\\1\\s*=\\s*''\\)",
                Pattern.CASE_INSENSITIVE)
            .matcher(where);
    if (!mEmpty.find()) {
      // Try without outer parentheses
      mEmpty =
          Pattern.compile(
                  "\\w+\\.(\\w+)\\s+IS\\s+NULL\\s+OR\\s+\\w+\\.\\1\\s*=\\s*''",
                  Pattern.CASE_INSENSITIVE)
              .matcher(where);
    }
    mEmpty.reset();
    while (mEmpty.find()) {
      String field = normalizeFieldName(mEmpty.group(1));
      filters.add(buildFieldFilter(field, Map.of("$empty", true), params));
      where = where.substring(0, mEmpty.start()) + "PROCESSED" + where.substring(mEmpty.end());
      mEmpty =
          Pattern.compile(
                  "\\w+\\.(\\w+)\\s+IS\\s+NULL\\s+OR\\s+\\w+\\.\\1\\s*=\\s*''",
                  Pattern.CASE_INSENSITIVE)
              .matcher(where);
    }

    // Pattern: alias.field = :pN
    Matcher m = SQL_COMPARISON.matcher(where);

    while (m.find()) {
      String field = normalizeFieldName(m.group(1));
      String op = m.group(2);
      String pname = m.group(3);
      Object rawVal = resolveParam(pname, params);
      Object val = toFilterValue(rawVal);

      String filterOp =
          switch (op) {
            case "=" -> "$eq";
            case "!=", "<>" -> "$ne";
            case ">" -> "$gt";
            case ">=" -> "$gte";
            case "<" -> "$lt";
            case "<=" -> "$lte";
            default -> "$eq";
          };

      filters.add(buildFieldFilter(field, Map.of(filterOp, val), params));
    }

    // IS NOT NULL pattern
    m = SQL_IS_NOT_NULL.matcher(where);
    while (m.find()) {
      filters.add(
          buildFieldFilter(normalizeFieldName(m.group(1)), Map.of("$notNull", true), params));
    }

    // IS NULL pattern
    m = SQL_IS_NULL.matcher(where);
    while (m.find()) {
      filters.add(buildFieldFilter(normalizeFieldName(m.group(1)), Map.of("$null", true), params));
    }

    // LIKE CONCAT pattern
    m = SQL_LIKE_CONCAT.matcher(where);
    while (m.find()) {
      String field = normalizeFieldName(m.group(1));
      String concatArgs = m.group(2);
      String pname = extractParamName(concatArgs);
      if (pname != null) {
        Object val = resolveParam(pname, params);
        if (val != null) {
          String[] parts = concatArgs.split(",\\s*");
          boolean prefixWild = parts[0].contains("%");
          boolean suffixWild = parts[parts.length - 1].contains("%");
          String regex;
          if (prefixWild && suffixWild) {
            regex = ".*" + Pattern.quote(val.toString()) + ".*";
          } else if (prefixWild) {
            regex = ".*" + Pattern.quote(val.toString());
          } else if (suffixWild) {
            regex = Pattern.quote(val.toString()) + ".*";
          } else {
            regex = Pattern.quote(val.toString());
          }
          filters.add(buildFieldFilter(field, Map.of("$regex", regex), params));
        }
      }
    }

    // Literal boolean: alias.field = true / alias.field = false
    m = SQL_LITERAL_BOOL.matcher(where);
    while (m.find()) {
      String field = normalizeFieldName(m.group(1));
      String op = m.group(2);
      boolean val = "true".equalsIgnoreCase(m.group(3));
      String filterOp = (op.equals("!=") || op.equals("<>")) ? "$ne" : "$eq";
      filters.add(buildFieldFilter(field, Map.of(filterOp, val), params));
    }

    // Empty string literal: alias.field = ''
    m = SQL_LITERAL_EMPTY_STR.matcher(where);
    while (m.find()) {
      filters.add(buildFieldFilter(normalizeFieldName(m.group(1)), Map.of("$eq", ""), params));
    }

    if (filters.isEmpty()) {
      return Filter.ALL;
    }
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return Filter.and(filters.toArray(new Filter[0]));
  }

  /** Extract the :pN parameter name from a CONCAT argument list. */
  private String extractParamName(final String concatArgs) {
    for (String part : concatArgs.split(",\\s*")) {
      String t = part.trim();
      if (t.startsWith(":")) {
        return t.substring(1);
      }
    }
    return null;
  }

  /** Resolve :pN (1-indexed) to the actual method argument. */
  private Object resolveParam(final String pname, final Object[] params) {
    try {
      if (pname.startsWith("p")) {
        int idx = Integer.parseInt(pname.substring(1)) - 1;
        if (params != null && idx >= 0 && idx < params.length) {
          return params[idx];
        }
      }
    } catch (NumberFormatException ignored) {
    }
    return null;
  }

  /** Parses a simple JSON string into a Map/List structure. */
  private Object parseJson(final String jsonStr) {
    String json = jsonStr.trim();
    if (json.equals("{}")) {
      return Map.of();
    }
    if (json.startsWith("{")) {
      return parseJsonObject(json);
    }
    if (json.startsWith("[")) {
      return parseJsonArray(json);
    }
    throw new IllegalArgumentException("Invalid JSON: " + json);
  }

  private Map<String, Object> parseJsonObject(String json) {
    Map<String, Object> result = new LinkedHashMap<>();
    json = json.trim();
    if (json.equals("{}")) {
      return result;
    }

    // Remove outer braces
    json = json.substring(1, json.length() - 1).trim();

    int i = 0;
    while (i < json.length()) {
      // Skip whitespace
      while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
        i++;
      }
      if (i >= json.length()) {
        break;
      }

      // Parse key
      if (json.charAt(i) != '"') {
        throw new IllegalArgumentException("Expected '\"' at " + i);
      }
      i++;
      int keyStart = i;
      while (i < json.length() && json.charAt(i) != '"') {
        if (json.charAt(i) == '\\') {
          i++;
        }
        i++;
      }
      String key = json.substring(keyStart, i);
      i++; // skip closing quote

      // Skip colon
      while (i < json.length()
          && (json.charAt(i) == ':' || Character.isWhitespace(json.charAt(i)))) {
        i++;
      }

      // Parse value
      Object value;
      if (i >= json.length()) {
        break;
      }
      char c = json.charAt(i);
      if (c == '"') {
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && json.charAt(i) != '"') {
          if (json.charAt(i) == '\\') {
            i++;
            if (i < json.length()) {
              char escaped = json.charAt(i);
              switch (escaped) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(escaped);
              }
            }
          } else {
            sb.append(json.charAt(i));
          }
          i++;
        }
        value = sb.toString();
        i++; // skip closing quote
      } else if (c == '{') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '{') {
            depth++;
          }
          if (json.charAt(i) == '}') {
            depth--;
          }
          i++;
        }
        value = parseJsonObject(json.substring(start, i));
      } else if (c == '[') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '[') {
            depth++;
          }
          if (json.charAt(i) == ']') {
            depth--;
          }
          i++;
        }
        value = parseJsonArray(json.substring(start, i));
      } else if (c == 't' || c == 'f') {
        // boolean
        int start = i;
        while (i < json.length() && Character.isLetter(json.charAt(i))) {
          i++;
        }
        value = Boolean.parseBoolean(json.substring(start, i));
      } else if (c == 'n') {
        // null
        i += 4;
        value = null;
      } else {
        // number
        int start = i;
        while (i < json.length()
            && (Character.isDigit(json.charAt(i))
                || json.charAt(i) == '.'
                || json.charAt(i) == '-')) {
          i++;
        }
        String numStr = json.substring(start, i).trim();
        if (numStr.contains(".")) {
          value = Double.parseDouble(numStr);
        } else {
          try {
            value = Integer.parseInt(numStr);
          } catch (NumberFormatException e) {
            value = numStr;
          }
        }
      }

      result.put(key, value);

      // Skip comma
      while (i < json.length()
          && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
        i++;
      }
    }

    return result;
  }

  private List<Object> parseJsonArray(String json) {
    List<Object> result = new ArrayList<>();
    json = json.trim();
    if (json.equals("[]")) {
      return result;
    }

    // Remove outer brackets
    json = json.substring(1, json.length() - 1).trim();

    int i = 0;
    while (i < json.length()) {
      // Skip whitespace and commas
      while (i < json.length()
          && (Character.isWhitespace(json.charAt(i)) || json.charAt(i) == ',')) {
        i++;
      }
      if (i >= json.length()) {
        break;
      }

      char c = json.charAt(i);
      Object value;
      if (c == '"') {
        i++;
        StringBuilder sb = new StringBuilder();
        while (i < json.length() && json.charAt(i) != '"') {
          if (json.charAt(i) == '\\') {
            i++;
            if (i < json.length()) {
              char escaped = json.charAt(i);
              switch (escaped) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                default -> sb.append(escaped);
              }
            }
          } else {
            sb.append(json.charAt(i));
          }
          i++;
        }
        value = sb.toString();
        i++; // skip closing quote
      } else if (c == '{') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '{') {
            depth++;
          }
          if (json.charAt(i) == '}') {
            depth--;
          }
          i++;
        }
        value = parseJsonObject(json.substring(start, i));
      } else if (c == '[') {
        int start = i;
        int depth = 1;
        i++;
        while (i < json.length() && depth > 0) {
          if (json.charAt(i) == '[') {
            depth++;
          }
          if (json.charAt(i) == ']') {
            depth--;
          }
          i++;
        }
        value = parseJsonArray(json.substring(start, i));
      } else {
        int start = i;
        while (i < json.length()
            && (Character.isDigit(json.charAt(i))
                || json.charAt(i) == '.'
                || json.charAt(i) == '-')) {
          i++;
        }
        String str = json.substring(start, i).trim();
        if (str.startsWith("$mn_qp:")) {
          value = str;
        } else if (str.equals("true")) {
          value = true;
        } else if (str.equals("false")) {
          value = false;
        } else if (str.equals("null")) {
          value = null;
        } else if (str.contains(".")) {
          value = Double.parseDouble(str);
        } else {
          try {
            value = Integer.parseInt(str);
          } catch (NumberFormatException e) {
            value = str;
          }
        }
      }

      result.add(value);
    }

    return result;
  }

  /** Builds a Nitrite Filter from a parsed JSON filter object. */
  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter buildFilterFromJson(final Map<String, Object> filterObj, final Object[] params) {
    if (filterObj.isEmpty()) {
      return Filter.ALL;
    }

    List<Filter> filters = new ArrayList<>();

    for (Map.Entry<String, Object> entry : filterObj.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (key.equals("$sort")) {
        continue; // Sort handled separately by parseSortFromJsonQuery
      }

      if (key.equals("$and")) {
        if (value instanceof List) {
          List<Filter> andFilters = new ArrayList<>();
          for (Object item : (List<?>) value) {
            if (item instanceof Map) {
              Filter f = buildFilterFromJson((Map<String, Object>) item, params);
              if (f != null && f != Filter.ALL) {
                andFilters.add(f);
              }
            }
          }
          if (!andFilters.isEmpty()) {
            filters.add(Filter.and(andFilters.toArray(new Filter[0])));
          }
        }
      } else if (key.equals("$or")) {
        if (value instanceof List) {
          List<Filter> orFilters = new ArrayList<>();
          for (Object item : (List<?>) value) {
            if (item instanceof Map) {
              Filter f = buildFilterFromJson((Map<String, Object>) item, params);
              if (f != null && f != Filter.ALL) {
                orFilters.add(f);
              }
            }
          }
          if (!orFilters.isEmpty()) {
            filters.add(Filter.or(orFilters.toArray(new Filter[0])));
          }
        }
      } else {
        // Field filter
        if (value instanceof Map) {
          Filter f = buildFieldFilter(key, (Map<String, Object>) value, params);
          if (f != null && f != Filter.ALL) {
            filters.add(f);
          }
        } else {
          // Simple equality: { "field": "value" }
          Filter f = buildFieldFilter(key, Collections.singletonMap("$eq", value), params);
          if (f != null && f != Filter.ALL) {
            filters.add(f);
          }
        }
      }
    }

    if (filters.isEmpty()) {
      return Filter.ALL;
    }
    if (filters.size() == 1) {
      return filters.get(0);
    }
    return Filter.and(filters.toArray(new Filter[0]));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private Filter buildFieldFilter(
      final String field, final Map<String, Object> operators, final Object[] params) {
    List<Filter> fieldFilters = new ArrayList<>();

    for (Map.Entry<String, Object> opEntry : operators.entrySet()) {
      String op = opEntry.getKey();
      Object value = opEntry.getValue();

      // Resolve old-style parameter placeholder (SQL path): "$mn_qp:0"
      if (value instanceof String s && s.startsWith("$mn_qp:")) {
        int paramIdx = Integer.parseInt(s.substring(7));
        if (params != null && paramIdx >= 0 && paramIdx < params.length) {
          value = toFilterValue(params[paramIdx]);
        }
      }
      // Resolve new-style parameter placeholder (JSON path): {"$mn_qp": 0}
      if (value instanceof Map<?, ?> vm
          && vm.size() == 1
          && vm.containsKey("$mn_qp")
          && vm.get("$mn_qp") instanceof Integer paramIdx) {
        if (params != null && paramIdx >= 0 && paramIdx < params.length) {
          value = toFilterValue(params[paramIdx]);
        }
      }

      // Convert value to BigDecimal if we are comparing against a stored Instant (stored as
      // BigDecimal)
      // to avoid Double precision issues.
      Object finalValue =
          (value instanceof Double || value instanceof Float || value instanceof Long)
              ? new BigDecimal(value.toString())
              : value;

      // Handle ID field specially - must be Long to match Nitrite's auto-gen ID type
      if ("id".equals(field) && finalValue != null) {
        try {
          finalValue = Long.valueOf(finalValue.toString());
        } catch (Exception ignored) {
        }
      }

      LOG.trace(
          "buildFieldFilter field={} op={} valueType={} value={}",
          field,
          op,
          (finalValue != null ? finalValue.getClass().getName() : "null"),
          finalValue);

      Filter f =
          switch (op) {
            case "$eq" -> FluentFilter.where(field).eq(finalValue);
            case "$ne" -> FluentFilter.where(field).notEq(finalValue);
            case "$gt" -> FluentFilter.where(field).gt((Comparable) finalValue);
            case "$gte" -> FluentFilter.where(field).gte((Comparable) finalValue);
            case "$lt" -> FluentFilter.where(field).lt((Comparable) finalValue);
            case "$lte" -> FluentFilter.where(field).lte((Comparable) finalValue);
            case "$in" -> {
              if (finalValue instanceof Collection) {
                yield FluentFilter.where(field)
                    .in(((Collection<?>) finalValue).toArray(new Comparable[0]));
              } else {
                // Single value - treat as single-element list
                yield FluentFilter.where(field).eq(finalValue);
              }
            }
            case "$nin" -> {
              if (finalValue instanceof Collection) {
                yield FluentFilter.where(field)
                    .notIn(((Collection<?>) finalValue).toArray(new Comparable[0]));
              } else {
                // Single value
                yield FluentFilter.where(field).notEq(finalValue);
              }
            }
            case "$null" ->
                finalValue.equals(true) ? FluentFilter.where(field).eq(null) : Filter.ALL;
            case "$notNull" ->
                finalValue.equals(true) ? FluentFilter.where(field).notEq(null) : Filter.ALL;
            case "$between" -> {
              if (finalValue instanceof List) {
                List<?> list = (List<?>) finalValue;
                yield FluentFilter.where(field)
                    .between((Comparable) list.get(0), (Comparable) list.get(1));
              }
              yield Filter.ALL;
            }
            case "$regex" -> FluentFilter.where(field).regex(finalValue.toString());
            case "$not" -> {
              // Negation of a filter
              if (finalValue instanceof Map) {
                Filter innerFilter =
                    buildFieldFilter(field, (Map<String, Object>) finalValue, params);
                yield innerFilter != Filter.ALL ? innerFilter.not() : Filter.ALL;
              }
              yield Filter.ALL;
            }
            case "$exists" ->
                finalValue.equals(true)
                    ? FluentFilter.where(field).notEq(null)
                    : FluentFilter.where(field).eq(null);
            case "$size" -> {
              // Size operator - Nitrite doesn't have direct size support, use document field size
              if (finalValue instanceof Map) {
                Map<?, ?> sizeCriteria = (Map<?, ?>) finalValue;
                // For now, handle simple equality
                Object sizeValue = sizeCriteria.get("$eq");
                if (sizeValue != null) {
                  // Note: This requires the document to have a pre-computed size field
                  // Full implementation would need custom filter
                  yield FluentFilter.where(field + "_size").eq(sizeValue);
                }
              }
              yield Filter.ALL;
            }
            case "$empty" ->
                finalValue.equals(true)
                    ? Filter.or(
                        FluentFilter.where(field).eq(""), FluentFilter.where(field).eq(null))
                    : Filter.and(
                        FluentFilter.where(field).notEq(""), FluentFilter.where(field).notEq(null));
            case "$true" -> FluentFilter.where(field).eq(true);
            case "$false" -> FluentFilter.where(field).eq(false);
            case "$arrayContains" -> {
              // For array fields, check if array contains the value
              if (finalValue instanceof Collection) {
                Collection<?> vals = (Collection<?>) finalValue;
                yield FluentFilter.where(field).in(vals.toArray(new Comparable[0]));
              }
              yield FluentFilter.where(field).eq(finalValue);
            }
            default -> FluentFilter.where(field).eq(finalValue);
          };

      if (f != null && f != Filter.ALL) {
        fieldFilters.add(f);
      }
    }

    if (fieldFilters.isEmpty()) {
      return Filter.ALL;
    }
    if (fieldFilters.size() == 1) {
      return fieldFilters.get(0);
    }
    return Filter.and(fieldFilters.toArray(new Filter[0]));
  }

  // ========== PreparedQuery operations ==========

  public <T, R> R findOne(@NonNull final PreparedQuery<T, R> q) {
    Class<T> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);

    // Count PreparedQuery (e.g. for pagination total): result type is Number
    Class<R> resultType = q.getResultType();
    if (Number.class.isAssignableFrom(resultType)) {
      return (R) Long.valueOf(collection.find(filter).size());
    }

    Document doc = collection.find(filter).firstOrNull();
    if (doc == null) {
      return null;
    }
    return (R) fromDocument(doc, rootEntity);
  }

  public <T, R> Optional<R> findOptional(@NonNull final PreparedQuery<T, R> q) {
    return Optional.ofNullable(findOne(q));
  }

  public <T> boolean exists(@NonNull final PreparedQuery<T, Boolean> q) {
    Class<T> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);
    return collection.find(filter).firstOrNull() != null;
  }

  public <T, R> Iterable<R> findAll(@NonNull final PreparedQuery<T, R> q) {
    LOG.trace("findAll query={} pageable={}", q.getQuery(), q.getPageable());
    Class<T> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);

    // Count queries (e.g. SELECT COUNT(...)) route through findAll when @Query is present.
    // The interceptor expects the first element to be a Long.
    Class<R> resultType = q.getResultType();
    if (Number.class.isAssignableFrom(resultType)) {
      long count = collection.find(filter).size();
      LOG.trace("findAll countQuery result={}", count);
      return Collections.singletonList((R) Long.valueOf(count));
    }

    // Resolve sort: JSON $sort → SQL ORDER BY → query hints → pageable
    Sort sortFromQuery = parseSortFromJsonQuery(q.getQuery());
    if (sortFromQuery == null) {
      sortFromQuery = parseSortFromSqlQuery(q.getQuery());
    }
    if (sortFromQuery == null) {
      sortFromQuery = parseSortFromHints(q.getQueryHints());
    }
    FindOptions options = buildFindOptions(q.getPageable(), sortFromQuery);
    var cursor = collection.find(filter, options);
    List<R> results = new ArrayList<>();
    for (Document doc : cursor) {
      results.add((R) fromDocument(doc, rootEntity));
    }
    LOG.trace("findAll resultCount={}", results.size());
    return results;
  }

  public <T, R> Stream<R> findStream(@NonNull final PreparedQuery<T, R> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  public <T> Stream<T> findStream(@NonNull final PagedQuery<T> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  public <R> Page<R> findPage(@NonNull final PagedQuery<R> q) {
    Iterable<R> results = findAll(q);
    List<R> list = new ArrayList<>();
    results.forEach(list::add);
    return Page.of(list, q.getPageable(), count(q));
  }

  public <T, R> R findSlice(@NonNull final PreparedQuery<T, R> q) {
    return (R) findAll(q);
  }

  public <T, R> R findPage(@NonNull final PreparedQuery<T, R> q) {
    Class<T> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);
    Sort sortFromQuery = parseSortFromJsonQuery(q.getQuery());
    if (sortFromQuery == null) {
      sortFromQuery = parseSortFromSqlQuery(q.getQuery());
    }
    if (sortFromQuery == null) {
      sortFromQuery = parseSortFromHints(q.getQueryHints());
    }

    // Get total count first (single query)
    long total = collection.find(filter).size();

    // Then apply pagination
    FindOptions options = buildFindOptions(q.getPageable(), sortFromQuery);
    var cursor = collection.find(filter, options);
    List<T> list = new ArrayList<>();
    for (Document doc : cursor) {
      list.add(fromDocument(doc, rootEntity));
    }
    return (R) Page.of(list, q.getPageable(), total);
  }

  public <T> long count(@NonNull final PreparedQuery<T, Long> q) {
    Class<T> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);
    return collection.find(filter).size();
  }

  /**
   * Reorders the PreparedQuery parameter array to match SQL :p1, :p2, ... positions. For UPDATE SQL
   * the processor assigns :p1 to the SET field regardless of its method argument position, so we
   * use QueryParameterBinding.getParameterIndex() to get the correct method-arg index for each SQL
   * placeholder.
   */
  private Object[] reorderParamsForSql(final PreparedQuery<?, ?> q) {
    Object[] rawParams = q.getParameterArray();
    List<QueryParameterBinding> bindings = q.getQueryBindings();
    if (bindings == null || bindings.isEmpty() || rawParams == null) {
      return rawParams;
    }
    Object[] reordered = new Object[bindings.size()];
    for (QueryParameterBinding binding : bindings) {
      String name = binding.getName(); // "p1", "p2", ...
      int paramIdx = binding.getParameterIndex(); // index into rawParams
      if (name != null && name.startsWith("p")) {
        try {
          int sqlPos = Integer.parseInt(name.substring(1)) - 1; // "p1" -> 0
          if (sqlPos >= 0
              && sqlPos < reordered.length
              && paramIdx >= 0
              && paramIdx < rawParams.length) {
            reordered[sqlPos] = rawParams[paramIdx];
          }
        } catch (NumberFormatException ignored) {
        }
      }
    }
    return reordered;
  }

  public Optional<Number> executeUpdate(@NonNull final PreparedQuery<?, Number> q) {
    String sql = q.getQuery();
    if (sql == null || !sql.trim().toUpperCase().startsWith("UPDATE")) {
      throw new UnsupportedOperationException(
          "executeUpdate() called with non-UPDATE statement: " + sql);
    }
    // Reorder params to match SQL :p1, :p2, ... positions using binding metadata.
    // The processor assigns :p1 to the SET field regardless of its method arg position,
    // so we cannot use raw method arg order.
    Object[] params = reorderParamsForSql(q);
    Map<String, Object> setFields = parseSetClause(sql, params);
    if (setFields.isEmpty()) {
      return Optional.of(0);
    }
    Filter filter = parseFilterFromUpdateStatement(sql, params);
    NitriteCollection collection = getCollection(q.getRootEntity());

    // Find matching docs, merge SET fields, replace by id (same pattern as update(entity))
    int count = 0;
    for (Document existing : collection.find(filter)) {
      for (Map.Entry<String, Object> entry : setFields.entrySet()) {
        existing.put(entry.getKey(), entry.getValue());
      }
      Object docId = existing.get("id");
      if (docId != null) {
        collection.update(FluentFilter.where("id").eq(docId), existing);
        count++;
      }
    }
    return Optional.of(count);
  }

  /** Parse SET clause from UPDATE SQL into a field→value map. */
  private Map<String, Object> parseSetClause(final String sql, final Object[] params) {
    int setIdx = sql.toUpperCase().indexOf(" SET ");
    int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
    if (setIdx < 0) {
      return Map.of();
    }
    String setClause =
        whereIdx >= 0 ? sql.substring(setIdx + 5, whereIdx) : sql.substring(setIdx + 5);
    Map<String, Object> fields = new LinkedHashMap<>();
    Matcher m = SQL_SET_ASSIGNMENT.matcher(setClause);
    while (m.find()) {
      String field = m.group(1);
      String pname = m.group(2);
      Object val = resolveParam(pname, params);
      fields.put(field, val);
    }
    return fields;
  }

  /** Parse WHERE clause from UPDATE SQL into a Nitrite Filter. */
  private Filter parseFilterFromUpdateStatement(final String sql, final Object[] params) {
    int whereIdx = sql.toUpperCase().indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String where = sql.substring(whereIdx + 7).trim();
    if (where.startsWith("(") && where.endsWith(")")) {
      where = where.substring(1, where.length() - 1);
    }
    return parseWhereClause(where, params);
  }

  public Optional<Number> executeDelete(@NonNull final PreparedQuery<?, Number> q) {
    Class<?> rootEntity = q.getRootEntity();
    NitriteCollection collection = getCollection(rootEntity);
    Filter filter = buildFilterFromPreparedQuery(q);
    var result = collection.remove(filter, false);
    return Optional.of(result.getAffectedCount());
  }

  public <R> List<R> execute(@NonNull final PreparedQuery<?, R> q) {
    List<R> list = new ArrayList<>();
    findAll(q).forEach(list::add);
    return list;
  }

  // ========== Unsupported QueryModel-based methods (deprecated API) ==========

  public <T, R> R findOptional(
      @NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
    throw new UnsupportedOperationException();
  }

  public <T, R> R findOne(
      @NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
    throw new UnsupportedOperationException();
  }

  public <T, R> Iterable<R> findAll(
      @NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
    throw new UnsupportedOperationException();
  }

  public <T, R> R findSlice(
      @NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
    throw new UnsupportedOperationException();
  }

  public <T, R> R findPage(
      @NonNull final QueryModel q, @NonNull final Class<T> e, @NonNull final Class<R> p) {
    throw new UnsupportedOperationException();
  }

  public <T> long count(@NonNull final QueryModel q, @NonNull final Class<T> e) {
    throw new UnsupportedOperationException();
  }

  public <T> boolean exists(@NonNull final QueryModel q, @NonNull final Class<T> e) {
    throw new UnsupportedOperationException();
  }

  public <T, R> R update(
      @NonNull final QueryModel q,
      @NonNull final Class<T> e,
      @NonNull final Map<String, Object> p) {
    throw new UnsupportedOperationException();
  }

  public <T, R> R updateAll(
      @NonNull final List<QueryModel> q,
      @NonNull final Class<T> e,
      @NonNull final List<Map<String, Object>> p) {
    throw new UnsupportedOperationException();
  }

  public <T> int delete(@NonNull final QueryModel q, @NonNull final Class<T> e) {
    throw new UnsupportedOperationException();
  }

  public <T> int deleteAll(@NonNull final Iterable<QueryModel> q, @NonNull final Class<T> e) {
    throw new UnsupportedOperationException();
  }

  public <T> int deleteAll(@NonNull final Class<T> e, @NonNull final Serializable... ids) {
    throw new UnsupportedOperationException();
  }

  public <T> int deleteAll(
      @NonNull final DeleteBatchOperation<T> op, @NonNull final Iterable<T> entities) {
    throw new UnsupportedOperationException();
  }

  public <T> T merge(@NonNull final UpdateOperation<T> op) {
    throw new UnsupportedOperationException();
  }

  public <T> Iterable<T> mergeAll(@NonNull final UpdateBatchOperation<T> op) {
    throw new UnsupportedOperationException();
  }

  public <T> Optional<T> findOne(@NonNull final EntityOperation<T> op) {
    throw new UnsupportedOperationException();
  }

  public <T> Iterable<T> findAll(@NonNull final EntityOperation<T> op) {
    throw new UnsupportedOperationException();
  }

  public <T> T delete(@NonNull final EntityOperation<T> op) {
    throw new UnsupportedOperationException();
  }

  public <T> Iterable<T> deleteAll(
      @NonNull final EntityOperation<T> op, @NonNull final Iterable<T> entities) {
    throw new UnsupportedOperationException();
  }
}
