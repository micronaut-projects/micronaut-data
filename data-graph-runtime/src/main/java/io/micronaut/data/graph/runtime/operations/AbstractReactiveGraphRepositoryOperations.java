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
import io.micronaut.data.exceptions.NonUniqueResultException;
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
import java.util.function.Supplier;

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
                .take(2)
                .collectList()
                .flatMap(records -> {
                    if (records.isEmpty()) {
                        return Mono.empty();
                    }
                    if (records.size() > 1 && !preparedQuery.getQueryLimit().isLimited()) {
                        return Mono.error(new NonUniqueResultException());
                    }
                    return Mono.justOrEmpty(operations.mapRecord(preparedQuery, records.get(0)));
                });
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
        // The nodes of a batch that are written but not yet linked, by the instance the save was called with
        final Map<Object, Supplier<Mono<Void>>> unlinked = new IdentityHashMap<>();
        private final ReactiveGraphSession session;
        private final OperationContext context;

        private Persister(ReactiveGraphSession session, AnnotationMetadata annotationMetadata, Class<?> repositoryType) {
            this.session = session;
            this.context = new OperationContext(annotationMetadata, repositoryType);
        }

        <T> Mono<T> save(RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            return link(instance).then(Mono.defer(() -> {
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
            }));
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
            if (distinct.size() < 2 || !operations.queryBuilder.supportsBatchNodeWrites() || operations.repeatsIdentifier(entity, distinct)) {
                return Flux.fromIterable(list).concatMap(instance -> save(entity, instance, insert));
            }
            return Flux.defer(() -> {
                // Recorded ahead of the entity events, which may replace an immutable instance that another node of
                // the batch relates to
                for (T instance : distinct) {
                    saved.put(instance, instance);
                }
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
         * Links the relationships of a node of a batch that is not yet linked, so that a node relating to it is
         * given the complete instance, as it is when it saves the related node itself.
         *
         * @param instance The instance the save was called with
         * @return A publisher that completes when the node is linked
         */
        Mono<Void> link(Object instance) {
            return Mono.defer(() -> {
                Supplier<Mono<Void>> link = unlinked.remove(instance);
                return link == null ? Mono.empty() : link.get();
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
            return Flux.fromIterable(targets).concatMap(target -> link(target).then(Mono.defer(() -> RelationshipTarget.cascadesPersist(association)
                ? isNew(entity, target)
                : Mono.just(operations.newWithoutLookup(entity, target))))).collectList().flatMap(news -> {
                List<E> results = new ArrayList<>(Collections.nCopies(targets.size(), (E) null));
                Map<E, List<Integer>> toInsert = new IdentityHashMap<>();
                Map<E, List<Integer>> toUpdate = new IdentityHashMap<>();
                for (int i = 0; i < targets.size(); i++) {
                    E target = targets.get(i);
                    Object existing = saved.get(target);
                    if (existing != null) {
                        results.set(i, (E) existing);
                    } else if (news.get(i)) {
                        if (!RelationshipTarget.cascadesPersist(association)) {
                            return Mono.error(RelationshipTarget.targetNotPersisted(association, owner, entity));
                        }
                        toInsert.computeIfAbsent(target, k -> new ArrayList<>()).add(i);
                    } else if (RelationshipTarget.cascadesUpdate(association)) {
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
            return Mono.defer(() -> {
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
            });
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
            return isNew(entity, instance).flatMap(insert -> save(entity, instance, insert));
        }

        private <T> Mono<Boolean> isNew(RuntimePersistentEntity<T> entity, T instance) {
            AbstractGraphRepositoryOperations.GraphQuery lookup = saved.containsKey(instance) ? null : operations.planFindExisting(entity, instance);
            if (lookup == null) {
                return Mono.just(operations.newWithoutLookup(entity, instance));
            }
            return run(lookup).map(result -> result.records().isEmpty());
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
        // The instances the save was called with, in the order of the entities: the entity events and a node write may
        // replace an immutable instance
        private final List<T> instances;

        NodesOperation(Persister persister, RuntimePersistentEntity<T> entity, List<T> instances, boolean insert) {
            super(persister.context, noCascade, operations.getConversionService(), operations.entityEventListener(), entity, instances, insert);
            this.persister = persister;
            this.instances = instances;
        }

        /**
         * One result per instance, as the blocking operation returns: a vetoed instance is returned as it is.
         */
        @Override
        public Flux<T> getEntities() {
            return entities.flatMapIterable(list -> list).map(d -> d.entity);
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
                Map<Data, T> originals = new IdentityHashMap<>();
                Map<AbstractGraphRepositoryOperations.NodeBatch, List<Data>> batches = new LinkedHashMap<>();
                Map<Data, @Nullable String> addLabels = new IdentityHashMap<>();
                for (int i = 0; i < list.size(); i++) {
                    Data d = list.get(i);
                    if (!d.vetoed) {
                        persister.saved.put(d.entity, d.entity);
                        originals.put(d, instances.get(i));
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
                            T original = Objects.requireNonNull(originals.get(d));
                            persister.written(original, d.entity);
                            persister.unlinked.put(original, () -> new RelationshipWriter<>(persister, persistentEntity, addLabels.get(d), created.contains(d)).write(d.entity)
                                .doOnNext(written -> {
                                    d.entity = written;
                                    persister.saved.put(original, written);
                                    persister.saved.put(written, written);
                                })
                                .then());
                        }
                    }))
                    .then(Flux.fromIterable(pending)
                        .concatMap(d -> persister.link(Objects.requireNonNull(originals.get(d))))
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
            List<Object> written = operations.populateRelationshipIds(target, target.written(items, savedTargets));
            List<RelationshipTarget.LinkRow> rows = target.linkRows(association, mapper, fromId, written, savedTargets);
            RuntimePersistentProperty<Object> identity = target.identity();
            // A single-valued association has one row
            Mono<Void> links = identity == null
                ? unlinkOthers(association, target.entity(), fromId, rows.get(0).toId()).then(runLinks(association, target.entity(), rows))
                : unlinkOtherRelationships(association, target.entity(), identity, fromId, rows.get(0).toId(), mapper.writeIdentity(target.associated(), written.get(0)))
                    .then(runIdentifiedLinks(association, target, rows, written));
            return links.thenReturn(written);
        }

        /**
         * Merges the relationships of the rows, which have no identifier: the rows that write the same properties
         * with one query, when the database supports it, and the others one at a time.
         */
        private Mono<Void> runLinks(RuntimeAssociation<T> association, RuntimePersistentEntity<?> target, List<RelationshipTarget.LinkRow> rows) {
            return Flux.fromIterable(RelationshipTarget.groupLinkRows(rows).entrySet())
                .concatMap(group -> {
                    List<RelationshipTarget.LinkRow> batch = group.getValue();
                    if (batch.size() > 1 && operations.queryBuilder.supportsBatchedLinks()) {
                        AbstractGraphRepositoryOperations.GraphQuery query = operations.planLinks(association, persistentEntity, target, batch, group.getKey());
                        return persister.run(query).doOnNext(result -> RelationshipTarget.checkLinked(association, result.records(), batch.size()));
                    }
                    return Flux.fromIterable(batch).concatMap(row ->
                        runLink(association, target, row.fromId(), row.toId(), row.properties(), null, RelationshipTarget.LinkMode.MERGE, null));
                })
                .then();
        }

        /**
         * Writes relationship entities with an identifier one at a time, as several of them may connect the same
         * nodes. One that was not saved, or that no longer connects these nodes, is a new one. Saving one that was
         * saved finds it by its identifier from either end, even if it is undirected. An instance held twice is one
         * relationship.
         *
         * @param written The written relationship entities, replaced in place with those holding their identifiers
         */
        private Mono<Void> runIdentifiedLinks(RuntimeAssociation<T> association,
                                              RelationshipTarget target,
                                              List<RelationshipTarget.LinkRow> rows,
                                              List<Object> written) {
            Map<Object, Object> linked = new IdentityHashMap<>();
            return Flux.fromIterable(rows).concatMap(row -> {
                Object item = written.get(row.position());
                Object done = linked.get(item);
                if (done != null) {
                    written.set(row.position(), done);
                    return Mono.empty();
                }
                Object savedId = mapper.writeIdentity(target.associated(), item);
                Mono<Object> updated = savedId == null
                    ? Mono.empty()
                    : runLink(association, target.entity(), row.fromId(), row.toId(), row.properties(), target.identity(), RelationshipTarget.LinkMode.UPDATE, savedId);
                return updated
                    .switchIfEmpty(Mono.defer(() -> runLink(association, target.entity(), row.fromId(), row.toId(), row.properties(), target.identity(), RelationshipTarget.LinkMode.CREATE, null)))
                    .doOnNext(id -> {
                        Object withId = target.withRelationshipId(mapper, item, id);
                        linked.put(item, withId);
                        written.set(row.position(), withId);
                    });
            }).then();
        }

        private Mono<Void> unlinkOthers(RuntimeAssociation<T> association, RuntimePersistentEntity<?> target, @Nullable Object fromId, @Nullable Object toId) {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.planUnlinkOthers(association, persistentEntity, target, fromId, toId, created);
            return query == null ? Mono.empty() : persister.run(query).then();
        }

        private Mono<Void> unlinkOtherRelationships(RuntimeAssociation<T> association,
                                                    RuntimePersistentEntity<?> target,
                                                    RuntimePersistentProperty<?> relationshipIdentity,
                                                    @Nullable Object fromId,
                                                    @Nullable Object toId,
                                                    @Nullable Object relationshipId) {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.planUnlinkOtherRelationships(association, persistentEntity, target, relationshipIdentity, fromId, toId, relationshipId, created);
            return query == null ? Mono.empty() : persister.run(query).then();
        }

        private Mono<Object> runLink(RuntimeAssociation<T> association,
                                     RuntimePersistentEntity<?> target,
                                     @Nullable Object fromId,
                                     @Nullable Object toId,
                                     Map<String, Object> properties,
                                     @Nullable RuntimePersistentProperty<?> relationshipIdentity,
                                     RelationshipTarget.LinkMode mode,
                                     @Nullable Object relationshipId) {
            AbstractGraphRepositoryOperations.GraphQuery query = operations.planLink(association, persistentEntity, target, fromId, toId, properties, relationshipIdentity, mode, relationshipId);
            return persister.run(query).flatMap(result -> Mono.justOrEmpty(RelationshipTarget.linkedRelationshipId(association, mode, result.records())));
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
            if (target instanceof Map<?, ?>) {
                // A node of a label no entity maps reads as its properties, and it is already linked
                return Mono.just(target);
            }
            RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) operations.getEntity(target.getClass());
            return persister.saveRelated(targetEntity, target).flatMap(savedTarget -> {
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
