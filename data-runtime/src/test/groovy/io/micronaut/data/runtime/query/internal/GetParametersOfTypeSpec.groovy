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
package io.micronaut.data.runtime.query.internal

import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.model.vector.search.ScoringFunction
import spock.lang.Specification

class GetParametersOfTypeSpec extends Specification {

    List<ScoringFunction> parametersOf(List<Argument<?>> arguments, List<Object> values) {
        def context = Mock(MethodInvocationContext) {
            getArguments() >> (arguments as Argument[])
            getParameterValues() >> (values as Object[])
        }
        return DefaultPreparedQuery.getParametersOfType(Argument.of(ScoringFunction), context, ConversionService.SHARED)
    }

    void "no arguments give an empty list"() {
        expect:
            parametersOf([], []).isEmpty()
    }

    void "values of the type are returned in parameter order, nulls and other types are skipped"() {
        expect:
            parametersOf(
                [Argument.of(ScoringFunction), Argument.of(String), Argument.of(ScoringFunction), Argument.of(ScoringFunction)],
                [ScoringFunction.COSINE, "x", null, ScoringFunction.DOT]
            ) == [ScoringFunction.COSINE, ScoringFunction.DOT]
    }

    void "a value of the type passed through a broadly declared argument is found"() {
        expect:
            parametersOf([Argument.OBJECT_ARGUMENT, Argument.of(String)], [ScoringFunction.L2_EUCLIDEAN, "y"]) == [ScoringFunction.L2_EUCLIDEAN]
    }

    void "a value is converted only when its argument is declared as the type"() {
        expect: "a value of another type declared as the type converts, the same value declared as Object or String does not"
            parametersOf([Argument.of(ScoringFunction)], ["COSINE"]) == [ScoringFunction.COSINE]
            parametersOf([Argument.OBJECT_ARGUMENT], ["COSINE"]).isEmpty()
            parametersOf([Argument.of(String)], ["COSINE"]).isEmpty()
    }

    void "a value that doesn't convert is skipped"() {
        expect:
            parametersOf([Argument.of(ScoringFunction)], ["not a function"]).isEmpty()
    }

    void "the result can't be modified"() {
        when:
            parametersOf([Argument.of(ScoringFunction)], [ScoringFunction.COSINE]).add(ScoringFunction.DOT)
        then:
            thrown(UnsupportedOperationException)
    }
}
