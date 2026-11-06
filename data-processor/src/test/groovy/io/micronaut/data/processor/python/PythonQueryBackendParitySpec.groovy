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
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType
import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingPaths
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonQueryBackendParitySpec extends AbstractPythonTypeElementSpec {

    void "Python snake case query retains native property names and metadata for #backend"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
${repositoryImport}
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    first_name: str

${repositoryAnnotation}
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    ${asyncModifier}def findByFirst_nameLikeOrderByFirst_nameDesc(self, value: str) -> list[Item]: ...
    @abstractmethod
    ${asyncModifier}def find_by_first_name_like_order_by_first_name_desc(self, value: str) -> list[Item]: ...
""")
        def camel = definition.executableMethods.find { it.methodName == "findByFirst_nameLikeOrderByFirst_nameDesc" }
        def snake = definition.executableMethods.find { it.methodName == "find_by_first_name_like_order_by_first_name_desc" }

        expect:
        camel != null
        snake != null
        getQuery(snake) == getQuery(camel)
        getQuery(snake) == expectedQuery
        snake.getAnnotation(Query).getValues() == camel.getAnnotation(Query).getValues()
        getParameterPropertyPaths(snake) == getParameterPropertyPaths(camel)
        getParameterPropertyPaths(snake) == ["first_name"] as String[]
        getParameterBindingPaths(snake) == getParameterBindingPaths(camel)
        getParameterBindingPaths(snake) == [""] as String[]
        getParameterBindingIndexes(snake) == getParameterBindingIndexes(camel)
        getParameterBindingIndexes(snake) == ["0"] as String[]
        getOperationType(snake) == getOperationType(camel)
        getOperationType(snake).name() == "QUERY"
        getDataInterceptor(snake) == getDataInterceptor(camel)
        getDataInterceptor(snake) == interceptor
        getDataResultType(snake) == getDataResultType(camel)
        getDataResultType(snake) == "python.Item"

        where:
        backend | repositoryImport                                              | repositoryAnnotation                  | asyncModifier | expectedQuery                                                                                                            | interceptor
        "JPA"   | "from micronaut.data.annotation import Repository"              | "@Repository"                         | ""            | 'SELECT item_ FROM python.Item AS item_ WHERE (item_.first_name LIKE :p1) ORDER BY item_.first_name DESC'                     | "io.micronaut.data.intercept.FindAllInterceptor"
        "R2DBC" | "from micronaut.data.r2dbc.annotation import R2dbcRepository"    | '@R2dbcRepository(dialect="POSTGRES")' | "async "      | 'SELECT item_."id",item_."first_name" FROM "item" item_ WHERE (item_."first_name" LIKE $1) ORDER BY item_."first_name" DESC' | "io.micronaut.data.intercept.async.FindAllAsyncInterceptor"
    }
}
