package io.micronaut.data.processor.visitors

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllByInterceptor
import io.micronaut.data.intercept.FindOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.data.model.query.encoder.entities.Person
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Specification
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import javax.persistence.Entity

class JpaOrderBySpec extends AbstractTypeElementSpec {

    void "test order by method definitions"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.query.encoder.entities.*;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;

@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long> {

    List<Person> queryByNameOrderByName(String n);
    
    List<Person> listOrderByName();
    
    List<String> listNameOrderByName();
    
    List<Person> listTop3OrderByName();
    
    List<Book> findTop3OrderByTitle();
}
""")

        when: "the query method is retrieved"
        def findOne = beanDefinition.getRequiredMethod("queryByNameOrderByName", String.class)
        def list = beanDefinition.getRequiredMethod("listOrderByName")
        def listName = beanDefinition.getRequiredMethod("listNameOrderByName")
        def listTop3 = beanDefinition.getRequiredMethod("listTop3OrderByName")

        then: "It was correctly compiled"
        findOne.synthesize(Query).value() == "SELECT person FROM $Person.name AS person WHERE (person.name = :p1) ORDER BY person.name ASC"
        list.synthesize(Query).value() == "SELECT person FROM $Person.name AS person ORDER BY person.name ASC"
        list.synthesize(PredatorMethod).resultType() == Person
        listName.synthesize(Query).value() == "SELECT person.name FROM $Person.name AS person ORDER BY person.name ASC"
        listName.synthesize(PredatorMethod).resultType() == String
        listTop3.synthesize(Query).value() == "SELECT person FROM $Person.name AS person ORDER BY person.name ASC"
        listTop3.synthesize(PredatorMethod).max() == 3
    }

    @Unroll
    void "test order by errors for method #method"() {
        when:
        compileListRepository(
                returnType,
                method,
                arguments
        )

        then: "The finder failed to compile"
        def e = thrown(RuntimeException)
        e.message.contains(message)

        where:
        rootEntity | returnType | method                | arguments | message
        Person     | Person     | 'listOrderByJunk'     | [:]       | 'Cannot order by non-existent property: junk'
        Person     | Person     | 'listJunkOrderByName' | [:]       | 'Cannot project on non-existent property: junk'
    }

    @CompileStatic
    BeanDefinition compileListRepository(Class returnType, String method, Map<String, Class> arguments) {
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
${returnType.isAnnotationPresent(Entity) ? 'import ' + returnType.getName() + ';' : ''}
import io.micronaut.data.model.query.encoder.entities.Person;
import java.util.List;

@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long>{
    List<$returnType.simpleName> $method(${arguments.entrySet().collect { "$it.value.name $it.key" }.join(',')});    
}


""")
        return beanDefinition
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
