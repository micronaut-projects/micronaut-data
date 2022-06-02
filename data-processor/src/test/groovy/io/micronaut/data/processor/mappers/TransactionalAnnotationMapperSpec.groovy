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
import io.micronaut.transaction.annotation.TransactionalAdvice

class TransactionalAnnotationMapperSpec extends AbstractTypeElementSpec {

    void "test transactional mapper - javax"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test {

    @javax.transaction.Transactional(rollbackOn = RuntimeException.class, dontRollbackOn=java.io.IOException.class)
    void one() {
    
    }
}

''')
        expect:
        definition.getRequiredMethod("one")
                .classValue(TransactionalAdvice, "noRollbackFor").get() == IOException
        definition.getRequiredMethod("one")
                .classValue(TransactionalAdvice, "rollbackFor").get() == RuntimeException
    }

    void "test transactional mapper - jakarta"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test {

    @jakarta.transaction.Transactional(rollbackOn = RuntimeException.class, dontRollbackOn=java.io.IOException.class)
    void one() {
    
    }
}

''')
        expect:
        definition.getRequiredMethod("one")
                .classValue(TransactionalAdvice, "noRollbackFor").get() == IOException
        definition.getRequiredMethod("one")
                .classValue(TransactionalAdvice, "rollbackFor").get() == RuntimeException
    }
}
