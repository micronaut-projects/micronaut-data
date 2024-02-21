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
package io.micronaut.data.processor.groovy

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.inject.ast.ClassElement
import spock.lang.PendingFeature

import java.util.function.Function

class GroovyEntityAnnotationMapperSpec extends AbstractBeanDefinitionSpec {

    private final static Function<String, String> CLAZZ = new Function<String, String>() {
        @Override
        String apply(String importPackage) {
            """
package test

import ${importPackage}.*

@Entity
@Table(name="test_tb1", indexes = [])
class Test {
    private String name
    @Id
    private Long id
    @Transient
    private String tmp

    @Column(name="test_name")
    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }


    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    String getTmp() {
        return tmp
    }

    void setTmp(String tmp) {
        this.tmp = tmp
    }
}
"""
        }
    }

    void "test groovy mapping javax.persistent entity with empty indexes"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', CLAZZ.apply('javax.persistence'))
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        introspection.getValue(MappedEntity, String).get() == 'test_tb1'
        def indexes = introspection.getAnnotation("io.micronaut.data.annotation.Indexes")
        indexes
        indexes.getValues().isEmpty()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        introspection.getIndexedProperty(MappedProperty, "test_name").isPresent()
        introspection.getIndexedProperty(MappedProperty, "test_name").get().name == 'name'
    }

    void "test @Transient field for javax.persistent entity"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', CLAZZ.apply('javax.persistence'))

        expect:
        introspection
        introspection.getProperty("tmp").isPresent()
    }

    void "test @Transient field for jakarta.persistent entity"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', CLAZZ.apply('jakarta.persistence'))

        expect:
        introspection
        introspection.getProperty("tmp").isPresent()
    }

    void "test groovy mapping jakarta.persistent entity with empty indexes"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', CLAZZ.apply('jakarta.persistence'))
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        introspection.getValue(MappedEntity, String).get() == 'test_tb1'
        def indexes = introspection.getAnnotation("io.micronaut.data.annotation.Indexes")
        indexes
        indexes.getValues().isEmpty()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        introspection.getIndexedProperty(MappedProperty, "test_name").isPresent()
        introspection.getIndexedProperty(MappedProperty, "test_name").get().name == 'name'
    }

    void "test groovy mapping javax.persistent entity with empty indexes SourcePersistentEntity"() {
        given:
        ClassElement test = buildClassElement('test.Test', CLAZZ.apply('javax.persistence'))
        expect:
        SourcePersistentEntity persistentEntity = new SourcePersistentEntity(test, (te) -> null)
        persistentEntity.getPersistentPropertyNames() == ["name", "id"]
    }

    void "test groovy mapping jakarta.persistent entity with empty indexes SourcePersistentEntity"() {
        given:
        ClassElement test = buildClassElement('test.Test', CLAZZ.apply('jakarta.persistence'))

        expect:
        SourcePersistentEntity persistentEntity = new SourcePersistentEntity(test, (te) -> null)
        persistentEntity.getPersistentPropertyNames() == ["name", "id"]
    }
}
