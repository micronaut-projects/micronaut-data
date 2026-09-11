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
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.model.CursoredPage;
import io.micronaut.data.model.Limit;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.Sort;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.StoredQuery;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.nitrite.conf.NitriteConfiguration;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import io.micronaut.data.nitrite.model.query.builder.NitriteSortKey;
import io.micronaut.data.nitrite.model.query.builder.NitriteSortKeys;
import io.micronaut.data.nitrite.operations.NitriteRepositoryOperations;
import io.micronaut.data.nitrite.runtime.criteria.NitriteCriteriaExecutor;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMeta;
import io.micronaut.data.nitrite.runtime.mapping.WritablePropertyMeta;
import io.micronaut.data.nitrite.runtime.query.DefaultNitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.DefaultNitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.GeneratedQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitritePreparedQuery;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryBinder;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.query.NitriteStoredQuery;
import io.micronaut.data.nitrite.runtime.query.ast.CompiledNitriteFilter;
import io.micronaut.data.nitrite.runtime.read.CursorWindow;
import io.micronaut.data.nitrite.runtime.read.NitriteFind;
import io.micronaut.data.nitrite.runtime.read.NitriteQueryExecutor;
import io.micronaut.data.nitrite.runtime.read.NitriteSortPlan;
import io.micronaut.data.nitrite.runtime.write.NitriteEntitiesOperations;
import io.micronaut.data.nitrite.runtime.write.NitriteEntityOperations;
import io.micronaut.data.nitrite.runtime.write.NitriteOperationContext;
import io.micronaut.data.nitrite.transaction.NitriteTransactionHolder;
import io.micronaut.data.operations.CriteriaRepositoryOperations;
import io.micronaut.data.runtime.config.DataSettings;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder;
import io.micronaut.data.runtime.date.DateTimeProvider;
import io.micronaut.data.runtime.operations.internal.AbstractRepositoryOperations;
import io.micronaut.data.runtime.operations.internal.SyncCascadeOperations;
import io.micronaut.data.runtime.query.MethodContextAwareStoredQueryDecorator;
import io.micronaut.data.runtime.query.PreparedQueryDecorator;
import io.micronaut.data.runtime.query.internal.DelegateStoredQuery;
import io.micronaut.serde.ObjectMapper;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import org.dizitart.no2.Nitrite;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.collection.NitriteId;
import org.dizitart.no2.filters.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
 *       PreparedQuery#getParameterArray()}.</li>
 *   <li>User-authored JSON {@code @Query} methods may use named placeholders like {@code :title}.
 *       These are bound using query bindings when available, otherwise by falling back to {@link
 *       PreparedQuery#getArguments()} names.</li>
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
 * @since 5.2.0
 */
@Internal
public final class DefaultNitriteRepositoryOperations extends AbstractRepositoryOperations
    implements NitriteRepositoryOperations, PreparedQueryDecorator, MethodContextAwareStoredQueryDecorator, NitriteOperationsHelper,
    SyncCascadeOperations.SyncCascadeOperationsHelper<NitriteOperationContext>,
    CriteriaRepositoryOperations {

    private static final Logger LOG =
        LoggerFactory.getLogger(DefaultNitriteRepositoryOperations.class);
    private static final Logger QUERY_LOG = DataSettings.QUERY_LOG;
    private static final AtomicLong ID_GENERATOR = new AtomicLong(System.currentTimeMillis());
    private static final Object[] EMPTY_PARAMS = new Object[0];

    private final Nitrite database;
    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final SyncCascadeOperations<NitriteOperationContext> cascadeOperations;
    private final CriteriaBuilder criteriaBuilder;
    private final NitriteCriteriaExecutor criteriaExecutor;
    private final NitriteQueryExecutor queryExecutor;
    private final ValueConverter valueConverter;
    private final NitriteCollectionRegistry collectionRegistry;
    private final NitriteQueryBinder queryBinder;
    private final boolean useCursorLimitForSortedReads;

    /**
     * Create Nitrite repository operations.
     *
     * @param database                   The Nitrite database
     * @param configuration              The Nitrite configuration
     * @param dateTimeProvider           Date/time provider used by the base operations
     * @param runtimeEntityRegistry      Entity metadata registry
     * @param conversionService          Conversion service (for field-level conversions)
     * @param attributeConverterRegistry Attribute converter registry
     * @param transactionHolder          Transaction context holder
     * @param serdeObjectMapper          Optional Micronaut Serde ObjectMapper
     */
    public DefaultNitriteRepositoryOperations(
        final Nitrite database,
        final NitriteConfiguration configuration,
        final DateTimeProvider<Object> dateTimeProvider,
        final RuntimeEntityRegistry runtimeEntityRegistry,
        final DataConversionService conversionService,
        final AttributeConverterRegistry attributeConverterRegistry,
        final NitriteTransactionHolder transactionHolder,
        final @Nullable ObjectMapper serdeObjectMapper) {
        super(dateTimeProvider, runtimeEntityRegistry, conversionService, attributeConverterRegistry);
        this.useCursorLimitForSortedReads = configuration.getSortedReadStrategy()
            .usesCursorLimit(configuration.getStorageMode());
        this.database = database;
        this.collectionRegistry = new NitriteCollectionRegistry(database, transactionHolder, configuration, this::getEntity);
        this.entityMapper =
            new NitriteEntityMapper(conversionService, serdeObjectMapper, runtimeEntityRegistry);
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
            conversionService,
            collectionRegistry::getCollection,
            this::getEntity,
            useCursorLimitForSortedReads);
        this.queryExecutor = new NitriteQueryExecutor(
            entityMapper,
            queryParser,
            filterBuilder,
            conversionService,
            collectionRegistry::getCollection,
            this::getEntity,
            this::buildFind,
            this,
            runtimeEntityRegistry.getEntityEventListener(),
            useCursorLimitForSortedReads);
        this.queryBinder = new NitriteQueryBinder(entityMapper);
    }

    /**
     * Create a NitriteFilterBuilder with sub-query executor for auto-join on MANY_TO_ONE associations.
     * The sub-query executor allows filtering by association properties (e.g., author.name) by
     * first querying the associated entity and then filtering by ID.
     */
    private NitriteFilterBuilder createFilterBuilderWithSubQueryExecutor() {
        // Use a mutable ref so the closure captures the fully-wired builder, not the plain one.
        // Without this, nested association paths (e.g. review.book.author.name) would silently
        // return no results because the inner sub-query builder lacks sub-query support.
        NitriteFilterBuilder[] builderRef = new NitriteFilterBuilder[1];
        NitriteFilterBuilder.SubQueryExecutor subQueryExecutor =
            (associatedEntity, filterMap, targetField, retainDocuments, params, namedParameters) -> {
            NitriteCollection assocCollection = getCollection(associatedEntity.getIntrospection().getBeanType());
            Filter subFilter = filterMap != null && !filterMap.isEmpty()
                ? builderRef[0].buildFilterFromJson(associatedEntity, filterMap, params, namedParameters)
                : Filter.ALL;
            return assocCollection.find(subFilter).toList().stream()
                .flatMap(doc -> {
                    if (retainDocuments) {
                        return Stream.of(doc);
                    }
                    String fieldName = targetField;
                    if (fieldName == null) {
                        RuntimePersistentProperty<?> identity = associatedEntity.getIdentity();
                        if (identity == null) {
                            // A composite identity has no single field to project; callers needing a
                            // composite match ask for the documents themselves via retainDocuments.
                            return Stream.empty();
                        }
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
        builderRef[0] = new NitriteFilterBuilder(entityMapper, subQueryExecutor);
        return builderRef[0];
    }

    @Override
    public Nitrite getDatabase() {
        return database;
    }

    @Override
    public void logFind(String collection, Filter filter) {
        QUERY_LOG.debug("Executing Nitrite 'find' on collection [{}] with filter: {}",
            collection, filter != null ? filter : "Filter.ALL");
    }

    @Override
    public void logInsert(String collection, Object entityOrDocs) {
        QUERY_LOG.debug("Executing Nitrite 'insert' into collection [{}] with document: {}",
            collection, entityOrDocs);
    }

    @Override
    public void logUpdate(String collection, Filter filter, Document update) {
        QUERY_LOG.debug("Executing Nitrite 'update' on collection [{}] with filter: {} and update: {}",
            collection, filter != null ? filter : "Filter.ALL", update);
    }

    // ========== CriteriaRepositoryOperations implementation ==========

    @Override
    public CriteriaBuilder getCriteriaBuilder() {
        return criteriaBuilder;
    }

    @Override
    public boolean exists(@NonNull CriteriaQuery<?> query) {
        return criteriaExecutor.exists(query);
    }

    @Override
    public <R> @Nullable R findOne(@NonNull CriteriaQuery<R> query) {
        return criteriaExecutor.findOne(query);
    }

    @Override
    @NonNull
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query) {
        return criteriaExecutor.findAll(query);
    }

    @Override
    @NonNull
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query, int offset, int limit) {
        return criteriaExecutor.findAll(query, offset, limit);
    }

    @Override
    @NonNull
    public Optional<Number> updateAll(@NonNull CriteriaUpdate<Number> query) {
        return criteriaExecutor.updateAll(query);
    }

    @Override
    @NonNull
    public Optional<Number> deleteAll(@NonNull CriteriaDelete<Number> query) {
        return criteriaExecutor.deleteAll(query);
    }

    // ========== SyncCascadeOperationsHelper implementation ==========

    @Override
    @SuppressWarnings("unchecked")
    public <T> T persistOne(NitriteOperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("persistOne: entity={}, type={}", entityValue, persistentEntity.getName());
        }

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
    @SuppressWarnings("rawtypes")
    public void persistManyAssociation(NitriteOperationContext ctx,
                                       RuntimeAssociation runtimeAssociation,
                                       Object parentEntityValue,
                                       RuntimePersistentEntity<Object> parentPersistentEntity,
                                       Object childEntityValue,
                                       RuntimePersistentEntity<Object> childPersistentEntity) {
        // Not needed for Nitrite - relationships are not stored as separate collections
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void persistManyAssociationBatch(NitriteOperationContext ctx,
                                            RuntimeAssociation runtimeAssociation,
                                            Object parentEntityValue,
                                            RuntimePersistentEntity<Object> parentPersistentEntity,
                                            Iterable<Object> childEntityValues,
                                            RuntimePersistentEntity<Object> childPersistentEntity) {
        // Not needed for Nitrite - relationships are not stored as separate collections
    }

    @Override
    public void logDelete(String collection, Filter filter) {
        QUERY_LOG.debug("Executing Nitrite 'remove' from collection [{}] with filter: {}",
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

    /**
     * Get collection name from entity class.
     */
    private String getCollectionName(final Class<?> type) {
        return collectionRegistry.getCollectionName(type);
    }

    /**
     * Get Nitrite collection for entity type.
     */
    @Override
    public NitriteCollection getCollection(final Class<?> type) {
        return collectionRegistry.getCollection(type);
    }

    /**
     * Generate and set ID on entity if @GeneratedValue is present and ID is null.
     */
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
            /*
             * A Long identity is taken from NitriteId.newId(), which is backed by a snowflake
             * generator and stays unique across restarts and across JVMs. A process-local counter
             * would not: two processes starting together would issue the same values.
             */
            Object generatedId = switch (idType) {
                case Class<?> c when c == UUID.class -> UUID.randomUUID();
                case Class<?> c when c == Long.class || c == long.class ->
                    NitriteId.newId().getIdValue();
                case Class<?> c when c == Integer.class || c == int.class ->
                    (int) (ID_GENERATOR.incrementAndGet() % Integer.MAX_VALUE);
                default -> UUID.randomUUID().toString();
            };
            if (idAccessor != null) {
                idAccessor.set(entity, generatedId);
            }
        }
    }

    @Override
    public <T> @Nullable T findOne(final Class<T> type, final Object id) {
        Filter filter = entityMapper.idEqualsFilter(type, id);
        Document doc = getCollection(type).find(filter).firstOrNull();
        if (doc == null) {
            return null;
        }
        return entityMapper.fromDocument(doc, type);
    }

    @Override
    public <T> T persist(@NonNull final InsertOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        return persistOne(ctx, operation.getEntity(), getEntity(operation.getRootEntity()));
    }

    @Override
    public <T> Iterable<T> persistAll(@NonNull final InsertBatchOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
            ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
            getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
            operation, true);
        op.persist();
        return op.getEntities();
    }

    @Override
    public <T> T update(@NonNull final UpdateOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        return updateOne(ctx, operation.getEntity(), getEntity(operation.getRootEntity()));
    }

    @Override
    public <T> Iterable<T> updateAll(@NonNull final UpdateBatchOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
            ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
            getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
            operation, false);
        op.persist(); // base class persistAll() will call execute() which handles insert vs update
        return op.getEntities();
    }

    @Override
    public <T> int delete(@NonNull final DeleteOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        NitriteEntityOperations<T> op = new NitriteEntityOperations<>(
            ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
            getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
            operation.getEntity(), NitriteEntityOperations.OperationType.DELETE);
        op.delete();
        return (int) op.getAffectedCount();
    }

    @Override
    public <T> Optional<Number> deleteAll(@NonNull final DeleteBatchOperation<T> operation) {
        NitriteOperationContext ctx = new NitriteOperationContext(operation.getAnnotationMetadata(), operation.getRepositoryType(), operation.getName());
        if (operation.all()) {
            NitriteCollection collection = getCollection(operation.getRootEntity());
            logDelete(collection.getName(), Filter.ALL);
            // clear() reports nothing, so the size is read before the documents are dropped.
            long size = collection.size();
            collection.clear();
            return Optional.of(size);
        }
        NitriteEntitiesOperations<T> op = new NitriteEntitiesOperations<>(
            ctx, cascadeOperations, runtimeEntityRegistry.getEntityEventListener(),
            getEntity(operation.getRootEntity()), conversionService, entityMapper, this,
            operation, false);
        op.delete();
        return Optional.of(op.getAffectedCount());
    }

    /**
     * Build a find with pagination and sorting, merging additional sort keys from the query.
     */
    private NitriteFind buildFind(final Pageable pageable, final List<NitriteSortKey> additionalSortKeys, @Nullable RuntimePersistentEntity<?> entity) {
        return buildFind(pageable, additionalSortKeys, null, entity);
    }

    /**
     * Build a find with pagination, limit, and sorting.
     *
     * <p>A key the find cannot order by - one computed per document, or one asking for nulls where
     * Nitrite does not place them - leaves the ordering and the row window to the returned
     * {@link NitriteSortPlan} instead, which orders the documents the find returns.
     */
    private NitriteFind buildFind(final Pageable pageable, final List<NitriteSortKey> additionalSortKeys, @Nullable final Limit limit, @Nullable RuntimePersistentEntity<?> entity) {
        FindOptions options = new FindOptions();
        boolean cursored = pageable.getMode() == Pageable.Mode.CURSOR_NEXT || pageable.getMode() == Pageable.Mode.CURSOR_PREVIOUS;
        if (!cursored && pageable.getOffset() > 0) {
            options.skip(pageable.getOffset());
        }
        // A key is merged under the field it orders by, so a runtime sort replaces the query's own
        // key for that field rather than ordering by it twice. A computed key orders by no field
        // and so is merged under itself, which nothing else can replace.
        Map<Object, NitriteSortKey> mergedKeys = new LinkedHashMap<>();
        for (NitriteSortKey key : additionalSortKeys) {
            mergedKeys.put(key.isExpression() ? key : key.field(), key);
        }
        for (NitriteSortKey key : NitriteSortKeys.fromSort(pageable.getSort())) {
            mergedKeys.put(key.field(), key);
        }
        if (cursored && entity != null) {
            // Cursor ordering appends the entity identity so that records sharing a primary sort
            // value cannot be skipped between pages.
            Map<String, Sort.Order> identityOrders = new LinkedHashMap<>();
            for (Sort.Order order : NitriteSortKeys.toOrders(List.copyOf(mergedKeys.values()))) {
                identityOrders.put(order.getProperty(), order);
            }
            appendIdentitySort(identityOrders, entity);
            mergedKeys.clear();
            for (NitriteSortKey key : NitriteSortKeys.fromSort(Sort.of(new ArrayList<>(identityOrders.values())))) {
                mergedKeys.put(key.field(), key);
            }
        }
        List<NitriteSortKey> sortKeys = mergedKeys.values().stream()
            .map(key -> key.isExpression()
                ? key
                : NitriteSortKey.ofField(normalizeSortProperty(
                    Objects.requireNonNull(key.field(), "A stored sort key must have a field"), entity),
                    key.ascending(), key.nullOrdering()))
            .toList();
        // Apply limit from pageable first, then from explicit Limit (for methods like listTop10).
        // For MVSTORE and in-memory, a sorted read has this bound taken off again by
        // CursorWindow.withholdSortedLimit and applied to its cursor instead, so that the find
        // does not take Nitrite's index-ordered path. RocksDB retains the bound because its
        // bounded indexed path is faster than the unbounded alternative.
        int effectiveLimit = -1;
        if (!cursored && pageable.getSize() > 0) {
            effectiveLimit = pageable.getSize();
        } else if (!cursored && limit != null && limit.maxResults() > 0) {
            effectiveLimit = limit.maxResults();
        }
        if (effectiveLimit > 0) {
            options.limit(effectiveLimit);
        }
        NitriteSortPlan sortPlan = NitriteSortPlan.build(
            sortKeys, entity, entityMapper, filterBuilder, EMPTY_PARAMS, Map.of());
        if (sortPlan.isNativelySortable()) {
            sortPlan.applyNativeOrder(options);
            return new NitriteFind(options, null);
        }
        sortPlan.withholdWindow(options);
        return new NitriteFind(options, sortPlan);
    }

    private void appendIdentitySort(Map<String, Sort.Order> orders, RuntimePersistentEntity<?> entity) {
        // Every identity property becomes a secondary sort key, a composite identity included: a
        // page sorted only by a non-unique field has no tie-breaker of its own, so records sharing
        // that sort value could be skipped or repeated across pages.
        // The direction is read from the caller's last sort key once, before anything is appended,
        // so every identity key of a composite identity shares one direction instead of each one
        // picking up the key the previous iteration just added.
        List<Sort.Order> requestedOrders = new ArrayList<>(orders.values());
        Sort.Order.Direction direction = requestedOrders.isEmpty()
            ? Sort.Order.Direction.ASC
            : requestedOrders.getLast().getDirection();
        for (PersistentProperty identity : entity.getIdentityProperties()) {
            if (requestedOrders.stream().anyMatch(order -> isIdentitySort(order.getProperty(), entity, identity))) {
                continue;
            }
            Sort.Order identityOrder = direction == Sort.Order.Direction.ASC
                ? Sort.Order.asc(identity.getName())
                : Sort.Order.desc(identity.getName());
            orders.put(identity.getName(), identityOrder);
        }
    }

    private boolean isIdentitySort(String property, RuntimePersistentEntity<?> entity, PersistentProperty identity) {
        String propertyName = normalizeSortProperty(property, entity);
        if (propertyName.indexOf('.') >= 0) {
            // A nested path such as "author.id" sorts by the association's identity, not the
            // root identity, so it does not make the ordering unique on its own.
            return false;
        }
        return propertyName.equals(identity.getName())
            || propertyName.equals(identity.getPersistedName())
            || "_id".equals(propertyName)
            || NitriteEntityMapper.ID_FIELD.equals(propertyName);
    }

    private String normalizeSortProperty(String property, @Nullable RuntimePersistentEntity<?> entity) {
        if (entity == null || !property.contains(".")) {
            return property;
        }
        int separator = property.indexOf('.');
        if (entity.getPropertyByName(property.substring(0, separator)) != null) {
            return property;
        }
        // Document-processor sorts can carry a root alias. Remove only that alias so a nested
        // property path such as "event.location.region" remains "location.region".
        return property.substring(separator + 1);
    }

    /**
     * Parses the sort a query carries: the {@code $sort} key of a Nitrite JSON filter, or the
     * {@code ORDER BY} clause of a SQL-shaped generated query.
     */
    @Override
    public @Nullable Sort parseSortFromQuery(@Nullable final String queryString) {
        if (queryString != null && !queryString.contains(NitriteQueryOperators.SORT)) {
            return GeneratedQueryParser.parseOrderBy(queryString);
        }
        List<Sort.Order> orders = NitriteSortKeys.toOrders(parseSortKeysFromQuery(queryString));
        return orders.isEmpty() ? null : Sort.of(orders);
    }

    @Override
    public List<NitriteSortKey> parseSortKeysFromQuery(@Nullable final String queryString) {
        if (queryString == null) {
            return List.of();
        }
        if (!queryString.contains(NitriteQueryOperators.SORT)) {
            Sort parsed = GeneratedQueryParser.parseOrderBy(queryString);
            return NitriteSortKeys.fromSort(parsed);
        }
        try {
            Object parsed = queryParser.parseJson(queryString);
            Object sortValue = null;
            if (parsed instanceof Map<?, ?> m) {
                sortValue = m.get(NitriteQueryOperators.SORT);
            } else if (parsed instanceof List<?> pipeline) {
                sortValue = pipeline.stream()
                    .filter(Map.class::isInstance)
                    .map(stage -> ((Map<?, ?>) stage).get(NitriteQueryOperators.SORT))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            }
            return NitriteSortKeys.fromJson(sortValue);
        } catch (Exception ignored) {
            // Best-effort JSON sort parsing; if it fails, assume no sort
            return List.of();
        }
    }

    @Override
    public List<NitriteSortKey> parseSortKeysFromHints(@Nullable final Map<String, Object> hints) {
        return NitriteSortKeys.fromSort(parseSortFromHints(hints));
    }

    /**
     * Parse sort string from query hints.
     */
    @Override
    public @Nullable Sort parseSortFromHints(@Nullable final Map<String, Object> hints) {
        if (hints == null || hints.isEmpty() || !(hints.get("sort") instanceof String sortStr) || sortStr.isEmpty()) {
            return null;
        }
        List<Sort.Order> orders = Stream.of(sortStr.split(",", -1))
            .map(part -> part.trim().split(":", -1))
            .filter(parts -> parts.length == 2)
            .map(parts -> Sort.Order.Direction.valueOf(parts[1]) == Sort.Order.Direction.ASC
                ? Sort.Order.asc(parts[0]) : Sort.Order.desc(parts[0]))
            .toList();
        return orders.isEmpty() ? null : Sort.of(orders);
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    public <T> Iterable<T> findAll(@NonNull final PagedQuery<T> query) {
        if (query instanceof PreparedQuery<?, ?> pq) {
            return queryExecutor.findAll((PreparedQuery) pq, (NitritePreparedQuery) getNitritePreparedQuery(pq));
        }

        Class<T> type = query.getRootEntity();
        Filter filter = Filter.ALL;
        Limit limit = query.getPageable().getMode() == Pageable.Mode.OFFSET ? query.getQueryLimit() : Limit.UNLIMITED;

        NitriteFind find = buildFind(query.getPageable(), List.of(), limit, getEntity(type));
        FindOptions findOptions = find.options();
        long cursorLimit = CursorWindow.withholdSortedLimit(findOptions, useCursorLimitForSortedReads);
        List<Document> documents = CursorWindow.limit(getCollection(type).find(filter, findOptions), cursorLimit).toList();
        if (find.sortPlan() != null) {
            documents = find.sortPlan().apply(documents, findOptions.collator());
        }
        List<T> results = new ArrayList<>();
        for (Document doc : documents) {
            results.add(entityMapper.fromDocument(doc, type));
        }
        return results;
    }

    @Override
    public <T> long count(@NonNull final PagedQuery<T> query) {
        if (query instanceof PreparedQuery<?, ?> pq) {
            return queryExecutor.count(pq, getNitritePreparedQuery(pq));
        }
        return getCollection(query.getRootEntity()).size();
    }

    private Object[] buildJsonParameterValues(@NonNull final PreparedQuery<?, ?> q) {
        return queryBinder.buildJsonParameterValues(q);
    }

    private Object[] ensureJsonParamsForFilter(@Nullable final Map<String, Object> filterMap,
                                               @NonNull final Object[] methodParams,
                                               @Nullable final Object[] jsonParams) {
        return queryBinder.ensureJsonParamsForFilter(filterMap, methodParams, jsonParams);
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
     * @param <E>         the entity type
     * @param <R>         the result type
     * @param storedQuery the original stored query
     * @return the Nitrite stored query
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <E, R> NitriteStoredQuery<E, R> createNitriteStoredQuery(@NonNull StoredQuery<E, R> storedQuery) {
        if (storedQuery instanceof NitriteStoredQuery<E, R> nsq) {
            return nsq;
        }
        NitriteQueryParser.ParsedJsonQuery parsed = queryParser.parseStoredQuery(storedQuery);
        CompiledNitriteFilter compiledFilter = parsed.filterMap() != null
            ? filterBuilder.compile(getEntity(storedQuery.getRootEntity()), parsed.filterMap())
            : null;
        return new DefaultNitriteStoredQuery<>(storedQuery, getEntity(storedQuery.getRootEntity()), conversionService,
            parsed.filterMap(), compiledFilter, parsed.updateMap());
    }

    /**
     * Create a specialized NitritePreparedQuery.
     *
     * @param <E>           the entity type
     * @param <R>           the result type
     * @param preparedQuery the original prepared query
     * @return the Nitrite prepared query
     */
    @NonNull
    @SuppressWarnings("unchecked")
    public <E, R> NitritePreparedQuery<E, R> createNitritePreparedQuery(@NonNull PreparedQuery<E, R> preparedQuery) {
        if (preparedQuery instanceof NitritePreparedQuery<E, R> npq) {
            return npq;
        }
        NitriteStoredQuery<E, R> storedQuery;
        if (preparedQuery instanceof DelegateStoredQuery<?, ?> dsq && dsq.getStoredQueryDelegate() instanceof NitriteStoredQuery<?, ?> nsq) {
            storedQuery = (NitriteStoredQuery<E, R>) nsq;
        } else {
            storedQuery = createNitriteStoredQuery(preparedQuery);
        }
        return new DefaultNitritePreparedQuery<>(preparedQuery, buildFilterFromPreparedQuery(preparedQuery, storedQuery), storedQuery.getFilterMap(), storedQuery.getCompiledFilter(), storedQuery.getUpdateMap());
    }

    private <E, R> NitritePreparedQuery<E, R> getNitritePreparedQuery(PreparedQuery<E, R> q) {
        if (q instanceof NitritePreparedQuery<E, R> nq) {
            return nq;
        }
        return createNitritePreparedQuery(q);
    }

    /**
     * Resolves the Nitrite filter for a prepared query, in descending order of preference:
     * the filter compiled once at stored-query creation, the stored JSON filter map, and finally
     * the query's own {@code WHERE} clause.
     *
     * <p>The last case covers every query whose string is SQL-shaped rather than a Nitrite JSON
     * filter — a JDQL {@code @Query}, and any repository compiled without
     * {@code io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder} on the annotation
     * processor path, where {@code RepositoryTypeElementVisitor} silently falls back to
     * {@code JpaQueryBuilder}. Returning {@code Filter.ALL} here instead would make such a query
     * match every document in the collection, so a single-argument finder would return the whole
     * collection rather than failing.
     *
     * @param q      the prepared query
     * @param stored the Nitrite stored query it was created from
     * @return the filter to execute
     */
    private Filter buildFilterFromPreparedQuery(final PreparedQuery<?, ?> q, NitriteStoredQuery<?, ?> stored) {
        Map<String, Object> namedParameters = buildNamedParameterValues(q);
        if (stored.getCompiledFilter() != null) {
            return stored.getCompiledFilter().bind(ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
        }
        if (stored.getFilterMap() != null) {
            return filterBuilder.buildFilterFromJson(getEntity(stored.getRootEntity()), stored.getFilterMap(), ensureJsonParamsForFilter(stored.getFilterMap(), q.getParameterArray(), buildJsonParameterValues(q)), namedParameters);
        }
        return queryExecutor.buildGeneratedFilter(q, getEntity(stored.getRootEntity()), buildJsonParameterValues(q));
    }

    @Override
    public @Nullable Object toFilterValue(@Nullable Object value) {
        return entityMapper.toFilterValue(value);
    }

    /**
     * Find one entity by prepared query.
     *
     * @param <T> the entity type
     * @param <R> the result type
     * @param q   the prepared query
     * @return the result
     */
    @Override
    public <T, R> @Nullable R findOne(@NonNull final PreparedQuery<T, R> q) {
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
     * @param q   the prepared query
     * @return true if exists
     */
    @Override
    public <T> boolean exists(@NonNull final PreparedQuery<T, Boolean> q) {
        NitritePreparedQuery<T, Boolean> nq = getNitritePreparedQuery(q);
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
     * @param value      the value to convert
     * @param targetType the target type
     * @return the converted value
     */
    private @Nullable Object convertValue(@Nullable Object value, Class<?> targetType) {
        return valueConverter.convertWithTemporalHandling(value, targetType);
    }

    /**
     * Find a stream by prepared query.
     *
     * @param <T> the entity type
     * @param <R> the result type
     * @param q   the prepared query
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
     * @param q   the paged query
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
     * @param q   the paged query
     * @return the page
     */
    @Override
    public <R> Page<R> findPage(@NonNull final PagedQuery<R> q) {
        List<R> list = toPageContent(findAll(q));
        Pageable pageable = q.getPageable();
        if (pageable.getMode() == Pageable.Mode.CURSOR_NEXT || pageable.getMode() == Pageable.Mode.CURSOR_PREVIOUS) {
            RuntimePersistentEntity<?> entity = getEntity(q.getRootEntity());
            Sort sort = resolveCursoredSort(q, entity);
            List<R> pageContent = applyCursorWindow(list, pageable, sort, entity);
            return CursoredPage.of(
                pageContent,
                pageable,
                createCursors(pageContent, sort, entity),
                pageable.requestTotal() ? count(q) : null
            );
        }
        return Page.of(list, q.getPageable(), count(q));
    }

    private <R> List<R> toPageContent(Iterable<R> results) {
        if (results instanceof List<R> list) {
            return list;
        }
        List<R> list = new ArrayList<>();
        results.forEach(list::add);
        return list;
    }

    private Sort resolveCursoredSort(PagedQuery<?> query, RuntimePersistentEntity<?> entity) {
        Sort sort = query.getPageable().getSort();
        if (query instanceof NitritePreparedQuery<?, ?> nitriteQuery) {
            Sort parsedSort = parseSortFromQuery(nitriteQuery.getQuery());
            if ((parsedSort == null || !parsedSort.isSorted()) && nitriteQuery.getQueryHints() != null) {
                parsedSort = parseSortFromHints(nitriteQuery.getQueryHints());
            }
            if (parsedSort != null && parsedSort.isSorted()) {
                if (sort == null || !sort.isSorted()) {
                    sort = parsedSort;
                } else {
                    List<Sort.Order> merged = new ArrayList<>(parsedSort.getOrderBy());
                    merged.addAll(sort.getOrderBy());
                    sort = Sort.of(merged);
                }
            }
        }
        if (sort == null || !sort.isSorted()) {
            Map<String, Sort.Order> identityOrders = new LinkedHashMap<>();
            appendIdentitySort(identityOrders, entity);
            if (identityOrders.isEmpty()) {
                throw new IllegalStateException("Cursored pagination requires a sort or identity property for " + entity);
            }
            return Sort.of(new ArrayList<>(identityOrders.values()));
        }
        Map<String, Sort.Order> orders = new LinkedHashMap<>();
        for (Sort.Order order : sort.getOrderBy()) {
            orders.put(order.getProperty(), order);
        }
        appendIdentitySort(orders, entity);
        return Sort.of(new ArrayList<>(orders.values()));
    }

    private <R> List<R> applyCursorWindow(List<R> results,
                                          Pageable pageable,
                                          Sort sort,
                                          RuntimePersistentEntity<?> entity) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<R> matching = pageable.cursor()
            .map(cursor -> results.stream()
                .filter(result -> compareToCursor(result, cursor, sort, entity) * cursorDirection(pageable) > 0)
                .toList())
            .orElse(results);
        int size = pageable.getSize();
        if (size < 0 || matching.size() <= size) {
            return matching;
        }
        if (pageable.getMode() == Pageable.Mode.CURSOR_PREVIOUS && pageable.cursor().isPresent()) {
            return new ArrayList<>(matching.subList(matching.size() - size, matching.size()));
        }
        return new ArrayList<>(matching.subList(0, size));
    }

    private int cursorDirection(Pageable pageable) {
        return pageable.getMode() == Pageable.Mode.CURSOR_PREVIOUS ? -1 : 1;
    }

    private int compareToCursor(Object result,
                                Pageable.Cursor cursor,
                                Sort sort,
                                RuntimePersistentEntity<?> entity) {
        List<Sort.Order> orders = sort.getOrderBy();
        if (orders.size() != cursor.size()) {
            throw new IllegalArgumentException("The cursor must match the sorting size");
        }
        for (int i = 0; i < orders.size(); i++) {
            Sort.Order order = orders.get(i);
            Object propertyValue = getPropertyValue(result, order.getProperty(), entity);
            int comparison = compareNullable(propertyValue, cursor.get(i));
            if (comparison != 0) {
                return order.isAscending() ? comparison : -comparison;
            }
        }
        return 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private int compareNullable(@Nullable Object left, @Nullable Object right) {
        if (Objects.equals(left, right)) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        if (left instanceof Comparable comparable) {
            return comparable.compareTo(right);
        }
        throw new IllegalArgumentException("Cursor property value must be comparable: " + left);
    }

    private List<Pageable.Cursor> createCursors(List<?> results,
                                                Sort sort,
                                                RuntimePersistentEntity<?> entity) {
        if (results.isEmpty()) {
            return List.of();
        }
        List<Sort.Order> orders = sort.getOrderBy();
        List<Pageable.Cursor> cursors = new ArrayList<>(results.size());
        for (Object result : results) {
            List<Object> elements = new ArrayList<>(orders.size());
            for (Sort.Order order : orders) {
                elements.add(getPropertyValue(result, order.getProperty(), entity));
            }
            cursors.add(Pageable.Cursor.of(elements));
        }
        return cursors;
    }

    private @Nullable Object getPropertyValue(Object result, String property, RuntimePersistentEntity<?> entity) {
        String propertyName = normalizeSortProperty(property, entity);
        PersistentPropertyPath propertyPath = entity.getPropertyPath(propertyName);
        if (propertyPath == null) {
            propertyPath = findPersistedPropertyPath(propertyName, entity);
        }
        if (propertyPath == null) {
            throw new IllegalArgumentException("Unknown cursor sort property [" + property + "] for " + entity);
        }
        return propertyPath.getPropertyValue(result);
    }

    private @Nullable PersistentPropertyPath findPersistedPropertyPath(String persistedName, RuntimePersistentEntity<?> entity) {
        if (entity.hasIdentity()) {
            RuntimePersistentProperty<?> identity = entity.getIdentity();
            if (identity.getPersistedName().equals(persistedName) || "_id".equals(persistedName)) {
                return entity.getPropertyPath(identity.getName());
            }
        }
        return entity.getPersistentProperties().stream()
            .filter(property -> property.getPersistedName().equals(persistedName))
            .map(property -> entity.getPropertyPath(property.getName()))
            .findFirst()
            .orElse(null);
    }

    @Override
    public Optional<Number> executeUpdate(@NonNull final PreparedQuery<?, Number> q) {
        return queryExecutor.executeUpdate(q, getNitritePreparedQuery(q));
    }

    /**
     * Build named parameter map from bindings and arguments.
     */
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
        return queryExecutor.executeDelete(q, getNitritePreparedQuery(q));
    }

    /**
     * Execute a prepared query.
     *
     * @param <R> the result type
     * @param q   the prepared query
     * @return the results
     */
    @Override
    public <R> List<R> execute(@NonNull final PreparedQuery<?, R> q) {
        List<R> list = new ArrayList<>();
        findAll(q).forEach(list::add);
        return list;
    }
}
