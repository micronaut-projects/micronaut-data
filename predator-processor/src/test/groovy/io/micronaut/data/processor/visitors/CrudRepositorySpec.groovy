package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.intercept.ExistsByInterceptor
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.SaveAllInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor

import javax.annotation.processing.SupportedAnnotationTypes

class CrudRepositorySpec extends AbstractTypeElementSpec {

    void "test build CRUD repository"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.query.encoder.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;

@Repository
interface MyInterface extends CrudRepository<Person, Long> {    
}
""")

        when:"the save method is retrieved"
        def saveMethod = beanDefinition.getRequiredMethod("save", Person.class)

        then:"It was correctly compiled"
        saveMethod.getValue(PredatorMethod, "entity", String).isPresent()
        saveMethod.getValue(PredatorMethod, "rootEntity", Class).get() == Person
        saveMethod.getReturnType().type == Person
        saveMethod.getArguments()[0].type == Person

        when:"the save all method is retrieved"
        def saveAll = beanDefinition.getRequiredMethod("saveAll", Iterable.class)

        then:"the save all method was correctly compiled"
        saveAll
        saveAll.getReturnType().asArgument().getFirstTypeVariable().get().type == Person
        saveAll.getArguments()[0].getFirstTypeVariable().get().type == Person
        saveAll.synthesize(PredatorMethod).rootEntity() == Person
        saveAll.synthesize(PredatorMethod).interceptor() == SaveAllInterceptor

        when:"the exists by id method is retrieved"
        def existsMethod = beanDefinition.getRequiredMethod("existsById", Long)

        then:"The method is correctly configured"
        existsMethod
        existsMethod.getArguments()[0].getFirstTypeVariable().get().type == Long
        existsMethod.synthesize(PredatorMethod).rootEntity() == Person
        existsMethod.synthesize(PredatorMethod).idType() == Long
        existsMethod.synthesize(PredatorMethod).interceptor() == ExistsByInterceptor

        when:"the findAll method is retrieved"
        def findAll = beanDefinition.getRequiredMethod("findAll")

        then:"The method is correctly configured"
        findAll
        findAll.getReturnType().asArgument().getFirstTypeVariable().get().type == Person
        findAll.synthesize(PredatorMethod).rootEntity() == Person
        findAll.synthesize(PredatorMethod).idType() == Long
        findAll.synthesize(PredatorMethod).interceptor() == FindAllInterceptor
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
