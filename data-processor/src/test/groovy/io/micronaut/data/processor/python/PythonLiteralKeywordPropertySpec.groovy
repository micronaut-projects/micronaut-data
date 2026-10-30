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

class PythonLiteralKeywordPropertySpec extends AbstractPythonTypeElementSpec {

    void "literal id property is used consistently in the predicate and ordering when the identity is named pk"() {
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
    pk: Annotated[int, Id]
    id: str
    code: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find_by_id(self, id: str) -> list[Item]: ...
    @abstractmethod
    def find_by_id_order_by_id(self, id: str) -> list[Item]: ...
    @abstractmethod
    def find_by_code_order_by_id(self, code: str) -> list[Item]: ...
    @abstractmethod
    def find_by_pk_order_by_pk(self, pk: int) -> list[Item]: ...
''')
        def byId = definition.executableMethods.find { it.methodName == 'find_by_id' }
        def byIdOrderById = definition.executableMethods.find { it.methodName == 'find_by_id_order_by_id' }
        def byCodeOrderById = definition.executableMethods.find { it.methodName == 'find_by_code_order_by_id' }
        def byPkOrderByPk = definition.executableMethods.find { it.methodName == 'find_by_pk_order_by_pk' }

        expect:
        getQuery(byId).endsWith('WHERE (item_.`id` = ?)')
        getParameterPropertyPaths(byId) == ['id'] as String[]
        getQuery(byIdOrderById).endsWith('WHERE (item_.`id` = ?) ORDER BY item_.`id` ASC')
        getParameterPropertyPaths(byIdOrderById) == ['id'] as String[]
        getQuery(byCodeOrderById).endsWith('WHERE (item_.`code` = ?) ORDER BY item_.`id` ASC')
        getQuery(byPkOrderByPk).endsWith('WHERE (item_.`pk` = ?) ORDER BY item_.`pk` ASC')
    }

    void "header keyword #snake is structural even when a property carries the keyword name"() {
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
    all: int
    one: int
    first: int
    distinct: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${camel}(self${parameters}) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> ${returnType}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)

        where:
        camel                 | snake                     | parameters    | returnType
        'findAll'             | 'find_all'                | ''            | 'list[Item]'
        'findAllByCode'       | 'find_all_by_code'        | ', code: str' | 'list[Item]'
        'deleteAll'           | 'delete_all'              | ''            | 'None'
        'findOneByCode'       | 'find_one_by_code'        | ', code: str' | 'Item | None'
        'findFirstByCode'     | 'find_first_by_code'      | ', code: str' | 'Item | None'
        'findDistinctByCode'  | 'find_distinct_by_code'   | ', code: str' | 'list[Item]'
        'findAllOrderByFirst' | 'find_all_order_by_first' | ''            | 'list[Item]'
        'findByFirst'         | 'find_by_first'           | ', first: int' | 'list[Item]'
    }

    void "escaped header keyword selects the literal property"() {
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
    all: int
    first: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find__all(self) -> list[int]: ...
    @abstractmethod
    def find__first_by_code(self, code: str) -> int: ...
''')
        def all = definition.executableMethods.find { it.methodName == 'find__all' }
        def first = definition.executableMethods.find { it.methodName == 'find__first_by_code' }

        expect:
        getQuery(all) == 'SELECT item_.`all` FROM `item` item_'
        getQuery(first) == 'SELECT item_.`first` FROM `item` item_ WHERE (item_.`code` = ?)'
        getParameterPropertyPaths(first) == ['code'] as String[]
    }
}
