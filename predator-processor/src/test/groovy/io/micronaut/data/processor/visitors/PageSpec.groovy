package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindPageInterceptor
import io.micronaut.data.intercept.UpdateInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class PageSpec extends AbstractTypeElementSpec {

    void "test page method match"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long> {

    Page<Person> list(Pageable pageable);
    
    Page<Person> findByName(String title, Pageable pageable);
}
""")

        when: "the list method is retrieved"
        def listMethod = beanDefinition.getRequiredMethod("list", Pageable)
        def listAnn = listMethod.synthesize(PredatorMethod)

        def findMethod = beanDefinition.getRequiredMethod("findByName", String, Pageable)
        def findAnn = findMethod.synthesize(PredatorMethod)

        then:"it is configured correctly"
        listAnn.interceptor() == FindPageInterceptor
        findAnn.interceptor() == FindPageInterceptor
        findMethod.getValue(Query.class, "countQuery", String).get() == 'SELECT COUNT(person) FROM io.micronaut.data.model.entities.Person AS person WHERE (person.name = :p1)'
        findMethod.getValue(Query.class, PredatorMethod.MEMBER_COUNT_PARAMETERS, AnnotationValue[].class)
                  .get()[0]

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
