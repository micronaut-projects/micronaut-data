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
}
