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
package io.micronaut.data.document.processor

import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaParser
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.data.processor.visitors.MappedEntityVisitor
import io.micronaut.data.processor.visitors.RepositoryTypeElementVisitor
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.beans.visitor.IntrospectedTypeElementVisitor
import io.micronaut.inject.visitor.TypeElementVisitor
import io.micronaut.inject.writer.BeanDefinitionVisitor
import org.intellij.lang.annotations.Language
import spock.lang.Requires

import javax.annotation.processing.SupportedAnnotationTypes

@Requires({ javaVersion <= 1.8 })
class AbstractDataSpec extends AbstractTypeElementSpec {

    SourcePersistentEntity buildJpaEntity(String name, @Language("JAVA") String source) {
        def pkg = NameUtils.getPackageName(name)
        ClassElement classElement = buildClassElement("""
package $pkg;

import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import jakarta.persistence.*;
import java.util.*;

$source
""")

        return TestUtils.sourcePersistentEntity(classElement)
    }

    SourcePersistentEntity buildEntity(String name, @Language("JAVA") String source) {
        def pkg = NameUtils.getPackageName(name)
        ClassElement classElement = buildClassElement("""
package $pkg;

import io.micronaut.data.annotation.*;

$source
""")

        return TestUtils.sourcePersistentEntity(classElement)
    }

    BeanDefinition<?> buildRepository(String name, @Language("JAVA") String source) {
        def pkg = NameUtils.getPackageName(name)
        return buildBeanDefinition(name + BeanDefinitionVisitor.PROXY_SUFFIX, """
package $pkg;

import io.micronaut.data.model.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.*;
import java.util.*;

$source
""")

    }

    BeanDefinition<?> buildJpaRepository(String name, @Language("JAVA") String source) {
        def pkg = NameUtils.getPackageName(name)
        return buildBeanDefinition(name + BeanDefinitionVisitor.PROXY_SUFFIX, """
package $pkg;

import jakarta.persistence.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.Repository;
import java.util.*;

$source
""")

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

    /**
     * Build an entity for the given name and properties
     * @param name The name
     * @param properties The properties
     * @return
     */
    String entity(String name, Map<String, Class> properties) {
        String props = properties.collect {
            String propertyName = NameUtils.capitalize(it.key)
            """
    public $it.value.name get$propertyName() {
        return $it.key;
    }

    public void set$propertyName($it.value.name $it.key) {
        this.$it.key = $it.key;
    }"""
        }.join("")

        return """
@MappedEntity
class $name {
    @Id
    @GeneratedValue
    private Long id;
    ${properties.collect { 'private ' + it.value.name + ' ' + it.key + ';' }.join("\n")}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    $props
}
"""
    }

    /**
     * Build an entity for the given name and properties
     * @param name The name
     * @param properties The properties
     * @return
     */
    String dto(String name, Map<String, Class> properties) {
        String props = properties.collect {
            String propertyName = NameUtils.capitalize(it.key)
            """
    public $it.value.name get$propertyName() {
        return $it.key;
    }

    public void set$propertyName($it.value.name $it.key) {
        this.$it.key = $it.key;
    }"""
        }.join("")

        return """
@io.micronaut.core.annotation.Introspected
class $name {
    ${properties.collect { 'private ' + it.value.name + ' ' + it.key + ';' }.join("\n")}

    $props
}
"""
    }

    @SupportedAnnotationTypes("*")
    static class MyTypeElementVisitorProcessor extends TypeElementVisitorProcessor {
        @Override
        protected Collection<TypeElementVisitor> findTypeElementVisitors() {
            return [new IntrospectedTypeElementVisitor(), new RepositoryTypeElementVisitor(), new MappedEntityVisitor()]
        }
    }
}
