package io.micronaut.data.processor.mappers

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.transaction.annotation.TransactionalAdvice

class TransactionalAnnotationMapperSpec extends AbstractTypeElementSpec {

    void "test transactional mapper - noRollback"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@javax.inject.Singleton
class Test {

    @javax.transaction.Transactional(dontRollbackOn=java.io.IOException.class)
    void one() {
    
    }
}

''')
        expect:
        definition.getRequiredMethod("one")
                .classValue(TransactionalAdvice, "noRollbackFor").get() == IOException
    }
}
