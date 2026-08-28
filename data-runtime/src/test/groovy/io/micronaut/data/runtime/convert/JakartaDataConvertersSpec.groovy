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
package io.micronaut.data.runtime.convert

import io.micronaut.core.convert.DefaultMutableConversionService
import io.micronaut.data.model.Sort
import io.micronaut.data.model.jpa.criteria.impl.ExpressionOrder
import jakarta.data.Order
import spock.lang.Specification

/**
 * Covers the Jakarta Data 1.1 conversions whose semantics are easy to get subtly wrong.
 */
class JakartaDataConvertersSpec extends Specification {

    private DefaultMutableConversionService conversionService

    void setup() {
        conversionService = new DefaultMutableConversionService()
        new JakartaDataConverters({ null }, { null }).register(conversionService)
    }

    void "test Limit offsets are zero based"() {
        expect: "Limit.of(maxResults, offset) takes a zero based offset"
        convertLimit(jakarta.data.Limit.of(8, 24)).offset() == 24
        convertLimit(jakarta.data.Limit.of(8, 24)).maxResults() == 8

        and: "an offset of zero starts at the first result"
        convertLimit(jakarta.data.Limit.of(5, 0)).offset() == 0

        and: "a limit without an offset starts at the first result"
        convertLimit(jakarta.data.Limit.of(5)).offset() == 0
        convertLimit(jakarta.data.Limit.of(5)).maxResults() == 5

        and: "Limit.range is an inclusive one based range"
        convertLimit(jakarta.data.Limit.range(6, 10)).offset() == 5
        convertLimit(jakarta.data.Limit.range(6, 10)).maxResults() == 5
    }

    void "test null ordering is carried across from a Jakarta Data sort"() {
        when:
        Sort sort = convertSort(jakarta.data.Sort.asc("name").nullsLast())

        then:
        sort.orderBy.size() == 1
        sort.orderBy[0].property == "name"
        sort.orderBy[0].ascending
        sort.orderBy[0].nullOrdering == Sort.Order.NullOrdering.LAST

        when:
        sort = convertSort(jakarta.data.Sort.desc("name").nullsFirst())

        then:
        !sort.orderBy[0].ascending
        sort.orderBy[0].nullOrdering == Sort.Order.NullOrdering.FIRST

        when: "no null ordering is requested"
        sort = convertSort(jakarta.data.Sort.asc("name"))

        then:
        sort.orderBy[0].nullOrdering == Sort.Order.NullOrdering.NONE
    }

    void "test an order over several sorts keeps each sort's null ordering"() {
        when:
        Sort sort = conversionService.convertRequired(
                Order.by(jakarta.data.Sort.asc("name").nullsLast(), jakarta.data.Sort.desc("age")),
                Sort)

        then:
        sort.orderBy.size() == 2
        sort.orderBy[0].nullOrdering == Sort.Order.NullOrdering.LAST
        sort.orderBy[1].nullOrdering == Sort.Order.NullOrdering.NONE
        !sort.orderBy[1].ascending
    }

    void "test sorting by an expression produces an order that carries the expression"() {
        when: "the sort names an expression rather than an attribute"
        Sort sort = convertSort(jakarta.data.Sort.asc(_TestEntity.length))

        then: "the order is one only the criteria paths can resolve"
        sort.orderBy[0] instanceof ExpressionOrder
        sort.orderBy[0].ascending

        when: "the sort names a plain attribute"
        sort = convertSort(jakarta.data.Sort.asc("name"))

        then: "an ordinary order by property name is enough"
        !(sort.orderBy[0] instanceof ExpressionOrder)
    }

    private io.micronaut.data.model.Limit convertLimit(jakarta.data.Limit limit) {
        conversionService.convertRequired(limit, io.micronaut.data.model.Limit)
    }

    private Sort convertSort(jakarta.data.Sort<?> sort) {
        conversionService.convertRequired(sort, Sort)
    }

    static class TestEntity {
        String name
    }

    static class _TestEntity {
        static final jakarta.data.metamodel.TextAttribute<TestEntity> name =
                jakarta.data.metamodel.TextAttribute.of(TestEntity, "name")
        static final jakarta.data.expression.NumericExpression<TestEntity, Integer> length = name.length()
    }
}
