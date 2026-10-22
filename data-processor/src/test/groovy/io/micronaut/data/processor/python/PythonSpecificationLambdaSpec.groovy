/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.processor.python

import io.micronaut.data.repository.jpa.JpaSpecificationExecutor
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification
import io.micronaut.data.repository.jpa.criteria.UpdateSpecification
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class PythonSpecificationLambdaSpec extends AbstractPythonTypeElementSpec {

    void "Python lambda can select the #method specification overload"() {
        given:
        def selectedParameterTypes = []
        def repository = Proxy.newProxyInstance(getClass().classLoader, [JpaSpecificationExecutor] as Class[],
            { proxy, calledMethod, arguments ->
                if (calledMethod.declaringClass == Object) {
                    switch (calledMethod.name) {
                        case 'toString': return 'Specification repository'
                        case 'hashCode': return System.identityHashCode(proxy)
                        case 'equals': return proxy.is(arguments[0])
                    }
                }
                assert calledMethod.name == method
                assert calledMethod.parameterTypes.toList() == [specificationType]
                selectedParameterTypes.add(calledMethod.parameterTypes[0])
                if (specificationType == PredicateSpecification) {
                    assert ((PredicateSpecification) arguments[0]).toPredicate(null, null) == null
                } else {
                    assert ((UpdateSpecification) arguments[0]).toPredicate(null, null, null) == null
                }
                0L
            } as InvocationHandler)
        def context = buildContext("""
from jakarta.inject import Singleton
from micronaut.context.annotation import Executable
from micronaut.data.repository.jpa import JpaSpecificationExecutor

@Singleton
class SpecificationCaller:
    @Executable
    def apply(self, repo: JpaSpecificationExecutor) -> bool:
        callback_arguments = []
        result = repo.${method}(lambda ${parameters}: callback_arguments.append((${parameters})))
        return result == 0 and callback_arguments == [${expectedArguments}]
""", true)
        def caller = context.getBean(context.classLoader.loadClass('python.SpecificationCaller')).asPolyglotValue()

        expect:
        caller.invokeMember('apply', repository).asBoolean()
        selectedParameterTypes == [specificationType]

        cleanup:
        context?.close()

        where:
        method      | specificationType      | parameters        | expectedArguments
        'deleteAll' | PredicateSpecification | 'root, cb'        | '(None, None)'
        'updateAll' | UpdateSpecification    | 'root, query, cb' | '(None, None, None)'
    }
}
