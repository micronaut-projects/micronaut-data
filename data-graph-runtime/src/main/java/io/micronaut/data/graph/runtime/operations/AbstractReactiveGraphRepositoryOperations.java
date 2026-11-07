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
package io.micronaut.data.graph.runtime.operations;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.graph.annotation.Direction;
import io.micronaut.data.graph.model.GraphEntities;
import io.micronaut.data.graph.runtime.GraphEntityMapper;
import io.micronaut.data.graph.runtime.GraphRecord;
import io.micronaut.data.graph.runtime.GraphResult;
import io.micronaut.data.graph.runtime.ReactiveGraphSession;
import io.micronaut.data.graph.model.query.builder.CypherQueryBuilder;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.runtime.DeleteBatchOperation;
import io.micronaut.data.model.runtime.DeleteOperation;
import io.micronaut.data.model.runtime.InsertBatchOperation;
import io.micronaut.data.model.runtime.InsertOperation;
import io.micronaut.data.model.runtime.PagedQuery;
import io.micronaut.data.model.runtime.PreparedQuery;
import io.micronaut.data.model.runtime.RuntimeAssociation;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.data.model.runtime.UpdateBatchOperation;
import io.micronaut.data.model.runtime.UpdateOperation;
import io.micronaut.data.runtime.convert.DataConversionService;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntitiesOperations;
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.ReactiveCascadeOperations;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Reactive repository operations shared by graph databases that speak Cypher and have a reactive driver. It plans
 * queries and maps results with the blocking operations of the same database, and runs them through a
 * {@link ReactiveGraphSession} that a database module provides.
 *
 * @author graemerocher
 * @since 5.3.0
 */
public abstract class AbstractReactiveGraphRepositoryOperations implements ReactiveGraphRepositoryOperations {

    /**
     * The blocking operations that plan queries and map results.
     */
    protected final AbstractGraphRepositoryOperations operations;

    private final GraphEntityMapper mapper;

    private final ReactiveCascadeOperations<OperationContext> noCascade;

    /**
     * @param operations The blocking operations of the same database
     */
    protected AbstractReactiveGraphRepositoryOperations(AbstractGraphRepositoryOperations operations) {
        this.operations = operations;
        this.mapper = operations.mapper;
        this.noCascade = new ReactiveCascadeOperations<>(operations.getConversionService(), new RelationshipsWrittenByNode());
    }

    /**
     * Checks that no schemaless property of the instances has the name of a declared property, for writes that
     * store the schemaless properties in a column of their own, which reads would hide behind the declared column.
     *
     * @param type      The entity type
     * @param instances The instances to write
     * @param <T>       The entity type
     */
    protected final <T> void checkDynamicAttributes(Class<T> type, Iterable<T> instances) {
        RuntimePersistentEntity<T> entity = mapper.getEntity(type);
        for (T instance : instances) {
            mapper.checkDynamicAttributes(entity, instance);
        }
    }

    /**
     * Runs work that produces one result within the current reactive connection or a new one.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The result
     */
    protected abstract <R> Mono<R> withSessionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work);

    /**
     * Runs work that produces many results within the current reactive connection or a new one.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The results
     */
    protected abstract <R> Flux<R> withSessionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work);

    @Override
    public DataConversionService getConversionService() {
        return operations.getConversionService();
    }

    // Queries

    @Override
    public <T> Mono<T> findOne(Class<T> type, Object id) {
        RuntimePersistentEntity<T> entity = operations.getEntity(type);
        AnnotationMetadata annotationMetadata = entity.getAnnotationMetadata();
        return withSessionMono(annotationMetadata, session -> {
            String query = operations.findByIdQuery(entity);
            Object encodedId = mapper.writeValue(entity.getIdentity(), id);
            return session.stream(query, Map.of(CypherQueryBuilder.ID_PARAMETER, encodedId))
                .next()
                .map(row -> operations.readEntity(entity, row, annotationMetadata));
        });
    }

    @Override
    public <T, R> Mono<R> findOne(PreparedQuery<T, R> preparedQuery) {
        return withSessionMono(preparedQuery.getAnnotationMetadata(), session -> {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.prepare(preparedQuery, true);
            return session.stream(query.query(), query.parameters())
                .next()
                .mapNotNull(row -> operations.mapRecord(preparedQuery, row));
        });
    }

    @Override
    public <T> Mono<T> findOptional(Class<T> type, Object id) {
        return findOne(type, id);
    }

    @Override
    public <T, R> Mono<R> findOptional(PreparedQuery<T, R> preparedQuery) {
        return findOne(preparedQuery);
    }

    @Override
    public <T> Mono<Boolean> exists(PreparedQuery<T, Boolean> preparedQuery) {
        return withSessionMono(preparedQuery.getAnnotationMetadata(), session -> {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.prepare(preparedQuery, false);
            return session.stream(query.query(), query.parameters())
                .next()
                .map(first -> first.size() == 0 || !(first.get(0) instanceof Boolean b) || b)
                .defaultIfEmpty(false);
        });
    }

    @Override
    public <T> Flux<T> findAll(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = operations.getEntity(pagedQuery.getRootEntity());
        AnnotationMetadata annotationMetadata = pagedQuery.getAnnotationMetadata();
        return withSessionFlux(annotationMetadata, session -> {
            AbstractGraphRepositoryOperations.checkOffsetPageable(pagedQuery.getPageable());
            StringBuilder query = new StringBuilder(operations.queryBuilder.buildFindAllQuery(entity));
            operations.appendSortAndLimit(query, entity, pagedQuery.getSort(), pagedQuery.getQueryLimit());
            return session.stream(query.toString(), Map.of())
                .map(row -> operations.readEntity(entity, row, annotationMetadata));
        });
    }

    @Override
    public <T> Mono<Long> count(PagedQuery<T> pagedQuery) {
        RuntimePersistentEntity<T> entity = operations.getEntity(pagedQuery.getRootEntity());
        return withSessionMono(pagedQuery.getAnnotationMetadata(), session -> session
            .stream(operations.queryBuilder.buildCountAllQuery(entity), Map.of())
            .next()
            .map(row -> row.size() > 0 && row.get(0) instanceof Number number ? number.longValue() : 0L)
            .defaultIfEmpty(0L));
    }

    @Override
    public <T, R> Flux<R> findAll(PreparedQuery<T, R> preparedQuery) {
        return withSessionFlux(preparedQuery.getAnnotationMetadata(), session -> {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.prepare(preparedQuery, true);
            return session.stream(query.query(), query.parameters())
                .mapNotNull(row -> operations.mapRecord(preparedQuery, row));
        });
    }

    @Override
    public <R> Flux<R> execute(PreparedQuery<?, R> preparedQuery) {
        return findAll(preparedQuery);
    }

    @Override
    public <R> Mono<Page<R>> findPage(PagedQuery<R> pagedQuery) {
        Pageable pageable = pagedQuery.getPageable();
        return findAll(pagedQuery).collectList().flatMap(content -> {
            if (pageable.requestTotal()) {
                return count(pagedQuery).map(total -> Page.of(content, pageable, total));
            }
            return Mono.just(Page.of(content, pageable, null));
        });
    }

    @Override
    public Mono<Number> executeUpdate(PreparedQuery<?, Number> preparedQuery) {
        return withSessionMono(preparedQuery.getAnnotationMetadata(), session -> {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.prepare(preparedQuery, false);
            return session.run(query.query(), query.parameters()).map(AbstractGraphRepositoryOperations::updateCount);
        });
    }

    @Override
    public Mono<Number> executeDelete(PreparedQuery<?, Number> preparedQuery) {
        return executeUpdate(preparedQuery);
    }

    @Override
    public Flux<GraphRecord> executeQuery(String query, Map<String, Object> parameters) {
        String statement = operations.finishedQuery(query);
        return withSessionFlux(AnnotationMetadata.EMPTY_METADATA, session -> session.stream(statement, operations.encodeParameters(parameters)));
    }

    @Override
    public <T> Flux<T> executeQuery(String query, Map<String, Object> parameters, Class<T> type) {
        return executeQuery(query, parameters).mapNotNull(row -> type.cast(
            operations.mapValue(row, row.size() == 0 ? null : row.get(0), type, type, AnnotationMetadata.EMPTY_METADATA)
        ));
    }

    // Writes

    @Override
    public <T> Mono<T> persist(InsertOperation<T> operation) {
        return withSessionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(operations.getEntity(operation.getRootEntity()), operation.getEntity(), true));
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return withSessionFlux(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(operations.getEntity(operation.getRootEntity()), operation, true);
        });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return withSessionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(operations.getEntity(operation.getRootEntity()), operation.getEntity(), false));
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return withSessionFlux(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(operations.getEntity(operation.getRootEntity()), operation, false);
        });
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return withSessionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .delete(operations.getEntity(operation.getRootEntity()), operation.getEntity()));
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = operations.getEntity(operation.getRootEntity());
        return withSessionMono(operation.getAnnotationMetadata(), session -> {
            if (operation.all()) {
                return session.run(operations.deleteAllQuery(entity), Map.of())
                    .map(AbstractGraphRepositoryOperations::updateCount);
            }
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return Flux.fromIterable(operation)
                .concatMap(instance -> persister.delete(entity, instance))
                .reduce(0L, (total, deleted) -> total + deleted.longValue())
                .map(Number.class::cast);
        });
    }

    /**
     * Writes entities and their relationships one query after another within one session.
     */
    private final class Persister {
        final Map<Object, Object> saved = new IdentityHashMap<>();
        private final ReactiveGraphSession session;
        private final OperationContext context;

        private Persister(ReactiveGraphSession session, AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
            this.session = session;
            this.context = new OperationContext(annotationMetadata, repositoryType);
        }

        <T> Mono<T> save(RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            return Mono.defer(() -> {
                Object existing = saved.get(instance);
                if (existing != null) {
                    return Mono.just((T) existing);
                }
                saved.put(instance, instance);
                NodeOperation<T> operation = new NodeOperation<>(this, entity, instance, insert);
                if (insert) {
                    operation.persist();
                } else {
                    operation.update();
                }
                return operation.getEntity()
                    .defaultIfEmpty(instance)
                    .doOnNext(result -> saved.put(instance, result));
            });
        }

        <T> Flux<T> saveAll(RuntimePersistentEntity<T> entity, Iterable<T> instances, boolean insert) {
            List<T> list = new ArrayList<>();
            instances.forEach(list::add);
            // An instance passed twice is written once, as save() does for an instance it meets again
            Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            List<T> distinct = new ArrayList<>(list.size());
            for (T instance : list) {
                if (seen.add(instance)) {
                    distinct.add(instance);
                }
            }
            if (distinct.size() < 2 || !operations.queryBuilder.supportsBatchNodeWrites()) {
                return Flux.fromIterable(list).concatMap(instance -> save(entity, instance, insert));
            }
            return Flux.defer(() -> {
                NodesOperation<T> operation = new NodesOperation<>(this, entity, distinct, insert);
                if (insert) {
                    operation.persist();
                } else {
                    operation.update();
                }
                return operation.getEntities().collectList().flatMapMany(written -> {
                    Map<T, T> results = new IdentityHashMap<>();
                    for (int i = 0; i < distinct.size(); i++) {
                        results.put(distinct.get(i), written.get(i));
                    }
                    List<T> ordered = new ArrayList<>(list.size());
                    for (T instance : list) {
                        ordered.add(results.get(instance));
                    }
                    return Flux.fromIterable(ordered);
                });
            });
        }

        /**
         * Records the instance that now stands for a node whose write completed, so that a related
         * node that links back to it while its relationships are still being written sees the
         * instance carrying the generated identifier rather than the original. The two differ for
         * immutable entities such as records.
         *
         * @param instance The instance the save was called with
         * @param written  The instance after the node was written
         */
        void written(Object instance, Object written) {
            saved.put(instance, written);
        }

        <T> Mono<Number> delete(RuntimePersistentEntity<T> entity, T instance) {
            return Mono.defer(() -> {
                DeleteNodeOperation<T> operation = new DeleteNodeOperation<>(this, entity, instance);
                operation.delete();
                return operation.getRowsUpdated();
            });
        }

        Mono<GraphResult> run(AbstractGraphRepositoryOperations.GraphQuery query) {
            return session.run(query.query(), query.parameters());
        }

        /**
         * Saves a related node, as an insert when it is written for the first time. An identifier the application
         * assigned says nothing, as a new node has one too, so the database is asked.
         *
         * @param entity   The entity
         * @param instance The related node
         * @param <T>      The entity type
         * @return The saved node
         */
        <T> Mono<T> saveRelated(RuntimePersistentEntity<T> entity, T instance) {
            AbstractGraphRepositoryOperations.GraphQuery lookup = saved.containsKey(instance) ? null : operations.planFindExisting(entity, instance);
            if (lookup == null) {
                return save(entity, instance, operations.newWithoutLookup(entity, instance));
            }
            return run(lookup).flatMap(result -> save(entity, instance, result.records().isEmpty()));
        }
    }

    /**
     * Writes one node, then its labels and relationships.
     *
     * @param <T> The entity type
     */
    private final class NodeOperation<T> extends AbstractReactiveEntityOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;
        private final T original;
        private @Nullable Object previousVersion;
        private boolean created;

        NodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            super(persister.context, noCascade, operations.getConversionService(), operations.entityEventListener(), entity, instance, insert);
            this.persister = persister;
            this.original = instance;
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Relationships are written after the node, see execute()
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Relationships are written after the node, see execute()
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            // Graph writes set every property, so only the version an update checks is needed
            if (!insert) {
                data = data.map(d -> {
                    previousVersion = operations.currentVersion(persistentEntity, d.entity);
                    return d;
                });
            }
        }

        @Override
        protected void execute() {
            data = data.flatMap(d -> {
                if (d.vetoed) {
                    return Mono.just(d);
                }
                return writeNode(d.entity).map(written -> {
                    d.entity = written;
                    return d;
                });
            });
        }

        private Mono<T> writeNode(T instance) {
            AbstractGraphRepositoryOperations.NodeWrite write = operations.planNodeWrite(persistentEntity, instance, insert, previousVersion);
            return persister.run(write.query()).flatMap(result -> {
                Object generatedId = operations.generatedId(persistentEntity, write, result.records());
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                created = generatedId != null;
                T written = generatedId != null && identity != null
                    ? updateEntityId((BeanProperty<T, Object>) identity.getProperty(), instance, generatedId)
                    : instance;
                persister.written(original, written);
                return new RelationshipWriter<>(persister, persistentEntity, write.addLabels(), created).write(written);
            });
        }

    }

    /**
     * Writes nodes of one entity with one query per kind of write, then the labels and relationships of each node.
     *
     * @param <T> The entity type
     */
    private final class NodesOperation<T> extends AbstractReactiveEntitiesOperations<OperationContext, T, RuntimeException> {
        private final Set<Data> created = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Persister persister;
        private final Map<Object, Object> previousVersions = new IdentityHashMap<>();

        NodesOperation(Persister persister, RuntimePersistentEntity<T> entity, Iterable<T> instances, boolean insert) {
            super(persister.context, noCascade, operations.getConversionService(), operations.entityEventListener(), entity, instances, insert);
            this.persister = persister;
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Relationships are written after the nodes, see execute()
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Relationships are written after the nodes, see execute()
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            if (!insert) {
                entities = entities.map(list -> {
                    for (Data d : list) {
                        Object version = operations.currentVersion(persistentEntity, d.entity);
                        if (version != null) {
                            previousVersions.put(d, version);
                        }
                    }
                    return list;
                });
            }
        }

        @Override
        protected void execute() {
            entities = entities.flatMap(list -> {
                List<Data> pending = new ArrayList<>();
                // The instances the save was called with: a node write may replace an immutable instance
                Map<Data, T> originals = new IdentityHashMap<>();
                Map<AbstractGraphRepositoryOperations.NodeBatch, List<Data>> batches = new LinkedHashMap<>();
                Map<Data, @Nullable String> addLabels = new IdentityHashMap<>();
                for (Data d : list) {
                    if (!d.vetoed) {
                        persister.saved.put(d.entity, d.entity);
                        originals.put(d, d.entity);
                        pending.add(d);
                        // Checked ahead of the writes, so that a label that cannot be stored leaves the nodes as they were
                        addLabels.put(d, operations.addLabelsQuery(persistentEntity, d.entity));
                        batches.computeIfAbsent(operations.planNodeBatch(persistentEntity, d.entity, insert, previousVersions.get(d)), k -> new ArrayList<>()).add(d);
                    }
                }
                return Flux.fromIterable(batches.entrySet())
                    .concatMap(batch -> writeNodes(batch.getKey(), batch.getValue()))
                    .then(Mono.fromRunnable(() -> {
                        // A node whose relationships are linked first may point to a node later in the batch, which
                        // must be found with the identifier it was just given: an immutable instance was replaced
                        for (Data d : pending) {
                            persister.written(Objects.requireNonNull(originals.get(d)), d.entity);
                        }
                    }))
                    .then(Flux.fromIterable(pending)
                        .concatMap(d -> {
                            T original = Objects.requireNonNull(originals.get(d));
                            return new RelationshipWriter<>(persister, persistentEntity, addLabels.get(d), created.contains(d)).write(d.entity).map(written -> {
                                d.entity = written;
                                persister.saved.put(original, written);
                                persister.saved.put(written, written);
                                return d;
                            });
                        })
                        .then(Mono.just(list)));
            });
        }

        private Mono<Void> writeNodes(AbstractGraphRepositoryOperations.NodeBatch batch, List<Data> nodes) {
            if (!batch.batched()) {
                return Flux.fromIterable(nodes).concatMap(d -> {
                    AbstractGraphRepositoryOperations.NodeWrite write = operations.planNodeWrite(persistentEntity, d.entity, insert, previousVersions.get(d));
                    return persister.run(write.query()).doOnNext(result -> operations.generatedId(persistentEntity, write, result.records()));
                }).then();
            }
            List<Map<String, @Nullable Object>> rows = new ArrayList<>(nodes.size());
            for (int i = 0; i < nodes.size(); i++) {
                Data d = nodes.get(i);
                rows.add(operations.nodeRow(persistentEntity, d.entity, i, batch.versioned() ? previousVersions.get(d) : null));
            }
            AbstractGraphRepositoryOperations.GraphQuery query = new AbstractGraphRepositoryOperations.GraphQuery(
                operations.batchQuery(persistentEntity, batch), Map.of(CypherQueryBuilder.ROWS_PARAMETER, rows));
            return persister.run(query).doOnNext(result -> {
                List<GraphRecord> records = result.records();
                operations.checkBatchWritten(persistentEntity, batch, rows, records.size());
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                if (batch.idPresent() || identity == null || !GraphEntities.isNativeId(identity)) {
                    return;
                }
                for (GraphRecord record : records) {
                    Data d = nodes.get(((Number) Objects.requireNonNull(record.get(CypherQueryBuilder.ROW_POSITION))).intValue());
                    d.entity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), d.entity, operations.batchGeneratedId(persistentEntity, record));
                    created.add(d);
                }
            }).then();
        }
    }

    /**
     * Adds the dynamic labels of a written node and links its relationships, saving the related nodes.
     *
     * @param <T> The entity type
     */
    private final class RelationshipWriter<T> {
        private final Persister persister;
        private final RuntimePersistentEntity<T> persistentEntity;
        private final @Nullable String addLabels;
        private final boolean created;

        RelationshipWriter(Persister persister, RuntimePersistentEntity<T> persistentEntity, @Nullable String addLabels, boolean created) {
            this.persister = persister;
            this.persistentEntity = persistentEntity;
            this.addLabels = addLabels;
            this.created = created;
        }

        Mono<T> write(T instance) {
            AbstractGraphRepositoryOperations.GraphQuery labels = operations.planAddLabels(persistentEntity, addLabels, instance);
            Mono<T> labelled = labels == null ? Mono.just(instance) : persister.run(labels).thenReturn(instance);
            return labelled.flatMap(this::writeRelationships).flatMap(this::writeDynamicRelationships);
        }

        private Mono<T> writeRelationships(T instance) {
            Mono<T> result = Mono.just(instance);
            for (RuntimePersistentProperty<T> property : persistentEntity.getPersistentProperties()) {
                if (property instanceof RuntimeAssociation<T> association && GraphEntities.isRelationship(association)) {
                    result = result.flatMap(current -> writeRelationship(current, association));
                }
            }
            return result;
        }

        private Mono<T> writeRelationship(T instance, RuntimeAssociation<T> association) {
            BeanProperty<T, Object> beanProperty = association.getProperty();
            Object value = beanProperty.get(instance);
            if (value == null) {
                return Mono.just(instance);
            }
            boolean many = value instanceof Iterable<?>;
            List<Object> items = new ArrayList<>();
            if (many) {
                for (Object item : (Iterable<?>) value) {
                    if (item != null) {
                        items.add(item);
                    }
                }
            } else {
                items.add(value);
            }
            Object fromId = mapper.writeIdentity(persistentEntity, instance);
            return Flux.fromIterable(items)
                .concatMap(item -> link(association, fromId, item))
                .collectList()
                .map(written -> {
                    if (sameItems(items, written)) {
                        return instance;
                    }
                    Object newValue = many ? operations.getConversionService().convertRequired(written, beanProperty.asArgument()) : written.get(0);
                    return AbstractGraphRepositoryOperations.withProperty(beanProperty, instance, newValue);
                });
        }

        private Mono<Object> link(RuntimeAssociation<T> association, @Nullable Object fromId, Object item) {
            RuntimePersistentEntity<Object> associated = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
            if (GraphEntities.isRelationshipEntity(associated)) {
                return linkRelationshipEntity(association, associated, fromId, item);
            }
            return persister.saveRelated(associated, item).flatMap(savedTarget -> {
                Object toId = mapper.writeIdentity(associated, savedTarget);
                return unlinkOthers(association, associated, fromId, toId)
                    .then(runLink(association, associated, fromId, toId, Map.of(), null, AbstractGraphRepositoryOperations.LinkMode.MERGE, null))
                    .thenReturn(savedTarget);
            });
        }

        private Mono<Object> linkRelationshipEntity(RuntimeAssociation<T> association, RuntimePersistentEntity<Object> associated, @Nullable Object fromId, Object item) {
            RuntimeAssociation<Object> targetAssociation = (RuntimeAssociation<Object>) AbstractGraphRepositoryOperations.targetNode(associated);
            BeanProperty<Object, Object> targetProperty = targetAssociation.getProperty();
            Object target = targetProperty.get(item);
            if (target == null) {
                return Mono.error(new DataAccessException("Relationship [" + associated.getSimpleName() + "] has no @TargetNode value"));
            }
            RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) targetAssociation.getAssociatedEntity();
            return persister.saveRelated(targetEntity, target).flatMap(savedTarget -> {
                Object relationship = savedTarget != target ? AbstractGraphRepositoryOperations.withProperty(targetProperty, item, savedTarget) : item;
                Map<String, Object> properties = mapper.writeProperties(associated, relationship);
                RuntimePersistentProperty<Object> relationshipIdentity = associated.getIdentity();
                Object toId = mapper.writeIdentity(targetEntity, savedTarget);
                boolean nativeId = relationshipIdentity != null && GraphEntities.isNativeId(relationshipIdentity);
                Object savedId = nativeId ? mapper.writeIdentity(associated, relationship) : null;
                Mono<Object> updated = savedId == null
                    ? Mono.empty()
                    : runLink(association, targetEntity, fromId, toId, properties, relationshipIdentity, AbstractGraphRepositoryOperations.LinkMode.UPDATE, savedId);
                // A relationship that was not saved, or that no longer connects these nodes, is a new one. An
                // undirected one is matched in either direction, so saving it from its other end finds it
                boolean create = nativeId && GraphEntities.direction(association) != Direction.UNDIRECTED;
                AbstractGraphRepositoryOperations.LinkMode mode = create ? AbstractGraphRepositoryOperations.LinkMode.CREATE : AbstractGraphRepositoryOperations.LinkMode.MERGE;
                return unlinkOthers(association, targetEntity, fromId, toId)
                    .then(updated.switchIfEmpty(Mono.defer(() -> runLink(association, targetEntity, fromId, toId, properties, relationshipIdentity, mode, null))))
                    .map(relationshipId -> {
                        if (nativeId) {
                            BeanProperty<Object, Object> idProperty = relationshipIdentity.getProperty();
                            return AbstractGraphRepositoryOperations.withProperty(idProperty, relationship, mapper.readValue(relationshipIdentity, relationshipId));
                        }
                        return relationship;
                    })
                    .defaultIfEmpty(relationship);
            });
        }

        private Mono<Void> unlinkOthers(RuntimeAssociation<T> association, RuntimePersistentEntity<?> target, @Nullable Object fromId, @Nullable Object toId) {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.planUnlinkOthers(association, persistentEntity, target, fromId, toId, created);
            return query == null ? Mono.empty() : persister.run(query).then();
        }

        private Mono<Object> runLink(RuntimeAssociation<T> association,
                                     RuntimePersistentEntity<?> target,
                                     @Nullable Object fromId,
                                     @Nullable Object toId,
                                     Map<String, Object> properties,
                                     @Nullable RuntimePersistentProperty<?> relationshipIdentity,
                                     AbstractGraphRepositoryOperations.LinkMode mode,
                                     @Nullable Object relationshipId) {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.planLink(association, persistentEntity, target, fromId, toId, properties, relationshipIdentity, mode, relationshipId);
            return persister.run(query).flatMap(result -> Mono.justOrEmpty(AbstractGraphRepositoryOperations.linkedRelationshipId(association, mode, result.records())));
        }

        private Mono<T> writeDynamicRelationships(T instance) {
            BeanProperty<Object, Object> property = mapper.dynamicRelationshipsProperty(persistentEntity);
            if (property == null || !(property.get(instance) instanceof Map<?, ?> relationships) || relationships.isEmpty()) {
                return Mono.just(instance);
            }
            Object fromId = mapper.writeIdentity(persistentEntity, instance);
            Map<String, List<Object>> targetsByType = new LinkedHashMap<>();
            relationships.forEach((type, value) -> targetsByType.put(String.valueOf(type), targets(value)));
            Map<String, List<Object>> written = new LinkedHashMap<>();
            return Flux.fromIterable(targetsByType.entrySet())
                .concatMap(entry -> Flux.fromIterable(entry.getValue())
                    .concatMap(target -> linkDynamic(entry.getKey(), fromId, target))
                    .collectList()
                    .doOnNext(savedTargets -> written.put(entry.getKey(), savedTargets)))
                .then(Mono.fromSupplier(() -> {
                    boolean changed = false;
                    for (Map.Entry<String, List<Object>> entry : targetsByType.entrySet()) {
                        changed |= !sameItems(entry.getValue(), written.getOrDefault(entry.getKey(), List.of()));
                    }
                    if (!changed) {
                        return instance;
                    }
                    return AbstractGraphRepositoryOperations.withProperty((BeanProperty<T, Object>) (BeanProperty<?, ?>) property, instance, written);
                }));
        }

        private static List<Object> targets(@Nullable Object value) {
            List<Object> targets = new ArrayList<>();
            if (value instanceof Iterable<?> iterable) {
                for (Object target : iterable) {
                    if (target != null) {
                        targets.add(target);
                    }
                }
            } else if (value != null) {
                targets.add(value);
            }
            return targets;
        }

        private Mono<Object> linkDynamic(String type, @Nullable Object fromId, Object target) {
            RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) operations.getEntity(target.getClass());
            return persister.saveRelated(targetEntity, target).flatMap(savedTarget -> {
                AbstractGraphRepositoryOperations.GraphQuery query = operations.planDynamicLink(persistentEntity, targetEntity, type, fromId,
                    mapper.writeIdentity(targetEntity, savedTarget));
                return persister.run(query).thenReturn(savedTarget);
            });
        }

        private static boolean sameItems(List<Object> original, List<Object> written) {
            if (original.size() != written.size()) {
                return false;
            }
            for (int i = 0; i < original.size(); i++) {
                if (original.get(i) != written.get(i)) {
                    return false;
                }
            }
            return true;
        }
    }

    /**
     * Deletes one node with its relationships.
     *
     * @param <T> The entity type
     */
    private final class DeleteNodeOperation<T> extends AbstractReactiveEntityOperations<OperationContext, T, RuntimeException> {
        private final Persister persister;

        private @Nullable Object previousVersion;

        DeleteNodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance) {
            super(persister.context, noCascade, operations.getConversionService(), operations.entityEventListener(), entity, instance, false);
            this.persister = persister;
        }

        @Override
        protected void cascadePre(Relation.Cascade cascadeType) {
            // Deleting a node detaches its relationships, there is nothing to cascade
        }

        @Override
        protected void cascadePost(Relation.Cascade cascadeType) {
            // Deleting a node detaches its relationships, there is nothing to cascade
        }

        @Override
        protected void collectAutoPopulatedPreviousValues() {
            data = data.map(d -> {
                previousVersion = operations.currentVersion(persistentEntity, d.entity);
                return d;
            });
        }

        @Override
        protected void execute() {
            data = data.flatMap(d -> {
                if (d.vetoed) {
                    return Mono.just(d);
                }
                return persister.run(operations.planDelete(persistentEntity, d.entity, previousVersion)).map(result -> {
                    d.rowsUpdated = AbstractGraphRepositoryOperations.updateCount(result).longValue();
                    operations.checkVersionedDelete(persistentEntity, previousVersion, d.rowsUpdated);
                    return d;
                });
            });
        }
    }

    /**
     * Relationships are written by {@link NodeOperation} after the node, so the cascade operations are never used.
     */
    private static final class RelationshipsWrittenByNode implements ReactiveCascadeOperations.ReactiveCascadeOperationsHelper<OperationContext> {

        @Override
        public <T> Mono<T> persistOne(OperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public <T> Flux<T> persistBatch(OperationContext ctx, Iterable<T> entityValues, RuntimePersistentEntity<T> persistentEntity, Predicate<T> predicate) {
            return Flux.error(new UnsupportedOperationException());
        }

        @Override
        public <T> Mono<T> updateOne(OperationContext ctx, T entityValue, RuntimePersistentEntity<T> persistentEntity) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> persistManyAssociation(OperationContext ctx, RuntimeAssociation runtimeAssociation, Object parentEntityValue,
                                                 RuntimePersistentEntity<Object> parentPersistentEntity, Object childEntityValue,
                                                 RuntimePersistentEntity<Object> childPersistentEntity) {
            return Mono.error(new UnsupportedOperationException());
        }

        @Override
        public Mono<Void> persistManyAssociationBatch(OperationContext ctx, RuntimeAssociation runtimeAssociation, Object parentEntityValue,
                                                      RuntimePersistentEntity<Object> parentPersistentEntity, Iterable<Object> childEntityValues,
                                                      RuntimePersistentEntity<Object> childPersistentEntity, Predicate<Object> veto) {
            return Mono.error(new UnsupportedOperationException());
        }
    }
}
