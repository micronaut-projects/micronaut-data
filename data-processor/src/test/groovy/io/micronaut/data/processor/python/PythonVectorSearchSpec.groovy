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
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonVectorSearchSpec extends AbstractPythonTypeElementSpec {

    void "Python ordinary search #snake delegates to the find matcher"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self, value: str) -> list[Item]: ...
    @abstractmethod
    def ${snake}(self, value: str) -> list[Item]: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).endsWith(queryEnding)
        getQuery(snakeMethod).startsWith(queryStart)
        !getQuery(snakeMethod).contains("mn_score")
        getParameterPropertyPaths(snakeMethod) == ["code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["0"] as String[]
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getDataInterceptor(snakeMethod) == "io.micronaut.data.intercept.FindAllInterceptor"

        where:
        camel                    | snake                       | queryStart        | queryEnding
        "searchDistinctByCode"   | "search_distinct_by_code"   | "SELECT DISTINCT " | 'WHERE (item_."code" = ?)'
        "searchFirstByCode"      | "search_first_by_code"      | "SELECT "          | 'WHERE (item_."code" = ?) LIMIT 1'
        "searchByCodeForUpdate"  | "search_by_code_for_update" | "SELECT "          | 'WHERE (item_."code" = ?) FOR UPDATE'
    }

    void "Python typed vector search #snake matches #camel for #dialect"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> SearchResults[Item]: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> SearchResults[Item]: ...
""", dialect)
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }
        def query = getQuery(snakeMethod)

        expect:
        camelMethod != null
        snakeMethod != null
        query == getQuery(camelMethod)
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        query.count(" AS mn_score") == 1
        query.contains(" AS mn_score FROM ")
        query.indexOf(" ORDER BY ") > query.indexOf(" WHERE ")
        query.substring(query.indexOf(" ORDER BY ")).contains(scoreToken)
        query.endsWith(queryEnding)
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterPropertyPaths(snakeMethod).contains("search_embedding")
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterBindingIndexes(snakeMethod).first() == "0"
        getParameterBindingIndexes(snakeMethod).last() == (camel.endsWith("OrderById") ? "1" : "0")
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getDataInterceptor(snakeMethod) == "io.micronaut.data.intercept.FindOneInterceptor"

        where:
        dialect    | camel                                     | snake                                           | parameters                                     | scoreToken         | queryEnding
        "POSTGRES" | "searchBySearch_embeddingNear"            | "search_by_search_embedding_near"               | ", vector: Vector, distance: float"             | " <=> "           | " ASC"
        "ORACLE"   | "searchBySearch_embeddingNear"            | "search_by_search_embedding_near"               | ", vector: Vector, distance: float"             | "VECTOR_DISTANCE(" | " ASC"
        "MYSQL"    | "searchBySearch_embeddingNear"            | "search_by_search_embedding_near"               | ", vector: Vector, distance: float"             | "DISTANCE("        | " ASC"
        "POSTGRES" | "searchBySearch_embeddingWithin"          | "search_by_search_embedding_within"             | ", vector: Vector, lower: float, upper: float"   | " <=> "           | " ASC"
        "POSTGRES" | "searchBySearch_embeddingBetween"         | "search_by_search_embedding_between"            | ", vector: Vector, lower: float, upper: float"   | " <=> "           | " ASC"
        "POSTGRES" | "searchTop2BySearch_embeddingNear"        | "search_top2_by_search_embedding_near"          | ", vector: Vector, distance: float"             | " <=> "           | " ASC LIMIT 2"
        "ORACLE"   | "searchTop2BySearch_embeddingNear"        | "search_top_2_by_search_embedding_near"         | ", vector: Vector, distance: float"             | "VECTOR_DISTANCE(" | " ASC FETCH NEXT 2 ROWS ONLY"
        "MYSQL"    | "searchFirst2BySearch_embeddingNear"      | "search_first_2_by_search_embedding_near"       | ", vector: Vector, distance: float"             | "DISTANCE("        | " ASC LIMIT 2"
        "POSTGRES" | "searchBySearch_embeddingNearOrderById"   | "search_by_search_embedding_near_order_by_id"  | ", vector: Vector, distance: float"             | 'item_."id"'      | " ASC"
    }

    void "Python typed vector search keeps the vector parameter offset after #snake"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> SearchResults[Item]: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> SearchResults[Item]: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }
        def query = getQuery(snakeMethod)

        expect:
        camelMethod != null
        snakeMethod != null
        query == getQuery(camelMethod)
        query.count(" AS mn_score") == 1
        query.contains(" AS mn_score FROM ")
        query.contains(predicateBeginning)
        query.endsWith(" ASC LIMIT 2")
        query.substring(query.indexOf(" ORDER BY ")).contains('item_."search_embedding" <=> ')
        getParameterBindingIndexes(snakeMethod) == ["1", "0", "1", "2", "1"] as String[]
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterPropertyPaths(snakeMethod).first() == "search_embedding"
        getParameterPropertyPaths(snakeMethod)[1] == precedingProperty
        getParameterPropertyPaths(snakeMethod).last() == "search_embedding"
        getDataInterceptor(snakeMethod) == "io.micronaut.data.intercept.FindOneInterceptor"

        where:
        camel                                                         | snake                                                                 | parameters                                                | precedingProperty     | predicateBeginning
        "searchTop2ByCodeAndSearch_embeddingNear"                      | "search_top2_by_code_and_search_embedding_near"                       | ", code: str, query: Vector, distance: float"              | "code"                | 'WHERE (item_."code" = ? AND '
        "searchTop2ByReference_embeddingAndSearch_embeddingNear"       | "search_top2_by_reference_embedding_and_search_embedding_near"        | ", reference: Vector, query: Vector, distance: float"      | "reference_embedding" | 'WHERE (item_."reference_embedding" = ? AND '
    }

    void "Python typed vector search ignores sort role when selecting the vector parameter"() {
        given:
        def definition = repository('''
    @abstractmethod
    def searchBySearch_embeddingNear(self, sort: Sort, query: Vector, distance: float) -> SearchResults[Item]: ...
    @abstractmethod
    def search_by_search_embedding_near(self, sort: Sort, query: Vector, distance: float) -> SearchResults[Item]: ...
''')
        def camelMethod = definition.executableMethods.find { it.methodName == "searchBySearch_embeddingNear" }
        def snakeMethod = definition.executableMethods.find { it.methodName == "search_by_search_embedding_near" }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).contains(" AS mn_score FROM ")
        getParameterBindingIndexes(snakeMethod) == ["1", "1", "2", "0"] as String[]
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterPropertyPaths(snakeMethod).first() == "search_embedding"
        getDataInterceptor(snakeMethod) == "io.micronaut.data.intercept.FindOneInterceptor"
    }

    private def repository(String declarations, String dialect = "POSTGRES") {
        buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model import Sort
from micronaut.data.model.vector import FloatVector, Vector
from micronaut.data.model.vector.search import SearchResults
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    reference_embedding: FloatVector
    search_embedding: FloatVector

@JdbcRepository(dialect="${dialect}")
class ItemRepository(GenericRepository[Item, int], ABC):
${declarations}
""")
    }
}
