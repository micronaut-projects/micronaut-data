package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.UpdateInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class JpaUpdateSpec extends AbstractTypeElementSpec {

    void "test update by ID"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.query.encoder.entities.*;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;
import io.micronaut.data.annotation.Id;

@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long> {

    void update(@Id Long myId, String name);
}
""")

        when: "update method is retrieved"
        def updateMethod = beanDefinition.getRequiredMethod("update", Long, String)
        def ann = updateMethod.synthesize(PredatorMethod)
        def query = updateMethod.synthesize(Query)

        then: "It was correctly compiled"
        ann.interceptor() == UpdateInterceptor
        query.value() == "UPDATE $Person.name person SET person.name=:p1 WHERE (person.id = :p2)"
        ann.id() == 'myId'
    }


    @Override
    protected JavaParser newJavaParser() {
        return new JavaParser() {
            @Override
            protected TypeElementVisitorProcessor getTypeElementVisitorProcessor() {
                return new MyTypeElementVisitorProcessor()
            }
        }
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), new RepositoryTypeElementVisitor()]
        }
    }
}
