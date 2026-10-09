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

import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonQueryGrammarReviewSpec extends AbstractPythonTypeElementSpec {

    void "Python misplaced keyword in #methodName reports the original method name"() {
        when:
        repository("""
    @abstractmethod
    def ${methodName}(self, value: str) -> list[Item]: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Unsupported Python snake_case derived query '${methodName}'")
        !exception.message.contains("PythonProperty")

        where:
        methodName << [
            "find_by_code_desc",
            "find_by_code_asc",
            "find_by_code_all",
            "find_by_code_one",
            "find_by_code_top",
            "find_by_code_first",
            "find_by_code_max",
            "find_by_and_code",
            "find_by_or_code",
            "find_by_code_distinct",
            "find_by_code_order_by_desc",
            "find_by_code_order_by_age_and_and_code",
            "find_by_code_order_by_age_desc_asc",
            "find_by_code_order_by_age_asc_desc",
            "find_by_code_order_by_age_desc_desc",
            "find_by_code_like_like",
            "find_by_age_greater_than_less_than",
            "find_by_code_is_null_like",
            "find_by_code_like_not",
            "find_by_code_not_not",
            "find_by_code_ignore_case_ignore_case",
            "find_by_code_ids",
            "find_by_code_ignore_case_not",
            "find_by_code_contains_ignore_case_ignore_case",
            "find_by_ids_like",
            "find_by_code_not_not_equals_ignore_case"
        ]
    }

    void "Python valid predicate #snake retains SQL and bindings of #camel"() {
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
        getQuery(snakeMethod) == "SELECT item_.`pk`,item_.`id`,item_.`code`,item_.`age`,item_.`all_day`,item_.`one_time_code`,item_.`distinct_code` FROM `item` item_ WHERE (${predicate})"
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterPropertyPaths(snakeMethod) == paths as String[]
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getParameterBindingIndexes(snakeMethod) == indexes as String[]

        where:
        camel                                                 | snake                                                        | parameters                      | predicate                                                                                                                        | paths            | indexes
        "CodeNotLike"                                         | "code_not_like"                                              | ", value: str"                  | 'item_.`code` NOT LIKE ?'                                                                                                          | ["code"]        | ["0"]
        "CodeNot"                                             | "code_not"                                                   | ", value: str"                  | 'item_.`code` != ?'                                                                                                                | ["code"]        | ["0"]
        "CodeContainsIgnoreCase"                              | "code_contains_ignore_case"                                  | ", value: str"                  | "LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%')"                                                                                | ["code"]        | ["0"]
        "CodeIgnoreCaseContains"                              | "code_ignore_case_contains"                                  | ", value: str"                  | "LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%')"                                                                                | ["code"]        | ["0"]
        "CodeNotContainsIgnoreCase"                           | "code_not_contains_ignore_case"                              | ", value: str"                  | "NOT(LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%'))"                                                                           | ["code"]        | ["0"]
        "CodeNotIgnoreCaseContains"                           | "code_not_ignore_case_contains"                              | ", value: str"                  | "NOT(LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%'))"                                                                           | ["code"]        | ["0"]
        "CodeNotIgnoreCase"                                   | "code_not_ignore_case"                                       | ", value: str"                  | 'NOT(LOWER(item_.`code`) = LOWER(?))'                                                                                              | ["code"]        | ["0"]
        "CodeNotEqualsIgnoreCase"                             | "code_not_equals_ignore_case"                                | ", value: str"                  | 'NOT(LOWER(item_.`code`) = LOWER(?))'                                                                                              | ["code"]        | ["0"]
        "CodeNotNotEquals"                                    | "code_not_not_equals"                                        | ", value: str"                  | 'item_.`code` = ?'                                                                                                                 | ["code"]        | ["0"]
        "Ids"                                                | "ids"                                                        | ", ids: list[int]"              | 'item_.`pk` IN (?)'                                                                                                                | ["pk"]          | ["0"]
        "CodeNotLikeAndCodeNotLike"                           | "code_not_like_and_code_not_like"                            | ", first: str, second: str"     | 'item_.`code` NOT LIKE ? AND item_.`code` NOT LIKE ?'                                                                               | ["code", "code"] | ["0", "1"]
        "CodeNotIgnoreCaseContainsOrCodeNotContainsIgnoreCase" | "code_not_ignore_case_contains_or_code_not_contains_ignore_case" | ", first: str, second: str" | "NOT(LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%')) OR NOT(LOWER(item_.`code`) LIKE CONCAT('%',LOWER(?),'%'))"                      | ["code", "code"] | ["0", "1"]
    }

    void "Python projects native keyword-prefixed property using #methodName"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${methodName}(self, value: str) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        expect:
        method != null
        getQuery(method) == "SELECT ${modifier}item_.`${property}` FROM `item` item_ WHERE (item_.`code` = ?)"
        getParameterPropertyPaths(method) == ["code"] as String[]
        getParameterBindingIndexes(method) == ["0"] as String[]

        where:
        methodName                              | property        | modifier    | returnType
        "find_all_day_by_code"                  | "all_day"       | ""          | "bool"
        "find_distinct_all_day_by_code"         | "all_day"       | "DISTINCT " | "bool"
        "find_one_time_code_by_code"            | "one_time_code" | ""          | "str"
        "find_distinct_one_time_code_by_code"   | "one_time_code" | "DISTINCT " | "str"
        "find_distinct_code_by_code"            | "distinct_code" | ""          | "str"
        "find_distinct_distinct_code_by_code"   | "distinct_code" | "DISTINCT " | "str"
        "find_age_by_code"                      | "age"           | ""          | "int"
    }

    void "Python native keyword-prefixed property #property works in predicates and ordering"() {
        given:
        def predicateName = "find_age_by_${property}"
        def orderName = "find_age_by_code_order_by_${property}_desc"
        def definition = repository("""
    @abstractmethod
    def ${predicateName}(self, value: ${parameterType}) -> list[int]: ...
    @abstractmethod
    def ${orderName}(self, value: str) -> list[int]: ...
""", extraFields)
        def predicate = definition.executableMethods.find { it.methodName == predicateName }
        def order = definition.executableMethods.find { it.methodName == orderName }

        expect:
        predicate != null
        order != null
        getQuery(predicate) == "SELECT item_.`age` FROM `item` item_ WHERE (item_.`${property}` = ?)"
        getParameterPropertyPaths(predicate) == [property] as String[]
        getParameterBindingIndexes(predicate) == ["0"] as String[]
        getQuery(order) == "SELECT item_.`age` FROM `item` item_ WHERE (item_.`code` = ?) ORDER BY item_.`${property}` DESC"
        getParameterPropertyPaths(order) == ["code"] as String[]
        getParameterBindingIndexes(order) == ["0"] as String[]

        where:
        property        | parameterType | extraFields
        "all_day"       | "bool"        | ""
        "one_time_code" | "str"         | ""
        "distinct_code" | "str"         | ""
        "asc_code"      | "str"         | "    asc_code: str"
        "desc_code"     | "str"         | "    desc_code: str"
        "and_code"      | "str"         | "    and_code: str"
        "or_code"       | "str"         | "    or_code: str"
        "top_code"      | "str"         | "    top_code: str"
        "code_like_like" | "str"        | "    code_like_like: str"
    }

    void "Python valid header #snake retains the query and limits of #camel"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self, value: str) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, value: str) -> ${returnType}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        getParameterPropertyPaths(snakeMethod) == ["code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["0"] as String[]

        where:
        camel                | snake                      | returnType
        "findAllByCode"      | "find_all_by_code"         | "list[Item]"
        "findOneByCode"      | "find_one_by_code"         | "Item | None"
        "findDistinctByCode" | "find_distinct_by_code"    | "list[Item]"
        "findTop3ByCode"     | "find_top3_by_code"        | "list[Item]"
        "findTop3ByCode"     | "find_top_3_by_code"       | "list[Item]"
        "findFirstByCode"    | "find_first_by_code"       | "Item | None"
    }

    void "Python valid ordering #methodName retains its SQL and filter binding"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${methodName}(self, value: str) -> list[int]: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        expect:
        method != null
        getQuery(method) == "SELECT item_.`age` FROM `item` item_ WHERE (item_.`code` = ?) ORDER BY item_.`age` ${direction}"
        getParameterPropertyPaths(method) == ["code"] as String[]
        getParameterBindingIndexes(method) == ["0"] as String[]

        where:
        methodName                             | direction
        "find_age_by_code_order_by_age"        | "ASC"
        "find_age_by_code_order_by_age_asc"    | "ASC"
        "find_age_by_code_order_by_age_desc"   | "DESC"
        "find_age_by_code_sort_by_age_desc"    | "DESC"
    }

    void "Python explicit header boundaries project structural keyword property #property"() {
        given:
        def filteredName = "find__${property}_by_code"
        def bareName = "find__${property}"
        def definition = repository("""
    @abstractmethod
    def ${filteredName}(self, value: str) -> str: ...
    @abstractmethod
    def ${bareName}(self) -> list[str]: ...
""", "    ${property}: str")
        def filtered = definition.executableMethods.find { it.methodName == filteredName }
        def bare = definition.executableMethods.find { it.methodName == bareName }

        expect:
        filtered != null
        bare != null
        getQuery(filtered) == "SELECT item_.`${property}` FROM `item` item_ WHERE (item_.`code` = ?)"
        getParameterPropertyPaths(filtered) == ["code"] as String[]
        getParameterBindingIndexes(filtered) == ["0"] as String[]
        getQuery(bare) == "SELECT item_.`${property}` FROM `item` item_"
        getParameterPropertyPaths(bare) == [] as String[]
        getParameterBindingIndexes(bare) == [] as String[]

        where:
        property << ["by_code", "order_by_code", "sort_by_code", "returning_code", "for_update_code",
                     "all_by_code", "one_by_code", "distinct_by_code"]
    }

    void "Python ordinary by clause keeps filtering when a native by_code property exists"() {
        given:
        def definition = repository('''
    @abstractmethod
    def find_by_code(self, value: str) -> list[Item]: ...
''', '    by_code: str')
        def method = definition.executableMethods.find { it.methodName == "find_by_code" }

        expect:
        method != null
        getQuery(method) == 'SELECT item_.`pk`,item_.`id`,item_.`code`,item_.`age`,item_.`all_day`,item_.`one_time_code`,item_.`distinct_code`,item_.`by_code` FROM `item` item_ WHERE (item_.`code` = ?)'
        getParameterPropertyPaths(method) == ["code"] as String[]
        getParameterBindingIndexes(method) == ["0"] as String[]
    }

    void "Python standalone ids restriction remains an operand before a logical separator"() {
        given:
        def definition = repository('''
    @abstractmethod
    def findByIdsAndCode(self, ids: list[int], value: str) -> list[Item]: ...
    @abstractmethod
    def find_by_ids_and_code(self, ids: list[int], value: str) -> list[Item]: ...
''')
        def camel = definition.executableMethods.find { it.methodName == "findByIdsAndCode" }
        def snake = definition.executableMethods.find { it.methodName == "find_by_ids_and_code" }

        expect:
        camel != null
        snake != null
        getQuery(snake) == getQuery(camel)
        getQuery(snake).endsWith('WHERE (item_.`pk` IN (?) AND item_.`code` = ?)')
        getParameterPropertyPaths(snake) == getParameterPropertyPaths(camel)
        getParameterBindingIndexes(snake) == ["0", "1"] as String[]
    }

    void "Python header And retains multiple projected columns"() {
        given:
        def definition = repository('''
    @abstractmethod
    def find_code_and_age_by_id(self, value: str) -> list[Item]: ...
''')
        def method = definition.executableMethods.find { it.methodName == "find_code_and_age_by_id" }

        expect:
        method != null
        getQuery(method) == 'SELECT item_.`code`,item_.`age` FROM `item` item_ WHERE (item_.`id` = ?)'
        getParameterPropertyPaths(method) == ["id"] as String[]
        getParameterBindingIndexes(method) == ["0"] as String[]
    }

    void "Python returning And retains the shared multi-selection limitation"() {
        when:
        repository('''
    @abstractmethod
    def deleteByCodeReturningCodeAndAge(self, value: str) -> list[Item]: ...
''', "", "POSTGRES")

        then:
        def camelFailure = thrown(RuntimeException)
        camelFailure.message.contains("Multi-selection is not supported")
        !camelFailure.message.contains("PythonProperty")

        when:
        repository('''
    @abstractmethod
    def delete_by_code_returning_code_and_age(self, value: str) -> list[Item]: ...
''', "", "POSTGRES")

        then:
        def snakeFailure = thrown(RuntimeException)
        snakeFailure.message.contains("Multi-selection is not supported")
        !snakeFailure.message.contains("PythonProperty")
    }

    void "Python native #collisionField does not absorb the filter or ordering in #snake"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self${parameters}) -> list[Item]: ...
    @abstractmethod
    def ${snake}(self${parameters}) -> list[Item]: ...
""", "    ${collisionField}: str")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod) == "SELECT item_.`pk`,item_.`id`,item_.`code`,item_.`age`,item_.`all_day`,item_.`one_time_code`,item_.`distinct_code`,item_.`${collisionField}` FROM `item` item_ ${queryEnding}"
        getParameterPropertyPaths(snakeMethod) == paths as String[]
        getParameterBindingIndexes(snakeMethod) == indexes as String[]

        where:
        camel                | snake                           | collisionField | parameters                 | queryEnding                                                   | paths           | indexes
        "findByCodeAndAge"   | "find_by_code__and__age"        | "by_code"      | ", value: str, age: int"   | 'WHERE (item_.`code` = ? AND item_.`age` = ?)'                  | ["code", "age"] | ["0", "1"]
        "findByCodeOrderByAge" | "find_by_code__order_by_age"  | "by_code"      | ", value: str"            | 'WHERE (item_.`code` = ?) ORDER BY item_.`age` ASC'             | ["code"]        | ["0"]
        "findOrderByAge"     | "find_order_by_age"            | "order_by_age" | ""                        | 'ORDER BY item_.`age` ASC'                                    | []              | []
    }

    void "Python native #collisionField does not absorb the update filter in #snake"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self, code: str, age: int) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, code: str, age: int) -> ${returnType}: ...
""", "    ${collisionField}: str", dialect)
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod) == query
        getParameterPropertyPaths(snakeMethod) == ["age", "code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["1", "0"] as String[]

        where:
        camel                   | snake                       | collisionField | returnType   | dialect    | query
        "updateByCodeReturning" | "update_by_code__returning" | "by_code"      | "list[Item]" | "POSTGRES" | 'UPDATE "item" SET "age"=? WHERE ("code" = ?) RETURNING "pk","id","code","age","all_day","one_time_code","distinct_code","by_code"'
        "updateAllByCode"       | "update_all_by_code"        | "all_by_code"  | "int"        | "H2"       | 'UPDATE `item` SET `age`=? WHERE (`code` = ?)'
    }

    void "Python valid header #snake keeps filtering with a native #collisionField property"() {
        given:
        def definition = repository("""
    @abstractmethod
    def ${camel}(self, value: str) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, value: str) -> ${returnType}: ...
""", "    ${collisionField}: str")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == getQuery(camelMethod)
        getQuery(snakeMethod) == "SELECT ${modifier}item_.`pk`,item_.`id`,item_.`code`,item_.`age`,item_.`all_day`,item_.`one_time_code`,item_.`distinct_code`,item_.`${collisionField}` FROM `item` item_ WHERE (item_.`code` = ?)"
        getParameterPropertyPaths(snakeMethod) == ["code"] as String[]
        getParameterBindingIndexes(snakeMethod) == ["0"] as String[]

        where:
        camel                | snake                   | collisionField     | returnType    | modifier
        "findAllByCode"      | "find_all_by_code"      | "all_by_code"      | "list[Item]"  | ""
        "findOneByCode"      | "find_one_by_code"      | "one_by_code"      | "Item | None" | ""
        "findDistinctByCode" | "find_distinct_by_code" | "distinct_by_code" | "list[Item]"  | "DISTINCT "
    }

    void "Python entity save retains its descriptive and_flush suffix"() {
        given:
        def definition = repository('''
    @abstractmethod
    def saveItem(self, item: Item) -> Item: ...
    @abstractmethod
    def save_and_flush(self, item: Item) -> Item: ...
''')
        def ordinary = definition.executableMethods.find { it.methodName == "saveItem" }
        def descriptive = definition.executableMethods.find { it.methodName == "save_and_flush" }

        expect:
        ordinary != null
        descriptive != null
        getQuery(descriptive) == getQuery(ordinary)
        getParameterPropertyPaths(descriptive) == getParameterPropertyPaths(ordinary)
        getParameterBindingIndexes(descriptive) == getParameterBindingIndexes(ordinary)
    }

    private def repository(String declarations, String extraFields = "", String dialect = "H2") {
        buildBeanDefinition("python", "ItemRepository\$Intercepted", """
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
    age: int
    all_day: bool
    one_time_code: str
    distinct_code: str
${extraFields}

@JdbcRepository(dialect="${dialect}")
class ItemRepository(GenericRepository[Item, int], ABC):
${declarations}
""")
    }
}
