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

    void "structural header projects native terminal keyword property #property"() {
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
    ${property}: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def findDistinct${camelProperty}(self) -> list[str]: ...
    @abstractmethod
    def findDistinct${camelProperty}ByCode(self, code: str) -> list[str]: ...
    @abstractmethod
    def find_distinct_${property}(self) -> list[str]: ...
    @abstractmethod
    def find_distinct_${property}_by_code(self, code: str) -> list[str]: ...
    @abstractmethod
    def find_distinct__${property}(self) -> list[str]: ...
    @abstractmethod
    def find_distinct__${property}__by_code(self, code: str) -> list[str]: ...
""")
        def camel = definition.executableMethods.find { it.methodName == "findDistinct${camelProperty}" }
        def filteredCamel = definition.executableMethods.find { it.methodName == "findDistinct${camelProperty}ByCode" }
        def snake = definition.executableMethods.find { it.methodName == "find_distinct_${property}" }
        def filteredSnake = definition.executableMethods.find { it.methodName == "find_distinct_${property}_by_code" }
        def explicit = definition.executableMethods.find { it.methodName == "find_distinct__${property}" }
        def filteredExplicit = definition.executableMethods.find { it.methodName == "find_distinct__${property}__by_code" }

        expect:
        getQuery(snake) == getQuery(camel)
        getQuery(explicit) == getQuery(camel)
        getQuery(snake) == "SELECT DISTINCT item_.`${property}` FROM `item` item_"
        getParameterPropertyPaths(snake) == [] as String[]
        getParameterBindingIndexes(snake) == [] as String[]
        getQuery(filteredSnake) == getQuery(filteredCamel)
        getQuery(filteredExplicit) == getQuery(filteredCamel)
        getQuery(filteredSnake) == "SELECT DISTINCT item_.`${property}` FROM `item` item_ WHERE (item_.`code` = ?)"
        getParameterPropertyPaths(filteredSnake) == ['code'] as String[]
        getParameterBindingIndexes(filteredSnake) == ['0'] as String[]

        where:
        property             | camelProperty
        'created_by'         | 'Created_by'
        'last_order_by'      | 'Last_order_by'
        'last_sort_by'       | 'Last_sort_by'
        'previous_returning' | 'Previous_returning'
    }

    void "terminal for update remains a structural clause after a projection header"() {
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
    last: str
    last_for_update: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def findDistinctLastForUpdate(self) -> list[str]: ...
    @abstractmethod
    def find_distinct_last_for_update(self) -> list[str]: ...
    @abstractmethod
    def find__last_for_update(self) -> list[str]: ...
''')
        def camel = definition.executableMethods.find { it.methodName == 'findDistinctLastForUpdate' }
        def snake = definition.executableMethods.find { it.methodName == 'find_distinct_last_for_update' }
        def literal = definition.executableMethods.find { it.methodName == 'find__last_for_update' }

        expect:
        getQuery(snake) == getQuery(camel)
        getQuery(snake) == 'SELECT DISTINCT item_.`last` FROM `item` item_ FOR UPDATE'
        getParameterPropertyPaths(snake) == [] as String[]
        getParameterBindingIndexes(snake) == [] as String[]
        getQuery(literal) == 'SELECT item_.`last_for_update` FROM `item` item_'
    }

    void "explicit projection endpoint preserves an internal structural keyword in a native property"() {
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
    created_by_name: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def findDistinctCreated_by_nameByCode(self, code: str) -> list[str]: ...
    @abstractmethod
    def find_distinct__created_by_name__by_code(self, code: str) -> list[str]: ...
''')
        def camel = definition.executableMethods.find { it.methodName == 'findDistinctCreated_by_nameByCode' }
        def explicit = definition.executableMethods.find { it.methodName == 'find_distinct__created_by_name__by_code' }

        expect:
        getQuery(explicit) == getQuery(camel)
        getQuery(explicit) == 'SELECT DISTINCT item_.`created_by_name` FROM `item` item_ WHERE (item_.`code` = ?)'
        getParameterPropertyPaths(explicit) == ['code'] as String[]
        getParameterBindingIndexes(explicit) == ['0'] as String[]
    }
}
