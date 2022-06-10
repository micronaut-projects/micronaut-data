/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.data.hibernate.reactive.operations;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import org.hibernate.reactive.common.spi.Implementor;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Helper to convert Hibernate Reactive's {@link CompletionStage} API to Reactor.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Internal
final class ReactiveHibernateHelper {

    private final Stage.SessionFactory sessionFactory;
    private final Scheduler contextScheduler;

    ReactiveHibernateHelper(Stage.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        contextScheduler = Schedulers.fromExecutor(((Implementor) sessionFactory).getContext());
    }

    public <T> Mono<T> find(Stage.Session session, Class<T> entityClass, Object id) {
        return monoFromCompletionStage(() -> session.find(entityClass, id));
    }

    public <T> Flux<T> list(Stage.Query<T> query) {
        return monoFromCompletionStage(query::getResultList).flatMapMany(Flux::fromIterable);
    }

    public <T> Mono<T> persist(Stage.Session session, T entity) {
        return monoFromCompletionStage(() -> session.persist(entity)).thenReturn(entity);
    }

    public <T> Mono<T> merge(Stage.Session session, T entity) {
        return monoFromCompletionStage(() -> session.merge(entity));
    }

    public <T> Mono<Void> remove(Stage.Session session, T entity) {
        return monoFromCompletionStage(() -> session.remove(entity));
    }

    public <T> Flux<T> mergeAll(Stage.Session session, Iterable<T> entities) {
        List<T> list = CollectionUtils.iterableToList(entities);
        return monoFromCompletionStage(() -> session.merge(list.toArray()))
                .thenReturn(list)
                .flatMapMany(Flux::fromIterable);
    }

    public <T> Flux<T> persistAll(Stage.Session session, Iterable<T> entities) {
        List<T> list = CollectionUtils.iterableToList(entities);
        return monoFromCompletionStage(() -> session.persist(list.toArray()))
                .thenReturn(list)
                .flatMapMany(Flux::fromIterable);
    }

    public <T> Mono<Number> removeAll(Stage.Session session, Iterable<T> entities) {
        List<T> list = CollectionUtils.iterableToList(entities);
        return monoFromCompletionStage(() -> session.remove(list.toArray()))
                .thenReturn(list.size());
    }

    public Mono<Void> flush(Stage.Session session) {
        return monoFromCompletionStage(session::flush);
    }

    public <T> Mono<T> singleResult(Stage.Query<T> query) {
        return monoFromCompletionStage(query::getSingleResult);
    }

    public Mono<Integer> executeUpdate(Stage.Query<Object> query) {
        return monoFromCompletionStage(query::executeUpdate);
    }

    public Mono<Stage.Session> openSession() {
        return monoFromCompletionStage(sessionFactory::openSession).subscribeOn(contextScheduler);
    }

    public Mono<Void> closeSession(Stage.Session session) {
        return monoFromCompletionStage(session::close);
    }

    public <T> Mono<T> withSession(Function<Stage.Session, Mono<T>> work) {
        return Mono.usingWhen(openSession(), work, this::closeSession);
    }

    public <T> Mono<T> withTransaction(Stage.Session session, Function<Stage.Transaction, Mono<T>> work) {
        return monoFromCompletionStage(() -> session.withTransaction(tx -> work.apply(tx).publishOn(contextScheduler).toFuture()));
    }

    public <T> Mono<T> withSessionAndTransaction(BiFunction<Stage.Session, Stage.Transaction, Mono<T>> work) {
        return withSession(session -> withTransaction(session, transaction -> work.apply(session, transaction).publishOn(contextScheduler)));
    }

    private <T> Mono<T> monoFromCompletionStage(Supplier<CompletionStage<T>> supplier) {
        return Mono.fromCompletionStage(supplier);
    }

}
