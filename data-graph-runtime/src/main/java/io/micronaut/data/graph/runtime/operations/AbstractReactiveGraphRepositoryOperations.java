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

    /**
     * Runs work that writes several statements, which must all apply or none, producing one result: within the
     * current reactive transaction, or in a transaction of its own that commits when the publisher completes and
     * rolls back when it fails. A database module whose statements auto-commit outside a transaction overrides
     * this method to begin one; the default runs the work like {@link #withSessionMono(AnnotationMetadata, Function)}.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The result
     * @since 5.3.0
     */
    protected <R> Mono<R> withTransactionMono(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Mono<R>> work) {
        return withSessionMono(annotationMetadata, work);
    }

    /**
     * Runs work that writes several statements, which must all apply or none, producing many results: within the
     * current reactive transaction, or in a transaction of its own that commits when the publisher completes and
     * rolls back when it fails. A database module whose statements auto-commit outside a transaction overrides
     * this method to begin one; the default runs the work like {@link #withSessionFlux(AnnotationMetadata, Function)}.
     *
     * @param annotationMetadata The annotation metadata of the operation
     * @param work               The work
     * @param <R>                The result type
     * @return The results
     * @since 5.3.0
     */
    protected <R> Flux<R> withTransactionFlux(AnnotationMetadata annotationMetadata, Function<ReactiveGraphSession, Flux<R>> work) {
        return withSessionFlux(annotationMetadata, work);
    }

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
        return withTransactionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(operations.getEntity(operation.getRootEntity()), operation.getEntity(), true));
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return withTransactionFlux(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(operations.getEntity(operation.getRootEntity()), operation, true);
        });
    }

    @Override
    public <T> Mono<T> update(UpdateOperation<T> operation) {
        return withTransactionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(operations.getEntity(operation.getRootEntity()), operation.getEntity(), false));
    }

    @Override
    public <T> Flux<T> updateAll(UpdateBatchOperation<T> operation) {
        return withTransactionFlux(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            return persister.saveAll(operations.getEntity(operation.getRootEntity()), operation, false);
        });
    }

    @Override
    public <T> Mono<Number> delete(DeleteOperation<T> operation) {
        return withTransactionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .delete(operations.getEntity(operation.getRootEntity()), operation.getEntity()));
    }

    @Override
    public <T> Mono<Number> deleteAll(DeleteBatchOperation<T> operation) {
        RuntimePersistentEntity<T> entity = operations.getEntity(operation.getRootEntity());
        if (operation.all()) {
            return withSessionMono(operation.getAnnotationMetadata(), session -> session.run(operations.deleteAllQuery(entity), Map.of())
                .map(AbstractGraphRepositoryOperations::updateCount));
        }
        return withTransactionMono(operation.getAnnotationMetadata(), session -> {
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
            if (list.size() < 2 || !operations.queryBuilder.supportsBatchNodeWrites()) {
                return Flux.fromIterable(list).concatMap(instance -> save(entity, instance, insert));
            }
            return Flux.defer(() -> {
                NodesOperation<T> operation = new NodesOperation<>(this, entity, list, insert);
                if (insert) {
                    operation.persist();
                } else {
                    operation.update();
                }
                return operation.getEntities();
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

        /**
         * Saves the nodes an association relates an owner to, as its cascade declares: nodes already saved by this
         * persister stand for themselves, new nodes are created together and existing nodes are updated together,
         * or linked as they are when the association does not cascade to them.
         *
         * @param association The association
         * @param owner       The owning entity
         * @param entity      The target entity
         * @param targets     The target instances
         * @param <E>         The target type
         * @return The saved instances, in the order of the targets
         */
        <E> Mono<List<E>> saveTargets(RuntimeAssociation<?> association, RuntimePersistentEntity<?> owner, RuntimePersistentEntity<E> entity, List<E> targets) {
            return Mono.defer(() -> {
                List<E> results = new ArrayList<>(Collections.nCopies(targets.size(), (E) null));
                Map<E, List<Integer>> toInsert = new IdentityHashMap<>();
                Map<E, List<Integer>> toUpdate = new IdentityHashMap<>();
                for (int i = 0; i < targets.size(); i++) {
                    E target = targets.get(i);
                    Object existing = saved.get(target);
                    if (existing != null) {
                        results.set(i, (E) existing);
                    } else if (operations.isNew(entity, target)) {
                        if (!AbstractGraphRepositoryOperations.cascadesPersist(association)) {
                            return Mono.error(AbstractGraphRepositoryOperations.targetNotPersisted(association, owner, entity));
                        }
                        toInsert.computeIfAbsent(target, k -> new ArrayList<>()).add(i);
                    } else if (AbstractGraphRepositoryOperations.cascadesUpdate(association)) {
                        toUpdate.computeIfAbsent(target, k -> new ArrayList<>()).add(i);
                    } else {
                        results.set(i, target);
                    }
                }
                return saveGroup(entity, toInsert, true, results)
                    .then(saveGroup(entity, toUpdate, false, results))
                    .thenReturn(results);
            });
        }

        private <E> Mono<Void> saveGroup(RuntimePersistentEntity<E> entity, Map<E, List<Integer>> group, boolean insert, List<E> results) {
            if (group.isEmpty()) {
                return Mono.empty();
            }
            return saveAll(entity, new ArrayList<>(group.keySet()), insert).collectList().doOnNext(savedTargets -> {
                int i = 0;
                for (List<Integer> positions : group.values()) {
                    E savedTarget = savedTargets.get(i++);
                    for (int position : positions) {
                        results.set(position, savedTarget);
                    }
                }
            }).then();
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
            AbstractGraphRepositoryOperations.NodeWrite write = operations.planNodeWrite(persistentEntity, instance, previousVersion);
            return persister.run(write.query()).flatMap(result -> {
                Object generatedId = operations.generatedId(persistentEntity, write, result.records());
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                T written = generatedId != null && identity != null
                    ? updateEntityId((BeanProperty<T, Object>) identity.getProperty(), instance, generatedId)
                    : instance;
                persister.written(original, written);
                return new RelationshipWriter<>(persister, persistentEntity).write(written);
            });
        }

    }

    /**
     * Writes nodes of one entity with one query per kind of write, then the labels and relationships of each node.
     *
     * @param <T> The entity type
     */
    private final class NodesOperation<T> extends AbstractReactiveEntitiesOperations<OperationContext, T, RuntimeException> {
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
                for (Data d : list) {
                    if (!d.vetoed) {
                        persister.saved.put(d.entity, d.entity);
                        originals.put(d, d.entity);
                        pending.add(d);
                        batches.computeIfAbsent(operations.planNodeBatch(persistentEntity, d.entity, previousVersions.get(d)), k -> new ArrayList<>()).add(d);
                    }
                }
                return Flux.fromIterable(batches.entrySet())
                    .concatMap(batch -> writeNodes(batch.getKey(), batch.getValue()))
                    .then(Flux.fromIterable(pending)
                        .concatMap(d -> {
                            T original = Objects.requireNonNull(originals.get(d));
                            persister.written(original, d.entity);
                            return new RelationshipWriter<>(persister, persistentEntity).write(d.entity).map(written -> {
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
                    AbstractGraphRepositoryOperations.NodeWrite write = operations.planNodeWrite(persistentEntity, d.entity, previousVersions.get(d));
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
                operations.checkBatchWritten(persistentEntity, batch, rows.size(), records.size());
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                if (batch.idPresent() || identity == null || !GraphEntities.isNativeId(identity)) {
                    return;
                }
                for (GraphRecord record : records) {
                    Data d = nodes.get(((Number) Objects.requireNonNull(record.get(CypherQueryBuilder.ROW_POSITION))).intValue());
                    d.entity = updateEntityId((BeanProperty<T, Object>) identity.getProperty(), d.entity, operations.batchGeneratedId(persistentEntity, record));
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

        RelationshipWriter(Persister persister, RuntimePersistentEntity<T> persistentEntity) {
            this.persister = persister;
            this.persistentEntity = persistentEntity;
        }

        Mono<T> write(T instance) {
            AbstractGraphRepositoryOperations.GraphQuery addLabels = operations.planAddLabels(persistentEntity, instance);
            Mono<T> labelled = addLabels == null ? Mono.just(instance) : persister.run(addLabels).thenReturn(instance);
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

        /**
         * Saves the nodes an association relates the entity to, then merges its relationships with one query per
         * set of relationship properties.
         */
        private Mono<T> writeRelationship(T instance, RuntimeAssociation<T> association) {
            BeanProperty<T, Object> beanProperty = association.getProperty();
            Object value = beanProperty.get(instance);
            List<Object> items = RelationshipTarget.relatedItems(value);
            if (items.isEmpty()) {
                return Mono.just(instance);
            }
            RelationshipTarget target = RelationshipTarget.of(association);
            Object fromId = mapper.writeIdentity(persistentEntity, instance);
            return Mono.fromCallable(() -> target.targets(items))
                .flatMap(targets -> persister.saveTargets(association, persistentEntity, target.entity(), targets))
                .flatMap(savedTargets -> linkTargets(association, target, fromId, items, savedTargets))
                .map(written -> operations.withWritten(beanProperty, instance, value instanceof Iterable<?>, items, written));
        }

        /**
         * Merges the relationships to the saved target nodes and returns the items as written, with the generated
         * relationship identifiers set.
         */
        private Mono<List<Object>> linkTargets(RuntimeAssociation<T> association,
                                               RelationshipTarget target,
                                               @Nullable Object fromId,
                                               List<Object> items,
                                               List<Object> savedTargets) {
            List<Object> written = target.written(items, savedTargets);
            List<AbstractGraphRepositoryOperations.LinkRow> rows = target.linkRows(association, mapper, fromId, written, savedTargets);
            return runLinks(association, target.entity(), rows, target.identity()).map(relationshipIds -> {
                target.applyRelationshipIds(mapper, written, relationshipIds);
                return written;
            });
        }

        /**
         * Merges the relationships of the rows: the rows that write the same properties with one query, when the
         * database supports it, and the others one at a time.
         *
         * @return The identifiers of the relationships by the position of their rows
         */
        private Mono<Map<Integer, Object>> runLinks(RuntimeAssociation<T> association,
                                                    RuntimePersistentEntity<?> target,
                                                    List<AbstractGraphRepositoryOperations.LinkRow> rows,
                                                    @Nullable RuntimePersistentProperty<?> relationshipIdentity) {
            Map<Integer, Object> ids = new LinkedHashMap<>();
            return Flux.fromIterable(AbstractGraphRepositoryOperations.groupLinkRows(rows).entrySet())
                .concatMap(group -> {
                    List<AbstractGraphRepositoryOperations.LinkRow> batch = group.getValue();
                    if (batch.size() > 1 && operations.queryBuilder.supportsBatchedLinks()) {
                        AbstractGraphRepositoryOperations.GraphQuery query = operations.planLinks(association, persistentEntity, target, batch, group.getKey(), relationshipIdentity);
                        return persister.run(query).doOnNext(result ->
                            ids.putAll(AbstractGraphRepositoryOperations.linkedRelationshipIds(association, result.records(), batch.size())));
                    }
                    return Flux.fromIterable(batch).concatMap(row -> {
                        AbstractGraphRepositoryOperations.GraphQuery query = operations.planLink(association, persistentEntity, target,
                            row.fromId(), row.toId(), row.properties(), relationshipIdentity);
                        return persister.run(query).doOnNext(result -> {
                            Object id = AbstractGraphRepositoryOperations.linkedRelationshipId(association, result.records());
                            if (id != null) {
                                ids.put(row.position(), id);
                            }
                        });
                    });
                })
                .then(Mono.fromSupplier(() -> ids));
        }

        private Mono<T> writeDynamicRelationships(T instance) {
            BeanProperty<Object, Object> property = mapper.dynamicRelationshipsProperty(persistentEntity);
            if (property == null || !(property.get(instance) instanceof Map<?, ?> relationships) || relationships.isEmpty()) {
                return Mono.just(instance);
            }
            Object fromId = mapper.writeIdentity(persistentEntity, instance);
            Map<String, List<Object>> targetsByType = new LinkedHashMap<>();
            relationships.forEach((type, value) -> targetsByType.put(String.valueOf(type), RelationshipTarget.relatedItems(value)));
            Map<String, List<Object>> written = new LinkedHashMap<>();
            return Flux.fromIterable(targetsByType.entrySet())
                .concatMap(entry -> Flux.fromIterable(entry.getValue())
                    .concatMap(target -> linkDynamic(entry.getKey(), fromId, target))
                    .collectList()
                    .doOnNext(savedTargets -> written.put(entry.getKey(), savedTargets)))
                .then(Mono.fromSupplier(() -> {
                    boolean changed = false;
                    for (Map.Entry<String, List<Object>> entry : targetsByType.entrySet()) {
                        changed |= !RelationshipTarget.sameItems(entry.getValue(), written.getOrDefault(entry.getKey(), List.of()));
                    }
                    if (!changed) {
                        return instance;
                    }
                    return AbstractGraphRepositoryOperations.withProperty((BeanProperty<T, Object>) (BeanProperty<?, ?>) property, instance, written);
                }));
        }

        private Mono<Object> linkDynamic(String type, @Nullable Object fromId, Object target) {
            RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) operations.getEntity(target.getClass());
            return persister.save(targetEntity, target, operations.isNew(targetEntity, target)).flatMap(savedTarget -> {
                AbstractGraphRepositoryOperations.GraphQuery query = operations.planDynamicLink(persistentEntity, targetEntity, type, fromId,
                    mapper.writeIdentity(targetEntity, savedTarget));
                return persister.run(query).thenReturn(savedTarget);
            });
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
