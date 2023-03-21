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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.operations.reactive.ReactorReactiveRepositoryOperations;
import io.micronaut.transaction.reactive.ReactorReactiveTransactionOperations;
import org.hibernate.reactive.stage.Stage;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.persistence.criteria.CriteriaBuilder;
import java.util.function.Function;

/**
 * Hibernate reactive repository operations.
 *
 * This interface is experimental might change in the future.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Experimental
public interface HibernateReactorRepositoryOperations extends ReactorReactiveRepositoryOperations,
        ReactorReactiveTransactionOperations<Stage.Session> {

    /**
     * Execute with a new or existing session.
     *
     * @param work The work
     * @param <T>  The published item
     * @return The produced result publisher
     */
    @NonNull
    <T> Mono<T> withSession(@NonNull Function<Stage.Session, Mono<T>> work);

    /**
     * Execute with a new or existing session.
     *
     * @param work The work
     * @param <T>  The published item
     * @return The produced result publisher
     */
    @NonNull
    <T> Flux<T> withSessionFlux(@NonNull Function<Stage.Session, Flux<T>> work);

    /**
     * Persist and flush the entity.
     *
     * @param entity The entity
     * @return The operation publisher
     */
    @NonNull
    Mono<Void> persistAndFlush(@NonNull Object entity);

    /**
     * Flush the current session.
     * @return The operation publisher
     */
    @NonNull
    Mono<Void> flush();

    /**
     * @return {@link CriteriaBuilder} that can be used to work with criteria.
     */
    @NonNull
    CriteriaBuilder getCriteriaBuilder();

}
