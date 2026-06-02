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
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.annotation.Version;
import io.micronaut.data.exceptions.OptimisticLockException;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.QueryParameterBinding;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMeta;
import io.micronaut.data.nitrite.runtime.mapping.WritablePropertyMeta;
import io.micronaut.data.nitrite.runtime.query.DefaultNitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.DefaultNitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteStoredQuery;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.UpdateOptions;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;
import org.dizitart.no2.filters.FluentFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.temporal.Temporal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;


/**
 * Default Nitrite repository operations. Implements core CRUD using Nitrite's Document codec.
 *
 * <p>This runtime executes the encoded query strings produced by the Nitrite query builder and by
 * the document processor.
 *
 * <h2>Supported query shapes</h2>
 *
 * <ul>
 *   <li><b>JSON filter</b> (Nitrite criteria encoding), for example:
 *       {@code {"title":{"$eq":"$mn_qp:0"}}}</li>
 *   <li><b>SQL-like SELECT/DELETE</b> generated by {@code micronaut-data-document-processor}</li>
 * </ul>
 *
 * <h2>Parameter binding</h2>
 *
 * <ul>
 *   <li>Criteria-generated JSON uses {@code "$mn_qp:<index>"} placeholders resolved from {@link
 *       io.micronaut.data.model.runtime.PreparedQuery#getParameterArray()}.</li>
 *   <li>User-authored JSON {@code @Query} methods may use named placeholders like {@code :title}.
 *       These are bound using query bindings when available, otherwise by falling back to {@link
 *       io.micronaut.data.model.runtime.PreparedQuery#getArguments()} names.</li>
 * </ul>
 *
 * <h2>Update semantics</h2>
 *
 * <p>Nitrite updates are partial updates represented as a document of fields to change. Although
 * the query builder and some JSON {@code @Query} methods use a Mongo-style {@code "$set"} wrapper,
 * this runtime unwraps {@code "$set"} before calling {@code collection.update(...)}. Passing a
 * literal {@code "$set"} key to Nitrite would create a {@code "$set"} field on the stored
 * document and would not update the intended properties.
 *
 * <h2>Numeric equality</h2>
 *
 * <p>The mapping layer may store numbers as different Java numeric types (for example {@code Long}
 * vs {@code BigDecimal}). Equality queries therefore tolerate numeric representation differences.
 *
 * @since 1.0.0
 */
@Singleton
@Internal
@SuppressWarnings({"unchecked", "rawtypes"})
public final class DefaultNitriteRepositoryOperations extends AbstractRepositoryOperations
    implements NitriteRepositoryOperations, PreparedQueryDecorator, MethodContextAwareStoredQueryDecorator, NitriteOperationsHelper,
               SyncCascadeOperations.SyncCascadeOperationsHelper<NitriteOperationContext>,
               io.micronaut.data.operations.CriteriaRepositoryOperations {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultNitriteRepositoryOperations.class);
  private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

  private final Nitrite database;
    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
  private final NitriteFilterBuilder filterBuilder;
  private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
  private final jakarta.persistence.criteria.CriteriaBuilder criteriaBuilder;
  private final NitriteCriteriaExecutor criteriaExecutor;
  private final NitriteQueryExecutor queryExecutor;
  private final ValueConverter valueConverter;
  private final NitriteCollectionRegistry collectionRegistry;

  /**
   * Create Nitrite repository operations.
   *
   * @param database The Nitrite database
   * @param configuration The Nitrite configuration
   * @param dateTimeProvider Date/time provider used by the base operations
   * @param runtimeEntityRegistry Entity metadata registry
   * @param conversionService Conversion service (for field-level conversions)
   * @param attributeConverterRegistry Attribute converter registry
   * @param transactionHolder Transaction context holder
   * @param serdeObjectMapper Micronaut Serde ObjectMapper (for entity ↔ Map conversion at boundary)
   */
  public DefaultNitriteRepositoryOperations(
      final Nitrite database,
      final NitriteConfiguration configuration,
      final DateTimeProvider<Object> dateTimeProvider,
      final RuntimeEntityRegistry runtimeEntityRegistry,
      final DataConversionService conversionService,
      final AttributeConverterRegistry attributeConverterRegistry,
      final NitriteTransactionHolder transactionHolder,
      final ObjectMapper serdeObjectMapper) {
    super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
    this.database = database;
    this.collectionRegistry = new NitriteCollectionRegistry(database, transactionHolder, configuration, this::getEntity);
    this.entityMapper =
        new NitriteEntityMapper(
            conversionService, serdeObjectMapper, database.getConfig().nitriteMapper(), runtimeEntityRegistry);
    this.entityMapper.setHelper(this);
    this.queryParser = new NitriteQueryParser();
    // Create filter builder with sub-query executor for auto-join on MANY_TO_ONE associations
    this.filterBuilder = createFilterBuilderWithSubQueryExecutor();
    this.cascadeOperations = new SyncCascadeOperations<>(conversionService, this);
    this.valueConverter = new ValueConverter(conversionService);
    QueryBuilder queryBuilder = new NitriteQueryBuilder();
    this.criteriaBuilder = new RuntimeCriteriaBuilder(runtimeEntityRegistry);
    this.criteriaExecutor = new NitriteCriteriaExecutor(
        queryBuilder,
        entityMapper,
        queryParser,
        filterBuilder,
        collectionRegistry::getCollection,
        this::getEntity);
    this.queryExecutor = new NitriteQueryExecutor(
        entityMapper,
        queryParser,
        filterBuilder,
        conversionService,
        collectionRegistry::getCollection,
        this::getEntity,
        this::buildFindOptions,
        this,
        runtimeEntityRegistry.getEntityEventListener());
    }

  /**
   * Create a NitriteFilterBuilder with sub-query executor for auto-join on MANY_TO_ONE associations.
   * The sub-query executor allows filtering by association properties (e.g., author.name) by
   * first querying the associated entity and then filtering by ID.
   */
  private NitriteFilterBuilder createFilterBuilderWithSubQueryExecutor() {
    NitriteFilterBuilder builder = new NitriteFilterBuilder(entityMapper);
    NitriteFilterBuilder.SubQueryExecutor subQueryExecutor = (associatedEntity, filterMap, targetField, params, namedParameters) -> {
        NitriteCollection assocCollection = getCollection(associatedEntity.getIntrospection().getBeanType());
        Filter subFilter = filterMap != null && !filterMap.isEmpty()
            ? builder.buildFilterFromJson(associatedEntity, filterMap, params, namedParameters)
            : Filter.ALL;
        return assocCollection.find(subFilter).toList().stream()
            .flatMap(doc -> {
                String fieldName = targetField;
                if (fieldName == null) {
                    RuntimePersistentProperty<?> identity = associatedEntity.getIdentity();
                    fieldName = identity.getPersistedName();
                }
                Object val = doc.get(fieldName);
                Object filterVal = entityMapper.toFilterValue(val);
                if (filterVal instanceof Collection<?> collection) {
                    return collection.stream().map(entityMapper::toFilterValue);
                }
                return Stream.of(filterVal);
            })
            .distinct()
            .toList();
    };
    return new NitriteFilterBuilder(entityMapper, subQueryExecutor);
  }

  @Override
  public Nitrite getDatabase() {
    return database;
  }

  @Override
  public void logFind(String collection, Filter filter) {
    LOG.debug("Executing Nitrite 'find' on collection [{}] with filter: {}",
        collection, filter != null ? filter : "Filter.ALL");
  }

  @Override
  public void logInsert(String collection, Object entityOrDocs) {
    LOG.debug("Executing Nitrite 'insert' into collection [{}] with document: {}",
        collection, entityOrDocs);
  }

  @Override
  public void logUpdate(String collection, Filter filter, Document update) {
    LOG.debug("Executing Nitrite 'update' on collection [{}] with filter: {} and update: {}",
        collection, filter != null ? filter : "Filter.ALL", update);
  }

  // ========== CriteriaRepositoryOperations implementation ==========

  @Override
  public jakarta.persistence.criteria.CriteriaBuilder getCriteriaBuilder() {
    return criteriaBuilder;
  }

  @Override
  public boolean exists(@NonNull jakarta.persistence.criteria.CriteriaQuery<?> query) {
    return criteriaExecutor.exists(query);
  }

  @Override
  public <R> R findOne(@NonNull jakarta.persistence.criteria.CriteriaQuery<R> query) {
    return criteriaExecutor.findOne(query);
  }

  @Override
  @NonNull
  public <T> List<T> findAll(@NonNull jakarta.persistence.criteria.CriteriaQuery<T> query) {
    return criteriaExecutor.findAll(query);
  }

  @Override
  @NonNull
  public <T> List<T> findAll(@NonNull jakarta.persistence.criteria.CriteriaQuery<T> query, int offset, int limit) {
    return criteriaExecutor.findAll(query, offset, limit);
  }

  @Override
  @NonNull
  public Optional<Number> updateAll(@NonNull jakarta.persistence.criteria.CriteriaUpdate<Number> query) {
    return criteriaExecutor.updateAll(query);
  }

  @Override
  @NonNull
  public Optional<Number> deleteAll(@NonNull jakarta.persistence.criteria.CriteriaDelete<Number> query) {
    return criteriaExecutor.deleteAll(query);
  }

  // ========== SyncCascadeOperationsHelper implementation ==========

  @Override
  @SuppressWarnings("unchecked")
  public <T> T persistOne(NitriteOperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
    LOG.debug("persistOne: entity={}, type={}", entityValue, persistentEntity.getName());

    // Set back-references using pre-computed mappedBy metadata — no annotation lookups on hot path.
    NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta((Class<T>) entityValue.getClass());
    // Early exit if no back-references to set
    if (meta.hasBackReferences()) {
      for (WritablePropertyMeta<T> assocMeta : meta.mappedByAssocs()) {
        Object value = assocMeta.prop().getProperty().get(entityValue);
        if (value instanceof Iterable<?> iterable) {
          // Move null check outside inner loop - loop-invariant hoist
          if (assocMeta.backRefProperty() != null) {
            for (Object child : iterable) {
              if (child != null && assocMeta.backRefProperty().get(child) == null) {
                assocMeta.backRefProperty().set(child, entityValue);
              }
            }
          }
        } else if (value != null && assocMeta.backRefProperty() != null) {
          if (assocMeta.backRefProperty().get(value) == null) {
            assocMeta.backRefProperty().set(value, entityValue);
          }
        }
      }
    }

    NitriteEntityOperations<T> op = new NitriteEntityOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        persistentEntity, conversionService, entityMapper, this, entityValue, NitriteEntityOperations.OperationType.INSERT);
    op.persist();
    return op.getEntity();
  }

  @Override
  public <T> List<T> persistBatch(NitriteOperationContext ctx, Iterable<T> entityValues,
                                   RuntimePersistentEntity<T> persistentEntity, Predicate<T> predicate) {
    List<T> results = new ArrayList<>();
    for (T entity : entityValues) {
      if (predicate != null && predicate.test(entity)) {
        continue;
      }
      results.add(persistOne(ctx, entity, persistentEntity));
    }
    return results;
  }

  @Override
  public <T> T updateOne(NitriteOperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
    NitriteEntityOperations<T> op = new NitriteEntityOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        persistentEntity, conversionService, entityMapper, this, entityValue, NitriteEntityOperations.OperationType.UPDATE);
    op.update();
    return op.getEntity();
  }

  @Override
  public void persistManyAssociation(NitriteOperationContext ctx,
                                      RuntimeAssociation runtimeAssociation,
                                      Object parentEntityValue,
                                      RuntimePersistentEntity<Object> parentPersistentEntity,
                                      Object childEntityValue,
                                      RuntimePersistentEntity<Object> childPersistentEntity) {
    // Not needed for Nitrite - relationships are not stored as separate collections
  }

  @Override
  public void persistManyAssociationBatch(NitriteOperationContext ctx,
                                           RuntimeAssociation runtimeAssociation,
                                           Object parentEntityValue,
                                           RuntimePersistentEntity<Object> parentPersistentEntity,
                                           Iterable<Object> childEntityValues,
                                           RuntimePersistentEntity<Object> childPersistentEntity) {
    // Not needed for Nitrite - relationships are not stored as separate collections
  }

  private void logDelete(String collection, Filter filter) {
    LOG.debug("Executing Nitrite 'remove' from collection [{}] with filter: {}",
        collection, filter != null ? filter : "Filter.ALL");
  }

  @Override
  public <T> T updateEntityId(BeanProperty<T, Object> property, T entity, Object id) {
      if (id != null) {
          if (property.getType().isInstance(id)) {
              property.set(entity, id);
          } else {
              conversionService.convert(id, property.getType()).ifPresent(converted -> property.set(entity, converted));
          }
      }
      return entity;
  }


  /** Get collection name from entity class. */
  private String getCollectionName(final Class<?> type) {
    return collectionRegistry.getCollectionName(type);
  }

  /** Get Nitrite collection for entity type. */
  @Override
  public NitriteCollection getCollection(final Class<?> type) {
    return collectionRegistry.getCollection(type);
  }

  /** Generate and set ID on entity if @GeneratedValue is present and ID is null. */
  @Override
  public <T> void generateIdIfNecessary(final T entity, final Class<T> type) {
    // Use cached metadata with pre-computed idAccessor
    NitriteEntityMeta<T> meta = entityMapper.getOrBuildMeta(type);
    var idProperty = meta.idProp();
    var idAccessor = meta.idAccessor();
    if (idProperty != null && idProperty.isAnnotationPresent(GeneratedValue.class)) {
      if (idAccessor != null && idAccessor.get(entity) != null) {
          return;
      }
      Class<?> idType = idProperty.getType();
      Object generatedId = (idType == String.class) ? UUID.randomUUID().toString() :
                           (idType == UUID.class) ? UUID.randomUUID() :
                           (idType == Long.class || idType == long.class) ? ID_GENERATOR.incrementAndGet() :
                           (idType == Integer.class || idType == int.class) ? (int) (ID_GENERATOR.incrementAndGet() % Integer.MAX_VALUE) :
                           UUID.randomUUID().toString();
      if (idAccessor != null) {
        idAccessor.set(entity, generatedId);
      }
    }
  }

  @Override
  public <T> T findOne(final Class<T> type, final Object id) {
    Filter filter = entityMapper.idEqualsFilter(type, id);
    Document doc = getCollection(type).find(filter).firstOrNull();
    if (doc == null) {
      return null;
    }
      return entityMapper.fromDocument(doc, type);
  }

  @Override
  public <T> T persist(@NonNull final InsertOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    return persistOne(ctx, operation.getEntity(), getEntity(operation.getRootEntity()));
  }

  @Override
  public <T> Iterable<T> persistAll(@NonNull final InsertBatchOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
        operation, true);
    op.persist();
    return op.getEntities();
  }

    @Override
  public <T> T update(@NonNull final UpdateOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    return updateOne(ctx, operation.getEntity(), getEntity(operation.getRootEntity()));
  }

  @Override
  public <T> Iterable<T> updateAll(@NonNull final UpdateBatchOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
        operation, false);
    op.persist(); // base class persistAll() will call execute() which handles insert vs update
    return op.getEntities();
  }

  @Override
  public <T> int delete(@NonNull final DeleteOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    NitriteEntityOperations<T> op = new NitriteEntityOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
        operation.getEntity(), NitriteEntityOperations.OperationType.DELETE);
    op.delete();
    return 1;
  }

  @Override
  public <T> Optional<Number> deleteAll(@NonNull final DeleteBatchOperation<T> operation) {
    NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType());
    if (operation.all()) {
      NitriteCollection collection = getCollection(operation.getRootEntity());
      logDelete(collection.getName(), Filter.ALL);
      collection.clear();
      return Optional.of(-1);
    }
    NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
        ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
        getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
        operation, false);
    op.delete();
    return Optional.of((long) op.getEntities().size());
  }

    /** Build FindOptions with pagination and sorting, merging additional sort from QueryModel. */
  private FindOptions buildFindOptions(final Pageable pageable, final Sort additionalSort) {
    return buildFindOptions(pageable, additionalSort, null);
  }

  /** Build FindOptions with pagination, limit, and sorting. */
  private FindOptions buildFindOptions(final Pageable pageable, final Sort additionalSort, final Limit limit) {
    FindOptions options = new FindOptions();
    if (pageable.getOffset() > 0) {
      options.skip(pageable.getOffset());
    }
    // Apply limit from pageable first, then from explicit Limit (for methods like listTop10)
    int effectiveLimit = -1;
    if (pageable.getSize() > 0) {
      effectiveLimit = pageable.getSize();
    } else if (limit != null && limit.maxResults() > 0) {
      effectiveLimit = limit.maxResults();
    }
    if (effectiveLimit > 0) {
      options.limit(effectiveLimit);
    }
    Map<String, Sort.Order> mergedOrders = new LinkedHashMap<>();
    if (additionalSort != null && additionalSort.isSorted()) {
      for (var order : additionalSort.getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }
      pageable.getSort();
      if (pageable.getSort().isSorted()) {
      for (var order : pageable.getSort().getOrderBy()) {
        mergedOrders.put(order.getProperty(), order);
      }
    }
    if (!mergedOrders.isEmpty()) {
      for (var order : mergedOrders.values()) {
        SortOrder sortOrder = order.getDirection() == Sort.Order.Direction.ASC ? SortOrder.Ascending : SortOrder.Descending;
        String property = order.getProperty();
        // Sort properties coming from the document processor may include an entity alias prefix
        // (for example "person.age"). Nitrite stores plain field names, so strip any prefix.
        if (property.contains(".")) {
          property = property.substring(property.lastIndexOf('.') + 1);
        }
        options.thenOrderBy(entityMapper.normalizeFieldName(property, null), sortOrder);
      }
    }
    return options;
  }

  /** Parses sort from a JSON query string's $sort key. */
  @Override
  public Sort parseSortFromJsonQuery(final String queryString) {
    if (queryString == null || !queryString.contains("$sort")) {
      return null;
    }
    try {
      Object parsed = queryParser.parseJson(queryString);
      Map<?, ?> sortObj = null;
      if (parsed instanceof Map<?, ?> m) {
        sortObj = m.get("$sort") instanceof Map<?, ?> s ? s : null;
      } else if (parsed instanceof List<?> pipeline) {
        for (Object stage : pipeline) {
          if (stage instanceof Map<?, ?> sm && sm.get("$sort") instanceof Map<?, ?> s) {
            sortObj = s;
            break;
          }
        }
      }
      if (sortObj != null) {
        List<Sort.Order> orders = new ArrayList<>();
        for (Map.Entry<?, ?> e : sortObj.entrySet()) {
          int dir = e.getValue() instanceof Number n ? n.intValue() : 1;
          orders.add(dir >= 1 ? Sort.Order.asc(e.getKey().toString()) : Sort.Order.desc(e.getKey().toString()));
        }
        return orders.isEmpty() ? null : Sort.of(orders);
      }
    } catch (Exception ignored) {
    }
    return null;
  }

  /** Parse sort string from query hints. */
  @Override
  public Sort parseSortFromHints(final Map<String, Object> hints) {
    if (hints == null || hints.isEmpty() || !(hints.get("sort") instanceof String sortStr) || sortStr.isEmpty()) {
      return null;
    }
    List<Sort.Order> orders = new ArrayList<>();
    for (String part : sortStr.split(",")) {
      String[] parts = part.trim().split(":");
      if (parts.length == 2) {
        orders.add(Sort.Order.Direction.valueOf(parts[1]) == Sort.Order.Direction.ASC ? Sort.Order.asc(parts[0]) : Sort.Order.desc(parts[0]));
      }
    }
    return orders.isEmpty() ? null : Sort.of(orders);
  }

  @Override
  public <T> Iterable<T> findAll(@NonNull final PagedQuery<T> query) {
    Class<T> type = query.getRootEntity();
    Filter filter = Filter.ALL;
    Sort sort = null;
    Limit limit = query.getQueryLimit();

    // If PagedQuery is actually a PreparedQuery, extract the filter and sort
    if (query instanceof PreparedQuery pq) {
      NitritePreparedQuery nq = getNitritePreparedQuery(pq);
      filter = nq.getNitriteFilter();
      sort = nq.getSort();
      if (sort == null || !sort.isSorted()) {
        sort = parseSortFromHints(nq.getQueryHints());
      }
    }

    var cursor = getCollection(type).find(filter, buildFindOptions(query.getPageable(), sort, limit));
    List<T> results = new ArrayList<>();
    for (Document doc : cursor) {
      results.add(entityMapper.fromDocument(doc, type));
    }
    return results;
  }

  @Override
  public <T> long count(@NonNull final PagedQuery<T> query) {
    // If PagedQuery is actually a PreparedQuery, use the filter for counting
    if (query instanceof PreparedQuery pq) {
      NitritePreparedQuery nq = getNitritePreparedQuery(pq);
      Filter filter = nq.getNitriteFilter();
      return getCollection(query.getRootEntity()).find(filter).size();
    }
    return getCollection(query.getRootEntity()).size();
  }

  /** Resolve JSON parameter values from PreparedQuery bindings. */
  private Object[] buildJsonParameterValues(@NonNull final PreparedQuery<?, ?> q) {
    Object[] methodParams = q.getParameterArray();
    List<QueryParameterBinding> bindings = q.getQueryBindings();
    if (bindings == null || bindings.isEmpty()) {
      return methodParams;
    }
    Object[] values = new Object[bindings.size()];
    for (int i = 0; i < bindings.size(); i++) {
      values[i] = resolveJsonBindingValue(bindings.get(i), methodParams);
    }
    return values;
  }

  /**
   * Resolves a single JSON binding value from a query parameter binding.
   * <p>
   * This method handles various parameter types including:
   * </p>
   * <ul>
   *   <li>Direct values (non-BindingParameter objects)</li>
   *   <li>Collection parameters (e.g., for IN clauses)</li>
   *   <li>Array parameters (e.g., String[] for IN clauses)</li>
   *   <li>Nested property paths within parameter objects</li>
   * </ul>
   * <p>
   * For collection and array parameters, the value is returned as-is to preserve the collection
   * structure for operations like IN queries.
   * </p>
   *
   * @param binding the query parameter binding containing the parameter metadata
   * @param methodParams the method parameters array
   * @return the resolved parameter value, properly converted for Nitrite filters
   */
  private Object resolveJsonBindingValue(@NonNull final QueryParameterBinding binding, final Object[] methodParams) {
    Object bindingValue = binding.getValue();
    // QueryParameterBinding#getValue may contain a criteria BindingParameter (for example
    // ParameterExpressionImpl). That is not a runtime value and must not be used as a query/update
    // parameter, otherwise it gets persisted as a string like "ParameterExpressionImpl{...}".
    if (bindingValue != null && !(bindingValue instanceof BindingParameter)) {
      return toFilterValue(bindingValue);
    }
    int idx = binding.getParameterIndex();
    Object base = (methodParams != null && idx >= 0 && idx < methodParams.length) ? methodParams[idx] : null;
    String[] parameterBindingPath = binding.getParameterBindingPath();
    String[] path = parameterBindingPath != null ? parameterBindingPath : binding.getPropertyPath();
    if (path == null
        || path.length == 0
        || isDirectBindableScalar(base)
        || base instanceof Collection
        || (base != null && base.getClass().isArray())) {
      return toFilterValue(base);
    }
    Object current = base;
    for (String segment : path) {
      if (current == null) {
        break;
      }
      current = readSegmentValue(current, segment);
    }
    return toFilterValue(current);
  }

  private boolean isDirectBindableScalar(Object value) {
    if (value == null) {
      return false;
    }
    return value instanceof CharSequence
        || value instanceof Number
        || value instanceof Boolean
        || value instanceof Character
        || value instanceof Enum<?>
        || value instanceof UUID
        || value instanceof Temporal
        || value instanceof Date;
  }

  private Object[] ensureJsonParamsForFilter(@NonNull final Map<String, Object> filterMap, @NonNull final Object[] methodParams, final Object[] jsonParams) {
    return NitriteQueryBinder.ensureJsonParamsForFilter(filterMap, jsonParams, out -> fillMissingParamsFromFilter(filterMap, methodParams, out));
  }

  /**
   * Fill missing params from method arguments based on field names.
   *
   * @param filterMap the filter map
   * @param methodParams the method parameters
   * @param out the output array of parameters
   */
  private void fillMissingParamsFromFilter(final Map<String, Object> filterMap, final Object[] methodParams, final Object[] out) {
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
          Integer idx = NitriteQueryBinder.extractPlaceholderIndex(opVal);
          if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
            out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
          }
        }
      } else {
        Integer idx = NitriteQueryBinder.extractPlaceholderIndex(value);
        if (idx != null && idx >= 0 && idx < out.length && out[idx] == null) {
          out[idx] = extractPropertyFromSingleArg(methodParams, entry.getKey());
        }
      }
    }
  }

  /**
   * Extract a property from a single method argument.
   *
   * @param methodParams the method parameters
   * @param property the property name
   * @return the extracted property value
   */
  private Object extractPropertyFromSingleArg(final Object[] methodParams, final String property) {
    if (methodParams == null || methodParams.length != 1 || methodParams[0] == null) {
      return null;
    }
    Object arg = methodParams[0];
    if ("id".equals(property) || "_id".equals(property)) {
      return entityMapper.toNitriteFilterValue(arg);
    }
    String[] parts = property.split("\\.");
    for (int start = 0; start < parts.length; start++) {
      Object current = arg;
      for (int i = start; i < parts.length; i++) {
        current = readSegmentValue(current, parts[i]);
        if (current == null) {
          break;
        }
      }
      if (current != null) {
        return entityMapper.toNitriteFilterValue(current);
      }
    }
    return entityMapper.toNitriteFilterValue(arg);
  }

  private Object readSegmentValue(Object current, String segment) {
    if (current == null) {
      return null;
    }
    String alt = NameUtils.snakeToCamel(segment);
    if (current instanceof Map<?, ?> m) {
      Object value = m.get(segment);
      return value != null || segment.equals(alt) ? value : m.get(alt);
    }
    if (current instanceof Document d) {
      Object value = d.get(segment);
      return value != null || segment.equals(alt) ? value : d.get(alt);
    }
    try {
      Document doc = (Document) database.getConfig().nitriteMapper().tryConvert(current, Document.class);
      Object value = doc.get(segment);
      return value != null || segment.equals(alt) ? value : doc.get(alt);
    } catch (Exception ignored) {
      return null;
    }
  }

  @Override
  public <E, R> PreparedQuery<E, R> decorate(PreparedQuery<E, R> preparedQuery) {
    return createNitritePreparedQuery(preparedQuery);
  }

  @Override
  public <E, R> StoredQuery<E, R> decorate(MethodInvocationContext<?, ?> context, StoredQuery<E, R> storedQuery) {
    return createNitriteStoredQuery(storedQuery);
  }

  /**
   * Create a specialized NitriteStoredQuery.
   *
   * @param <E> the entity type
   * @param <R> the result type
   * @param storedQuery the original stored query
   * @return the Nitrite stored query
   */
  @NonNull
  public <E, R> NitriteStoredQuery<E, R> createNitriteStoredQuery(@NonNull StoredQuery<E, R> storedQuery) {
    if (storedQuery instanceof NitriteStoredQuery nsq) {
      return nsq;
    }
    Map<String, Object> filterMap = null;
    io.micronaut.data.nitrite.runtime.query.compiled.CompiledNitriteFilter compiledFilter = null;
    Map<String, Object> updateMap = null;
    String query = storedQuery.getQuery();
    String trimmedQuery = query.trim();
    if (trimmedQuery.startsWith("{") || trimmedQuery.startsWith("[")) {
      try {
        Object parsed = queryParser.parseJson(query);
        filterMap = new LinkedHashMap<>(queryParser.extractFilterMap(parsed));
        if (parsed instanceof Map<?, ?> m) {
          filterMap.remove("$project");
          updateMap = (Map<String, Object>) m.get("$set");
          if (updateMap == null) {
            updateMap = parseUpdateMapFromAnnotation(storedQuery);
          }
        }
        compiledFilter = filterBuilder.compile(getEntity(storedQuery.getRootEntity()), filterMap);
      } catch (Exception ignored) {
      }
    }
    return new DefaultNitriteStoredQuery<>(storedQuery, getEntity(storedQuery.getRootEntity()), conversionService, filterMap, compiledFilter, updateMap);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parseUpdateMapFromAnnotation(final StoredQuery<?, ?> storedQuery) {
    try {
      String updateStr = storedQuery.getAnnotationMetadata().stringValue(Query.class, "update").orElse(null);
      if (updateStr == null || updateStr.isBlank()) {
        return null;
      }
      Object parsed = queryParser.parseJson(updateStr);
      if (parsed instanceof Map<?, ?> m && m.get("$set") instanceof Map<?, ?> setMap) {
        return new LinkedHashMap<>((Map<String, Object>) setMap);
      }
    } catch (Exception ignored) {
    }
    return null;
  }


  /**
   * Create a specialized NitritePreparedQuery.
   *
   * @param <E> the entity type
   * @param <R> the result type
   * @param preparedQuery the original prepared query
   * @return the Nitrite prepared query
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
    return new DefaultNitritePreparedQuery<>(preparedQuery, buildFilterFromPreparedQuery(preparedQuery, storedQuery), storedQuery.getFilterMap(), storedQuery.getCompiledFilter(), storedQuery.getUpdateMap());
  }

  private <E, R> NitritePreparedQuery<E, R> getNitritePreparedQuery(PreparedQuery<E, R> q) {
    if (q instanceof NitritePreparedQuery nq) {
      return nq;
    }
    return createNitritePreparedQuery(q);
  }

  private Filter buildFilterFromPreparedQuery(final PreparedQuery<?, ?> q, NitriteStoredQuery<?, ?> stored) {
    Map<String, Object> namedParameters = buildNamedParameterValues(q);
    if (stored.getCompiledFilter() != null) {
        return stored.getCompiledFilter().bind(ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
    }
    if (stored.getFilterMap() != null) {
      return filterBuilder.buildFilterFromJson(getEntity(stored.getRootEntity()), stored.getFilterMap(), ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
    }
    return Filter.ALL;
  }


    /**
   * Resolves parameter value from JSON placeholder or named parameter.
   * <p>
   * This method handles both positional placeholders (e.g., {@code $mn_qp:0}) and named parameters
   * (e.g., {@code :name}). For placeholder parameters, it correctly returns {@code null} when the
   * parameter value is null, allowing proper handling of null values in update operations.
   * </p>
   *
   * @param value the raw parameter value which may contain placeholders
   * @param jsonParams the JSON parameters array
   * @param namedParameters the named parameters map
   * @return the resolved parameter value, or {@code null} if the parameter is null
   */
  private Object resolveParameterValue(Object value, Object[] jsonParams, Map<String, Object> namedParameters) {
    return NitriteQueryBinder.resolveParameterValue(value, jsonParams, namedParameters, this::toFilterValue);
  }

  @Override
  public Object toFilterValue(Object value) {
    return entityMapper.toFilterValue(value);
  }

  /**
   * Find one entity by prepared query.
   *
   * @param <T> the entity type
   * @param <R> the result type
   * @param q the prepared query
   * @return the result
   */
  @Override
  public <T, R> R findOne(@NonNull final PreparedQuery<T, R> q) {
    R result = queryExecutor.findOne(q, getNitritePreparedQuery(q));
    // Projected scalar results (e.g. LocalDate, Instant) come back as raw numbers from Nitrite.
    // Convert them here before the framework interceptor calls ConversionService on them.
    if (!(result instanceof Number)) {
      return result;
    }
    @SuppressWarnings("unchecked")
    R converted = (R) convertValue(result, q.getResultType());
    return converted;
  }

    /**
   * Check if an entity exists by prepared query.
   *
   * @param <T> the entity type
   * @param q the prepared query
   * @return true if exists
   */
  @Override
  public <T> boolean exists(@NonNull final PreparedQuery<T, Boolean> q) {
    NitritePreparedQuery nq = getNitritePreparedQuery(q);
    logFind(getCollectionName(nq.getRootEntity()), nq.getNitriteFilter());
    return getCollection(nq.getRootEntity()).find(nq.getNitriteFilter()).firstOrNull() != null;
  }

  @Override
  public <T, R> Iterable<R> findAll(@NonNull final PreparedQuery<T, R> q) {
    return queryExecutor.findAll(q, getNitritePreparedQuery(q));
  }

  /**
   * Convert a value to the target type.
   *
   * @param value the value to convert
   * @param targetType the target type
   * @return the converted value
   */
  private Object convertValue(Object value, Class<?> targetType) {
    return valueConverter.convertWithTemporalHandling(value, targetType);
  }

    /**
   * Find a stream by prepared query.
   *
   * @param <T> the entity type
   * @param <R> the result type
   * @param q the prepared query
   * @return the stream
   */
  @Override
  public <T, R> Stream<R> findStream(@NonNull final PreparedQuery<T, R> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  /**
   * Find a stream by paged query.
   *
   * @param <T> the entity type
   * @param q the paged query
   * @return the stream
   */
  @Override
  public <T> Stream<T> findStream(@NonNull final PagedQuery<T> q) {
    return StreamSupport.stream(findAll(q).spliterator(), false);
  }

  /**
   * Find a page by paged query.
   *
   * @param <R> the result type
   * @param q the paged query
   * @return the page
   */
  @Override
  public <R> Page<R> findPage(@NonNull final PagedQuery<R> q) {
    Iterable<R> results = findAll(q);
    List<R> list = new ArrayList<>();
    results.forEach(list::add);
    return Page.of(list, q.getPageable(), count(q));
  }

  @Override
  public Optional<Number> executeUpdate(@NonNull final PreparedQuery<?, Number> q) {
    NitritePreparedQuery<?, Number> nq = getNitritePreparedQuery(q);
    Map<String, Object> setFields = null;
    Filter filter;
    Object[] jsonParams = buildJsonParameterValues(nq);
    Map<String, Object> namedParameters = buildNamedParameterValues(nq);
    if (nq.getFilterMap() != null) {
      Map<String, Object> rawSetFields = (Map<String, Object>) nq.getFilterMap().get("$set");
      if (rawSetFields == null) {
        rawSetFields = nq.getUpdateMap(); // fallback: $set stored separately in annotation
      }
      if (rawSetFields != null) {
        setFields = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : rawSetFields.entrySet()) {
          setFields.put(entry.getKey(), resolveParameterValue(entry.getValue(), jsonParams, namedParameters));
        }
      }
      filter = filterBuilder.buildFilterFromJson(getEntity(nq.getRootEntity()), nq.getFilterMap(), jsonParams, namedParameters);
    } else {
      throw new UnsupportedOperationException("executeUpdate() requires a JSON filter map: " + nq.getQuery());
    }
    if (setFields == null || setFields.isEmpty()) {
      return Optional.of(0);
    }
    Document updateDoc = Document.createDocument();
    for (Map.Entry<String, Object> entry : setFields.entrySet()) {
      updateDoc.put(entry.getKey(), entry.getValue());
    }

    // Auto-increment version if it's part of the query and NOT already set to a non-null value in update doc
    if (isOptimisticLocking(q)) {
        RuntimePersistentEntity<?> persistentEntity = getEntity(nq.getRootEntity());
        RuntimePersistentProperty<?> versionProp = persistentEntity.getVersion();
        String vPersistedName = versionProp.getPersistedName();
        String vPropName = versionProp.getName();

        Object currentValInUpdate = updateDoc.get(vPersistedName);
        if (currentValInUpdate == null) {
            // Find current version from named parameters or arguments
            Object currentVersion = namedParameters.get(vPropName);
            if (currentVersion == null) {
                currentVersion = namedParameters.get(vPersistedName);
            }
            if (currentVersion instanceof Number n) {
                updateDoc.put(vPersistedName, n.longValue() + 1);
            }
        }
    }
    Filter finalFilter = filter != null ? filter : nq.getNitriteFilter();
    logUpdate(getCollectionName(nq.getRootEntity()), finalFilter, updateDoc);
    long count = getCollection(nq.getRootEntity()).update(finalFilter, updateDoc, UpdateOptions.updateOptions(false)).getAffectedCount();
    if (count == 0 && finalFilter != Filter.ALL && isOptimisticLocking(q)) {
        throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
    }
    return Optional.of(count);
  }

  private boolean isOptimisticLocking(@NonNull final PreparedQuery<?, ?> q) {
    if (q.getArguments() != null && Arrays.stream(q.getArguments()).anyMatch(arg -> arg.getAnnotationMetadata().hasAnnotation(Version.class))) {
        return true;
    }
    // Check if method name suggests versioned update/delete (common in TCK)
    String methodName = q.getName();
    if (methodName.contains("AndVersion") || methodName.contains("ByVersion")) {
        return true;
    }
    // Check if query has "version" in its filters/assignments
    String query = q.getQuery().toLowerCase();
      return query.contains("version");
  }

  /** Build named parameter map from bindings and arguments. */
  private Map<String, Object> buildNamedParameterValues(@NonNull final PreparedQuery<?, ?> q) {
    return NitriteQueryBinder.buildNamedParameterValues(q, this::toFilterValue);
  }

  /**
   * Execute a delete by prepared query.
   *
   * @param q the prepared query
   * @return the number of affected rows
   */
  @Override
  public Optional<Number> executeDelete(@NonNull final PreparedQuery<?, Number> q) {
    NitritePreparedQuery nq = getNitritePreparedQuery(q);
    logDelete(getCollectionName(nq.getRootEntity()), nq.getNitriteFilter());
    long count = getCollection(nq.getRootEntity()).remove(nq.getNitriteFilter(), false).getAffectedCount();
    if (count == 0 && nq.getNitriteFilter() != Filter.ALL && isOptimisticLocking(q)) {
        throw new OptimisticLockException("Execute update returned unexpected row count. Expected: 1 got: 0");
    }
    return Optional.of(count);
  }

  /**
   * Execute a prepared query.
   *
   * @param <R> the result type
   * @param q the prepared query
   * @return the results
   */
  @Override
  public <R> List<R> execute(@NonNull final PreparedQuery<?, R> q) {
    List<R> list = new ArrayList<>();
    findAll(q).forEach(list::add);
    return list;
  }
}
