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

import io.micronaut.data.annotation.Query
import io.micronaut.data.processor.visitors.finders.Restrictions
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonRegisteredRestrictionParitySpec extends AbstractPythonTypeElementSpec {

    void "every registered restriction has Python parity coverage"() {
        given:
        // Successful cases in PythonFullDerivedQuerySpec and PythonVectorSearchSpec.
        def existingSuccessful = [
            "After", "Before", "Between", "Contains", "ContainsIgnoreCase", "Containing", "ContainingIgnoreCase",
            "EndingWith", "EndingWithIgnoreCase", "EndsWith", "EndsWithIgnoreCase", "Equal", "EqualIgnoreCase",
            "Equals", "EqualsIgnoreCase", "False", "GreaterThan", "GreaterThanEqual", "GreaterThanEquals",
            "IgnoreCaseBetween", "Ilike", "In", "InList", "InRange", "IsEmpty", "IsNotEmpty", "IsNotNull", "IsNull",
            "LessThan", "LessThanEqual", "LessThanEquals", "Like", "NotEqual", "NotEquals", "NotIn", "NotInList",
            "NotNull", "Null", "StartingWith", "StartingWithIgnoreCase", "StartsWith", "StartsWithIgnoreCase", "True", "Within"
        ]
        // PythonFullDerivedQuerySpec verifies the shared SQL visitor rejection for these operators.
        def sharedSqlLimitations = ["ArrayContains", "CollectionContains", "Regex", "Rlike"]

        expect:
        Restrictions.PROPERTY_RESTRICTIONS_MAP.keySet() ==
            (existingSuccessful + sharedSqlLimitations + ["GeoWithin", "GeoIntersects", "Near"]) as Set
        Restrictions.RESTRICTIONS_MAP.keySet() == ["Ids"] as Set
    }

    void "Python spatial restriction #snake matches #camel on a supported SQL dialect"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.geo import Point
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    home_point: Point

@JdbcRepository(dialect="POSTGRES")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def findByHome_point${camel}(self${parameters}) -> list[Item]: ...
    @abstractmethod
    def find_by_home_point_${snake}(self${parameters}) -> list[Item]: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == "findByHome_point${camel}" }
        def snakeMethod = definition.executableMethods.find { it.methodName == "find_by_home_point_${snake}" }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).endsWith(" WHERE (${predicate})")
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterPropertyPaths(snakeMethod).first() == "home_point"
        getParameterBindingPaths(snakeMethod) == getParameterBindingPaths(camelMethod)
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterBindingIndexes(snakeMethod) == indexes as String[]
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getDataResultType(snakeMethod) == getDataResultType(camelMethod)
        getOperationType(snakeMethod) == getOperationType(camelMethod)

        where:
        camel           | snake            | parameters                             | predicate                                                          | indexes
        "GeoWithin"     | "geo_within"     | ", value: Point"                       | 'ST_CoveredBy(item_."home_point",ST_GeomFromGeoJSON(?))'               | ["0"]
        "GeoIntersects" | "geo_intersects" | ", value: Point"                       | 'ST_Intersects(item_."home_point",ST_GeomFromGeoJSON(?))'              | ["0"]
        "Near"          | "near"           | ", value: Point, distance: float"      | 'ST_DWithin(item_."home_point",ST_GeomFromGeoJSON(?),?)'               | ["0", "1"]
    }
}
