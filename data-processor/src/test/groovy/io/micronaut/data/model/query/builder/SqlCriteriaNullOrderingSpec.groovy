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
package io.micronaut.data.model.query.builder

import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.tck.entities.Restaurant
import jakarta.persistence.criteria.Nulls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class SqlCriteriaNullOrderingSpec extends Specification {

    @Shared
    RuntimeCriteriaBuilder builder = new RuntimeCriteriaBuilder()

    @Unroll
    void "test criteria order with null precedence on #dialect"() {
        given:
        def query = builder.createQuery(Restaurant)
        def root = query.from(Restaurant)
        query.select(root.get("name"))
        query.orderBy(
            builder.asc(root.get("name"), Nulls.LAST),
            builder.sort(root.get("name"), false, true, Nulls.FIRST),
            builder.desc(builder.length(root.get("name")), Nulls.LAST),
            builder.asc(root.get("id"))
        )

        when:
        def result = query.build(new SqlQueryBuilder(dialect))

        then:
        result.query.endsWith(" ORDER BY $orderBy")
        result.query.count("(") == result.query.count(")")

        where:
        dialect            | orderBy
        Dialect.MYSQL      | 'CASE WHEN restaurant_.`name` IS NULL THEN 1 ELSE 0 END,restaurant_.`name` ASC,' +
                             'CASE WHEN restaurant_.`name` IS NULL THEN 0 ELSE 1 END,LOWER(restaurant_.`name`) DESC,' +
                             'CASE WHEN LENGTH(restaurant_.`name`) IS NULL THEN 1 ELSE 0 END,LENGTH(restaurant_.`name`) DESC,' +
                             'restaurant_.`id` ASC'
        Dialect.SQL_SERVER | 'CASE WHEN restaurant_.[name] IS NULL THEN 1 ELSE 0 END,restaurant_.[name] ASC,' +
                             'CASE WHEN restaurant_.[name] IS NULL THEN 0 ELSE 1 END,LOWER(restaurant_.[name]) DESC,' +
                             'CASE WHEN LEN(restaurant_.[name]) IS NULL THEN 1 ELSE 0 END,LEN(restaurant_.[name]) DESC,' +
                             'restaurant_.[id] ASC'
        Dialect.POSTGRES   | 'restaurant_."name" ASC NULLS LAST,' +
                             'LOWER(restaurant_."name") DESC NULLS FIRST,' +
                             'LENGTH(restaurant_."name") DESC NULLS LAST,' +
                             'restaurant_."id" ASC'
        Dialect.H2         | 'restaurant_.`name` ASC NULLS LAST,' +
                             'LOWER(restaurant_.`name`) DESC NULLS FIRST,' +
                             'LENGTH(restaurant_.`name`) DESC NULLS LAST,' +
                             'restaurant_.`id` ASC'
    }

    @Unroll
    void "test criteria order with a parameterized expression and null precedence on #dialect binds the parameter twice"() {
        given:
        def query = builder.createQuery(Restaurant)
        def root = query.from(Restaurant)
        query.select(root.get("name"))
        query.orderBy(builder.asc(builder.concat(root.get("name"), builder.parameter(String)), Nulls.FIRST))

        when:
        def result = query.build(new SqlQueryBuilder(dialect))

        then:
        result.query.endsWith(" ORDER BY $orderBy")
        result.parameterBindings.size() == 2

        where:
        dialect            | orderBy
        Dialect.MYSQL      | 'CASE WHEN CONCAT(restaurant_.`name`,?) IS NULL THEN 0 ELSE 1 END,CONCAT(restaurant_.`name`,?) ASC'
        Dialect.SQL_SERVER | 'CASE WHEN CONCAT(restaurant_.[name],?) IS NULL THEN 0 ELSE 1 END,CONCAT(restaurant_.[name],?) ASC'
    }
}
