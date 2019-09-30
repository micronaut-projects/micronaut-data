package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.tck.entities.City
import io.micronaut.data.tck.entities.Company

class OrderBySpec extends AbstractDataSpec {

    void "test order by date created"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

@Repository
interface MyInterface extends GenericRepository<Company, Long> {

    Company $method($arguments);
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
        method                         | arguments     | query
        "findByNameOrderByDateCreated" | "String name" | "SELECT company_ FROM $Company.name AS company_ WHERE (company_.name = :p1) ORDER BY company_.dateCreated ASC"
    }

    void "test order by date created - sql"() {
        given:
        def repository = buildRepository('test.MyInterface', """
import io.micronaut.data.tck.entities.*;

import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
interface MyInterface extends GenericRepository<Company, Long> {

    Company $method($arguments);
}
"""
        )

        def execMethod = repository.findPossibleMethods(method)
                .findFirst()
                .get()
        def ann = execMethod
                .synthesize(Query)

        expect:
        ann.value().endsWith(query)

        where:
        method                         | arguments     | query
        "findByNameOrderByDateCreated" | "String name" | "ORDER BY company_.date_created ASC"
    }
}
