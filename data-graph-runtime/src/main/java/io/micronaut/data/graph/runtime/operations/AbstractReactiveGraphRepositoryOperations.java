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
import io.micronaut.data.runtime.operations.internal.AbstractReactiveEntityOperations;
import io.micronaut.data.runtime.operations.internal.OperationContext;
import io.micronaut.data.runtime.operations.internal.ReactiveCascadeOperations;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
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
        return withSessionMono(operation.getAnnotationMetadata(), session -> new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType())
            .save(operations.getEntity(operation.getRootEntity()), operation.getEntity(), true));
    }

    @Override
    public <T> Flux<T> persistAll(InsertBatchOperation<T> operation) {
        return withSessionFlux(operation.getAnnotationMetadata(), session -> {
            Persister persister = new Persister(session, operation.getAnnotationMetadata(), operation.getRepositoryType());
            RuntimePersistentEntity<T> entity = operations.getEntity(operation.getRootEntity());
            return Flux.fromIterable(operation).concatMap(instance -> persister.save(entity, instance, true));
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
            RuntimePersistentEntity<T> entity = operations.getEntity(operation.getRootEntity());
            return Flux.fromIterable(operation).concatMap(instance -> persister.save(entity, instance, false));
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
        private final ReactiveGraphSession session;
        private final OperationContext context;
        private final Map<Object, Object> saved = new IdentityHashMap<>();

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

        NodeOperation(Persister persister, RuntimePersistentEntity<T> entity, T instance, boolean insert) {
            super(persister.context, noCascade, operations.getConversionService(), operations.entityEventListener(), entity, instance, insert);
            this.persister = persister;
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
            // Graph writes set every property, so previous values are not needed
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
            AbstractGraphRepositoryOperations.NodeWrite write = operations.planNodeWrite(persistentEntity, instance);
            return persister.run(write.query()).flatMap(result -> {
                Object generatedId = operations.generatedId(persistentEntity, write, result.records());
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                T written = generatedId != null && identity != null
                    ? updateEntityId((BeanProperty<T, Object>) identity.getProperty(), instance, generatedId)
                    : instance;
                AbstractGraphRepositoryOperations.GraphQuery addLabels = operations.planAddLabels(persistentEntity, written);
                Mono<T> labelled = addLabels == null ? Mono.just(written) : persister.run(addLabels).thenReturn(written);
                return labelled.flatMap(this::writeRelationships).flatMap(this::writeDynamicRelationships);
            });
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
                    Object newValue = many ? conversionService.convertRequired(written, beanProperty.asArgument()) : written.get(0);
                    return AbstractGraphRepositoryOperations.withProperty(beanProperty, instance, newValue);
                });
        }

        private Mono<Object> link(RuntimeAssociation<T> association, @Nullable Object fromId, Object item) {
            RuntimePersistentEntity<Object> associated = (RuntimePersistentEntity<Object>) association.getAssociatedEntity();
            if (GraphEntities.isRelationshipEntity(associated)) {
                RuntimeAssociation<Object> targetAssociation = (RuntimeAssociation<Object>) AbstractGraphRepositoryOperations.targetNode(associated);
                BeanProperty<Object, Object> targetProperty = targetAssociation.getProperty();
                Object target = targetProperty.get(item);
                if (target == null) {
                    return Mono.error(new DataAccessException("Relationship [" + associated.getSimpleName() + "] has no @TargetNode value"));
                }
                RuntimePersistentEntity<Object> targetEntity = (RuntimePersistentEntity<Object>) targetAssociation.getAssociatedEntity();
                return persister.save(targetEntity, target, operations.isNew(targetEntity, target)).flatMap(savedTarget -> {
                    Object relationship = savedTarget != target ? AbstractGraphRepositoryOperations.withProperty(targetProperty, item, savedTarget) : item;
                    RuntimePersistentProperty<Object> relationshipIdentity = associated.getIdentity();
                    AbstractGraphRepositoryOperations.GraphQuery query = operations.planLink(association, persistentEntity, targetEntity, fromId,
                        mapper.writeIdentity(targetEntity, savedTarget), mapper.writeProperties(associated, relationship), relationshipIdentity);
                    return persister.run(query).map(result -> {
                        Object relationshipId = AbstractGraphRepositoryOperations.linkedRelationshipId(association, result.records());
                        if (relationshipIdentity != null && GraphEntities.isNativeId(relationshipIdentity) && relationshipId != null) {
                            BeanProperty<Object, Object> idProperty = relationshipIdentity.getProperty();
                            return AbstractGraphRepositoryOperations.withProperty(idProperty, relationship, mapper.readValue(relationshipIdentity, relationshipId));
                        }
                        return relationship;
                    });
                });
            }
            return persister.save(associated, item, operations.isNew(associated, item)).flatMap(savedTarget -> {
                AbstractGraphRepositoryOperations.GraphQuery query = operations.planLink(association, persistentEntity, associated, fromId,
                    mapper.writeIdentity(associated, savedTarget), Map.of(), null);
                return persister.run(query).map(result -> {
                    AbstractGraphRepositoryOperations.linkedRelationshipId(association, result.records());
                    return savedTarget;
                });
            });
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
            return persister.save(targetEntity, target, operations.isNew(targetEntity, target)).flatMap(savedTarget -> {
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
            // Deletes match the node by its identifier only
        }

        @Override
        protected void execute() {
            data = data.flatMap(d -> {
                if (d.vetoed) {
                    return Mono.just(d);
                }
                return persister.run(operations.planDelete(persistentEntity, d.entity)).map(result -> {
                    d.rowsUpdated = AbstractGraphRepositoryOperations.updateCount(result).longValue();
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
