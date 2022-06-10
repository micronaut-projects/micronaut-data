/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate.reactive.repository.jpa;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.hibernate.reactive.repository.jpa.intercept.ReactiveCountSpecificationInterceptor;
import io.micronaut.data.hibernate.reactive.repository.jpa.intercept.ReactiveFindAllSpecificationInterceptor;
import io.micronaut.data.hibernate.reactive.repository.jpa.intercept.ReactiveFindOneSpecificationInterceptor;
import io.micronaut.data.hibernate.reactive.repository.jpa.intercept.ReactiveFindPageSpecificationInterceptor;
import io.micronaut.data.intercept.annotation.DataMethod;
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import org.reactivestreams.Publisher;

/**
 * Interface to allow execution of {@link Specification}s based on the JPA criteria API.
 *
 * Reactive-streams version of {@link io.micronaut.data.jpa.repository.JpaSpecificationExecutor}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.5.0
 */
public interface ReactiveStreamsJpaSpecificationExecutor<T> {

    /**
     * Publishes a single entity matching the given {@link Specification}.
     *
     * @param spec can be {@literal null}.
     * @return never {@literal null}.
     */
    @DataMethod(interceptor = ReactiveFindOneSpecificationInterceptor.class)
    Publisher<T> findOne(@Nullable Specification<T> spec);

    /**
     * Publishes all entities matching the given {@link Specification}.
     *
     * @param spec can be {@literal null}.
     * @return never {@literal null}.
     */
    @DataMethod(interceptor = ReactiveFindAllSpecificationInterceptor.class)
    Publisher<T> findAll(@Nullable Specification<T> spec);

    /**
     * Publishes a {@link Page} of entities matching the given {@link Specification}.
     *
     * @param spec can be {@literal null}.
     * @param pageable must not be {@literal null}.
     * @return never {@literal null}.
     */
    @DataMethod(interceptor = ReactiveFindPageSpecificationInterceptor.class)
    Publisher<Page<T>> findAll(@Nullable Specification<T> spec, Pageable pageable);

    /**
     * Publishes all entities matching the given {@link Specification} and {@link Sort}.
     *
     * @param spec can be {@literal null}.
     * @param sort must not be {@literal null}.
     * @return never {@literal null}.
     */
    @DataMethod(interceptor = ReactiveFindAllSpecificationInterceptor.class)
    Publisher<T> findAll(@Nullable Specification<T> spec, Sort sort);

    /**
     * Publishes the number of instances that the given {@link Specification} will return.
     *
     * @param spec the {@link Specification} to count instances for. Can be {@literal null}.
     * @return the number of instances.
     */
    @DataMethod(interceptor = ReactiveCountSpecificationInterceptor.class)
    Publisher<Long> count(@Nullable Specification<T> spec);
}
