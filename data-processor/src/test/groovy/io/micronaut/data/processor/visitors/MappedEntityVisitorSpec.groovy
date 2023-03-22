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

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType
import spock.lang.Unroll

class MappedEntityVisitorSpec extends AbstractTypeElementSpec {

    void 'test fail compilation for a bean method that does not meet requirements'() {
        when:
        buildBeanIntrospection('test.BadBean', '''
package test;

import jakarta.persistence.*;
import jakarta.inject.*;

@Singleton
class BadBean {
    @PrePersist
    String junk() {
        return null;
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Method annotated with @PrePersist must return void and declare exactly one argument that represents the entity type to listen for')
    }

    void 'test fail compilation for entity event methods that dont meet requirements'() {
        when:
        buildBeanIntrospection('test.BadEventEntity', '''
package test;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
class BadEventEntity {
    @Id
    private Integer id;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    @PrePersist
    String junk(String str) {
        return null;
    }
}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains('Method annotated with @PrePersist must return void and declare no arguments')
    }

    void "test to-one with no annotation"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
class TestEntity {
    private Name name;
    @Id
    private Integer id;

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}

@Entity
class Name {
    @Id
    private Integer id;

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

}

''')
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        !introspection.getRequiredProperty("name", Object).stringValue(MappedProperty).isPresent()
    }

    void "test embedded"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
class TestEntity {
    @Embedded
    private Name name;
    @Id
    private Integer id;

    public Name getName() {
        return name;
    }

    public void setName(Name name) {
        this.name = name;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}

@Embeddable
class Name {
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}

''')
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
    }

    void "test mapping Embeddable"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.*;
import java.util.UUID;

@Embeddable
class TestEntity {
    private String name;
    private UUID someOther;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public UUID getSomeOther() {
        return someOther;
    }

    public void setSomeOther(UUID someOther) {
        this.someOther = someOther;
    }
}

''')
        expect:
        introspection != null
        introspection.hasStereotype(Embeddable)
        introspection.getPropertyNames()
        def so = introspection.getProperty("someOther").get()
//        so.stringValue(MappedProperty).get() == 'some_other'
        so.getValue(MappedProperty, "type", DataType).orElse(null) == DataType.UUID
    }

    void "test mapping javax.persistent entity"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import jakarta.persistence.*;
import java.util.UUID;
@Entity
class TestEntity {
    private String name;
    @Id
    private Integer id;
    private UUID someOther;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public UUID getSomeOther() {
        return someOther;
    }

    public void setSomeOther(UUID someOther) {
        this.someOther = someOther;
    }
}

''')
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        !introspection.stringValue(MappedEntity).present
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        def so = introspection.getProperty("someOther").get()
        !so.stringValue(MappedProperty).present
        introspection.getProperty("id").get().getValue(MappedProperty, "type", DataType).get() == DataType.INTEGER
        so.getValue(MappedProperty, "type", DataType).orElse(null) == DataType.UUID
    }

    void "test mapping with custom type def"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
class TestEntity {
    private SName name;
    @Id
    private Integer id;

    public SName getName() {
        return name;
    }

    public void setName(SName name) {
        this.name = name;
    }


    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}

@TypeDef(type=DataType.STRING)
class SName {
    String name;
}

''')
        expect:
        introspection != null
        introspection.hasStereotype(MappedEntity)
        introspection.getPropertyNames()
        introspection.getProperty("name").get().enumValue(MappedProperty, "type", DataType).get() == DataType.STRING
    }

    @Unroll
    void "test detecting custom data type: #dataType from type: #type"(String type, DataType dataType) {
        given:
            def introspection = buildBeanIntrospection('test.TestEntity', """
package test;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.core.convert.ConversionContext;
import jakarta.persistence.*;
import java.util.UUID;
import io.micronaut.data.annotation.MappedProperty;

@Entity
class TestEntity {
    @MappedProperty(converter = SNameTypeConverter.class)
    private SName name;
    @Id
    private Integer id;

    public SName getName() {
        return name;
    }

    public void setName(SName name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }
}

class SName {
    String name;
}

class SNameTypeConverter implements AttributeConverter<SName, $type> {

    @Override
    public $type convertToPersistedValue(SName entityValue, ConversionContext context) {
        return null;
    }

    @Override
    public SName convertToEntityValue($type persistedValue, ConversionContext context) {
        return null;
    }

}

""")
        expect:
            introspection != null
            introspection.hasStereotype(MappedEntity)
            introspection.getProperty("name").get().enumValue(MappedProperty, "type", DataType).get() == dataType

        where:
            type                    || dataType
            String.class.getName()  || DataType.STRING
            Integer.class.getName() || DataType.INTEGER
// Core doesn't detect primitive arrays
//            'byte[]'                || DataType.BYTE_ARRAY
//            'int[]'                 || DataType.INTEGER_ARRAY
    }
}
