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
package io.micronaut.data.repository.reactive;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * A repository that supports pagination.
 *
 * @param <E> The entity type
 * @param <ID> The ID type
 * @author Denis Stepanov
 * @since 3.4.0
 */
public interface ReactorPageableRepository<E, ID> extends ReactorCrudRepository<E, ID>, ReactiveStreamsPageableRepository<E, ID> {

    @Override
    @NonNull
    Flux<E> findAll(@NonNull Sort sort);

    @Override
    @NonNull
    Mono<Page<E>> findAll(@NonNull Pageable pageable);
}
