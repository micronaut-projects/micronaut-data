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

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonPropertyAliasSpec extends AbstractPythonTypeElementSpec {

    void "Python aggregate alias does not resolve to an embedded property path"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Embeddable, Id, MappedEntity, Relation
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@Embeddable
@dataclass
class Limits:
    property0: int

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    age: int
    maxPython: Annotated[Limits, Relation(Relation.Kind.EMBEDDED)]

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def findMaxAgeByCode(self, code: str) -> int: ...
    @abstractmethod
    def find_max_age_by_code(self, code: str) -> int: ...
''')
        def camelMethod = definition.executableMethods.find { it.methodName == "findMaxAgeByCode" }
        def snakeMethod = definition.executableMethods.find { it.methodName == "find_max_age_by_code" }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).startsWith('SELECT MAX(item_.`age`) ')
        getQuery(snakeMethod).endsWith('WHERE (item_.`code` = ?)')
        getParameterPropertyPaths(snakeMethod) == ["code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["0"] as String[]
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
    }

    void "Python descriptive #snake retains the update filter and bindings of #camel"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
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
    first_name: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${camel}(self, value: str, first_name: str) -> int: ...
    @abstractmethod
    def ${snake}(self, value: str, first_name: str) -> int: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod) == 'UPDATE `item` SET `first_name`=? WHERE (`code` = ?)'
        getParameterPropertyPaths(snakeMethod) == ["first_name", "code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["1", "0"] as String[]
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        camel             | snake
        "updateItemByCode" | "update_item_by_code"
        "modifyItemByCode" | "modify_item_by_code"
    }
}
