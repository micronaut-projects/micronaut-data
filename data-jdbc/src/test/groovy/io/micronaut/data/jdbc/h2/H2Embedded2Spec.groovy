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
package io.micronaut.data.jdbc.h2

import groovy.transform.EqualsAndHashCode
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import spock.lang.Shared
import spock.lang.Specification

import static io.micronaut.data.model.query.builder.sql.Dialect.H2

@MicronautTest
@H2DBProperties
class H2Embedded2Spec extends Specification {

    @Inject
    @Shared
    FooRepo repo

    def filledInnerCanBeRetrieved() {
        when:
            var saved = repo.save(new Foo(0, new Bar("1", "2")))
            var found = repo.findById(saved.id).get()
        then:
            found.bar == new Bar("1", "2")
    }

    void partiallyFilledInnerCanBeRetrieved() {
        when:
            var saved = repo.save(new Foo(0, new Bar("1", null)))
            var found = repo.findById(saved.id).get()
        then:
            found.bar == new Bar("1", null)
    }

}

@JdbcRepository(dialect = H2)
interface FooRepo extends CrudRepository<Foo, Integer> {
}

@EqualsAndHashCode
@Embeddable
@Introspected
class Bar {

    String bar1
    @Nullable
    String bar2

    Bar(String bar1, @Nullable String bar2) {
        this.bar1 = bar1
        this.bar2 = bar2
    }

}

@EqualsAndHashCode
@Entity
@Introspected
class Foo {

    @Id
    int id

    // If not marked as @Nullable object creation fails
    @Nullable
    @Embedded
    Bar bar

    Foo(int id, @Nullable Bar bar) {
        this.id = id
        this.bar = bar
    }
}
