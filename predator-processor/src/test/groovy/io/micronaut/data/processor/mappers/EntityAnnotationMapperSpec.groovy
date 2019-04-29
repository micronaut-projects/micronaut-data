package io.micronaut.data.processor.mappers

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Persisted

class EntityAnnotationMapperSpec extends AbstractTypeElementSpec {


    void "test mapping javax.persistent entity"() {
        given:
        def introspection = buildBeanIntrospection('test.Test', '''
package test;

import io.micronaut.core.annotation.Introspected;
import javax.persistence.*;

@Entity
@Table(name="test_tb1")
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

''')
        expect:
        introspection != null
        introspection.hasStereotype(Persisted)
        introspection.getPropertyNames()
        introspection.getValue(Persisted, String).get() == 'test_tb1'
        !introspection.getProperty("tmp").isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Id).get().name == 'id'
        introspection.getIndexedProperty(io.micronaut.data.annotation.Persisted, "test_name").isPresent()
        introspection.getIndexedProperty(io.micronaut.data.annotation.Persisted, "test_name").get().name == 'name'
    }


    protected BeanIntrospection buildBeanIntrospection(String className, String cls) {
        def beanDefName= '$' + NameUtils.getSimpleName(className) + '$Introspection'
        def packageName = NameUtils.getPackageName(className)
        String beanFullName = "${packageName}.${beanDefName}"

        ClassLoader classLoader = buildClassLoader(className, cls)
        return (BeanIntrospection)classLoader.loadClass(beanFullName).newInstance()
    }
}