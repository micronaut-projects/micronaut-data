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
package io.micronaut.data.jdbc.h2;

import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.jpa.reactive.ReactorJpaSpecificationExecutor;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.repository.reactive.ReactorCrudRepository;
import io.micronaut.data.tck.entities.Person;
import reactor.core.publisher.Flux;

@JdbcRepository(dialect = Dialect.H2)
public interface H2ReactiveSpecificationPersonRepository extends ReactorCrudRepository<Person, Long>, ReactorJpaSpecificationExecutor<Person> {

    Flux<Person> findAllPaged(PredicateSpecification<Person> spec, Pageable pageable);
}
