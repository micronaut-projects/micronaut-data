/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.visitors

import groovy.transform.CompileStatic
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.data.annotation.Query
import io.micronaut.data.intercept.FindAllInterceptor
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.tck.entities.Book
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.Unroll

import javax.annotation.processing.SupportedAnnotationTypes
import jakarta.persistence.Entity

class AssociationQuerySpec extends AbstractTypeElementSpec {
    @Unroll
    void "test build repository with association queries for #method"() {
        given:
        BeanDefinition beanDefinition = compileListRepository(resultType, method, arguments)
        def parameterTypes = arguments.values() as Class[]

        expect: "The finder is valid"
        !beanDefinition.isAbstract()
        beanDefinition != null

        def executableMethod = beanDefinition.getRequiredMethod(method, parameterTypes)
        def ann = executableMethod.synthesize(DataMethod)
        ann.interceptor() == interceptor
        ann.rootEntity() == rootEntity
        ann.resultType() == resultType
        executableMethod.getValue(DataMethod, "interceptor", Class).get() == interceptor
        executableMethod.getValue(Query, String).orElse(null) == query

        where:
        rootEntity | resultType | method             | arguments      | query                                                                                                             | interceptor
        Book       | Book       | 'findByAuthorName' | [name: String] | "SELECT book_ FROM $rootEntity.name AS book_ LEFT JOIN book_.author book_author_ WHERE (book_author_.name = :p1)" | FindAllInterceptor
    }

    @CompileStatic
    BeanDefinition compileListRepository(Class returnType, String method, Map<String, Class> arguments) {
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.annotation.Repository;
${returnType.isAnnotationPresent(Entity) ? 'import ' + returnType.getName() + ';' : ''}
import io.micronaut.data.model.entities.Person;
import java.util.List;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.repository.GenericRepository;

@Repository
@io.micronaut.context.annotation.Executable
interface MyInterface extends io.micronaut.data.repository.GenericRepository<$returnType.simpleName, Long>{
    @Join(value="author", type=Join.Type.LEFT)
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
