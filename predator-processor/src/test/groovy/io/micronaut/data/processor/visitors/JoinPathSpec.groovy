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
        "findByBooksTitle" | "Author"   | "String title" | "JOIN book author_books_ ON author_.id=author_books_.author_id WHERE (author_books_.title = ?)"
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
        "findByCitiesName" | "CountryRegion" | "String name" | "INNER JOIN countryRegionCity CountryRegion_cities_countryRegionCity_ ON CountryRegion_.id=CountryRegion_cities_countryRegionCity_.countryRegionId  INNER JOIN T_CITY CountryRegion_cities_ ON CountryRegion_cities_countryRegionCity_.cityId=CountryRegion_cities_.id"
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
        "findByCountryRegionName"        | ["countryRegion"]         | "WHERE (region_.name = ?)"

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
        String columnNames = columns(City, 'T_CITY_')
        String queryStr = ann.value()

        expect:
        queryStr.startsWith("SELECT ${columnNames}")
        queryStr.endsWith(whereClause)
        queryStr.contains('INNER JOIN')

        where:
        method                           | joinPaths                 | whereClause
        "findByCountryRegionName"        | ["countryRegion.country"] | "WHERE (T_CITY_country_region_.name = ?)"
        "findByCountryRegionCountryName" | []                        | "WHERE (T_CITY_country_region_country_.name = ?)"
        "findByCountryRegionName"        | []                        | "WHERE (T_CITY_country_region_.name = ?)"
        "findByCountryRegionName"        | ["countryRegion"]         | "WHERE (T_CITY_country_region_.name = ?)"

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
        String columnNames = columns(City, 'T_CITY_')
        String queryStr = ann.value()

        expect:
        queryStr.startsWith("SELECT ${columnNames}")
        queryStr.contains('INNER JOIN')
        queryStr.count("INNER JOIN") == joinCount
        queryStr.contains(joinExpression)

        where:
        method                           | joinCount | joinPaths                                  | joinExpression
        "findByCountryRegionCountryName" | 2         | ["countryRegion"]                          | 'ON T_CITY_country_region_.countryId=T_CITY_country_region_country_.uuid'
        "findByCountryRegionCountryName" | 2         | ["countryRegion", "countryRegion.country"] | 'ON T_CITY_country_region_.countryId=T_CITY_country_region_country_.uuid'
        "findByCountryRegionName"        | 2         | ["countryRegion.country"]                  | 'ON T_CITY_country_region_.countryId=T_CITY_country_region_country_.uuid'
        "findByCountryRegionCountryName" | 2         | []                                         | 'ON T_CITY_country_region_.countryId=T_CITY_country_region_country_.uuid'
        "findByCountryRegionName"        | 1         | []                                         | 'ON T_CITY_.country_region_id=T_CITY_country_region_.id'
        "findByCountryRegionName"        | 1         | ["countryRegion"]                          | 'ON T_CITY_.country_region_id=T_CITY_country_region_.id'

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
