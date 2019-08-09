package io.micronaut.data.processor.sql


import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.model.entities.Person
import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildInsertSpec extends AbstractDataSpec {

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
            .stringValue(DataMethod.class, DataMethod.META_MEMBER_INSERT_STMT)
            .orElse(null) == 'INSERT INTO person (name,age,enabled) VALUES (?,?,?)'
    }
}
