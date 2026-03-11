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
package io.micronaut.data.runtime.intercept

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.data.annotation.OptimisticLockConflict
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.exceptions.OptimisticLockExceptionHandler
import io.micronaut.data.intercept.annotation.DataMethod
import spock.lang.Specification

class OptimisticLockConflictPolicyResolverSpec extends Specification {

    void "test default policy is fail fast"() {
        given:
            def resolver = new OptimisticLockConflictPolicyResolver(null)
            MethodInvocationContext<Object, Object> context = Mock(MethodInvocationContext)
            context.enumValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_POLICY, OptimisticLockConflict.Policy) >> Optional.empty()
            def exception = new OptimisticLockException("boom")

        when:
            def policy = resolver.resolvePolicy(context)

        then:
            policy == OptimisticLockConflict.Policy.FAIL_FAST

        when:
            throw exception

        then:
            def e = thrown(OptimisticLockException)
            e.is(exception)
    }

    void "test delegate policy invokes handler"() {
        given:
            OptimisticLockExceptionHandler handler = Mock(OptimisticLockExceptionHandler)
            def resolver = new OptimisticLockConflictPolicyResolver(handler)
            MethodInvocationContext<Object, Object> context = Mock(MethodInvocationContext)
            context.enumValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_POLICY, OptimisticLockConflict.Policy) >> Optional.of(OptimisticLockConflict.Policy.DELEGATE)
            def exception = new OptimisticLockException("boom")

        when:
            def result = resolver.resolveDelegate(context, exception)

        then:
            result == "resolved"
            1 * handler.handle(exception, context) >> "resolved"
    }

    void "test reload and retry policy is resolved"() {
        given:
            def resolver = new OptimisticLockConflictPolicyResolver(null)
            MethodInvocationContext<Object, Object> context = Mock(MethodInvocationContext)
            context.enumValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_POLICY, OptimisticLockConflict.Policy) >> Optional.of(OptimisticLockConflict.Policy.RELOAD_AND_RETRY)

        when:
            def policy = resolver.resolvePolicy(context)

        then:
            policy == OptimisticLockConflict.Policy.RELOAD_AND_RETRY
    }

    void "test delegate policy without handler fails clearly"() {
        given:
            def resolver = new OptimisticLockConflictPolicyResolver(null)
            MethodInvocationContext<Object, Object> context = Mock(MethodInvocationContext)
            context.enumValue(DataMethod, DataMethod.META_MEMBER_OPTIMISTIC_LOCK_CONFLICT_POLICY, OptimisticLockConflict.Policy) >> Optional.of(OptimisticLockConflict.Policy.DELEGATE)
            def exception = new OptimisticLockException("boom")

        when:
            resolver.resolveDelegate(context, exception)

        then:
            def e = thrown(IllegalStateException)
            e.message.contains("No OptimisticLockExceptionHandler bean is configured")
            e.cause.is(exception)
    }
}
