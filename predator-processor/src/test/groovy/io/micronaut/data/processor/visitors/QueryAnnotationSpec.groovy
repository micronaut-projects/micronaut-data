package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.intercept.FindAllByInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class QueryAnnotationSpec extends AbstractTypeElementSpec {
    void "test build CRUD repository"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.query.encoder.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;

@Repository
interface MyInterface {

    @Query("from Person p where p.name = :n")
    List<Person> listPeople(String n);   
    
    @Query("from Person p where p.name = :n")
    Person queryByName(String n);
    
    @Query("from Person p where p.name = :n")
    Person findPerson(String n);
}
""")

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("listPeople", String.class)

        then: "It was correctly compiled"
        def ann = listMethod.synthesize(PredatorMethod)
        ann.rootEntity() == Person
        ann.interceptor() == FindAllByInterceptor
        ann.parameterBinding()[0].name() == 'n'
        ann.parameterBinding()[0].value() == 'n'
        listMethod.getReturnType().type == List

        when: "the findOne method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByName", String.class)

        then: "It was correctly compiled"
        def ann2 = findOne.synthesize(PredatorMethod)
        ann2.rootEntity() == Person
        ann2.interceptor() == FindOneInterceptor
        ann2.parameterBinding()[0].name() == 'n'
        ann2.parameterBinding()[0].value() == 'n'
        findOne.getReturnType().type == Person
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
