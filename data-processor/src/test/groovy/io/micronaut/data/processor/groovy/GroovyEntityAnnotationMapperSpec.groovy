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
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty

class GroovyEntityAnnotationMapperSpec extends AbstractBeanDefinitionSpec {

    void "test groovy mapping javax.persistent entity with empty indexes"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test

import javax.persistence.*

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

''')
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        introspection.getValue(MappedEntity, String).get() == 'test_tb1'
        def indexes = introspection.getAnnotation("io.micronaut.data.annotation.Indexes")
        indexes
        indexes.getValues().isEmpty()
        !introspection.getProperty("tmp").isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        introspection.getIndexedProperty(MappedProperty, "test_name").isPresent()
        introspection.getIndexedProperty(MappedProperty, "test_name").get().name == 'name'
    }
}
