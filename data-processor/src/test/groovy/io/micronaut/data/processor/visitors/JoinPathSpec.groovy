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
package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.tck.entities.City
import io.micronaut.inject.ExecutableMethod
import spock.lang.Issue
import spock.lang.Unroll

class JoinPathSpec extends AbstractDataSpec {

    @Issue('##356')
    void "test join with custom ID"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<User, Long> {

    @Join(value = "authorities")
    User findById(Long id);
}

@MappedEntity
class Authority {

    @Id
    private String name12345;
    
    public String getName12345() {
        return name12345;
    }
    
    public void setName12345(String name12345) {
        this.name12345 = name12345;
    }
}

@MappedEntity
class User {

    @Id
    @GeneratedValue
    private Long id;
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }

    @Relation(Relation.Kind.ONE_TO_MANY)
    private Set<Authority> authorities = new HashSet<>();
    
    public Set<Authority> getAuthorities() {
        return authorities;
    }
    
    public void setAuthorities(Set<Authority> authorities) {
        this.authorities = authorities;
    }
}
""")
        def query = repository.getRequiredMethod("findById", Long)
                .stringValue(Query).get()

        expect:
        query == 'SELECT user_."id",user_authorities_."name12345" AS authorities_name12345 FROM "user" user_ INNER JOIN "user_authority" user_authorities_user_authority_ ON user_."id"=user_authorities_user_authority_."user_id"  INNER JOIN "authority" user_authorities_ ON user_authorities_user_authority_."authority_id"=user_authorities_."name12345" WHERE (user_."id" = ?)'
    }

    @Unroll
    void "test JPA projection across nested property path for #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@io.micronaut.context.annotation.Executable
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
@io.micronaut.context.annotation.Executable
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
        "findByBooksTitle" | "Author"   | "String title" | "JOIN \"book\" author_books_ ON author_.\"id\"=author_books_.\"author_id\" WHERE (author_books_.\"title\" = ?)"
    }


    @Unroll
    void "test SQL projection across one-to-many with join table #method"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
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
        "findByCitiesName" | "CountryRegion" | "String name" | "INNER JOIN \"countryRegionCity\" country_region_cities_countryRegionCity_ ON country_region_.\"id\"=country_region_cities_countryRegionCity_.\"countryRegionId\"  INNER JOIN \"T_CITY\" country_region_cities_ ON country_region_cities_countryRegionCity_.\"cityId\"=country_region_cities_.\"id\" WHERE (country_region_cities_.\"C_NAME\" = ?)"
    }

    @Unroll
    void "test SQL join with custom alias #method"() {
        given:
        String joinAnn = ""
        if (joinPaths) {
            joinAnn = joinPaths.collect() {
                """
@Join(value="$it.key", alias="$it.value")
"""
            }.join('')
        }
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
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
        queryStr.endsWith(whereClause)
        queryStr.contains('INNER JOIN')

        where:
        method                           | joinPaths               | whereClause
        "findByCountryRegionName"        | ["countryRegion": 'r_'] | "WHERE (r_.\"name\" = ?)"
        "findByCountryRegionCountryName" | ["countryRegion": 'r_', 'countryRegion.country':'c_'] | "WHERE (c_.\"name\" = ?)"

    }

    @Unroll
    void "test 2 join annotations with custom overlapping aliases"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import java.util.List;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends GenericRepository<City, Long> {

    @Join(value = "countryRegion", alias = "r_")
    @Join(value = "countryRegion.country", alias = "c_")
    List<City> getByCountryRegionCountryName(String name);
}
"""
        )

        def execMethod = repository.findPossibleMethods("getByCountryRegionCountryName")
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)
        String queryStr = ann.value()

        expect:
        queryStr.contains('INNER JOIN "CountryRegion" r_ ON city_."country_region_id"=r_."id" INNER JOIN "country" c_ ON r_."countryId"=c_."uuid"')

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
@io.micronaut.context.annotation.Executable
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
@io.micronaut.context.annotation.Executable
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
        "findByCountryRegionCountryName" | 2         | ["countryRegion"]                          | 'ON city_country_region_."countryId"=city_country_region_country_."uuid"'
        "findByCountryRegionCountryName" | 2         | ["countryRegion", "countryRegion.country"] | 'ON city_country_region_."countryId"=city_country_region_country_."uuid"'
        "findByCountryRegionName"        | 2         | ["countryRegion.country"]                  | 'ON city_country_region_."countryId"=city_country_region_country_."uuid"'
        "findByCountryRegionCountryName" | 2         | []                                         | 'ON city_country_region_."countryId"=city_country_region_country_."uuid"'
        "findByCountryRegionName"        | 1         | []                                         | 'ON city_."country_region_id"=city_country_region_."id"'
        "findByCountryRegionName"        | 1         | ["countryRegion"]                          | 'ON city_."country_region_id"=city_country_region_."id"'

    }

    private String columns(Class t, String alias) {
        def builder = new SqlQueryBuilder()
        StringBuilder columns = new StringBuilder()
        builder.selectAllColumns(PersistentEntity.of(t), alias, columns)
        columns.toString()
    }

    @Unroll
    void "test JPA projection across nested property path for #method with @Join"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@io.micronaut.context.annotation.Executable
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

    void "test nested to-many joins results in the correct query"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
@RepositoryConfiguration(queryBuilder=io.micronaut.data.model.query.builder.sql.SqlQueryBuilder.class)
@io.micronaut.context.annotation.Executable
interface MyInterface extends io.micronaut.data.tck.repositories.ShelfRepository {

}
"""
        )

        def method = repository.findPossibleMethods("findById").findFirst().get()
        def query = method.stringValue(Query).get()

        expect:
        query.contains('LEFT JOIN "shelf_book" b_shelf_book_ ON shelf_."id"=b_shelf_book_."shelf_id" ')
        query.contains('LEFT JOIN "book" b_ ON b_shelf_book_."book_id"=b_."id" ')
        query.contains('LEFT JOIN "page" p_ ON b_."id"=p_."book_id" ')
    }
}
