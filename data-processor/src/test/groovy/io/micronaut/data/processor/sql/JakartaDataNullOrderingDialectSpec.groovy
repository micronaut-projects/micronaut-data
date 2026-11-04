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

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Unroll

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

/**
 * MySQL and SQL Server have no {@code NULLS FIRST} / {@code NULLS LAST} syntax, so a compile-time null ordering
 * must be emulated the same way as a runtime {@link io.micronaut.data.model.Sort}.
 */
class JakartaDataNullOrderingDialectSpec extends AbstractDataSpec {

    @Unroll
    void "test Jakarta Data @OrderBy nullOrdering on #dialect"() {
        given:
        def repository = buildRepository('test.RestaurantRepoNulls', """
import jakarta.data.Sort;
import jakarta.data.repository.Find;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Query;
import jakarta.data.repository.Repository;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.${dialect.name()})
@Repository
interface RestaurantRepoNulls {

    @Find
    @OrderBy(value = "name", nullOrdering = Sort.Nulls.LAST)
    List<Restaurant> findNullsLast();

    @Find
    @OrderBy(value = "name", descending = true, nullOrdering = Sort.Nulls.FIRST)
    List<Restaurant> findDescNullsFirst();

    @Find
    @OrderBy(value = "name", ignoreCase = true, nullOrdering = Sort.Nulls.LAST)
    List<Restaurant> findIgnoreCaseNullsLast();

    @Find
    @OrderBy("name")
    List<Restaurant> findUnspecified();

    @Query("WHERE name IS NOT NULL")
    @OrderBy(value = "name", nullOrdering = Sort.Nulls.LAST)
    List<Restaurant> queryNullsLast();
}
""")

        expect:
        getQuery(repository.getRequiredMethod("findNullsLast")).endsWith(" ORDER BY CASE WHEN ${name} IS NULL THEN 1 ELSE 0 END,${name} ASC")
        getQuery(repository.getRequiredMethod("findDescNullsFirst")).endsWith(" ORDER BY CASE WHEN ${name} IS NULL THEN 0 ELSE 1 END,${name} DESC")
        getQuery(repository.getRequiredMethod("findIgnoreCaseNullsLast")).endsWith(" ORDER BY CASE WHEN ${name} IS NULL THEN 1 ELSE 0 END,LOWER(${name}) ASC")
        getQuery(repository.getRequiredMethod("findUnspecified")).endsWith(" ORDER BY ${name} ASC")
        getQuery(repository.getRequiredMethod("queryNullsLast")).endsWith(" ORDER BY CASE WHEN ${name} IS NULL THEN 1 ELSE 0 END,${name} ASC")
        !getQuery(repository.getRequiredMethod("findNullsLast")).contains("NULLS")

        where:
        dialect            | name
        Dialect.MYSQL      | 'restaurant_.`name`'
        Dialect.SQL_SERVER | 'restaurant_.[name]'
    }
}
