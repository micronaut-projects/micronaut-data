/*
 * Copyright 2017-2024 original authors
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
package io.micronaut.data.runtime.intercept.criteria.async

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.exceptions.DataAccessException
import io.micronaut.data.intercept.RepositoryMethodKey
import io.micronaut.data.operations.CriteriaRepositoryOperations
import io.micronaut.data.operations.RepositoryOperations
import io.micronaut.data.operations.async.AsyncCapableRepository
import io.micronaut.data.operations.async.AsyncRepositoryOperations
import jakarta.persistence.criteria.CriteriaBuilder
import spock.lang.Specification

class AbstractAsyncSpecificationInterceptorSpec extends Specification {

    void "validates async capable repository operations before superclass construction"() {
        given:
            def operations = Mock(AsyncCapableCriteriaRepositoryOperations)
            def asyncOperations = Mock(AsyncRepositoryOperations)
            operations.getConversionService() >> Mock(ConversionService)
            operations.async() >> asyncOperations
            operations.getCriteriaBuilder() >> Mock(CriteriaBuilder)

        when:
            def interceptor = new TestAsyncSpecificationInterceptor(operations)

        then:
            interceptor.asyncRepositoryOperations.is(asyncOperations)
    }

    void "throws before superclass construction when repository operations are not async capable"() {
        given:
            def operations = Mock(RepositoryOperations)

        when:
            new TestAsyncSpecificationInterceptor(operations)

        then:
            def e = thrown(DataAccessException)
            e.message.contains("does not support asynchronous operations")
            0 * operations.getConversionService()
    }

    private interface AsyncCapableCriteriaRepositoryOperations extends AsyncCapableRepository, CriteriaRepositoryOperations {
    }

    private static final class TestAsyncSpecificationInterceptor extends AbstractAsyncSpecificationInterceptor<Object, Object> {

        TestAsyncSpecificationInterceptor(RepositoryOperations operations) {
            super(operations)
        }

        AsyncRepositoryOperations getAsyncRepositoryOperations() {
            return asyncOperations
        }

        @Override
        Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
            return null
        }
    }
}
