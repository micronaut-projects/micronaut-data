/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.tck.entities.City
import spock.lang.Unroll

class JoinPathSpec extends AbstractDataSpec {

    @Unroll
    void "test JPA projection across nested property path for #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
interface MyInterface extends GenericRepository<City, Long> {

    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value() == query

        where:
        method                           | returnType | arguments     | query
        "findByCountryRegionCountryName" | "City"     | "String name" | "SELECT city_ FROM $City.name AS city_ WHERE (city_.countryRegion.country.name = :p1)"
        "findByCountryRegionName"        | "City"     | "String name" | "SELECT city_ FROM $City.name AS city_ WHERE (city_.countryRegion.name = :p1)"
    }


    @Unroll
    void "test SQL projection across one-to-many with mappedBy #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<Author, Long> {

    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().endsWith(suffix)

        where:
        method             | returnType | arguments      | suffix
        "findByBooksTitle" | "Author"   | "String title" | "JOIN book author_books_ ON author_.id=author_books_.author_id WHERE (author_books_.\"title\" = ?)"
    }


    @Unroll
    void "test SQL projection across one-to-many with join table #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<CountryRegion, Long> {

    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().contains(joinTableExpression)

        where:
        method             | returnType      | arguments     | joinTableExpression
        "findByCitiesName" | "CountryRegion" | "String name" | "INNER JOIN countryRegionCity country_region_cities_countryRegionCity_ ON country_region_.id=country_region_cities_countryRegionCity_.countryRegionId  INNER JOIN T_CITY country_region_cities_ ON country_region_cities_countryRegionCity_.cityId=country_region_cities_.id"
    }

    @Unroll
    void "test SQL join with custom alias #method"() {
        given:
        String joinAnn = ""
        if (joinPaths) {
            joinAnn = joinPaths.collect() {
                """
@Join(value="$it", alias="region_")
"""
            }.join('')
        }
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<City, Long> {

    $joinAnn
    City $method(String name);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)
        String queryStr = ann.value()

        expect:
        queryStr.contains('region_.id AS region_id,region_.name AS region_name')
        queryStr.endsWith(whereClause)
        queryStr.contains('INNER JOIN')

        where:
        method                           | joinPaths                 | whereClause
        "findByCountryRegionName"        | ["countryRegion"]         | "WHERE (region_.\"name\" = ?)"

    }

    @Unroll
    void "test SQL join WHERE statement for association projection #method"() {
        given:
        String joinAnn = ""
        if (joinPaths) {
            joinAnn = joinPaths.collect() {
                """
@Join("$it")
"""
            }.join('')
        }
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<City, Long> {

    $joinAnn
    City $method(String name);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)
        String columnNames = columns(City, 'city_')
        String queryStr = ann.value()

        expect:
        queryStr.startsWith("SELECT ${columnNames}")
        queryStr.endsWith(whereClause)
        queryStr.contains('INNER JOIN')

        where:
        method                           | joinPaths                 | whereClause
        "findByCountryRegionName"        | ["countryRegion.country"] | "WHERE (city_country_region_.\"name\" = ?)"
        "findByCountryRegionCountryName" | []                        | "WHERE (city_country_region_country_.\"name\" = ?)"
        "findByCountryRegionName"        | []                        | "WHERE (city_country_region_.\"name\" = ?)"
        "findByCountryRegionName"        | ["countryRegion"]         | "WHERE (city_country_region_.\"name\" = ?)"

    }

    @Unroll
    void "test SQL join JOIN statement for association projection #method"() {
        given:
        String joinAnn = ""
        if (joinPaths) {
            joinAnn = joinPaths.collect() {
                """
@Join("$it")
"""
            }.join('')
        }
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;
@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<City, Long> {

    $joinAnn
    City $method(String name);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)
        String columnNames = columns(City, 'city_')
        String queryStr = ann.value()

        expect:
        queryStr.startsWith("SELECT ${columnNames}")
        queryStr.contains('INNER JOIN')
        queryStr.count("INNER JOIN") == joinCount
        queryStr.contains(joinExpression)

        where:
        method                           | joinCount | joinPaths                                  | joinExpression
        "findByCountryRegionCountryName" | 2         | ["countryRegion"]                          | 'ON city_country_region_.countryId=city_country_region_country_.uuid'
        "findByCountryRegionCountryName" | 2         | ["countryRegion", "countryRegion.country"] | 'ON city_country_region_.countryId=city_country_region_country_.uuid'
        "findByCountryRegionName"        | 2         | ["countryRegion.country"]                  | 'ON city_country_region_.countryId=city_country_region_country_.uuid'
        "findByCountryRegionCountryName" | 2         | []                                         | 'ON city_country_region_.countryId=city_country_region_country_.uuid'
        "findByCountryRegionName"        | 1         | []                                         | 'ON city_.country_region_id=city_country_region_.id'
        "findByCountryRegionName"        | 1         | ["countryRegion"]                          | 'ON city_.country_region_id=city_country_region_.id'

    }

    private String columns(Class t, String alias) {
        def builder = new SqlQueryBuilder()
        return builder.selectAllColumns(PersistentEntity.of(t), alias)
    }

    @Unroll
    void "test JPA projection across nested property path for #method with @Join"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
interface MyInterface extends GenericRepository<City, Long> {

    @Join("$joinPath")
    $returnType $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().contains("JOIN FETCH city_.countryRegion city_country_region_")
        ann.value().contains(whereClause)

        where:
        joinPath        | method                           | returnType | arguments     | whereClause
        "countryRegion" | "findByCountryRegionCountryName" | "City"     | "String name" | "WHERE (city_country_region_.country.name = :p1)"
        "countryRegion" | "findByCountryRegionName"        | "City"     | "String name" | "WHERE (city_country_region_.name = :p1)"
    }
}
