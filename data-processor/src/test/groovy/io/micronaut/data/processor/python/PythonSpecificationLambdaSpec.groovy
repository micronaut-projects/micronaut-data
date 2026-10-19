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
import org.graalvm.polyglot.Context
import spock.lang.Specification

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Proxy

class PythonSpecificationLambdaSpec extends Specification {

    void "Python lambda can select the #method specification overload"() {
        given:
        def repository = Proxy.newProxyInstance(getClass().classLoader, [JpaSpecificationExecutor] as Class[],
            { proxy, calledMethod, arguments -> 0L } as InvocationHandler)
        def context = Context.newBuilder('python').allowAllAccess(true).build()
        context.getBindings('python').putMember('repo', repository)

        expect:
        context.eval('python', "repo.${method}(lambda root, cb: None)").asLong() == 0L

        cleanup:
        context.close()

        where:
        method << ['deleteAll', 'updateAll']
    }
}
