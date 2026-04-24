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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static io.micronaut.data.model.query.builder.sql.Dialect.SQLITE;
import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
@SQLiteDBProperties
class SQLiteEmbedded2Test {

    @Inject
    FooRepo repo;

    @Test
    void filledInnerCanBeRetrieved() {
        Foo saved = repo.save(new Foo(0, new Bar("1", "2")));
        Foo found = repo.findById(saved.getId()).orElseThrow();
        assertEquals(new Bar("1", "2"), found.getBar());
    }

    @Test
    void partiallyFilledInnerCanBeRetrieved() {
        Foo saved = repo.save(new Foo(0, new Bar("1", null)));
        Foo found = repo.findById(saved.getId()).orElseThrow();
        assertEquals(new Bar("1", null), found.getBar());
    }
}

@JdbcRepository(dialect = SQLITE)
interface FooRepo extends CrudRepository<Foo, Integer> {
}

@Embeddable
@Introspected
final class Bar {
    private String bar1;
    @Nullable
    private String bar2;

    Bar() {
    }

    Bar(String bar1, @Nullable String bar2) {
        this.bar1 = bar1;
        this.bar2 = bar2;
    }

    String getBar1() {
        return bar1;
    }

    void setBar1(String bar1) {
        this.bar1 = bar1;
    }

    @Nullable
    String getBar2() {
        return bar2;
    }

    void setBar2(@Nullable String bar2) {
        this.bar2 = bar2;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Bar bar)) {
            return false;
        }
        return Objects.equals(bar1, bar.bar1) && Objects.equals(bar2, bar.bar2);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bar1, bar2);
    }
}

@Entity
@Introspected
final class Foo {
    @Id
    private int id;

    @Nullable
    @Embedded
    private Bar bar;

    Foo() {
    }

    Foo(int id, @Nullable Bar bar) {
        this.id = id;
        this.bar = bar;
    }

    int getId() {
        return id;
    }

    void setId(int id) {
        this.id = id;
    }

    @Nullable
    Bar getBar() {
        return bar;
    }

    void setBar(@Nullable Bar bar) {
        this.bar = bar;
    }
}
