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

import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonQueryHeaderCollisionSpec extends AbstractPythonTypeElementSpec {

    void "structural finder header survives a native property named #snakeTail"() {
        given:
        def definition = buildBeanDefinition('python', 'ItemRepository$Intercepted', """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    age: int
    ${snakeTail}: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find${camelTail}(self, code: str) -> ${returnType}: ...
    @abstractmethod
    def find_${snakeTail}(self, code: str) -> ${returnType}: ...
    @abstractmethod
    def find_${explicitTail}(self, code: str) -> ${returnType}: ...
""")
        def camel = definition.executableMethods.find { it.methodName == "find${camelTail}" }
        def snake = definition.executableMethods.find { it.methodName == "find_${snakeTail}" }
        def explicit = definition.executableMethods.find { it.methodName == "find_${explicitTail}" }

        expect:
        camel != null
        snake != null
        explicit != null
        getQuery(explicit) == getQuery(camel)
        getQuery(snake) == getQuery(camel)
        getQuery(snake).contains(' WHERE ')
        getParameterPropertyPaths(snake) == ['code'] as String[]
        getParameterBindingIndexes(snake) == ['0'] as String[]

        where:
        camelTail      | snakeTail         | explicitTail         | returnType
        'FirstByCode'  | 'first_by_code'   | 'first__by_code'      | 'list[Item]'
        'Top3ByCode'   | 'top3_by_code'    | 'top3__by_code'       | 'list[Item]'
        'Top3ByCode'   | 'top_3_by_code'   | 'top_3__by_code'      | 'list[Item]'
        'First3ByCode' | 'first3_by_code'  | 'first3__by_code'     | 'list[Item]'
        'First3ByCode' | 'first_3_by_code' | 'first_3__by_code'    | 'list[Item]'
        'MaxAgeByCode' | 'max_age_by_code' | 'max__age__by_code'  | 'int'
    }

    void "structural header keeps the age projection and filter across nested property collisions for #snakeTail"() {
        given:
        def definition = buildBeanDefinition('python', 'ItemRepository$Intercepted', """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    age: int
    age_by_code: int
    ${snakeTail}: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find${camelTail}(self, code: str) -> ${returnType}: ...
    @abstractmethod
    def find_${snakeTail}(self, code: str) -> ${returnType}: ...
    @abstractmethod
    def find_${explicitTail}(self, code: str) -> ${returnType}: ...
""")
        def camel = definition.executableMethods.find { it.methodName == "find${camelTail}" }
        def snake = definition.executableMethods.find { it.methodName == "find_${snakeTail}" }
        def explicit = definition.executableMethods.find { it.methodName == "find_${explicitTail}" }

        expect:
        camel != null
        snake != null
        explicit != null
        getQuery(snake) == getQuery(camel)
        getQuery(explicit) == getQuery(camel)
        getQuery(snake).contains(' WHERE ')
        getParameterPropertyPaths(snake) == ['code'] as String[]
        getParameterBindingIndexes(snake) == ['0'] as String[]

        where:
        camelTail          | snakeTail              | explicitTail              | returnType
        'MaxAgeByCode'     | 'max_age_by_code'      | 'max__age__by_code'       | 'int'
        'MinAgeByCode'     | 'min_age_by_code'      | 'min__age__by_code'       | 'int'
        'SumAgeByCode'     | 'sum_age_by_code'      | 'sum__age__by_code'       | 'int'
        'AvgAgeByCode'     | 'avg_age_by_code'      | 'avg__age__by_code'       | 'float'
        'AllAgeByCode'     | 'all_age_by_code'      | 'all__age__by_code'       | 'list[int]'
        'DistinctAgeByCode'| 'distinct_age_by_code' | 'distinct__age__by_code'  | 'list[int]'
        'Top3AgeByCode'    | 'top3_age_by_code'     | 'top3__age__by_code'      | 'list[int]'
        'Top3AgeByCode'    | 'top_3_age_by_code'    | 'top_3__age__by_code'     | 'list[int]'
        'First3AgeByCode'  | 'first3_age_by_code'   | 'first3__age__by_code'    | 'list[int]'
        'First3AgeByCode'  | 'first_3_age_by_code'  | 'first_3__age__by_code'   | 'list[int]'
    }

    void "literal projections and explicit boundaries remain available beside structural headers"() {
        given:
        def definition = buildBeanDefinition('python', 'ItemRepository$Intercepted', '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    age: int
    age_by_code: int
    max_age_by_code: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find_age_by_code(self) -> list[int]: ...
    @abstractmethod
    def find__max_age_by_code(self) -> list[int]: ...
    @abstractmethod
    def find_max__age__by_code(self, code: str) -> int: ...
''')
        def literal = definition.executableMethods.find { it.methodName == 'find_age_by_code' }
        def escaped = definition.executableMethods.find { it.methodName == 'find__max_age_by_code' }
        def structural = definition.executableMethods.find { it.methodName == 'find_max__age__by_code' }

        expect:
        getQuery(literal) == 'SELECT item_.`age_by_code` FROM `item` item_'
        getParameterPropertyPaths(literal) == [] as String[]
        getParameterBindingIndexes(literal) == [] as String[]
        getQuery(escaped) == 'SELECT item_.`max_age_by_code` FROM `item` item_'
        getParameterPropertyPaths(escaped) == [] as String[]
        getParameterBindingIndexes(escaped) == [] as String[]
        getQuery(structural) == 'SELECT MAX(item_.`age`) FROM `item` item_ WHERE (item_.`code` = ?)'
        getParameterPropertyPaths(structural) == ['code'] as String[]
        getParameterBindingIndexes(structural) == ['0'] as String[]
    }
}
