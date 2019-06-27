package io.micronaut.data.processor.sql

import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.entities.Person
import io.micronaut.data.processor.visitors.AbstractPredatorSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildInsertSpec extends AbstractPredatorSpec {

    void "test build SQL insert statement"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;

@Repository
@RepositoryConfiguration(queryBuilder=SqlQueryBuilder.class)
interface MyInterface extends CrudRepository<Person, Long> {
}
""")

        expect:
        beanDefinition.getRequiredMethod("save", Person)
            .stringValue(PredatorMethod.class, PredatorMethod.META_MEMBER_INSERT_STMT)
            .orElse(null) == 'INSERT INTO person (name,age,enabled) VALUES (?,?,?)'
    }
}
