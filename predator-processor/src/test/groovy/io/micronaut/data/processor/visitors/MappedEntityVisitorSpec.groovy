package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.model.DataType

class MappedEntityVisitorSpec extends AbstractTypeElementSpec {

    void "test mapping javax.persistent entity"() {
        given:
        def introspection = buildBeanIntrospection('test.TestEntity', '''
package test;

import io.micronaut.core.annotation.Introspected;
import javax.persistence.*;
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
        introspection.stringValue(MappedEntity).get() == 'test_entity'
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        def so = introspection.getProperty("someOther").get()
        so.stringValue(MappedProperty).get() == 'some_other'
        introspection.getProperty("id").get().getValue(MappedProperty, "type", DataType).get() == DataType.INTEGER
        so.getValue(MappedProperty, "type", DataType).orElse(null) == DataType.STRING
    }
}
