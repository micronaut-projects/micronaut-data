package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.annotation.Query
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor


class RepositoryTypeElementVisitorSpec extends AbstractTypeElementSpec {
    void "test simple finder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test;

import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.query.encoder.entities.Person;

@Repository
interface MyInterface {
    Person findByName(String name);    
    
    Person getByNameOrAgeGreaterThan(String name, int age);
}


''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.getRequiredMethod("findByName", String)
        beanDefinition.getRequiredMethod("findByName", String).getAnnotationMetadata().hasAnnotation(Query)
        beanDefinition.getRequiredMethod("findByName", String).getValue(Query, String).get() ==
                'SELECT DISTINCT person FROM io.micronaut.data.model.query.encoder.entities.Person AS person WHERE (person.name = :p1)'

        beanDefinition.getRequiredMethod("getByNameOrAgeGreaterThan", String, int.class).getValue(Query, String).get() ==
                'SELECT DISTINCT person FROM io.micronaut.data.model.query.encoder.entities.Person AS person WHERE ((person.name = :p1 OR person.age > :p2))'


    }
}
