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
package io.micronaut.data.processor.mappers

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.processor.model.SourcePersistentEntity
import io.micronaut.inject.ast.ClassElement
import org.intellij.lang.annotations.Language

import java.util.function.Function

class EntityAnnotationMapperSpec extends AbstractTypeElementSpec {

    private static final String CLAZZ = '''
package test;

import io.micronaut.core.annotation.Introspected;
import javax.persistence.*;

@Entity
@Table(name="test_tb1", indexes = {@Index(name = "idx_test_name", columnList = "name",  unique = true)})
class Test {
    private String name;
    @Id
    private Long id;
    @Transient
    private String tmp;

    @Column(name="test_name")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTmp() {
        return tmp;
    }

    public void setTmp(String tmp) {
        this.tmp = tmp;
    }
}
'''

    void "test mapping javax.persistent entity"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', CLAZZ)
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        introspection.getValue(MappedEntity, String).get() == 'test_tb1'
        def indexes = introspection.getAnnotation("io.micronaut.data.annotation.Indexes")
        def idx = indexes.getValues()["value"][0]["values"]
        idx["name"] == "idx_test_name"
        idx["columns"][0] == "name"
        idx["unique"] == true
        introspection.getProperty("tmp").isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        introspection.getIndexedProperty(MappedProperty, "test_name").isPresent()
        introspection.getIndexedProperty(MappedProperty, "test_name").get().name == 'name'
    }

    void "test mapping javax.persistent entity SourcePersistentEntity"() {
        given:
        ClassElement test = buildClassElement(CLAZZ)
        expect:
        SourcePersistentEntity persistentEntity = new SourcePersistentEntity(test, (te) -> null)
        persistentEntity.getPersistentPropertyNames() == ["name", "id"]
    }


    protected BeanIntrospection buildBeanIntrospection(String className, @Language("JAVA") String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }
}
