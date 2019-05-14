package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.FindByIdInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.FindPageInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.Pageable
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class FindSpec extends AbstractTypeElementSpec {

    void "test find method match"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.annotation.Repository;
@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long> {

    Person find(Long id);
    
    Person find(Long id, String name);
    
    Person findById(Long id);
    
    Iterable<Person> findByIds(Iterable<Long> ids);
}
""")

        when: "the list method is retrieved"

        def findMethod = beanDefinition.getRequiredMethod("find", Long)
        def findMethod2 = beanDefinition.getRequiredMethod("find", Long, String)
        def findMethod3 = beanDefinition.getRequiredMethod("findById", Long)
        def findByIds = beanDefinition.getRequiredMethod("findByIds", Iterable.class)

        def findAnn = findMethod.synthesize(PredatorMethod)
        def findAnn2 = findMethod2.synthesize(PredatorMethod)
        def findAnn3 = findMethod3.synthesize(PredatorMethod)
        def findByIdsAnn = findByIds.synthesize(PredatorMethod)

        then:"it is configured correctly"
        findAnn.interceptor() == FindByIdInterceptor
        findAnn3.interceptor() == FindByIdInterceptor
        findAnn2.interceptor() == FindOneInterceptor
        findByIdsAnn.interceptor() == FindAllInterceptor
        findByIds.synthesize(Query).value() == 'SELECT person FROM io.micronaut.data.model.entities.Person AS person WHERE (person.id IN (:p1))'
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
