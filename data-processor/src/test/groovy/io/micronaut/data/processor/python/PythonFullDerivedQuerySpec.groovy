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
import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonFullDerivedQuerySpec extends AbstractPythonTypeElementSpec {

    void "Python restriction #snake has the same SQL and bindings as #camel"() {
        given:
        def definition = repository("""
    @abstractmethod
    def findBy${camel}(self${parameters}) -> list[Item]: ...
    @abstractmethod
    def find_by_${snake}(self${parameters}) -> list[Item]: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == "findBy${camel}" }
        def snakeMethod = definition.executableMethods.find { it.methodName == "find_by_${snake}" }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).contains(" WHERE ")
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        camel                                  | snake                                       | parameters
        "First_name"                           | "first_name"                                | ", value: str"
        "First_nameEquals"                     | "first_name_equals"                         | ", value: str"
        "First_nameEqual"                      | "first_name_equal"                          | ", value: str"
        "First_nameNotEquals"                  | "first_name_not_equals"                     | ", value: str"
        "First_nameNotEqual"                   | "first_name_not_equal"                      | ", value: str"
        "First_nameNot"                        | "first_name_not"                            | ", value: str"
        "First_nameIgnoreCase"                 | "first_name_ignore_case"                    | ", value: str"
        "First_nameEqualsIgnoreCase"           | "first_name_equals_ignore_case"             | ", value: str"
        "First_nameEqualIgnoreCase"            | "first_name_equal_ignore_case"              | ", value: str"
        "First_nameNotIgnoreCase"              | "first_name_not_ignore_case"                | ", value: str"
        "AgeGreaterThan"                       | "age_greater_than"                          | ", value: int"
        "AgeGreaterThanEquals"                 | "age_greater_than_equals"                   | ", value: int"
        "AgeGreaterThanEqual"                  | "age_greater_than_equal"                    | ", value: int"
        "AgeLessThan"                          | "age_less_than"                             | ", value: int"
        "AgeLessThanEquals"                    | "age_less_than_equals"                      | ", value: int"
        "AgeLessThanEqual"                     | "age_less_than_equal"                       | ", value: int"
        "Created_atAfter"                      | "created_at_after"                          | ", value: int"
        "Created_atBefore"                     | "created_at_before"                         | ", value: int"
        "AgeBetween"                           | "age_between"                               | ", lower: int, upper: int"
        "AgeInRange"                           | "age_in_range"                              | ", lower: int, upper: int"
        "AgeNotBetween"                        | "age_not_between"                           | ", lower: int, upper: int"
        "First_nameIgnoreCaseBetween"          | "first_name_ignore_case_between"            | ", lower: str, upper: str"
        "CodeIn"                               | "code_in"                                   | ", values: list[str]"
        "CodeInList"                           | "code_in_list"                              | ", values: list[str]"
        "CodeNotIn"                            | "code_not_in"                               | ", values: list[str]"
        "CodeNotInList"                        | "code_not_in_list"                          | ", values: list[str]"
        "First_nameLike"                       | "first_name_like"                           | ", value: str"
        "First_nameNotLike"                    | "first_name_not_like"                       | ", value: str"
        "First_nameIlike"                      | "first_name_ilike"                          | ", value: str"
        "First_nameContains"                   | "first_name_contains"                       | ", value: str"
        "First_nameContaining"                 | "first_name_containing"                     | ", value: str"
        "First_nameContainsIgnoreCase"         | "first_name_contains_ignore_case"           | ", value: str"
        "First_nameContainingIgnoreCase"       | "first_name_containing_ignore_case"         | ", value: str"
        "First_nameStartsWith"                 | "first_name_starts_with"                    | ", value: str"
        "First_nameStartingWith"               | "first_name_starting_with"                  | ", value: str"
        "First_nameStartsWithIgnoreCase"       | "first_name_starts_with_ignore_case"        | ", value: str"
        "First_nameStartingWithIgnoreCase"     | "first_name_starting_with_ignore_case"      | ", value: str"
        "First_nameEndsWith"                   | "first_name_ends_with"                      | ", value: str"
        "First_nameEndingWith"                 | "first_name_ending_with"                    | ", value: str"
        "First_nameEndsWithIgnoreCase"         | "first_name_ends_with_ignore_case"          | ", value: str"
        "First_nameEndingWithIgnoreCase"       | "first_name_ending_with_ignore_case"        | ", value: str"
        "First_nameNull"                       | "first_name_null"                           | ""
        "First_nameIsNull"                     | "first_name_is_null"                        | ""
        "First_nameNotNull"                    | "first_name_not_null"                       | ""
        "First_nameIsNotNull"                  | "first_name_is_not_null"                    | ""
        "First_nameIsEmpty"                    | "first_name_is_empty"                       | ""
        "First_nameIsNotEmpty"                 | "first_name_is_not_empty"                   | ""
        "EnabledTrue"                          | "enabled_true"                              | ""
        "EnabledFalse"                         | "enabled_false"                             | ""
        "First_nameAndLast_name"               | "first_name_and_last_name"                  | ", first: str, last: str"
        "First_nameOrLast_name"                | "first_name_or_last_name"                   | ", first: str, last: str"
        "AgeGreaterThanAndEnabledTrue"         | "age_greater_than_and_enabled_true"         | ", value: int"
        "First_nameAndAgeGreaterThanOrCode"    | "first_name_and_age_greater_than_or_code"   | ", first: str, value: int, code: str"
    }

    void "Python operation #snake has the same query and interceptor as #camel"() {
        given:
        def definition = repository("""
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
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getOperationType(snakeMethod) == getOperationType(camelMethod)

        where:
        camel                                    | snake                                          | parameters                              | returnType
        "countByFirst_nameAndAgeGreaterThan"     | "count_by_first_name_and_age_greater_than"     | ", first: str, value: int"               | "int"
        "countDistinctFirst_nameByEnabledTrue"   | "count_distinct_first_name_by_enabled_true"   | ""                                      | "int"
        "countAllByAgeBetween"                  | "count_all_by_age_between"                    | ", lower: int, upper: int"               | "int"
        "existsByCodeInList"                    | "exists_by_code_in_list"                      | ", values: list[str]"                    | "bool"
        "existsByFirst_nameIgnoreCase"          | "exists_by_first_name_ignore_case"            | ", value: str"                           | "bool"
        "deleteByCodeNotInList"                 | "delete_by_code_not_in_list"                  | ", values: list[str]"                    | "int"
        "deleteAllByAgeLessThan"                | "delete_all_by_age_less_than"                 | ", value: int"                           | "int"
        "removeByFirst_nameAndLast_name"        | "remove_by_first_name_and_last_name"          | ", first: str, last: str"                 | "int"
        "eraseByEnabledFalse"                  | "erase_by_enabled_false"                      | ""                                      | "int"
        "eliminateByAgeBetween"                | "eliminate_by_age_between"                    | ", lower: int, upper: int"               | "int"
        "updateByCodeAndAgeGreaterThan"         | "update_by_code_and_age_greater_than"         | ", value: str, lower: int, first_name: str" | "int"
        "modifyByEnabledFalse"                 | "modify_by_enabled_false"                     | ", first_name: str"                      | "int"
        "updateOneByCode"                      | "update_one_by_code"                          | ", value: str, first_name: str"           | "int"
        "findById"                             | "find_by_id"                                  | ", id: int"                              | "Item | None"
        "findByIds"                            | "find_by_ids"                                 | ", ids: list[int]"                        | "list[Item]"
        "existsById"                           | "exists_by_id"                                | ", id: int"                              | "bool"
        "deleteById"                           | "delete_by_id"                                | ", id: int"                              | "int"
        "saveOne"                              | "save_one"                                    | ", item: Item"                           | "Item"
        "insertItem"                           | "insert_item"                                 | ", item: Item"                           | "Item"
        "persistItem"                          | "persist_item"                                | ", item: Item"                           | "Item"
        "storeItem"                            | "store_item"                                  | ", item: Item"                           | "Item"
        "saveAll"                              | "save_all"                                    | ", items: list[Item]"                     | "list[Item]"
        "updateOne"                            | "update_one"                                  | ", item: Item"                           | "Item"
        "updateAll"                            | "update_all"                                  | ", items: list[Item]"                     | "int"
        "deleteOne"                            | "delete_one"                                  | ", item: Item"                           | "int"
        "deleteAll"                            | "delete_all"                                  | ", items: list[Item]"                     | "int"
        "upsertAll"                            | "upsert_all"                                  | ", items: list[Item]"                     | "list[Item]"
    }

    void "Python query shape #snake matches #camel"() {
        given:
        def definition = repository("""
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
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        camel                                                  | snake                                                         | parameters                    | returnType
        "findFirstByCode"                                      | "find_first_by_code"                                          | ", value: str"                | "Item | None"
        "findTop3ByEnabledTrue"                                | "find_top3_by_enabled_true"                                   | ""                            | "list[Item]"
        "findTop3ByEnabledTrue"                                | "find_top_3_by_enabled_true"                                  | ""                            | "list[Item]"
        "findFirst2ByAgeGreaterThanOrderByLast_nameDesc"        | "find_first2_by_age_greater_than_order_by_last_name_desc"      | ", value: int"                | "list[Item]"
        "findFirst2ByAgeGreaterThanOrderByLast_nameDesc"        | "find_first_2_by_age_greater_than_order_by_last_name_desc"     | ", value: int"                | "list[Item]"
        "findDistinctByFirst_name"                             | "find_distinct_by_first_name"                                 | ", value: str"                | "list[Item]"
        "findItemByCode"                                       | "find_item_by_code"                                           | ", value: str"                | "Item | None"
        "findListByCode"                                       | "find_list_by_code"                                           | ", value: str"                | "list[Item]"
        "findAllOrderByCreated_at"                             | "find_all_order_by_created_at"                                | ""                            | "list[Item]"
        "findByCodeOrderByLast_nameDescAndFirst_nameAsc"        | "find_by_code_order_by_last_name_desc_and_first_name_asc"      | ", value: str"                | "list[Item]"
        "findByCodeSortByCreated_atDesc"                        | "find_by_code_sort_by_created_at_desc"                        | ", value: str"                | "list[Item]"
        "findByCodeForUpdate"                                  | "find_by_code_for_update"                                     | ", value: str"                | "list[Item]"
        "findFirst_name"                                      | "find_first_name"                                             | ""                            | "list[str]"
        "findFirst_nameByCode"                                | "find_first_name_by_code"                                     | ", value: str"                | "str"
        "findDistinctFirst_nameByEnabledTrue"                 | "find_distinct_first_name_by_enabled_true"                    | ""                            | "list[str]"
        "findMaxAgeByEnabledTrue"                             | "find_max_age_by_enabled_true"                                | ""                            | "int"
        "findMinCreated_atByCode"                             | "find_min_created_at_by_code"                                | ", value: str"                | "int"
        "findSumAgeByCode"                                    | "find_sum_age_by_code"                                        | ", value: str"                | "int"
        "findAvgAgeByEnabledTrue"                             | "find_avg_age_by_enabled_true"                                | ""                            | "float"
        "listByFirst_name"                                   | "list_by_first_name"                                          | ", value: str"                | "list[Item]"
        "getByCode"                                          | "get_by_code"                                                 | ", value: str"                | "Item | None"
        "queryByLast_name"                                   | "query_by_last_name"                                          | ", value: str"                | "list[Item]"
        "retrieveByAgeGreaterThan"                           | "retrieve_by_age_greater_than"                                | ", value: int"                | "list[Item]"
        "readByEnabledTrue"                                  | "read_by_enabled_true"                                        | ""                            | "list[Item]"
        "searchByCodeContainsIgnoreCase"                      | "search_by_code_contains_ignore_case"                         | ", value: str"                | "list[Item]"
    }

    void "Python PostgreSQL returning query #snake matches #camel"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> ${returnType}: ...
""", "POSTGRES")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).contains(" RETURNING ")
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getOperationType(snakeMethod) == getOperationType(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        camel                                  | snake                                       | parameters                         | returnType
        "saveReturning"                        | "save_returning"                            | ", item: Item"                     | "Item"
        "insertReturning"                      | "insert_returning"                          | ", item: Item"                     | "Item"
        "saveAllReturning"                     | "save_all_returning"                        | ", items: list[Item]"               | "list[Item]"
        "updateReturning"                      | "update_returning"                          | ", item: Item"                     | "Item"
        "updateAllReturning"                   | "update_all_returning"                      | ", items: list[Item]"               | "list[Item]"
        "updateByCodeReturning"                | "update_by_code_returning"                  | ", value: str, first_name: str"      | "list[Item]"
        "updateByCodeReturningFirst_name"      | "update_by_code_returning_first_name"       | ", value: str, first_name: str"      | "list[str]"
        "deleteReturning"                      | "delete_returning"                          | ", item: Item"                     | "Item"
        "deleteAllReturning"                   | "delete_all_returning"                      | ", items: list[Item]"               | "list[Item]"
        "deleteByAgeLessThanReturning"         | "delete_by_age_less_than_returning"         | ", value: int"                      | "list[Item]"
        "deleteByCodeReturningFirst_name"      | "delete_by_code_returning_first_name"       | ", value: str"                      | "list[str]"
    }

    void "Python literal property #methodName takes precedence over grammar"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${methodName}(self, value: str) -> list[Item]: ...
""", "H2", """
    code_like: str
    code_not: str
    code_and_first_name: str
    code_returning: str
    first_name_ignore_case: str
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        expect:
        method != null
        getQuery(method).endsWith("WHERE (item_.`${property}` = ?)")

        where:
        methodName                       | property
        "find_by_code_like"              | "code_like"
        "find_by_code_not"               | "code_not"
        "find_by_code_and_first_name"    | "code_and_first_name"
        "find_by_code_returning"         | "code_returning"
        "find_by_first_name_ignore_case" | "first_name_ignore_case"
    }

    void "Python explicit operator separator selects #methodName instead of a literal property"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${methodName}(self, value: str) -> list[Item]: ...
""", "H2", """
    code_like: str
    code_not: str
    first_name_ignore_case: str
    code_: Annotated[str, MappedProperty("literal_code_suffix")]
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        expect:
        method != null
        getQuery(method).endsWith(queryEnding)
        getParameterPropertyPaths(method) == [methodName.contains("first_name") ? "first_name" : "code"] as String[]

        where:
        methodName                       | queryEnding
        "find_by_code__like"             | 'WHERE (item_.`code` LIKE ?)'
        "find_by_code__not"              | 'WHERE (item_.`code` != ?)'
        "find_by_first_name__ignore_case" | 'WHERE (LOWER(item_.`first_name`) = LOWER(?))'
    }

    void "Python rejects incomplete or unsupported mutating query #methodName"() {
        when:
        repository("""
    @abstractmethod
    def ${methodName}(self${parameters}) -> int: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message

        where:
        methodName                   | parameters
        "update_by_"                 | ", value: str, first_name: str"
        "delete_by_"                 | ", value: str"
        "update_by_missing"          | ", value: str, first_name: str"
        "delete_by_missing"          | ", value: str"
        "update_by_code_and_"        | ", value: str, first_name: str"
        "delete_by_code_or_"         | ", value: str"
        "update_by_code__"           | ", value: str, first_name: str"
        "delete_by_code__"           | ", value: str"
        "update2_by_code"            | ", value: str, first_name: str"
        "updateé_by_code"            | ", value: str, first_name: str"
        "updateOne_by_code"          | ", value: str, first_name: str"
        "updateOne_by_codeByFirst_name" | ", value: str, first_name: str"
    }

    void "Python explicit logical separator bypasses a literal compound property"() {
        given:
        def definition = repository('''
    @abstractmethod
    def find_by_code__and__first_name(self, code: str, first: str) -> list[Item]: ...
''', "H2", '''
    code_and_first_name: str
''')
        def method = definition.executableMethods.find { it.methodName == "find_by_code__and__first_name" }

        expect:
        method != null
        getQuery(method).endsWith('WHERE (item_.`code` = ? AND item_.`first_name` = ?)')
        getParameterPropertyPaths(method) == ["code", "first_name"] as String[]
    }

    void "Python explicit returning separator bypasses a literal returning property"() {
        given:
        def definition = repository('''
    @abstractmethod
    def update_by_code__returning(self, value: str, first_name: str) -> list[Item]: ...
''', "POSTGRES", '''
    code_returning: str
''')
        def method = definition.executableMethods.find { it.methodName == "update_by_code__returning" }

        expect:
        method != null
        getQuery(method).startsWith('UPDATE "item" SET "first_name"=? WHERE ("code" = ?) RETURNING ')
        getParameterPropertyPaths(method) == ["first_name", "code"] as String[]
    }

    void "Python explicit association separator preserves native multiword properties"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity, Relation
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("owner")
@dataclass
class Owner:
    id: Annotated[int, Id]
    first_name: str

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    home_owner_first_name: str
    home_owner: Annotated[Owner, Relation(Relation.Kind.MANY_TO_ONE)]

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find_by_home_owner_first_name(self, value: str) -> list[Item]: ...
    @abstractmethod
    def find_by_home_owner__first_name(self, value: str) -> list[Item]: ...
''')
        def literal = definition.executableMethods.find { it.methodName == "find_by_home_owner_first_name" }
        def association = definition.executableMethods.find { it.methodName == "find_by_home_owner__first_name" }

        expect:
        literal != null
        association != null
        getQuery(literal).endsWith('WHERE (item_.`home_owner_first_name` = ?)')
        getQuery(association).endsWith('WHERE (item_home_owner_.`first_name` = ?)')
        getParameterPropertyPaths(association) == ["home_owner.first_name"] as String[]
    }

    void "Python registered restriction #snake retains the SQL backend limitation of #camel"() {
        when:
        repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> list[Item]: ...
""", dialect)

        then:
        def camelFailure = thrown(RuntimeException)
        camelFailure.message.contains(backendMessage)

        when:
        repository("""
    @abstractmethod
    def ${snake}(self${parameters}) -> list[Item]: ...
""", dialect)

        then:
        def snakeFailure = thrown(RuntimeException)
        snakeFailure.message.contains(backendMessage)
        !snakeFailure.message.contains("Unsupported Python snake_case derived query")

        where:
        dialect | camel                          | snake                               | parameters                       | backendMessage
        "H2"    | "findByFirst_nameRegex"        | "find_by_first_name_regex"          | ", value: str"                   | "Regexp is not supported by this implementation"
        "H2"    | "findByFirst_nameRlike"        | "find_by_first_name_rlike"          | ", value: str"                   | "Regexp is not supported by this implementation"
        "H2"    | "findByCodeArrayContains"      | "find_by_code_array_contains"       | ", value: str"                   | "ArrayContains is not supported by this implementation"
        "H2"    | "findByCodeCollectionContains" | "find_by_code_collection_contains" | ", value: str"                   | "ArrayContains is not supported by this implementation"
        "ANSI"  | "findByCodeGeoWithin"          | "find_by_code_geo_within"           | ", value: str"                   | "GeoWithin is not supported by dialect: ANSI"
        "ANSI"  | "findByCodeGeoIntersects"      | "find_by_code_geo_intersects"       | ", value: str"                   | "GeoIntersects is not supported by dialect: ANSI"
        "ANSI"  | "findByCodeNear"               | "find_by_code_near"                 | ", value: str, distance: float"   | "Near is not supported by dialect: ANSI"
    }

    void "Python vector #snake matches #camel for #dialect"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.vector import FloatVector, Vector
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    search_embedding: FloatVector

@JdbcRepository(dialect="${dialect}")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${camel}(self${parameters}) -> list[Item]: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> list[Item]: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).contains(" WHERE ")
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)

        where:
        dialect    | camel                                | snake                                     | parameters
        "POSTGRES" | "findBySearch_embeddingNear"         | "find_by_search_embedding_near"           | ", vector: Vector, distance: float"
        "ORACLE"   | "findBySearch_embeddingNear"         | "find_by_search_embedding_near"           | ", vector: Vector, distance: float"
        "MYSQL"    | "findBySearch_embeddingNear"         | "find_by_search_embedding_near"           | ", vector: Vector, distance: float"
        "POSTGRES" | "findBySearch_embeddingWithin"       | "find_by_search_embedding_within"         | ", vector: Vector, lower: float, upper: float"
        "ORACLE"   | "findBySearch_embeddingWithin"       | "find_by_search_embedding_within"         | ", vector: Vector, lower: float, upper: float"
        "MYSQL"    | "findBySearch_embeddingWithin"       | "find_by_search_embedding_within"         | ", vector: Vector, lower: float, upper: float"
        "POSTGRES" | "findBySearch_embeddingBetween"      | "find_by_search_embedding_between"        | ", vector: Vector, lower: float, upper: float"
        "ORACLE"   | "findBySearch_embeddingBetween"      | "find_by_search_embedding_between"        | ", vector: Vector, lower: float, upper: float"
        "MYSQL"    | "findBySearch_embeddingBetween"      | "find_by_search_embedding_between"        | ", vector: Vector, lower: float, upper: float"
        "POSTGRES" | "searchTop2BySearch_embeddingNear"   | "search_top2_by_search_embedding_near"     | ", vector: Vector, distance: float"
    }

    void "Python upsert rejects derived suffix #methodName"() {
        when:
        repository("""
    @abstractmethod
    def ${methodName}(self, item: Item) -> Item: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message

        where:
        methodName << ["upsert_by_code", "upsert_one", "upsert_returning", "upsert_all_by_code"]
    }

    void "Python parser preserves literal and generated-alias collision fields for #methodName"() {
        given:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity, MappedProperty
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    pk: Annotated[int, Id]
    id: str
    class_: Annotated[str, MappedProperty("literal_class")]
    code: str
    age: int
    pythonProperty0: str
    pythonProperty1: int
    maxPythonProperty0: int
    maxPythonProperty1: int
    display_AndName: Annotated[str, MappedProperty("literal_display")]
    rankingDesc: Annotated[int, MappedProperty("literal_ranking")]

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        expect:
        method != null
        getQuery(method).startsWith(queryStart)
        getQuery(method).endsWith(queryEnding)
        getParameterPropertyPaths(method) == paths as String[]

        where:
        methodName                       | parameters                   | returnType   | queryStart                         | queryEnding                                               | paths
        "find_by_class_"                 | ", value: str"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`literal_class` = ?)'                        | ["class_"]
        "find_by_id"                     | ", value: str"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`id` = ?)'                                   | ["id"]
        "delete_by_id"                   | ", value: str"               | "int"        | 'DELETE  FROM `item` '             | 'WHERE (`id` = ?)'                                        | ["id"]
        "update_by_id"                   | ", value: str, code: str"    | "int"        | 'UPDATE `item` SET `code`=? '      | 'WHERE (`id` = ?)'                                        | ["code", "id"]
        "find_by_code_and_age"           | ", value: str, age: int"     | "list[Item]" | "SELECT "                         | 'WHERE (item_.`code` = ? AND item_.`age` = ?)'               | ["code", "age"]
        "find_max_age_by_code"           | ", value: str"               | "int"        | 'SELECT MAX(item_.`age`) '         | 'WHERE (item_.`code` = ?)'                                 | ["code"]
        "find_by_pythonProperty0"        | ", value: str"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`python_property0` = ?)'                      | ["pythonProperty0"]
        "find_by_pythonProperty1"        | ", value: int"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`python_property1` = ?)'                      | ["pythonProperty1"]
        "find_by_display_AndName"        | ", value: str"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`literal_display` = ?)'                       | ["display_AndName"]
        "find_by_rankingDesc"            | ", value: int"               | "list[Item]" | "SELECT "                         | 'WHERE (item_.`literal_ranking` = ?)'                       | ["rankingDesc"]
        "find_by_code_order_by_rankingDesc" | ", value: str"            | "list[Item]" | "SELECT "                         | 'WHERE (item_.`code` = ?) ORDER BY item_.`literal_ranking` ASC' | ["code"]
    }

    void "Python derived criteria preserve argument validation for #snake"() {
        when:
        repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> list[Item]: ...
""")

        then:
        def camelFailure = thrown(RuntimeException)
        camelFailure.message.contains(diagnostic)

        when:
        repository("""
    @abstractmethod
    def ${snake}(self${parameters}) -> list[Item]: ...
""")

        then:
        def snakeFailure = thrown(RuntimeException)
        snakeFailure.message.contains(diagnostic)

        where:
        camel                      | snake                             | parameters       | diagnostic
        "findByAgeBetween"         | "find_by_age_between"             | ", value: int"  | "Insufficient arguments to method criteria: Between"
        "findByCodeInList"         | "find_by_code_in_list"            | ""              | "Insufficient arguments to method criteria: InList"
        "findByFirst_nameContains" | "find_by_first_name_contains"     | ", value: int"  | "is not compatible with property"
        "findByAgeContains"        | "find_by_age_contains"            | ", value: int"  | "Expected a string expression"
    }

    void "Python rejects empty ordering and incomplete operator write filters for #methodName"() {
        when:
        repository("""
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message

        where:
        methodName                     | parameters                  | returnType
        "find_by_code_order_by_"       | ", value: str"              | "list[Item]"
        "find_by_code_sort_by_"        | ", value: str"              | "list[Item]"
        "find_by_code_order_by_missing" | ", value: str"             | "list[Item]"
        "update_by_code__and"          | ", value: str, code: str"   | "int"
        "delete_by_code__or"           | ", value: str"              | "int"
        "update_by_code_and"           | ", value: str, code: str"   | "int"
        "delete_by_code_or"            | ", value: str"              | "int"
        "update_by_code__not_"         | ", value: str, code: str"   | "int"
        "delete_by_age_between"        | ", value: int"              | "int"
    }

    void "Python generated property aliases do not shadow native fields or aggregates for #snake"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> ${returnType}: ...
""", "H2", '''
    pythonProperty0: str
    pythonProperty1: int
    maxPythonProperty0: int
    maxPythonProperty1: int
''')
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod).startsWith(queryStart)
        getQuery(snakeMethod).endsWith(queryEnding)
        getParameterPropertyPaths(snakeMethod) == paths as String[]

        where:
        camel              | snake                   | parameters                | returnType   | queryStart                 | queryEnding                                  | paths
        "findByCodeAndAge" | "find_by_code_and_age"  | ", code: str, age: int"  | "list[Item]" | "SELECT "                 | 'WHERE (item_.`code` = ? AND item_.`age` = ?)' | ["code", "age"]
        "findMaxAgeByCode" | "find_max_age_by_code"  | ", code: str"            | "int"        | 'SELECT MAX(item_.`age`) ' | 'WHERE (item_.`code` = ?)'                   | ["code"]
    }

    void "Python formerly unsupported #snake retains its filter and bindings"() {
        given:
        def definition = repository("""
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
        getQuery(snakeMethod).endsWith(queryEnding)
        getParameterPropertyPaths(snakeMethod) == paths as String[]

        where:
        camel                      | snake                       | parameters                          | returnType   | queryEnding                                             | paths
        "updateFirst_nameByCode"   | "update_first_name_by_code" | ", value: str, first_name: str"     | "int"        | 'UPDATE `item` SET `first_name`=? WHERE (`code` = ?)'     | ["first_name", "code"]
        "findByCodeGreaterThan"    | "find_by_code_greater_than" | ", value: str"                      | "list[Item]" | 'WHERE (item_.`code` > ?)'                               | ["code"]
    }

    private def repository(String declarations, String dialect = "H2", String extraFields = "") {
        buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity, MappedProperty
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    first_name: str
    last_name: str
    age: int
    code: str
    enabled: bool
    created_at: int
${extraFields}

@JdbcRepository(dialect="${dialect}")
class ItemRepository(GenericRepository[Item, int], ABC):
${declarations}
""")
    }
}
