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
import io.micronaut.data.jpa.repository.criteria.Specification;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Interface to allow execution of {@link Specification}s based on the JPA criteria API.
 *
 * Reactor version of {@link ReactiveStreamsJpaSpecificationExecutor}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.5.0
 */
public interface ReactorJpaSpecificationExecutor<T> extends ReactiveStreamsJpaSpecificationExecutor<T> {

    @Override
    Mono<T> findOne(@Nullable Specification<T> spec);

    @Override
    Flux<T> findAll(@Nullable Specification<T> spec);

    @Override
    Mono<Page<T>> findAll(@Nullable Specification<T> spec, Pageable pageable);

    @Override
    Flux<T> findAll(@Nullable Specification<T> spec, Sort sort);

    @Override
    Mono<Long> count(@Nullable Specification<T> spec);
}
