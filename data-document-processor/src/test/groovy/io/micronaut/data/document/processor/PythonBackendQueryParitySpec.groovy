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
package io.micronaut.data.document.processor

import io.micronaut.data.annotation.Query
import io.micronaut.data.document.mongo.MongoAnnotations
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.document.processor.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.document.processor.TestUtils.getParameterBindingPaths
import static io.micronaut.data.document.processor.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.document.processor.TestUtils.getQuery

class PythonBackendQueryParitySpec extends AbstractPythonTypeElementSpec {

    void "Python #backend query #snake retains the query and bindings of #camel"() {
        given:
        def definition = repository(backend, """
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
        getQuery(camelMethod).contains(property)
        getQuery(camelMethod).contains(operator)
        getQuery(camelMethod).contains(order)
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        getParameterPropertyPaths(camelMethod) == [property] as String[]
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterBindingPaths(snakeMethod) == getParameterBindingPaths(camelMethod)
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        snakeMethod.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE) ==
            camelMethod.stringValue(DataMethod, DataMethod.META_MEMBER_RESULT_TYPE)
        snakeMethod.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get() ==
            camelMethod.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get()
        snakeMethod.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get() ==
            DataMethod.OperationType.QUERY
        snakeMethod.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType) ==
            camelMethod.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType)

        where:
        backend  | camel                                            | snake                                                 | property     | operator         | order
        "Mongo"  | "findByFirst_nameLikeOrderByFirst_nameDesc"       | "find_by_first_name_like_order_by_first_name_desc"    | "first_name" | '$regex'         | '$sort'
        "Cosmos" | "findByFirst_nameLikeOrderByFirst_nameDesc"       | "find_by_first_name_like_order_by_first_name_desc"    | "first_name" | " LIKE "         | " ORDER BY "
        "Mongo"  | "findByFirst_nameRegexOrderByFirst_nameDesc"      | "find_by_first_name_regex_order_by_first_name_desc"   | "first_name" | '$regex'         | '$sort'
        "Cosmos" | "findByTag_valuesArrayContainsOrderByFirst_name"  | "find_by_tag_values_array_contains_order_by_first_name" | "tag_values" | "ARRAY_CONTAINS" | " ORDER BY "
    }

    void "Python Mongo executor #camel assigns its native parameter roles"() {
        given:
        def definition = mongoExecutor(camel, parameters, returnType)
        def method = definition.executableMethods.find { it.methodName == camel }

        expect:
        method != null
        getQuery(method) == "{}"
        method.enumValue(DataMethod, DataMethod.META_MEMBER_OPERATION_TYPE, DataMethod.OperationType).get() == operation
        method.stringValue(DataMethod, DataMethod.META_MEMBER_INTERCEPTOR).get().endsWith(interceptor)
        roles.every { role, name -> method.stringValue(DataMethod, role).get() == name }
        operation != DataMethod.OperationType.UPDATE || method.stringValue(Query, "update").get() == "{}"

        where:
        camel       | parameters                    | returnType   | operation                     | interceptor             | roles
        "findAll"   | "options: MongoFindOptions"   | "list[Item]" | DataMethod.OperationType.QUERY  | "FindAllInterceptor"    | [(MongoAnnotations.FIND_OPTIONS_ROLE): "options"]
        "findOne"   | "options: MongoFindOptions"   | "Item"       | DataMethod.OperationType.QUERY  | "FindOneInterceptor"    | [(MongoAnnotations.FIND_OPTIONS_ROLE): "options"]
        "deleteAll" | "filter: Bson"                | "int"        | DataMethod.OperationType.DELETE | "DeleteAllInterceptor"  | [(MongoAnnotations.FILTER_ROLE): "filter"]
        "updateAll" | "filter: Bson, update: Bson"  | "int"        | DataMethod.OperationType.UPDATE | "UpdateInterceptor"     | [(MongoAnnotations.FILTER_ROLE): "filter", (MongoAnnotations.UPDATE_ROLE): "update"]
    }

    void "Python Mongo executor #snake retains the query and parameter roles of #camel"() {
        given:
        def definition = mongoExecutor("""
    @abstractmethod
    def ${camel}(self, ${parameters}) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, ${parameters}) -> ${returnType}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(camelMethod) == "{}"
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        snakeMethod.getAnnotation(DataMethod).getValues() == camelMethod.getAnnotation(DataMethod).getValues()

        where:
        camel       | snake        | parameters                   | returnType
        "findAll"   | "find_all"   | "options: MongoFindOptions"  | "list[Item]"
        "findOne"   | "find_one"   | "options: MongoFindOptions"  | "Item"
        "deleteAll" | "delete_all" | "filter: Bson"               | "int"
        "updateAll" | "update_all" | "filter: Bson, update: Bson" | "int"
    }

    private def mongoExecutor(String method, String parameters, String returnType) {
        mongoExecutor("""
    @abstractmethod
    def ${method}(self, ${parameters}) -> ${returnType}: ...
""")
    }

    private def mongoExecutor(String declarations) {
        repository("Mongo", declarations, """
from micronaut.data.mongodb.operations.options import MongoFindOptions
from org.bson.conversions import Bson
""")
    }

    private def repository(String backend, String declarations, String additionalImports = "") {
        buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.${backend == "Mongo" ? "mongodb" : "cosmos"}.annotation import ${backend}Repository
from micronaut.data.repository import GenericRepository
${additionalImports}

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[str, Id]
    first_name: str
    tag_values: list[str]

@${backend}Repository
class ItemRepository(GenericRepository[Item, str], ABC):
${declarations}
""")
    }
}
