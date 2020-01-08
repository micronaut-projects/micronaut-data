package io.micronaut.data.processor.sql

import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.processor.visitors.AbstractDataSpec
import spock.lang.Unroll

class BuildUpdateSpec extends AbstractDataSpec {
    @Unroll
    void "test build update with datasource set"() {
        given:
        def repository = buildRepository('test.MovieRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;

@JdbcRepository(dialect= Dialect.MYSQL)
interface MovieRepository extends CrudRepository<Movie, Integer> {
    void updateById(int id, String theLongName, String title);
}

${entity('Movie', [title: String, theLongName: String])}
""")
        def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
        method.stringValue(Query).get() == query
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == bindingPaths
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == binding


        where:
        methodName   | query                                                             | bindingPaths                               | binding
        'update'     | 'UPDATE `movie` SET `title`=?,`the_long_name`=? WHERE (`id` = ?)' | ['title', 'theLongName', 'id'] as String[] | [] as String[]
        'updateById' | 'UPDATE `movie` SET `the_long_name`=?,`title`=? WHERE (`id` = ?)' | ['', '', ''] as String[]                   | ['1', '2', '0'] as String[]
    }

    @Unroll
    void "test build update with custom ID"() {
        given:
        def repository = buildRepository('test.CompanyRepository', """
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Company;
@JdbcRepository(dialect= Dialect.MYSQL)
interface CompanyRepository extends CrudRepository<Company, Long> {
}
""")
        def method = repository.findPossibleMethods(methodName).findFirst().get()

        expect:
        method.stringValue(Query).get() == query
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING_PATHS) == bindingPaths
        method.stringValues(DataMethod, DataMethod.META_MEMBER_PARAMETER_BINDING) == binding


        where:
        methodName   | query                                                                                         | bindingPaths                               | binding
        'update'     | 'UPDATE `company` SET `last_updated`=?,`name`=?,`url`=? WHERE (`my_id` = ?)' | ['lastUpdated', 'name', 'url', 'myId'] as String[] | [] as String[]
    }
}
