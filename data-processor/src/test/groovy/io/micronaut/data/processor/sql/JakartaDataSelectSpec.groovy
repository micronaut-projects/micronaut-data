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
package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JakartaDataSelectSpec extends AbstractDataSpec {

    void "test Jakarta Data @Select projection on a @Query method"() {
        given:
        def repository = buildRepository('test.RestaurantRepoSelect', """
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import jakarta.data.repository.Select;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoSelect extends CrudRepository<Restaurant, Long> {

    @Query("WHERE name > ?1")
    @Select("name")
    @OrderBy("name")
    Optional<String> nextName(String currentName);

    @Query("WHERE name > ?1")
    @Select("name")
    @Select("address.street")
    List<Object[]> nameAndStreet(String currentName);

    @Query("WHERE name > ?1")
    List<Restaurant> noProjection(String currentName);
}
""")

        when: "a single @Select projects that column and narrows the result type"
        def nextName = repository.getRequiredMethod("nextName", String)

        then:
        getQuery(nextName) == 'SELECT restaurant_.`name` FROM `restaurant` restaurant_ WHERE (restaurant_.`name` > ?) ORDER BY restaurant_.`name` ASC'

        when: "repeated @Select projects each column"
        def nameAndStreet = repository.getRequiredMethod("nameAndStreet", String)

        then:
        getQuery(nameAndStreet) == 'SELECT restaurant_.`name`,restaurant_.`street` FROM `restaurant` restaurant_ WHERE (restaurant_.`name` > ?)'

        when: "a query without @Select still selects the whole entity"
        def noProjection = repository.getRequiredMethod("noProjection", String)

        then:
        getQuery(noProjection).startsWith('SELECT restaurant_.`id`,restaurant_.`name`')
    }
}
