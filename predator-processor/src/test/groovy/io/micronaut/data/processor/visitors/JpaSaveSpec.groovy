package io.micronaut.data.processor.visitors


import io.micronaut.data.intercept.SaveEntityInterceptor
import io.micronaut.data.intercept.SaveOneInterceptor
import io.micronaut.data.intercept.annotation.PredatorMethod
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class JpaSaveSpec extends AbstractPredatorSpec {

    void "test save"() {
        given:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyInterface' + BeanDefinitionVisitor.PROXY_SUFFIX, """
package test;

import io.micronaut.data.model.entities.Person;
import io.micronaut.data.repository.CrudRepository;
import io.micronaut.data.annotation.Repository;
import io.micronaut.data.annotation.Query;
import java.util.List;
import io.micronaut.data.annotation.Id;

@Repository
interface MyInterface extends io.micronaut.data.repository.Repository<Person, Long> {

    Person save(String name, int age);
    
}
""")

        when: "save method is retrieved"
        def updateMethod = beanDefinition.getRequiredMethod("save", String, int.class)
        def updateAnn = updateMethod.synthesize(PredatorMethod)

        then: "It was correctly compiled"
        updateAnn.interceptor() == SaveOneInterceptor
    }
}
