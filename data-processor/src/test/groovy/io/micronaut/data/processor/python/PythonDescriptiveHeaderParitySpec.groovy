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

import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.TypeRole
import io.micronaut.data.intercept.annotation.DataMethod
import io.micronaut.data.intercept.annotation.DataMethodQuery
import io.micronaut.python.annotation.processing.test.AbstractPythonTypeElementSpec

import static io.micronaut.data.processor.visitors.TestUtils.getDataInterceptor
import static io.micronaut.data.processor.visitors.TestUtils.getOperationType
import static io.micronaut.data.processor.visitors.TestUtils.getParameterBindingIndexes
import static io.micronaut.data.processor.visitors.TestUtils.getParameterPropertyPaths
import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonDescriptiveHeaderParitySpec extends AbstractPythonTypeElementSpec {

    void "Native descriptive entity mutation #camel retains its operation and entity role"() {
        given:
        def definition = mutationRepository("""
    @abstractmethod
    def ${camel}(self, item: Item) -> Item: ...
""")
        def method = definition.executableMethods.find { it.methodName == camel }

        expect:
        assertDescriptiveEntityMutation(method, camel)

        where:
        camel << ["saveFirstDraft", "saveDistinctCopy", "updateFirstDraft", "updateDistinctCopy",
                  "saveTop3Draft", "saveFirst2Draft", "saveMax", "saveByHand",
                  "persistFirstDraft", "storeDistinctCopy", "insertMax", "modifyFirstDraft",
                  "saveForUpdateDraft", "updateForUpdateDraft", "saveAllAndFlush"]
    }

    void "Python descriptive entity mutation #snake retains #camel operation and metadata"() {
        given:
        def definition = mutationRepository("""
    @abstractmethod
    def ${camel}(self, item: Item) -> Item: ...
    @abstractmethod
    def ${snake}(self, item: Item) -> Item: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        assertDescriptiveEntityMutation(snakeMethod, camel)
        assertParity(camelMethod, snakeMethod)
        snakeMethod.getAnnotation(DataMethod).stringValue(TypeRole.ENTITY) ==
            camelMethod.getAnnotation(DataMethod).stringValue(TypeRole.ENTITY)

        where:
        camel                | snake
        "saveFirstDraft"     | "save_first_draft"
        "saveDistinctCopy"   | "save_distinct_copy"
        "updateFirstDraft"   | "update_first_draft"
        "updateDistinctCopy" | "update_distinct_copy"
        "saveTop3Draft"      | "save_top3_draft"
        "saveFirst2Draft"    | "save_first2_draft"
        "saveFirst2Draft"    | "save_first_2_draft"
        "saveMax"            | "save_max"
        "saveByHand"         | "save_by_hand"
        "persistFirstDraft"  | "persist_first_draft"
        "storeDistinctCopy"  | "store_distinct_copy"
        "insertMax"          | "insert_max"
        "modifyFirstDraft"   | "modify_first_draft"
        "saveForUpdateDraft" | "save_for_update_draft"
        "updateForUpdateDraft" | "update_for_update_draft"
        "saveAllAndFlush"    | "save_all_and_flush"
    }

    void "Native descriptive update #camel retains its real by clause"() {
        given:
        def definition = mutationRepository("""
    @abstractmethod
    def ${camel}(self, value: str, age: int) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == camel }

        expect:
        assertFilteredDescriptiveUpdate(method)

        where:
        camel                      | returnType
        "updateFirstDraftByCode"   | "int"
        "modifyDistinctCopyByCode" | "None"
        "updateOrderByCode"        | "int"
        "modifySortByCode"         | "None"
    }

    void "Python descriptive update #snake retains its real filter and bindings"() {
        given:
        def definition = mutationRepository("""
    @abstractmethod
    def ${camel}(self, value: str, age: int) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, value: str, age: int) -> ${returnType}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        assertFilteredDescriptiveUpdate(snakeMethod)
        assertParity(camelMethod, snakeMethod)

        where:
        camel                      | snake                              | returnType
        "updateFirstDraftByCode"   | "update_first_draft_by_code"       | "int"
        "modifyDistinctCopyByCode" | "modify_distinct_copy_by_code"     | "None"
        "updateOrderByCode"        | "update_order_by_code"            | "int"
        "modifySortByCode"         | "modify_sort_by_code"             | "None"
    }

    void "Python descriptive update rejects a missing filter property in #methodName"() {
        when:
        mutationRepository("""
    @abstractmethod
    def ${methodName}(self, value: str, age: int) -> int: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Unsupported Python snake_case derived query '${methodName}'")
        !exception.message.contains("PythonProperty")

        where:
        methodName << ["update_first_draft_by_missing", "modify_distinct_copy_by_missing"]
    }

    void "Native save entity header #entityName returning #returnType is descriptive"() {
        given:
        def name = "save${entityName}"
        def definition = repository(entityName, """
    @abstractmethod
    def ${name}(self, item: ${entityName}) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == name }

        expect:
        assertSave(method)

        where:
        entityName     | returnType
        "FirstItem"    | "FirstItem"
        "DistinctItem" | "DistinctItem"
        "MaxItem"      | "MaxItem"
        "AllItem"      | "AllItem"
        "Top3Item"     | "Top3Item"
        "FirstItem"    | "int"
        "FirstItem"    | "None"
    }

    void "Python save entity header #snake returning #returnType retains #camel metadata"() {
        given:
        def definition = repository(entityName, """
    @abstractmethod
    def ${camel}(self, item: ${entityName}) -> ${returnType}: ...
    @abstractmethod
    def ${snake}(self, item: ${entityName}) -> ${returnType}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        assertSave(snakeMethod)
        assertParity(camelMethod, snakeMethod)

        where:
        entityName     | camel              | snake                | returnType
        "FirstItem"    | "saveFirstItem"    | "save_first_item"    | "FirstItem"
        "DistinctItem" | "saveDistinctItem" | "save_distinct_item" | "DistinctItem"
        "MaxItem"      | "saveMaxItem"      | "save_max_item"      | "MaxItem"
        "AllItem"      | "saveAllItem"      | "save_all_item"      | "AllItem"
        "Top3Item"     | "saveTop3Item"     | "save_top3_item"     | "Top3Item"
        "FirstItem"    | "saveFirstItem"    | "save_first_item"    | "int"
        "FirstItem"    | "saveFirstItem"    | "save_first_item"    | "None"
    }

    void "Native update entity header returning #returnType retains its filter"() {
        given:
        def definition = repository("FirstItem", """
    @abstractmethod
    def updateFirstItemByCode(self, value: str, first_name: str) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == "updateFirstItemByCode" }

        expect:
        assertUpdate(method)

        where:
        returnType << ["int", "None"]
    }

    void "Python update entity header returning #returnType retains its native filter and metadata"() {
        given:
        def definition = repository("FirstItem", """
    @abstractmethod
    def updateFirstItemByCode(self, value: str, first_name: str) -> ${returnType}: ...
    @abstractmethod
    def update_first_item_by_code(self, value: str, first_name: str) -> ${returnType}: ...
""")
        def camel = definition.executableMethods.find { it.methodName == "updateFirstItemByCode" }
        def snake = definition.executableMethods.find { it.methodName == "update_first_item_by_code" }

        expect:
        assertUpdate(snake)
        assertParity(camel, snake)

        where:
        returnType << ["int", "None"]
    }

    void "Native find entity header #entityName retains its filter without modifiers"() {
        given:
        def name = "find${entityName}ByCode"
        def definition = repository(entityName, """
    @abstractmethod
    def ${name}(self, value: str) -> ${entityName}: ...
""")
        def method = definition.executableMethods.find { it.methodName == name }

        expect:
        assertFind(method)

        where:
        entityName << ["FirstItem", "DescItem", "TopItem"]
    }

    void "Python find entity header #snake retains #camel filter and metadata"() {
        given:
        def definition = repository(entityName, """
    @abstractmethod
    def ${camel}(self, value: str) -> ${entityName}: ...
    @abstractmethod
    def ${snake}(self, value: str) -> ${entityName}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        assertFind(snakeMethod)
        assertParity(camelMethod, snakeMethod)

        where:
        entityName  | camel                 | snake
        "FirstItem" | "findFirstItemByCode" | "find_first_item_by_code"
        "DescItem"  | "findDescItemByCode"  | "find_desc_item_by_code"
        "TopItem"   | "findTopItemByCode"   | "find_top_item_by_code"
    }

    void "Native type name overlapping a limited filter retains first_name and limit #limit"() {
        given:
        def definition = repository(entityName, """
    @abstractmethod
    def ${camel}(self, value: str) -> ${entityName}: ...
""")
        def method = definition.executableMethods.find { it.methodName == camel }

        expect:
        method != null
        getQuery(method) == "SELECT item_.`id`,item_.`code`,item_.`first_name` FROM `item` item_ WHERE (item_.`first_name` = ?) LIMIT ${limit}"
        getParameterPropertyPaths(method) == ["first_name"] as String[]
        getParameterBindingIndexes(method) == ["0"] as String[]
        getDataInterceptor(method) == "io.micronaut.data.intercept.FindOneInterceptor"
        getOperationType(method) == DataMethod.OperationType.QUERY

        where:
        entityName        | camel                    | limit
        "FirstByFirstName" | "findFirstByFirst_name"   | 1
        "Top3ByFirstName"  | "findTop3ByFirst_name"    | 3
    }

    void "Python type name overlapping a limited filter preserves #snake clause boundaries"() {
        given:
        def definition = repository(entityName, """
    @abstractmethod
    def ${camel}(self, value: str) -> ${entityName}: ...
    @abstractmethod
    def ${snake}(self, value: str) -> ${entityName}: ...
""")
        def camelMethod = definition.executableMethods.find { it.methodName == camel }
        def snakeMethod = definition.executableMethods.find { it.methodName == snake }

        expect:
        camelMethod != null
        snakeMethod != null
        getQuery(snakeMethod) == "SELECT item_.`id`,item_.`code`,item_.`first_name` FROM `item` item_ WHERE (item_.`first_name` = ?) LIMIT ${limit}"
        snakeMethod.getAnnotation(Query).getValues() == camelMethod.getAnnotation(Query).getValues()
        getParameterPropertyPaths(snakeMethod) == ["first_name"] as String[]
        getParameterPropertyPaths(snakeMethod) == getParameterPropertyPaths(camelMethod)
        getParameterBindingIndexes(snakeMethod) == ["0"] as String[]
        getParameterBindingIndexes(snakeMethod) == getParameterBindingIndexes(camelMethod)
        getDataInterceptor(snakeMethod) == getDataInterceptor(camelMethod)
        getOperationType(snakeMethod) == getOperationType(camelMethod)
        snakeMethod.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT) ==
            camelMethod.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT)

        where:
        entityName        | camel                    | snake                            | limit
        "FirstByFirstName" | "findFirstByFirst_name"   | "find_first_by_first_name"       | 1
        "Top3ByFirstName"  | "findTop3ByFirst_name"    | "find_top3_by_first_name"        | 3
    }

    void "Python keyword leading native property retains projection precedence"() {
        given:
        def definition = repository("FirstItem", '''
    @abstractmethod
    def findFirst_itemByCode(self, value: str) -> str: ...
    @abstractmethod
    def find_first_item_by_code(self, value: str) -> str: ...
''', '    first_item: str')
        def camel = definition.executableMethods.find { it.methodName == "findFirst_itemByCode" }
        def snake = definition.executableMethods.find { it.methodName == "find_first_item_by_code" }

        expect:
        snake != null
        getQuery(snake) == 'SELECT item_.`first_item` FROM `item` item_ WHERE (item_.`code` = ?)'
        getParameterPropertyPaths(snake) == ["code"] as String[]
        getParameterBindingIndexes(snake) == ["0"] as String[]
        assertParity(camel, snake)
    }

    void "Python structural return type name preserves the by clause"() {
        given:
        def definition = repository("AllByCode", '''
    @abstractmethod
    def findAllByCode(self, value: str) -> AllByCode: ...
    @abstractmethod
    def find_all_by_code(self, value: str) -> AllByCode: ...
''')
        def camel = definition.executableMethods.find { it.methodName == "findAllByCode" }
        def snake = definition.executableMethods.find { it.methodName == "find_all_by_code" }

        expect:
        assertFind(snake)
        assertParity(camel, snake)
    }

    void "Python return type description is not accepted in a predicate"() {
        when:
        repository("FirstItem", '''
    @abstractmethod
    def find_by_first_item(self, value: str) -> FirstItem: ...
''')

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Unsupported Python snake_case derived query 'find_by_first_item'")
        !exception.message.contains("PythonProperty")
    }

    private static void assertDescriptiveEntityMutation(AnnotationMetadataProvider method, String camel) {
        boolean update = camel.startsWith("update") || camel.startsWith("modify")
        assert method != null
        assert getQuery(method) == (update
            ? 'UPDATE `item` SET `code`=?,`age`=? WHERE (`id` = ?)'
            : 'INSERT INTO `item` (`code`,`age`,`id`) VALUES (?,?,?)')
        assert getParameterPropertyPaths(method) == ["code", "age", "id"] as String[]
        assert method.getAnnotation(DataMethod).stringValue(TypeRole.ENTITY).orElse(null) == "item"
        assert getDataInterceptor(method) == (update
            ? "io.micronaut.data.intercept.UpdateEntityInterceptor"
            : camel.startsWith("save")
                ? "io.micronaut.data.intercept.SaveEntityInterceptor"
                : "io.micronaut.data.intercept.InsertEntityInterceptor")
        assert getOperationType(method) == (update ? DataMethod.OperationType.UPDATE : DataMethod.OperationType.INSERT)
        assert method.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private static void assertFilteredDescriptiveUpdate(AnnotationMetadataProvider method) {
        assert method != null
        assert getQuery(method) == 'UPDATE `item` SET `age`=? WHERE (`code` = ?)'
        assert getParameterPropertyPaths(method) == ["age", "code"] as String[]
        assert getParameterBindingIndexes(method) == ["1", "0"] as String[]
        assert method.getAnnotation(DataMethod).stringValue(TypeRole.ENTITY).isEmpty()
        assert getDataInterceptor(method) == "io.micronaut.data.intercept.UpdateInterceptor"
        assert getOperationType(method) == DataMethod.OperationType.UPDATE
        assert method.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private static void assertSave(AnnotationMetadataProvider method) {
        assert method != null
        assert getQuery(method) == 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        assert getParameterPropertyPaths(method) == ["code", "first_name", "id"] as String[]
        assert getDataInterceptor(method) == "io.micronaut.data.intercept.SaveEntityInterceptor"
        assert getOperationType(method) == DataMethod.OperationType.INSERT
        assert method.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private static void assertFind(AnnotationMetadataProvider method) {
        assert method != null
        assert getQuery(method) == 'SELECT item_.`id`,item_.`code`,item_.`first_name` FROM `item` item_ WHERE (item_.`code` = ?)'
        assert getParameterPropertyPaths(method) == ["code"] as String[]
        assert getParameterBindingIndexes(method) == ["0"] as String[]
        assert getDataInterceptor(method) == "io.micronaut.data.intercept.FindOneInterceptor"
        assert getOperationType(method) == DataMethod.OperationType.QUERY
        assert method.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private static void assertUpdate(AnnotationMetadataProvider method) {
        assert method != null
        assert getQuery(method) == 'UPDATE `item` SET `first_name`=? WHERE (`code` = ?)'
        assert getParameterPropertyPaths(method) == ["first_name", "code"] as String[]
        assert getParameterBindingIndexes(method) == ["1", "0"] as String[]
        assert getDataInterceptor(method) == "io.micronaut.data.intercept.UpdateInterceptor"
        assert getOperationType(method) == DataMethod.OperationType.UPDATE
        assert method.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private static void assertParity(AnnotationMetadataProvider camel, AnnotationMetadataProvider snake) {
        assert camel != null
        assert snake != null
        assert snake.getAnnotation(Query).getValues() == camel.getAnnotation(Query).getValues()
        assert getParameterPropertyPaths(snake) == getParameterPropertyPaths(camel)
        assert getParameterBindingIndexes(snake) == getParameterBindingIndexes(camel)
        assert getDataInterceptor(snake) == getDataInterceptor(camel)
        assert getOperationType(snake) == getOperationType(camel)
        assert snake.getAnnotation(DataMethod).stringValue(DataMethod.META_MEMBER_RESULT_TYPE) ==
            camel.getAnnotation(DataMethod).stringValue(DataMethod.META_MEMBER_RESULT_TYPE)
        assert snake.getAnnotation(DataMethod).intValue(DataMethodQuery.META_MEMBER_LIMIT).orElse(-1) == -1
    }

    private def mutationRepository(String declarations) {
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
    id: Annotated[int, Id]
    code: str
    age: int

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
${declarations}
""")
    }

    private def repository(String entityName, String declarations, String extraFields = "") {
        buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("item", alias="item_")
@dataclass
class ${entityName}:
    id: Annotated[int, Id]
    code: str
    first_name: str
${extraFields}

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[${entityName}, int], ABC):
${declarations}
""")
    }
}
