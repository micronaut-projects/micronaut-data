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

import static io.micronaut.data.model.vector.search.ScoringFunction.COSINE
import static io.micronaut.data.model.vector.search.ScoringFunction.DOT
import static io.micronaut.data.model.vector.search.ScoringFunction.L2_EUCLIDEAN

/**
 * {@link DefaultPreparedQuery#getParametersOfType} finds the arguments of a given type in a repository method call; it
 * is used on every call to find a {@link ScoringFunction} for vector search.
 *
 * <p>Each test simulates one call: {@code scoringFunctionsOf(param(ScoringFunction, COSINE), param(String, "x"))} is a
 * call of {@code method(ScoringFunction a, String b)} with {@code (COSINE, "x")}, each {@code param} giving the declared
 * type of a parameter and the value passed for it. The result is the scoring functions found, in parameter order.</p>
 */
class GetParametersOfTypeSpec extends Specification {

    void "a method without parameters has no scoring function"() {
        expect: "method()"
            scoringFunctionsOf().isEmpty()
    }

    void "values of the type are returned in parameter order, nulls and other types are skipped"() {
        expect: "method(ScoringFunction a, String b, ScoringFunction c, ScoringFunction d) called with (COSINE, \"x\", null, DOT)"
            scoringFunctionsOf(
                param(ScoringFunction, COSINE),
                param(String, "x"),
                param(ScoringFunction, null),
                param(ScoringFunction, DOT)
            ) == [COSINE, DOT]
    }

    void "a scoring function passed to an Object parameter is found"() {
        expect: "method(Object a, String b) called with (L2_EUCLIDEAN, \"y\")"
            scoringFunctionsOf(param(Object, L2_EUCLIDEAN), param(String, "y")) == [L2_EUCLIDEAN]
    }

    void "a value is converted only when its parameter is declared as the type"() {
        expect: "the String \"COSINE\" converts when the parameter is a ScoringFunction, not when it is an Object or a String"
            scoringFunctionsOf(param(ScoringFunction, "COSINE")) == [COSINE]
            scoringFunctionsOf(param(Object, "COSINE")).isEmpty()
            scoringFunctionsOf(param(String, "COSINE")).isEmpty()
    }

    void "a value that doesn't convert is skipped"() {
        expect: "method(ScoringFunction a) called with a String that names no scoring function"
            scoringFunctionsOf(param(ScoringFunction, "not a function")).isEmpty()
    }

    void "the result can't be modified"() {
        when:
            scoringFunctionsOf(param(ScoringFunction, COSINE)).add(DOT)
        then:
            thrown(UnsupportedOperationException)
    }

    /**
     * A parameter of the simulated method: its declared type and the value passed for it.
     */
    static Map<String, Object> param(Class<?> declaredType, Object value) {
        return [type: Argument.of(declaredType), value: value]
    }

    /**
     * Calls {@code getParametersOfType} for {@link ScoringFunction} on a method invocation with the given parameters.
     */
    List<ScoringFunction> scoringFunctionsOf(Map<String, Object>... params) {
        def invocation = Mock(MethodInvocationContext) {
            getArguments() >> (params.collect { it.type } as Argument[])
            getParameterValues() >> (params.collect { it.value } as Object[])
        }
        return DefaultPreparedQuery.getParametersOfType(Argument.of(ScoringFunction), invocation, ConversionService.SHARED)
    }
}
