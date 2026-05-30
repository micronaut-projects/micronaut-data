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

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.StringUtils;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.nitrite.annotation.FullTextIndex;
import io.micronaut.data.nitrite.annotation.SpatialIndex;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.DefaultNitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.DefaultNitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteUpdateExecutor;
import io.micronaut.data.nitrite.transaction.NitriteTransactionContext;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.index.IndexOptions;
import org.dizitart.no2.index.IndexType;
import org.dizitart.no2.repository.ObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.dizitart.no2.index.IndexOptions.indexOptions;

/**
 * Default Nitrite repository operations. Implements core CRUD using Nitrite's Document codec.
 */
@Singleton
@Internal
@SuppressWarnings({"removal", "unchecked", "rawtypes"})
public final class DefaultNitriteRepositoryOperations extends AbstractRepositoryOperations
    implements NitriteRepositoryOperations, PreparedQueryDecorator, MethodContextAwareStoredQueryDecorator {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultNitriteRepositoryOperations.class);

  // Patterns for parsing SQL WHERE clauses from document processor
  private static final Pattern SQL_COMPARISON =
      Pattern.compile("(?:\\w+\\.)?(\\w+)\\s*(=|!=|<>|>|<|>=|<=)\\s*:(\\w+)");
  private static final Pattern SQL_IS_NOT_NULL =
      Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+NOT\\s+NULL");
  private static final Pattern SQL_IS_NULL =
      Pattern.compile("(?:\\w+\\.)?(\\w+)\\s+IS\\s+(?!NOT\\s+)NULL");

  private final Nitrite database;
  private final NitriteConfiguration configuration;
  private final NitriteEntityMapper entityMapper;
  private final NitriteTransactionHolder transactionHolder;
  private final NitriteQueryParser queryParser;
  private final NitriteFilterBuilder filterBuilder;
  private final NitriteUpdateExecutor updateExecutor;
  private final Set<String> indexedCollections = ConcurrentHashMap.newKeySet();

  /**
   * Default constructor.
   *
   * @param database the nitrite database
   * @param configuration the nitrite configuration
   * @param dateTimeProvider the date time provider
   * @param runtimeEntityRegistry the runtime entity registry
   * @param conversionService the conversion service
   * @param attributeConverterRegistry the attribute converter registry
   * @param transactionHolder the transaction holder
   * @param serdeObjectMapper the Micronaut Serde ObjectMapper
   */
  public DefaultNitriteRepositoryOperations(
      final Nitrite database,
      final NitriteConfiguration configuration,
      final DateTimeProvider<Object> dateTimeProvider,
      final RuntimeEntityRegistry runtimeEntityRegistry,
      final DataConversionService conversionService,
      final AttributeConverterRegistry attributeConverterRegistry,
      final NitriteTransactionHolder transactionHolder,
      final io.micronaut.serde.ObjectMapper serdeObjectMapper) {
    super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
    this.database = database;
    this.configuration = configuration;
    this.entityMapper =
        new NitriteEntityMapper(
            conversionService, serdeObjectMapper, database.getConfig().nitriteMapper(), runtimeEntityRegistry);
    this.transactionHolder = transactionHolder;
    this.queryParser = new NitriteQueryParser();
    this.filterBuilder = new NitriteFilterBuilder(entityMapper);
    this.updateExecutor = new NitriteUpdateExecutor(entityMapper);
  }

  @Override
  public Nitrite getDatabase() {
    return database;
  }

  /**
   * Returns the repository for the given entity type.
   *
   * @param entityType the entity type
   * @return the repository
   * @param <T> the entity type
   */
  public <T> ObjectRepository<T> getRepository(final Class<T> entityType) {
    return database.getRepository(entityType);
  }

  /**
   * Returns the repository for the given entity type and discriminator.
   *
   * @param entityType the entity type
   * @param discriminator the discriminator
   * @return the repository
   * @param <T> the entity type
   */
  public <T> ObjectRepository<T> getRepository(final Class<T> entityType, final String discriminator) {
    return database.getRepository(entityType, discriminator);
  }

  private String getCollectionName(final Class<?> type) {
    MappedEntity mappedEntity = type.getAnnotation(MappedEntity.class);
    return (mappedEntity != null && !mappedEntity.value().isEmpty()) ? mappedEntity.value() : type.getSimpleName();
  }

  private NitriteCollection getCollection(final Class<?> type) {
    String name = getCollectionName(type);
    NitriteCollection collection;
    NitriteTransactionContext ctx = transactionHolder.get();
    if (ctx != null) {
      database.getCollection(name);
      collection = ctx.getCollection(name);
    } else {
      collection = database.getCollection(name);
    }
    ensureIndexes(type, collection);
    return collection;
  }

  private void ensureIndexes(Class<?> type, NitriteCollection collection) {
    if (!configuration.isCreateIndexes() || indexedCollections.contains(collection.getName())) {
      return;
    }
    indexedCollections.add(collection.getName());
    RuntimePersistentEntity<?> entity = getEntity(type);
    List<AnnotationValue<Index>> indexes = entity.getAnnotationMetadata().getAnnotationValuesByType(Index.class);
    for (AnnotationValue<Index> index : indexes) {
      String[] columns = index.getRequiredValue("columns", String[].class);
      boolean unique = index.getRequiredValue("unique", Boolean.class);
      IndexOptions options = indexOptions(unique ? IndexType.UNIQUE : IndexType.NON_UNIQUE);
      try {
        collection.createIndex(options, columns);
      } catch (Exception e) {
        if (LOG.isWarnEnabled()) {
          LOG.warn("Could not create index for collection {}: {}", collection.getName(), e.getMessage());
        }
      }
    }
    for (RuntimePersistentProperty<?> property : entity.getPersistentProperties()) {
      if (property.getAnnotationMetadata().hasAnnotation(Index.class)) {
        AnnotationValue<Index> index = property.getAnnotationMetadata().getAnnotation(Index.class);
        boolean unique = index != null && index.booleanValue("unique").orElse(false);
        try {
          collection.createIndex(indexOptions(unique ? IndexType.UNIQUE : IndexType.NON_UNIQUE), property.getPersistedName());
        } catch (Exception e) {
          if (LOG.isWarnEnabled()) {
            LOG.warn("Could not create index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
          }
        }
      }
      if (property.getAnnotationMetadata().hasAnnotation(FullTextIndex.class)) {
        try {
          collection.createIndex(indexOptions(IndexType.FULL_TEXT), property.getPersistedName());
        } catch (Exception e) {
          if (LOG.isWarnEnabled()) {
            LOG.warn("Could not create full-text index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
          }
        }
      }
      if (property.getAnnotationMetadata().hasAnnotation(SpatialIndex.class)) {
        try {
          collection.createIndex(indexOptions("Spatial"), property.getPersistedName());
        } catch (Exception e) {
          if (LOG.isWarnEnabled()) {
            LOG.warn("Could not create spatial index for field {} in collection {}: {}", property.getName(), collection.getName(), e.getMessage());
          }
        }
      }
    }
  }

  private <T> void generateIdIfNecessary(@NonNull final T entity, @NonNull final Class<T> type) {
    RuntimePersistentEntity<T> persistentEntity = getEntity(type);
    var idProperty = persistentEntity.getIdentity();
    if (idProperty != null && idProperty.isAnnotationPresent(GeneratedValue.class)) {
      Class<?> idType = idProperty.getType();
      Object generatedId = (idType == String.class) ? UUID.randomUUID().toString() :
                           (idType == UUID.class) ? UUID.randomUUID() :
                           (idType == Long.class || idType == long.class) ? System.currentTimeMillis() :
                           (idType == Integer.class || idType == int.class) ? (int) (System.currentTimeMillis() % Integer.MAX_VALUE) :
                           UUID.randomUUID().toString();
      idProperty.getProperty().set(entity, generatedId);
    }
  }

  @Override
  @Nullable
  public <T> T findOne(@NonNull final Class<T> type, @NonNull final Object id) {
    Document doc = getCollection(type).find(entityMapper.idEqualsFilter(type, id)).firstOrNull();
    return doc == null ? null : entityMapper.fromDocument(doc, type);
  }

  @Override
  @NonNull
  public <T> T persist(@NonNull final InsertOperation<T> operation) {
    T entity = operation.getEntity();
    Class<T> type = operation.getRootEntity();
    generateIdIfNecessary(entity, type);
    getCollection(type).insert(entityMapper.toDocument(entity));
    return entity;
  }

  @Override
  @NonNull
  public <T> Iterable<T> persistAll(@NonNull final InsertBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    for (T entity : operation) {
      generateIdIfNecessary(entity, type);
      collection.insert(entityMapper.toDocument(entity));
    }
    return operation;
  }

  @Override
  @NonNull
  public <T> T update(@NonNull final UpdateOperation<T> operation) {
    T entity = operation.getEntity();
    Class<T> type = operation.getRootEntity();
    Object idValue = entityMapper.getEntityIdValue(entity, type);
    if (idValue != null) {
      getCollection(type).update(entityMapper.idEqualsFilter(type, idValue), entityMapper.toDocument(entity));
    }
    return entity;
  }

  @Override
  @NonNull
  public <T> Iterable<T> updateAll(@NonNull final UpdateBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    for (T entity : operation) {
      Object idValue = entityMapper.getEntityIdValue(entity, type);
      if (idValue != null) {
        collection.update(entityMapper.idEqualsFilter(type, idValue), entityMapper.toDocument(entity));
      }
    }
    return operation;
  }

  @Override
  public <T> int delete(@NonNull final DeleteOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    Object idValue = entityMapper.getEntityIdValue(operation.getEntity(), type);
    if (idValue == null) {
      return 0;
    }
    return getCollection(type).remove(entityMapper.idEqualsFilter(type, idValue), false).getAffectedCount();
  }

  @Override
  @NonNull
  public <T> Optional<Number> deleteAll(@NonNull final DeleteBatchOperation<T> operation) {
    Class<T> type = operation.getRootEntity();
    NitriteCollection collection = getCollection(type);
    if (operation.all()) {
      collection.clear();
      return Optional.of(-1);
    }
    int count = 0;
    for (T entity : operation) {
      Object idValue = entityMapper.getEntityIdValue(entity, type);
      if (idValue != null && collection.remove(entityMapper.idEqualsFilter(type, idValue), false).getAffectedCount() > 0) {
        count++;
      }
    }
    return Optional.of(count);
  }

  @NonNull
  private FindOptions buildFindOptions(@Nullable final Pageable pageable, @Nullable final String jsonQuery) {
    FindOptions options = buildFindOptions(pageable, (Sort) null);
    if (jsonQuery != null && jsonQuery.contains("\"$skip\"")) {
      try {
        Object parsed = queryParser.parseJson(jsonQuery);
        if (parsed instanceof Map m) {
          if (m.get("$skip") instanceof Number n) {
            options.skip(n.longValue());
          }
          if (m.get("$limit") instanceof Number n) {
            options.limit(n.intValue());
          }
        }
      } catch (Exception ignored) {
          // ignore parsing error for skip/limit
      }
    }
    return options;
  }

  @NonNull
  private FindOptions buildFindOptions(@Nullable final Pageable pageable, @Nullable final Sort additionalSort) {
    FindOptions options = new FindOptions();
    if (pageable != null && pageable.getOffset() > 0) {
      options.skip(pageable.getOffset());
    }
    if (pageable != null && pageable.getSize() > 0) {
      options.limit(pageable.getSize());
    }
    Map<String, Sort.Order> mergedOrders = new LinkedHashMap<>();
    if (additionalSort != null && additionalSort.isSorted()) {
      for (var order : additionalSort.getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }
    if (pageable != null && pageable.getSort() != null && pageable.getSort().isSorted()) {
      for (var order : pageable.getSort().getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }
    if (!mergedOrders.isEmpty()) {
      for (var order : mergedOrders.values()) {
        SortOrder sortOrder = order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.Ascending : SortOrder.Descending;
        String property = order.getProperty();
        if (property != null && property.contains(".")) {
          property = property.substring(property.lastIndexOf('.') + 1);
        }
        options.thenOrderBy(entityMapper.normalizeFieldName(Objects.requireNonNull(property)), sortOrder);
      }
    }
    return options;
  }

  @Nullable
  private Sort parseSortFromSqlQuery(@Nullable final String sql) {
    if (sql == null) {
      return null;
    }
    int idx = sql.toUpperCase(Locale.ROOT).indexOf(" ORDER BY ");
    if (idx < 0) {
      return null;
    }
    String clause = sql.substring(idx + 10).trim();
    List<Sort.Order> orders = new ArrayList<>();
    for (String part : StringUtils.tokenizeToStringArray(clause, ",")) {
      String[] tokens = StringUtils.tokenizeToStringArray(part.trim(), " \t\n\r\f");
      if (tokens.length == 0 || tokens[0].isBlank()) {
        continue;
      }
      String f = tokens[0];
      String field = f.contains(".") ? f.substring(f.lastIndexOf('.') + 1) : f;
      orders.add(tokens.length > 1 && "DESC".equalsIgnoreCase(tokens[1]) ? Sort.Order.desc(field) : Sort.Order.asc(field));
    }
    return orders.isEmpty() ? null : Sort.of(orders);
  }

  @Nullable
  private Sort parseSortFromJsonQuery(@Nullable final String queryString) {
    if (queryString == null || !queryString.contains("\"$sort\"")) {
      return null;
    }
    try {
      Object parsed = queryParser.parseJson(queryString);
      if (parsed instanceof Map m && m.get("$sort") instanceof Map sortObj) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Map.Entry<?, ?> e : ((Map<?, ?>) sortObj).entrySet()) {
          int dir = e.getValue() instanceof Number ? ((Number) e.getValue()).intValue() : 1;
          orders.add(dir >= 1 ? Sort.Order.asc(e.getKey().toString()) : Sort.Order.desc(e.getKey().toString()));
        }
        return orders.isEmpty() ? null : Sort.of(orders);
      }
    } catch (Exception ignored) {
        // ignore parsing error for sort
    }
    return null;
  }

  @Nullable
  private Sort parseSortFromHints(@Nullable final Map<String, Object> hints) {
    if (hints == null || hints.isEmpty() || !(hints.get("sort") instanceof String sortStr) || sortStr.isEmpty()) {
      return null;
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String part : StringUtils.tokenizeToStringArray(sortStr, ",")) {
      String[] parts = StringUtils.tokenizeToStringArray(part.trim(), ":");
      if (parts.length == 2) {
        try {
          orders.add(Sort.Order.Direction.valueOf(parts[1].toUpperCase(Locale.ROOT)) == Sort.Order.Direction.ASC ? Sort.Order.asc(parts[0]) : Sort.Order.desc(parts[0]));
        } catch (IllegalArgumentException ignored) {
            // ignore invalid sort direction
        }
      }
    }
    return orders.isEmpty() ? null : Sort.of(orders);
  }

  @Override
  @NonNull
  public <T> Iterable<T> findAll(@NonNull final PagedQuery<T> query) {
    Class<T> type = (Class<T>) query.getRootEntity();
    var cursor = getCollection(type).find(Filter.ALL, buildFindOptions(query.getPageable(), (String) null));
    List<T> results = new ArrayList<>();
    for (Document doc : cursor) {
      results.add(entityMapper.fromDocument(doc, type));
    }
    return results;
  }

  @Override
  public <T> long count(@NonNull final PagedQuery<T> query) {
    return getCollection(query.getRootEntity()).size();
  }

  @NonNull
  private Object[] buildJsonParameterValues(@NonNull final PreparedQuery<?, ?> q) {
    Object[] methodParams = q.getParameterArray();
    List<QueryParameterBinding> bindings = q.getQueryBindings();
    if (bindings == null || bindings.isEmpty()) {
      return methodParams != null ? methodParams : new Object[0];
    }
    Object[] values = new Object[bindings.size()];
    for (int i = 0; i < bindings.size(); i++) {
      values[i] = resolveJsonBindingValue(bindings.get(i), methodParams);
    }
    return values;
  }

  @Nullable
  private Object resolveJsonBindingValue(@NonNull final QueryParameterBinding binding, @Nullable final Object[] methodParams) {
    Object bindingValue = binding.getValue();
    if (bindingValue != null && !(bindingValue instanceof BindingParameter)) {
      return entityMapper.toFilterValue(bindingValue);
    }
    int idx = binding.getParameterIndex();
    Object base = (methodParams != null && idx >= 0 && idx < methodParams.length) ? methodParams[idx] : null;
    String[] path = binding.getParameterBindingPath() != null ? binding.getParameterBindingPath() : binding.getPropertyPath();
    if (path == null || path.length == 0) {
      return entityMapper.toFilterValue(base);
    }
    Object current = base;
    for (String segment : path) {
      if (current == null) {
        break;
      }
      if (current instanceof Map<?, ?> m) {
        current = m.get(segment);
      } else if (current instanceof Document d) {
        current = d.get(segment);
      } else {
        try {
          current = ((Document) Objects.requireNonNull(database.getConfig().nitriteMapper().tryConvert(current, Document.class))).get(segment);
        } catch (Exception ignored) {
            // ignore conversion error during path resolution
          return entityMapper.toFilterValue(base);
        }
      }
    }
    return entityMapper.toFilterValue(current);
  }

  @NonNull
  private Object[] ensureJsonParamsForFilter(@NonNull final Map<String, Object> filterMap, @Nullable final Object[] methodParams, @NonNull final Object[] jsonParams) {
    int maxIdx = findMaxPlaceholderIndex(filterMap);
    if (maxIdx < 0) {
      return jsonParams;
    }
    Object[] out = jsonParams;
    if (out.length <= maxIdx) {
      out = Arrays.copyOf(out, maxIdx + 1);
    }
    fillMissingParamsFromFilter(filterMap, methodParams, out);
    return out;
  }

  private int findMaxPlaceholderIndex(@NonNull final Map<String, Object> filterMap) {
    int max = -1;
    for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
      Object value = entry.getValue();
      if (value instanceof Map<?, ?> m) {
        if ("$and".equals(entry.getKey()) || "$or".equals(entry.getKey())) {
          if (value instanceof List<?> list) {
            for (Object item : list) {
              if (item instanceof Map<?, ?> im) {
                max = Math.max(max, findMaxPlaceholderIndex((Map<String, Object>) im));
              }
            }
          }
          continue;
        }
        for (Object opVal : m.values()) {
          Integer idx = extractPlaceholderIndex(opVal);
          if (idx != null) {
            max = Math.max(max, idx);
          }
        }
      } else {
        Integer idx = extractPlaceholderIndex(value);
        if (idx != null) {
          max = Math.max(max, idx);
        }
      }
    }
    return max;
  }

  private void fillMissingParamsFromFilter(@NonNull final Map<String, Object> filterMap, @Nullable final Object[] methodParams, @NonNull final Object[] out) {
    for (Map.Entry<String, Object> entry : filterMap.entrySet()) {
      Object value = entry.getValue();
      if (("$and".equals(entry.getKey()) || "$or".equals(entry.getKey())) && value instanceof List<?> list) {
        for (Object item : list) {
          if (item instanceof Map<?, ?> im) {
            fillMissingParamsFromFilter((Map<String, Object>) im, methodParams, out);
          }
        }
        continue;
      }
      if (value instanceof Map<?, ?> ops) {
        for (Object opVal : ops.values()) {
          Integer idx = extractPlaceholderIndex(opVal);
          if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
            out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
          }
        }
      } else {
        Integer idx = extractPlaceholderIndex(value);
        if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
          out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
        }
      }
    }
  }

  @Nullable
  private Integer extractPlaceholderIndex(@Nullable final Object value) {
    if (value instanceof String s && s.startsWith("$mn_qp:")) {
      try {
        return Integer.parseInt(s.substring(7));
      } catch (NumberFormatException ignored) {
          // ignore parsing error for placeholder index
      }
    }
    if (value instanceof Map<?, ?> vm && vm.size() == 1 && vm.get("$mn_qp") instanceof Integer idx) {
        return idx;
    }
    return null;
  }

  @Nullable
  private Object extractPropertyFromSingleArg(@Nullable final Object[] methodParams, @NonNull final String property) {
    if (methodParams == null || methodParams.length != 1 || methodParams[0] == null) {
      return null;
    }
    try {
      return entityMapper.toFilterValue(((Document) Objects.requireNonNull(database.getConfig().nitriteMapper().tryConvert(methodParams[0], Document.class))).get(property));
    } catch (Exception ignored) {
        // ignore conversion error during property extraction
      return null;
    }
  }

  @Override
  @NonNull
  public <E, R> PreparedQuery<E, R> decorate(@NonNull PreparedQuery<E, R> preparedQuery) {
    return createNitritePreparedQuery(preparedQuery);
  }

  @Override
  @NonNull
  public <E, R> StoredQuery<E, R> decorate(@NonNull MethodInvocationContext<?, ?> context, @NonNull StoredQuery<E, R> storedQuery) {
    return createNitriteStoredQuery(storedQuery);
  }

  /**
   * Creates a {@link NitriteStoredQuery}.
   *
   * @param storedQuery the stored query
   * @return the nitrite stored query
   * @param <E> the entity type
   * @param <R> the result type
   */
  @NonNull
  public <E, R> NitriteStoredQuery<E, R> createNitriteStoredQuery(@NonNull StoredQuery<E, R> storedQuery) {
    if (storedQuery instanceof NitriteStoredQuery nsq) {
      return nsq;
    }
    Map<String, Object> filterMap = null;
    Map<String, Object> updateMap = null;
    boolean sql = false;
    String query = storedQuery.getQuery();
    if (query.trim().startsWith("{")) {
      try {
        Object parsed = queryParser.parseJson(query);
        if (parsed instanceof Map m) {
          filterMap = (Map<String, Object>) m;
          updateMap = (Map<String, Object>) filterMap.get("$set");
        }
      } catch (Exception ignored) {
          // ignore parsing error for stored query JSON
      }
    } else {
      String upper = query.trim().toUpperCase(Locale.ROOT);
      sql = upper.startsWith("SELECT") || upper.startsWith("DELETE") || upper.startsWith("UPDATE");
    }
    return new DefaultNitriteStoredQuery<>(storedQuery, getEntity(storedQuery.getRootEntity()), conversionService, filterMap, null, updateMap, sql);
  }

  /**
   * Creates a {@link NitritePreparedQuery}.
   *
   * @param preparedQuery the prepared query
   * @return the nitrite prepared query
   * @param <E> the entity type
   * @param <R> the result type
   */
  @NonNull
  public <E, R> NitritePreparedQuery<E, R> createNitritePreparedQuery(@NonNull PreparedQuery<E, R> preparedQuery) {
    if (preparedQuery instanceof NitritePreparedQuery npq) {
      return npq;
    }
    NitriteStoredQuery<E, R> storedQuery;
    if (preparedQuery instanceof DelegateStoredQuery dsq && dsq.getStoredQueryDelegate() instanceof NitriteStoredQuery nsq) {
      storedQuery = nsq;
    } else {
      storedQuery = createNitriteStoredQuery(preparedQuery);
    }
    return new DefaultNitritePreparedQuery<>(preparedQuery, buildFilterFromPreparedQuery(preparedQuery, storedQuery), storedQuery.getFilterMap(), null, storedQuery.getUpdateMap(), storedQuery.isSql());
  }

  @NonNull
  private <E, R> NitritePreparedQuery<E, R> getNitritePreparedQuery(@NonNull PreparedQuery<E, R> q) {
    if (q instanceof NitritePreparedQuery nq) {
      return nq;
    }
    return createNitritePreparedQuery(q);
  }

  @NonNull
  private Filter buildFilterFromPreparedQuery(@NonNull final PreparedQuery<?, ?> q, @NonNull NitriteStoredQuery<?, ?> stored) {
    Map<String, Object> namedParameters = buildNamedParameterValues(q);
    Map<String, Object> filterMap = stored.getFilterMap();
    RuntimePersistentEntity<?> entity = getEntity(stored.getRootEntity());
    if (filterMap != null) {
      return filterBuilder.buildFilterFromJson(entity, filterMap, ensureJsonParamsForFilter(filterMap, q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
    }
    String queryString = q.getQuery().trim();
    if (queryString.isEmpty()) {
      return Filter.ALL;
    }
    String upper = queryString.toUpperCase(Locale.ROOT);
    if (upper.startsWith("DELETE")) {
      return parseFilterFromDeleteStatement(queryString, q.getParameterArray(), namedParameters, entity);
    }
    if (upper.startsWith("SELECT")) {
      return parseFilterFromSelectStatement(queryString, q.getParameterArray(), namedParameters, entity);
    }
    if (upper.startsWith("UPDATE")) {
      return parseFilterFromUpdateStatement(queryString, reorderParamsForSql(q), namedParameters, entity);
    }
    throw new IllegalStateException("Unsupported query format: " + queryString);
  }

  @NonNull
  private Filter parseFilterFromDeleteStatement(
      @NonNull final String sql, @Nullable final Object[] params, @NonNull final Map<String, Object> namedParameters, @NonNull final RuntimePersistentEntity<?> entity) {
    int whereIdx = sql.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String where = sql.substring(whereIdx + 7);
    int orderByIdx = where.toUpperCase(Locale.ROOT).indexOf(" ORDER BY");
    return parseWhereClause(
        orderByIdx >= 0 ? where.substring(0, orderByIdx) : where, params, namedParameters, entity);
  }

  @NonNull
  private Filter parseFilterFromSelectStatement(
      @NonNull final String sql, @Nullable final Object[] params, @NonNull final Map<String, Object> namedParameters, @NonNull final RuntimePersistentEntity<?> entity) {
    int whereIdx = sql.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String where = sql.substring(whereIdx + 7);
    int orderByIdx = where.toUpperCase(Locale.ROOT).indexOf(" ORDER BY");
    String w = (orderByIdx >= 0 ? where.substring(0, orderByIdx) : where).trim();
    return parseWhereClause(
        w.startsWith("(") && w.endsWith(")") ? w.substring(1, w.length() - 1) : w,
        params,
        namedParameters,
        entity);
  }

  @NonNull
  private Filter parseWhereClause(
      @NonNull String where, @Nullable final Object[] params, @NonNull final Map<String, Object> namedParameters, @NonNull final RuntimePersistentEntity<?> entity) {
    List<Filter> filters = new ArrayList<>();
    String emptyPat = "(?:\\w+\\.)?(\\w+)\\s+IS\\s+NULL\\s+OR\\s+(?:\\w+\\.)?\\1\\s*=\\s*''";
    Matcher mEmpty =
        Pattern.compile("\\(" + emptyPat + "\\)", Pattern.CASE_INSENSITIVE).matcher(where);
    if (!mEmpty.find()) {
      mEmpty = Pattern.compile(emptyPat, Pattern.CASE_INSENSITIVE).matcher(where);
    }
    mEmpty.reset();
    while (mEmpty.find()) {
      filters.add(
          filterBuilder.buildFieldFilter(
              entity,
              entityMapper.normalizeFieldName(Objects.requireNonNull(mEmpty.group(1))),
              Collections.singletonMap("$empty", true),
              params,
              namedParameters));
      where = where.substring(0, mEmpty.start()) + "PROCESSED" + where.substring(mEmpty.end());
      mEmpty = Pattern.compile(emptyPat, Pattern.CASE_INSENSITIVE).matcher(where);
    }
    Matcher m = SQL_COMPARISON.matcher(where);
    while (m.find()) {
      String op = m.group(2);
      String filterOp =
          switch (op != null ? op : "=") {
            case "=" -> "$eq";
            case "!=", "<>" -> "$ne";
            case ">" -> "$gt";
            case ">=" -> "$gte";
            case "<" -> "$lt";
            case "<=" -> "$lte";
            default -> "$eq";
          };
      filters.add(
          filterBuilder.buildFieldFilter(
              entity,
              entityMapper.normalizeFieldName(Objects.requireNonNull(m.group(1))),
              Collections.singletonMap(
                  filterOp,
                  entityMapper.toFilterValue(resolveSqlParam(m.group(3), params, namedParameters))),
              params,
              namedParameters));
    }
    m = SQL_IS_NOT_NULL.matcher(where);
    while (m.find()) {
      filters.add(
          filterBuilder.buildFieldFilter(
              entity,
              entityMapper.normalizeFieldName(Objects.requireNonNull(m.group(1))),
              Collections.singletonMap("$notNull", true),
              params,
              namedParameters));
    }
    m = SQL_IS_NULL.matcher(where);
    while (m.find()) {
      filters.add(
          filterBuilder.buildFieldFilter(
              entity,
              entityMapper.normalizeFieldName(Objects.requireNonNull(m.group(1))),
              Collections.singletonMap("$null", true),
              params,
              namedParameters));
    }
    return filters.isEmpty()
        ? Filter.ALL
        : filters.size() == 1 ? filters.get(0) : Filter.and(filters.toArray(new Filter[0]));
  }

  @Nullable
  private Object resolveParam(@Nullable final String pname, @Nullable final Object[] params) {
    if (pname == null) {
      return null;
    }
    try {
      if (pname.startsWith("p")) {
        int idx = Integer.parseInt(pname.substring(1)) - 1;
        if (params != null && idx >= 0 && idx < params.length) {
          return params[idx];
        }
      }
    } catch (NumberFormatException ignored) {
        // ignore invalid parameter index format
    }
    return null;
  }

  @Nullable
  private Object resolveSqlParam(
      @Nullable final String pname, @Nullable final Object[] params, @NonNull final Map<String, Object> namedParameters) {
    if (namedParameters.containsKey(pname)) {
      return namedParameters.get(pname);
    }
    return resolveParam(pname, params);
  }

  @Nullable
  private Object resolveParameterValue(@Nullable Object value, @Nullable Object[] jsonParams, @NonNull Map<String, Object> namedParameters) {
    if (value instanceof String s) {
      Object resolved = null;
      if (s.startsWith("$mn_qp:")) {
        try {
          int idx = Integer.parseInt(s.substring(7));
          if (jsonParams != null && idx >= 0 && idx < jsonParams.length) {
            resolved = jsonParams[idx];
          }
        } catch (Exception ignored) {
            // ignore invalid JSON parameter index format
        }
      } else if (s.startsWith(":")) {
        String pname = s.substring(1);
        if (namedParameters.containsKey(pname)) {
          resolved = namedParameters.get(pname);
        }
      }
      if (resolved != null) {
        return entityMapper.toFilterValue(resolved);
      }
    }
    if (value instanceof Map vm && vm.get("$mn_qp") instanceof Integer idx && jsonParams != null && idx >= 0 && idx < jsonParams.length) {
      return entityMapper.toFilterValue(jsonParams[idx]);
    }
    return value;
  }

  @Override
  @Nullable
  public <T, R> R findOne(@NonNull final PreparedQuery<T, R> q) {
    NitritePreparedQuery<T, R> nq = getNitritePreparedQuery(q);
    NitriteCollection coll = getCollection(nq.getRootEntity());
    String query = nq.getQuery();
    boolean isUpdate = nq.getUpdateMap() != null
        || (query != null && query.trim().regionMatches(true, 0, "UPDATE", 0, 6));
    if (isUpdate) {
      Optional<Number> result = executeUpdate((PreparedQuery<?, Number>) nq);
      return Number.class.isAssignableFrom(nq.getResultType()) ? (R) result.orElse(0L) : null;
    }
    if (Number.class.isAssignableFrom(nq.getResultType())) {
      return (R) Long.valueOf(coll.find(nq.getNitriteFilter()).size());
    }
    Document doc = coll.find(nq.getNitriteFilter()).firstOrNull();
    return doc == null ? null : (R) entityMapper.fromDocument(doc, nq.getRootEntity());
  }

  /**
   * Finds an optional result for a prepared query.
   *
   * @param q the prepared query
   * @return the optional result
   * @param <T> the entity type
   * @param <R> the result type
   */
  public <T, R> Optional<R> findOptional(@NonNull final PreparedQuery<T, R> q) {
    return Optional.ofNullable(findOne(q));
  }

  @Override
  public <T> boolean exists(@NonNull final PreparedQuery<T, Boolean> q) {
    NitritePreparedQuery nq = getNitritePreparedQuery(q);
    return getCollection(nq.getRootEntity()).find(nq.getNitriteFilter()).firstOrNull() != null;
  }

  @Override
  @NonNull
  public <T, R> Iterable<R> findAll(@NonNull final PreparedQuery<T, R> q) {
    NitritePreparedQuery<T, R> nq = getNitritePreparedQuery(q);
    NitriteCollection coll = getCollection(nq.getRootEntity());
    if (Number.class.isAssignableFrom(nq.getResultType())) {
      return Collections.singletonList((R) Long.valueOf(coll.find(nq.getNitriteFilter()).size()));
    }
    Sort s = nq.getSort();
    if (s == null || !s.isSorted()) {
      s = parseSortFromJsonQuery(nq.getQuery());
      if (s == null) {
        s = parseSortFromSqlQuery(nq.getQuery());
      }
      if (s == null) {
        s = parseSortFromHints(nq.getQueryHints());
      }
    }
    var cursor = coll.find(nq.getNitriteFilter(), buildFindOptions(nq.getPageable(), s));
    List<R> results = new ArrayList<>();
    for (Document doc : cursor) {
      results.add((R) entityMapper.fromDocument(doc, nq.getRootEntity()));
    }
    return results;
  }

  @Override
  @NonNull
  public <T, R> Stream<R> findStream(@NonNull final PreparedQuery<T, R> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  @Override
  @NonNull
  public <T> Stream<T> findStream(@NonNull final PagedQuery<T> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  @Override
  @NonNull
  public <R> Page<R> findPage(@NonNull final PagedQuery<R> q) {
    Iterable<R> results = findAll(q);
    List<R> list = new ArrayList<>();
    results.forEach(list::add);
    return Page.of(list, q.getPageable(), count(q));
  }

  /**
   * Finds a slice for a prepared query.
   *
   * @param q the prepared query
   * @return the slice result
   * @param <T> the entity type
   * @param <R> the result type
   */
  @NonNull
  public <T, R> R findSlice(@NonNull final PreparedQuery<T, R> q) {
    return (R) findAll(q);
  }

  @Nullable
  private Object[] reorderParamsForSql(@NonNull final PreparedQuery<?, ?> q) {
    Object[] raw = q.getParameterArray();
    List<QueryParameterBinding> bindings = q.getQueryBindings();
    if (bindings == null || bindings.isEmpty() || raw == null) {
      return raw;
    }
    Object[] reordered = new Object[bindings.size()];
    for (QueryParameterBinding b : bindings) {
      if (b.getName() != null && b.getName().startsWith("p")) {
        try {
          int pos = Integer.parseInt(b.getName().substring(1)) - 1;
          if (pos >= 0 && pos < reordered.length && b.getParameterIndex() >= 0 && b.getParameterIndex() < raw.length) {
            reordered[pos] = raw[b.getParameterIndex()];
          }
        } catch (NumberFormatException ignored) {
            // ignore invalid SQL parameter index format
        }
      }
    }
    return reordered;
  }

  @Override
  @NonNull
  public Optional<Number> executeUpdate(@NonNull final PreparedQuery<?, Number> q) {
    NitritePreparedQuery<?, Number> nq = getNitritePreparedQuery(q);
    Map<String, Object> setFields = null;
    Filter filter = null;
    Object[] jsonParams = buildJsonParameterValues(nq);
    Map<String, Object> namedParameters = buildNamedParameterValues(nq);
    Map<String, Object> filterMap = nq.getFilterMap();
    RuntimePersistentEntity<?> entity = getEntity(nq.getRootEntity());
    if (filterMap != null) {
      Map<String, Object> rawSetFields = (Map<String, Object>) filterMap.get("$set");
      if (rawSetFields != null) {
        setFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawSetFields.entrySet()) {
          setFields.put(entry.getKey(), resolveParameterValue(entry.getValue(), jsonParams, namedParameters));
        }
      }
      filter = filterBuilder.buildFilterFromJson(entity, filterMap, jsonParams, namedParameters);
    } else if (nq.getQuery().trim().toUpperCase(Locale.ROOT).startsWith("UPDATE")) {
      Object[] sqlParams = reorderParamsForSql(nq);
      setFields =
          updateExecutor.parseSetClause(
              nq.getQuery(),
              sqlParams,
              (pname, ps) -> entityMapper.toFilterValue(resolveSqlParam(pname, ps, namedParameters)));
      filter = parseFilterFromUpdateStatement(nq.getQuery(), sqlParams, namedParameters, entity);
    } else {
      throw new UnsupportedOperationException("executeUpdate() called with non-UPDATE statement: " + nq.getQuery());
    }
    if (setFields == null || setFields.isEmpty()) {
      return Optional.of(0);
    }
    Document updateDoc = Document.createDocument();
    for (Map.Entry<String, Object> entry : setFields.entrySet()) {
      updateDoc.put(entry.getKey(), entry.getValue());
    }
    return Optional.of(getCollection(nq.getRootEntity()).update(filter != null ? filter : nq.getNitriteFilter(), updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount());
  }

  @NonNull
  private Map<String, Object> buildNamedParameterValues(@NonNull final PreparedQuery<?, ?> q) {
    Object[] params = q.getParameterArray();
    if (params == null || params.length == 0) {
      return new LinkedHashMap<>();
    }
    Map<String, Object> result = new LinkedHashMap<>();
    List<QueryParameterBinding> bindings = q.getQueryBindings();
    if (bindings != null) {
      for (QueryParameterBinding b : bindings) {
        if (b.getName() != null && b.getParameterIndex() >= 0 && b.getParameterIndex() < params.length) {
          result.put(b.getName(), entityMapper.toFilterValue(params[b.getParameterIndex()]));
        }
      }
    }
    Argument[] args = q.getArguments();
    if (args != null) {
      int len = Math.min(args.length, params.length);
      for (int i = 0; i < len; i++) {
        if (args[i].getName() != null && !args[i].getName().isEmpty()) {
          result.putIfAbsent(args[i].getName(), entityMapper.toFilterValue(params[i]));
        }
      }
    }
    return result;
  }

  @NonNull
  private Filter parseFilterFromUpdateStatement(
      @NonNull final String sql, @Nullable final Object[] params, @NonNull final Map<String, Object> namedParameters, @NonNull final RuntimePersistentEntity<?> entity) {
    int whereIdx = sql.toUpperCase(Locale.ROOT).indexOf(" WHERE ");
    if (whereIdx < 0) {
      return Filter.ALL;
    }
    String w = sql.substring(whereIdx + 7).trim();
    String clause = w.startsWith("(") && w.endsWith(")") ? w.substring(1, w.length() - 1) : w;
    return parseWhereClause(clause, params, namedParameters, entity);
  }

  @Override
  @NonNull
  public Optional<Number> executeDelete(@NonNull final PreparedQuery<?, Number> q) {
    NitritePreparedQuery nq = getNitritePreparedQuery(q);
    return Optional.of(getCollection(nq.getRootEntity()).remove(nq.getNitriteFilter(), false).getAffectedCount());
  }

  @Override
  @NonNull
  public <R> List<R> execute(@NonNull final PreparedQuery<?, R> q) {
    List<R> list = new ArrayList<>();
    findAll(q).forEach(list::add);
    return list;
  }
}
