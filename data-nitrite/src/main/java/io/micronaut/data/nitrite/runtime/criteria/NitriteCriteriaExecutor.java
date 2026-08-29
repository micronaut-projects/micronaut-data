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
package io.micronaut.data.nitrite.runtime.criteria;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.PersistentEntityCriteriaUpdate;
import io.micronaut.data.model.jpa.criteria.PersistentEntityQuery;
import io.micronaut.data.model.jpa.criteria.PersistentAssociationPath;
import io.micronaut.data.model.jpa.criteria.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaDelete;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaQuery;
import io.micronaut.data.model.jpa.criteria.impl.AbstractPersistentEntityCriteriaUpdate;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.JoinPath;
import io.micronaut.data.model.query.builder.QueryBuilder;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.model.query.builder.QueryResult;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.nitrite.model.query.NitriteQueryOperators;
import io.micronaut.data.nitrite.model.query.builder.NitriteQueryBuilder;
import io.micronaut.data.nitrite.model.query.builder.NitriteRuntimeFilter;
import io.micronaut.data.nitrite.runtime.CollectionUpdateLock;
import io.micronaut.data.nitrite.runtime.ValueConverter;
import io.micronaut.data.nitrite.runtime.mapping.NitriteEntityMapper;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterBuilder;
import io.micronaut.data.nitrite.runtime.query.NitriteFilterUtils;
import io.micronaut.data.nitrite.runtime.query.NitriteQueryParser;
import io.micronaut.data.nitrite.runtime.read.CollectionAggregator;
import io.micronaut.data.nitrite.runtime.read.CollectionProjectionMapper;
import io.micronaut.data.nitrite.runtime.read.JoinFetcher;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.From;
import jakarta.persistence.criteria.Selection;
import org.dizitart.no2.collection.Document;
import org.dizitart.no2.collection.DocumentCursor;
import org.dizitart.no2.collection.FindOptions;
import org.dizitart.no2.collection.NitriteCollection;
import org.dizitart.no2.common.Lookup;
import org.dizitart.no2.common.RecordStream;
import org.dizitart.no2.common.SortOrder;
import org.dizitart.no2.filters.Filter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

/**
 * Helper class to execute Criteria queries for Nitrite.
 *
 * @since 5.2.0
 */
@Internal
public final class NitriteCriteriaExecutor {

    private final QueryBuilder queryBuilder;
    private final NitriteEntityMapper entityMapper;
    private final NitriteQueryParser queryParser;
    private final NitriteFilterBuilder filterBuilder;
    private final Function<Class<?>, NitriteCollection> collectionFactory;
    private final Function<Class<?>, RuntimePersistentEntity<?>> entityFactory;
    private final CollectionProjectionMapper projectionMapper;
    private final ConversionService conversionService;
    private final JoinFetcher joinFetcher;

    /**
     * Creates a new NitriteCriteriaExecutor.
     *
     * @param queryBuilder the query builder
     * @param entityMapper the entity mapper
     * @param queryParser the query parser
     * @param filterBuilder the filter builder
     * @param conversionService the conversion service
     * @param collectionFactory the collection factory function
     * @param entityFactory the entity factory function
     */
    public NitriteCriteriaExecutor(QueryBuilder queryBuilder,
                                   NitriteEntityMapper entityMapper,
                                   NitriteQueryParser queryParser,
                                   NitriteFilterBuilder filterBuilder,
                                   ConversionService conversionService,
                                   Function<Class<?>, NitriteCollection> collectionFactory,
                                   Function<Class<?>, RuntimePersistentEntity<?>> entityFactory) {
        this.queryBuilder = queryBuilder;
        this.entityMapper = entityMapper;
        this.queryParser = queryParser;
        this.filterBuilder = filterBuilder;
        this.collectionFactory = collectionFactory;
        this.entityFactory = entityFactory;
        this.conversionService = conversionService;
        this.projectionMapper = new CollectionProjectionMapper(new ValueConverter(conversionService), entityMapper);
        this.joinFetcher = new JoinFetcher(entityMapper, collectionFactory, entityFactory, conversionService);
    }

    /**
     * Checks if any entities match the given criteria query.
     *
     * @param query the criteria query
     * @return true if at least one entity matches
     */
    public boolean exists(@NonNull CriteriaQuery<?> query) {
        CriteriaReadPlan plan = prepareRead(query, -1, -1);
        if (!plan.innerJoin()) {
            return collectionFactory.apply(plan.entityType()).find(plan.filter()).iterator().hasNext();
        }
        return !readDocuments(plan, true).documents().isEmpty();
    }

    /**
     * Finds a single entity matching the criteria query.
     *
     * @param query the criteria query
     * @param <R> the result type
     * @return the first matching entity, or null if none found
     */
    public <R> @Nullable R findOne(@NonNull CriteriaQuery<R> query) {
        CriteriaReadPlan plan = prepareRead(query, -1, -1);
        Class<R> resultType = (Class<R>) plan.resultType();

        // A numeric result type alone does not imply a count: max/min/sum/avg also produce a
        // $group stage, so the selection decides which aggregate to execute.
        AggregateSelection aggregateSelection = extractAggregateSelection(plan.parsedQuery());
        if (aggregateSelection != null) {
            Object aggregate = new CollectionAggregator().aggregate(
                readDocuments(plan, plan.innerJoin()).documents(),
                aggregateSelection.field(),
                aggregateSelection.function());
            if (aggregate == null) {
                return null;
            }
            return conversionService.convert(aggregate, resultType).orElse((R) aggregate);
        }
        // Handle count queries specially
        if (Long.class.equals(resultType) || long.class.equals(resultType)) {
            String queryStr = plan.queryString();
            if (queryStr != null && queryStr.contains(NitriteQueryOperators.GROUP)) {
                String fieldPath = queryParser.extractGroupFieldPath(plan.parsedQuery());
                if (fieldPath != null) {
                    long count = readDocuments(plan, plan.innerJoin()).documents().stream()
                        .map(doc -> doc.get(fieldPath))
                        .filter(Objects::nonNull)
                        .distinct()
                        .count();
                    return (R) Long.valueOf(count);
                }
            }
            return (R) Long.valueOf(readDocuments(plan, plan.innerJoin()).documents().size());
        }

        DocumentRead read = readDocuments(plan, plan.projectedFields().isEmpty() || plan.innerJoin());
        if (read.documents().isEmpty()) {
            return null;
        }
        R result = mapDocument(read.documents().getFirst(), plan, resultType);
        if (result != null && plan.projectedFields().isEmpty()) {
            fetchRemainingJoins(List.of(result), plan, read.nativeJoinPaths());
        }
        return result;
    }

    /**
     * Finds all entities matching the criteria query.
     *
     * @param query the criteria query
     * @param <T> the entity type
     * @return list of matching entities
     */
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query) {
        return findAll(query, -1, -1);
    }

    /**
     * Finds all entities matching the criteria query with offset and limit.
     *
     * @param query the criteria query
     * @param offset the offset
     * @param limit the limit
     * @param <T> the entity type
     * @return list of matching entities
     */
    public <T> List<T> findAll(@NonNull CriteriaQuery<T> query, int offset, int limit) {
        CriteriaReadPlan plan = prepareRead(query, offset, limit);
        Class<T> type = (Class<T>) plan.resultType();
        DocumentRead read = readDocuments(plan, plan.projectedFields().isEmpty() || plan.innerJoin());
        List<T> results = mapDocuments(read.documents(), plan, type);
        if (plan.projectedFields().isEmpty()) {
            fetchRemainingJoins(results, plan, read.nativeJoinPaths());
        }
        return results;
    }

    private CriteriaReadPlan prepareRead(CriteriaQuery<?> query, int offset, int limit) {
        Class<?> entityType = getEntityType(query);
        RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
        Class<?> resultType = ((PersistentEntityQuery<?>) query).getResultType();
        AbstractPersistentEntityCriteriaQuery<?> persistentQuery = (AbstractPersistentEntityCriteriaQuery<?>) query;
        QueryBuilder.SelectQueryDefinition definition = persistentQuery.toSelectQueryDefinition();
        NitriteRuntimeFilter runtimeFilter = tryBuildRuntimeFilter(persistentQuery);
        Set<JoinPath> joinPaths;
        Filter filter;
        FindOptions options;
        Window window;
        List<String> projectedFields;
        Object parsedQuery = null;
        String queryString = null;

        if (runtimeFilter != null) {
            joinPaths = resolveJoinPaths(query, definition.getJoinPaths(), persistentEntity);
            boolean innerJoin = hasInnerJoin(joinPaths);
            filter = buildFilterFromRuntimeFilter(runtimeFilter, entityType);
            options = buildFindOptionsFromRuntimeFilter(
                runtimeFilter, persistentEntity, offset, limit, !innerJoin);
            window = runtimeFilterWindow(runtimeFilter, offset, limit);
            projectedFields = queryParser.extractProjectionFields(runtimeFilter.projection());
        } else {
            QueryResult queryResult = Objects.requireNonNull(
                persistentQuery.build(AnnotationMetadata.EMPTY_METADATA, queryBuilder),
                "Failed to build query for criteria query");
            queryString = queryResult.getQuery();
            joinPaths = resolveJoinPaths(query, queryResult.getJoinPaths(), persistentEntity);
            boolean innerJoin = hasInnerJoin(joinPaths);
            parsedQuery = parseQueryForFilter(queryString);
            filter = buildFilterFromQueryResult(queryResult, entityType, parsedQuery);
            options = buildFindOptions(persistentEntity, offset, limit, parsedQuery, !innerJoin);
            window = pipelineWindow(parsedQuery, offset, limit);
            projectedFields = queryParser.extractProjectionFields(parsedQuery);
        }

        return new CriteriaReadPlan(
            entityType,
            persistentEntity,
            resultType,
            filter,
            options,
            joinPaths,
            hasInnerJoin(joinPaths),
            window,
            projectedFields,
            selectionAliases(query),
            selectionJavaTypes(query),
            parsedQuery,
            queryString);
    }

    private DocumentRead readDocuments(CriteriaReadPlan plan, boolean fetchAssociations) {
        DocumentCursor cursor = collectionFactory.apply(plan.entityType()).find(plan.filter(), plan.options());
        NativeJoin nativeJoin = fetchAssociations ? resolveNativeJoin(plan) : null;
        RecordStream<Document> stream = cursor;
        Set<JoinPath> nativeJoinPaths = Set.of();

        if (nativeJoin != null) {
            stream = cursor.join(nativeJoin.foreignCollection().find(), nativeJoin.lookup());
            nativeJoinPaths = Set.of(nativeJoin.joinPath());
        } else if (!plan.innerJoin() && !plan.projectedFields().isEmpty()) {
            stream = cursor.project(nativeProjection(plan));
        }

        List<Document> documents = stream.toList();
        if (plan.innerJoin()) {
            documents = nativeJoin == null
                ? restrictToInnerJoins(documents, plan.joinPaths(), plan.entityType())
                : documents.stream()
                    .filter(document -> hasNativeJoinValue(document, nativeJoin.targetField()))
                    .toList();
            documents = applyWindow(documents, plan.window().offset(), plan.window().limit());
        }
        return new DocumentRead(documents, nativeJoinPaths);
    }

    private Document nativeProjection(CriteriaReadPlan plan) {
        Document projection = Document.createDocument();
        for (String field : plan.projectedFields()) {
            projection.put(entityMapper.normalizeFieldName(field, plan.persistentEntity()), null);
        }
        return projection;
    }

    private @Nullable NativeJoin resolveNativeJoin(CriteriaReadPlan plan) {
        if (plan.joinPaths().size() != 1 || plan.persistentEntity().hasCompositeIdentity()) {
            return null;
        }
        JoinPath joinPath = plan.joinPaths().iterator().next();
        if (joinPath.getAssociationPath().length != 1) {
            return null;
        }
        Association associationValue = joinPath.getAssociation();
        if (!(associationValue instanceof RuntimeAssociation<?> association)
            || association.getKind() != Relation.Kind.ONE_TO_MANY) {
            return null;
        }
        String mappedBy = association.getAnnotationMetadata()
            .stringValue(Relation.class, "mappedBy")
            .orElse(null);
        if (mappedBy == null) {
            return null;
        }
        RuntimePersistentEntity<?> associatedEntity = association.getAssociatedEntity();
        Class<?> associatedType = associatedEntity.getIntrospection().getBeanType();
        if (!entityMapper.getCompositeJoinColumns(associatedType, mappedBy).isEmpty()) {
            return null;
        }
        RuntimePersistentProperty<?> foreignProperty = associatedEntity.getPropertyByName(mappedBy);
        if (foreignProperty == null) {
            return null;
        }

        Lookup lookup = new Lookup();
        lookup.setLocalField(NitriteEntityMapper.ID_FIELD);
        lookup.setForeignField(foreignProperty.getPersistedName());
        lookup.setTargetField(association.getPersistedName());
        return new NativeJoin(
            joinPath,
            lookup,
            association.getPersistedName(),
            collectionFactory.apply(associatedType));
    }

    private static boolean hasNativeJoinValue(Document document, String targetField) {
        Object value = document.get(targetField);
        return value instanceof Collection<?> collection ? !collection.isEmpty() : value != null;
    }

    private <T> @Nullable T mapDocument(Document document, CriteriaReadPlan plan, Class<T> type) {
        if (plan.projectedFields().isEmpty()) {
            return entityMapper.fromDocument(document, type);
        }
        return projectionMapper.mapDocument(
            document,
            plan.projectedFields(),
            plan.selectionAliases(),
            plan.selectionJavaTypes(),
            plan.persistentEntity(),
            type,
            false);
    }

    private <T> List<T> mapDocuments(List<Document> documents, CriteriaReadPlan plan, Class<T> type) {
        return documents.stream()
            .map(document -> mapDocument(document, plan, type))
            .filter(Objects::nonNull)
            .toList();
    }

    private <T> void fetchRemainingJoins(
        List<T> results,
        CriteriaReadPlan plan,
        Set<JoinPath> nativeJoinPaths) {
        if (results.isEmpty() || plan.joinPaths().isEmpty()) {
            return;
        }
        Set<JoinPath> remaining = new LinkedHashSet<>(plan.joinPaths());
        remaining.removeAll(nativeJoinPaths);
        if (!remaining.isEmpty()) {
            joinFetcher.fetch(results, remaining, plan.entityType());
        }
    }

    private Set<JoinPath> resolveJoinPaths(
        CriteriaQuery<?> query,
        Collection<JoinPath> compiledJoinPaths,
        RuntimePersistentEntity<?> persistentEntity) {
        Set<JoinPath> explicitPaths = new LinkedHashSet<>();
        query.getRoots().forEach(root -> collectExplicitJoinPaths(
            root, persistentEntity, new ArrayList<>(), explicitPaths));
        // Keyed by path, explicit last: JoinPath equality ignores the join type, so a set holding
        // the compiled DEFAULT path would silently swallow the INNER one declared on the criteria
        // root and the restriction would never run.
        Map<String, JoinPath> merged = new LinkedHashMap<>();
        for (JoinPath compiled : compiledJoinPaths) {
            merged.put(compiled.getPath(), compiled);
        }
        for (JoinPath explicit : explicitPaths) {
            merged.put(explicit.getPath(), explicit);
        }
        return new LinkedHashSet<>(merged.values());
    }

    private void collectExplicitJoinPaths(
        From<?, ?> from,
        RuntimePersistentEntity<?> persistentEntity,
        List<Association> leadingAssociations,
        Set<JoinPath> joinPaths) {
        for (var join : from.getJoins()) {
            String associationName = join instanceof PersistentPropertyPath<?> propertyPath
                ? propertyPath.getProperty().getName()
                : join.getAttribute().getName();
            RuntimePersistentProperty<?> property = persistentEntity.getPropertyByName(associationName);
            if (!(property instanceof RuntimeAssociation<?> association)) {
                continue;
            }
            List<Association> associationPath = new ArrayList<>(leadingAssociations);
            associationPath.add(association);
            Association[] associations = associationPath.toArray(new Association[0]);
            String path = associationPath.stream().map(Association::getName).collect(Collectors.joining("."));
            // The declared join type is carried on the path, not discarded: an INNER join has to
            // restrict the result set, while the FETCH types a @Join annotation produces only load.
            joinPaths.add(new JoinPath(path, associations, resolveJoinType(join), null));
            collectExplicitJoinPaths(
                join, association.getAssociatedEntity(), associationPath, joinPaths);
        }
    }

    private static Join.Type resolveJoinType(From<?, ?> join) {
        // The criteria implementation keeps the declared type in its own enum and returns null from
        // the Jakarta getJoinType(), so read it here. A join declared without a type stays DEFAULT,
        // which loads the association without restricting the result set.
        if (join instanceof PersistentAssociationPath<?, ?> associationPath) {
            Join.Type declared = associationPath.getAssociationJoinType();
            if (declared != null) {
                return declared;
            }
        }
        return Join.Type.DEFAULT;
    }

    /**
     * Reads the aliases declared on the criteria selection, positionally. A selection item without
     * an alias contributes null, so the projected field name stays the tuple key for it.
     *
     * @param query The criteria query
     * @return The declared aliases, in selection order
     */
    private static List<String> selectionAliases(CriteriaQuery<?> query) {
        Selection<?> selection = query.getSelection();
        if (selection == null) {
            return List.of();
        }
        if (selection.isCompoundSelection()) {
            return selection.getCompoundSelectionItems().stream()
                .map(Selection::getAlias)
                .toList();
        }
        return Collections.singletonList(selection.getAlias());
    }

    private static List<Class<?>> selectionJavaTypes(CriteriaQuery<?> query) {
        Selection<?> selection = query.getSelection();
        if (selection == null) {
            return List.of();
        }
        if (selection.isCompoundSelection()) {
            return selection.getCompoundSelectionItems().stream()
                .<Class<?>>map(item -> (Class<?>) item.getJavaType())
                .toList();
        }
        return List.of(selection.getJavaType());
    }

    private static Window runtimeFilterWindow(NitriteRuntimeFilter runtimeFilter, int offset, int limit) {
        return new Window(
            offset > 0 ? offset : runtimeFilter.offset(),
            limit > 0 ? limit : runtimeFilter.limit());
    }

    private Window pipelineWindow(@Nullable Object parsedQuery, int offset, int limit) {
        int windowOffset = offset;
        int windowLimit = limit;
        List<?> stages = parsedQuery instanceof List<?> pipeline ? pipeline
            : parsedQuery instanceof Map<?, ?> map ? List.of(map) : List.of();
        for (Object stage : stages) {
            if (!(stage instanceof Map<?, ?> stageMap)) {
                continue;
            }
            if (windowOffset <= 0 && stageMap.get(NitriteQueryOperators.SKIP) instanceof Number skip) {
                windowOffset = skip.intValue();
            }
            if (windowLimit <= 0 && stageMap.get(NitriteQueryOperators.LIMIT) instanceof Number lim) {
                windowLimit = lim.intValue();
            }
        }
        return new Window(windowOffset, windowLimit);
    }

    private static boolean hasInnerJoin(Set<JoinPath> joinPaths) {
        return joinPaths.stream().anyMatch(joinPath -> joinPath.getJoinType() == Join.Type.INNER);
    }

    /**
     * Drops the documents that an INNER join excludes: a root whose joined association came back
     * empty did not match on the join. This runs on documents, before any offset/limit window or
     * count, because a root excluded by the join must not consume a slot in the page or be counted.
     * Only INNER paths restrict - the fetch types load the association without filtering.
     *
     * @param documents The documents matching the predicate
     * @param joinPaths The resolved join paths
     * @param entityType The root entity type
     * @return The retained documents
     */
    private List<Document> restrictToInnerJoins(List<Document> documents, Set<JoinPath> joinPaths, Class<?> entityType) {
        List<JoinPath> innerPaths = joinPaths.stream()
            .filter(joinPath -> joinPath.getJoinType() == Join.Type.INNER)
            .toList();
        if (innerPaths.isEmpty() || documents.isEmpty()) {
            return documents;
        }
        List<Document> retained = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Object root = entityMapper.fromDocument(document, entityType);
            if (root == null) {
                continue;
            }
            List<Object> single = List.of(root);
            joinFetcher.fetch(single, joinPaths, entityType);
            if (innerPaths.stream().allMatch(joinPath -> hasJoinedValues(root, joinPath))) {
                retained.add(document);
            }
        }
        return retained;
    }

    private static List<Document> applyWindow(List<Document> documents, int offset, int limit) {
        int from = Math.min(Math.max(offset, 0), documents.size());
        int to = limit < 0 ? documents.size() : Math.min(from + limit, documents.size());
        return documents.subList(from, to);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private boolean hasJoinedValues(Object root, JoinPath joinPath) {
        List<Object> current = List.of(root);
        for (Association association : joinPath.getAssociationPath()) {
            List<Object> next = new ArrayList<>();
            for (Object owner : current) {
                Object value = ((BeanProperty) ((RuntimeAssociation<?>) association).getProperty()).get(owner);
                if (value instanceof Collection<?> collection) {
                    next.addAll(collection);
                } else if (value != null) {
                    next.add(value);
                }
            }
            if (next.isEmpty()) {
                return false;
            }
            current = next;
        }
        return true;
    }

    /**
     * Updates all entities matching the criteria query.
     *
     * @param query the criteria update query
     * @return optional containing the number of updated entities
     */
    public Optional<Number> updateAll(@NonNull CriteriaUpdate<Number> query) {
        // For Nitrite, we need to fetch entities, apply updates, and save back
        try {
            // Build the query result to get the filter
            QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaUpdate<?>) query)
                    .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria update");

            Class<?> entityType = getEntityType(query);
            Filter filter = buildFilterFromQueryResult(queryResult, entityType);

            // Get the update values from the CriteriaUpdate
            RuntimePersistentEntity<?> persistentEntity = entityFactory.apply(entityType);
            Map<String, Object> updateValues = getUpdateValues(query, persistentEntity);

            NitriteCollection collection = collectionFactory.apply(entityType);
            // Each matching document is read, modified and written back as a whole, so the
            // sequence is held under one lock: Nitrite releases its own lock between the read and
            // the write, which would let a concurrent update be overwritten.
            return Optional.of(CollectionUpdateLock.withLock(collection.getName(), () -> {
                List<Document> docs = new ArrayList<>();
                for (Document doc : collection.find(filter)) {
                    docs.add(doc);
                }
                for (Document doc : docs) {
                    for (Map.Entry<String, Object> entry : updateValues.entrySet()) {
                        doc.put(entry.getKey(), entry.getValue());
                    }
                    Filter idFilter = NitriteFilterUtils.eq("_id", doc.get("_id"));
                    collection.update(idFilter, doc);
                }
                return docs.size();
            }));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to update entities by criteria", e);
        }
    }

    private Map<String, Object> getUpdateValues(CriteriaUpdate<?> query, RuntimePersistentEntity<?> persistentEntity) {
        if (query instanceof AbstractPersistentEntityCriteriaUpdate<?> update) {
            Map<String, Object> rawValues = update.getUpdateValues();
            Map<String, Object> resolvedValues = new LinkedHashMap<>();
            int index = 0;
            for (Map.Entry<String, Object> entry : rawValues.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof BindingParameter bindingParam) {
                    // Each bound assignment takes its own index; sharing index 0 would collapse the
                    // parameters of a multi-column update onto one another. Literal assignments are
                    // not bound and must not consume an index.
                    value = bindingParam.bind(BindingParameter.BindingContext.create().index(index)).getValue();
                    index++;
                }
                // Assignments are keyed by the Java property path; the stored document uses the
                // persisted path, which differs for @MappedProperty at any depth.
                resolvedValues.put(NitriteEntityMapper.persistedPath(entry.getKey(), persistentEntity), value);
            }
            return Collections.unmodifiableMap(resolvedValues);
        }
        return Map.of();
    }

    /**
     * Deletes all entities matching the criteria delete query.
     *
     * @param query the criteria delete query
     * @return optional containing the number of deleted entities
     */
    public Optional<Number> deleteAll(@NonNull CriteriaDelete<Number> query) {
        QueryResult queryResult = Objects.requireNonNull(((AbstractPersistentEntityCriteriaDelete<?>) query)
                .build(AnnotationMetadata.EMPTY_METADATA, queryBuilder), "Failed to build query for criteria delete");
        Class<?> type = getEntityType(query);
        Filter filter = buildFilterFromQueryResult(queryResult, type);
        long count = collectionFactory.apply(type).remove(filter).getAffectedCount();
        return Optional.of(count);
    }

    private Class<?> getEntityType(Object query) {
        if (query instanceof CriteriaUpdate<?> update) {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityCriteriaUpdate<?>) update).getPersistentEntity()).getIntrospection().getBeanType();
        } else if (query instanceof CriteriaDelete<?> delete) {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityCriteriaDelete<?>) delete).getPersistentEntity()).getIntrospection().getBeanType();
        } else {
            return ((RuntimePersistentEntity<?>) ((PersistentEntityQuery<?>) query).getPersistentEntity()).getIntrospection().getBeanType();
        }
    }

    /**
     * Attempts the fast path: build the filter/sort/projection directly from the Criteria
     * predicate tree, skipping the JSON serialize/reparse round trip {@link #buildFilterFromQueryResult}
     * needs. Returns {@code null} when the query needs the aggregation pipeline (joins, group,
     * count) — callers fall back to the {@link QueryResult}-based path for that case.
     */
    private @Nullable NitriteRuntimeFilter tryBuildRuntimeFilter(AbstractPersistentEntityCriteriaQuery<?> query) {
        if (!(queryBuilder instanceof NitriteQueryBuilder nitriteQueryBuilder)) {
            return null;
        }
        return nitriteQueryBuilder.buildRuntimeFilter(query.toSelectQueryDefinition());
    }

    private Filter buildFilterFromRuntimeFilter(NitriteRuntimeFilter runtimeFilter, Class<?> entityType) {
        Map<String, Object> filterMap = runtimeFilter.filter();
        if (filterMap.isEmpty()) {
            return Filter.ALL;
        }
        List<QueryParameterBinding> bindings = runtimeFilter.parameterBindings();
        Object[] params = new Object[bindings.size()];
        for (int i = 0; i < bindings.size(); i++) {
            params[i] = bindings.get(i).getValue();
        }
        return filterBuilder.buildFilterFromJson(entityFactory.apply(entityType), filterMap, params, Map.of());
    }

    private FindOptions buildFindOptionsFromRuntimeFilter(NitriteRuntimeFilter runtimeFilter, RuntimePersistentEntity<?> persistentEntity, int offset, int limit) {
        return buildFindOptionsFromRuntimeFilter(runtimeFilter, persistentEntity, offset, limit, true);
    }

    /**
     * @param applyWindow false to leave skip/limit off the options, so the caller can page the
     *                    result itself after an INNER join has restricted it
     */
    private FindOptions buildFindOptionsFromRuntimeFilter(NitriteRuntimeFilter runtimeFilter, RuntimePersistentEntity<?> persistentEntity, int offset, int limit, boolean applyWindow) {
        FindOptions options = new FindOptions();
        if (!applyWindow) {
            offset = -1;
            limit = -1;
        }
        if (offset > 0) {
            options.skip((long) offset);
        } else if (applyWindow && runtimeFilter.offset() > 0) {
            options.skip((long) runtimeFilter.offset());
        }
        if (limit > 0) {
            options.limit((long) limit);
        } else if (applyWindow && runtimeFilter.limit() != -1) {
            options.limit((long) runtimeFilter.limit());
        }
        for (Map.Entry<String, Object> entry : runtimeFilter.sort().entrySet()) {
            SortOrder order = ((Number) entry.getValue()).intValue() == 1 ? SortOrder.Ascending : SortOrder.Descending;
            options.thenOrderBy(entityMapper.normalizeFieldName(entry.getKey(), persistentEntity), order);
        }
        return options;
    }

    /**
     * Parses the query JSON once, guarded the same way {@link #buildFilterFromQueryResult} is,
     * so callers that need the filter plus sort/aggregate info can share a single parse instead
     * of each re-parsing the same string.
     */
    private @Nullable Object parseQueryForFilter(@Nullable String queryString) {
        if (queryString == null || queryString.trim().isEmpty() || "{}".equals(queryString.trim())) {
            return null;
        }
        try {
            return queryParser.parseJson(queryString);
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build Nitrite filter from criteria query: " + queryString, e);
        }
    }

    private Filter buildFilterFromQueryResult(QueryResult queryResult, Class<?> entityType) {
        return buildFilterFromQueryResult(queryResult, entityType, parseQueryForFilter(queryResult.getQuery()));
    }

    private Filter buildFilterFromQueryResult(QueryResult queryResult, Class<?> entityType, @Nullable Object parsedQuery) {
        if (parsedQuery == null) {
            return Filter.ALL;
        }
        try {
            Map<String, Object> filterMap = queryParser.extractFilterMap(parsedQuery);
            if (filterMap == null || filterMap.isEmpty()) {
                return Filter.ALL;
            }

            // Build parameter array from parameter bindings
            List<QueryParameterBinding> bindings = queryResult.getParameterBindings();
            Object[] params = new Object[bindings.size()];
            for (int i = 0; i < bindings.size(); i++) {
                params[i] = bindings.get(i).getValue();
            }

            return filterBuilder.buildFilterFromJson(
                    entityFactory.apply(entityType),
                    filterMap,
                    params,
                Map.of());
        } catch (Exception e) {
            throw new IllegalStateException(
                "Failed to build Nitrite filter from criteria query: " + queryResult.getQuery(), e);
        }
    }

    private @Nullable AggregateSelection extractAggregateSelection(@Nullable Object parsedQuery) {
        try {
            // Only a pipeline carries a $group stage; a single-stage query never does.
            if (!(parsedQuery instanceof List<?> pipeline)) {
                return null;
            }
            for (Object stage : pipeline) {
                if (!(stage instanceof Map<?, ?> stageMap) || !(stageMap.get(NitriteQueryOperators.GROUP) instanceof Map<?, ?> group)) {
                    continue;
                }
                if (group.get("_id") != null) {
                    // A grouped aggregate produces one value per key; only the ungrouped form can
                    // be answered by collapsing the whole result set to a single value.
                    return null;
                }
                for (Map.Entry<?, ?> entry : group.entrySet()) {
                    if (!(entry.getValue() instanceof Map<?, ?> operation)) {
                        continue;
                    }
                    for (String operator : List.of(NitriteQueryOperators.MAX, NitriteQueryOperators.MIN,
                        NitriteQueryOperators.SUM, NitriteQueryOperators.AVG)) {
                        Object field = operation.get(operator);
                        if (field instanceof String path && path.startsWith("$")) {
                            String function = Character.toUpperCase(operator.charAt(1)) + operator.substring(2);
                            return new AggregateSelection(function, path.substring(1));
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // The normal query path reports malformed criteria queries.
        }
        return null;
    }

    private FindOptions buildFindOptions(RuntimePersistentEntity<?> persistentEntity, int offset, int limit, @Nullable Object parsedQuery) {
        return buildFindOptions(persistentEntity, offset, limit, parsedQuery, true);
    }

    /**
     * @param applyWindow false to leave skip/limit off the options, so the caller can page the
     *                    result itself after an INNER join has restricted it
     */
    private FindOptions buildFindOptions(RuntimePersistentEntity<?> persistentEntity, int offset, int limit, @Nullable Object parsedQuery, boolean applyWindow) {
        FindOptions options = new FindOptions();
        if (!applyWindow) {
            offset = -1;
            limit = -1;
        }

        // Handle explicit offset/limit parameters
        if (offset > 0) {
            options.skip((long) offset);
        }
        if (limit > 0) {
            options.limit((long) limit);
        }

        if (parsedQuery != null) {
            try {
                List<Map<?, ?>> stages;
                if (parsedQuery instanceof Map<?, ?> m) {
                    stages = List.of(m);
                } else if (parsedQuery instanceof List<?> pipeline) {
                    stages = pipeline.stream()
                        .filter(Map.class::isInstance)
                        .<Map<?, ?>>map(s -> (Map<?, ?>) s)
                        .toList();
                } else {
                    return options;
                }

                for (Map<?, ?> stage : stages) {
                    if (stage.get(NitriteQueryOperators.SORT) instanceof Map<?, ?> sortMap) {
                        for (Map.Entry<?, ?> entry : sortMap.entrySet()) {
                            SortOrder order = ((Number) entry.getValue()).intValue() == 1 ? SortOrder.Ascending : SortOrder.Descending;
                            options.thenOrderBy(entityMapper.normalizeFieldName(entry.getKey().toString(), persistentEntity), order);
                        }
                    }
                    if (applyWindow && offset <= 0 && stage.get(NitriteQueryOperators.SKIP) instanceof Number skip) {
                        options.skip(skip.longValue());
                    }
                    if (applyWindow && limit <= 0 && stage.get(NitriteQueryOperators.LIMIT) instanceof Number lim) {
                        options.limit(lim.longValue());
                    }
                }

            } catch (Exception e) {
                // Ignore parse errors for options
            }
        }
        return options;
    }

    private record CriteriaReadPlan(
        Class<?> entityType,
        RuntimePersistentEntity<?> persistentEntity,
        Class<?> resultType,
        Filter filter,
        FindOptions options,
        Set<JoinPath> joinPaths,
        boolean innerJoin,
        Window window,
        List<String> projectedFields,
        List<String> selectionAliases,
        List<Class<?>> selectionJavaTypes,
        @Nullable Object parsedQuery,
        @Nullable String queryString) {
    }

    private record DocumentRead(List<Document> documents, Set<JoinPath> nativeJoinPaths) {
    }

    private record NativeJoin(
        JoinPath joinPath,
        Lookup lookup,
        String targetField,
        NitriteCollection foreignCollection) {
    }

    private record Window(int offset, int limit) {
    }

    private record AggregateSelection(String function, String field) {
    }
}
