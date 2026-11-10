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

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class PythonDerivedQuerySpec extends AbstractPythonTypeElementSpec {

    void "Python entity operation retains descriptive name #methodName"() {
        when:
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
    def ${methodName}(self, item: Item) -> Item: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method) == query

        where:
        methodName     | query
        "saveBook"     | 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        "save_book"    | 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        "insert_item"  | 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        "persist_item" | 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        "save_one"     | 'INSERT INTO `item` (`code`,`first_name`,`id`) VALUES (?,?,?)'
        "updateItem"   | 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
        "update_item"  | 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
        "update_code"  | 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
        "update_first_name" | 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
        "update_one"   | 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
    }

    void "Python identity alias uses a differently named primary key for #methodName"() {
        when:
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
    isbn: Annotated[str, Id]
    title: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, str], ABC):
    @abstractmethod
    def ${methodName}(self, id: str) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method).endsWith(queryEnding)

        where:
        methodName     | returnType    | queryEnding
        "findById"     | "Item | None" | 'WHERE (item_.`isbn` = ?)'
        "find_by_id"   | "Item | None" | 'WHERE (item_.`isbn` = ?)'
        "deleteById"   | "int"         | 'DELETE  FROM `item`  WHERE (`isbn` = ?)'
        "delete_by_id" | "int"         | 'DELETE  FROM `item`  WHERE (`isbn` = ?)'
        "existsById"   | "bool"        | 'WHERE (item_.`isbn` = ?)'
        "exists_by_id" | "bool"        | 'WHERE (item_.`isbn` = ?)'
    }

    void "Python rejects unsupported snake case derived method #methodName"() {
        when:
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
    first_name: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")
        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Unsupported Python snake_case derived query")
        exception.message.contains(methodName)
        exception.message.contains("derived query")

        where:
        methodName                           | parameters                    | returnType
        "update2_by_code"                    | ", code: str, first_name: str" | "int"
        "updateé_by_code"                    | ", code: str, first_name: str" | "int"
        "updateOne_by_code"                  | ", code: str, first_name: str" | "int"
        "updateFirstName_by_code"            | ", code: str, first_name: str" | "int"
        "updateOne_by_codeByFirst_name"      | ", code: str, first_name: str" | "int"
    }

    void "Python literal property is not a parser keyword for #methodName"() {
        when:
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
    ids: int
    returning: str
    like: str
    first_name: str
    code: str
    codeLike: str
    codeReturning: str
    codeAndFirst_name: str
    codeNot: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method).endsWith(queryEnding)

        where:
        methodName                  | parameters                         | returnType   | queryEnding
        "update_by_ids"             | ", ids: int, first_name: str"       | "int"        | 'UPDATE `item` SET `first_name`=? WHERE (`ids` = ?)'
        "update_by_returning"       | ", returning: str, first_name: str" | "int"        | 'UPDATE `item` SET `first_name`=? WHERE (`returning` = ?)'
        "find_by_like"              | ", like: str"                      | "list[Item]" | 'WHERE (item_.`like` = ?)'
        "find_by_codeLike"          | ", value: str"                     | "list[Item]" | 'WHERE (item_.`code_like` = ?)'
        "update_by_codeReturning"   | ", value: str, first_name: str"     | "int"        | 'UPDATE `item` SET `first_name`=? WHERE (`code_returning` = ?)'
        "find_by_codeAndFirst_name" | ", value: str"                     | "list[Item]" | 'WHERE (item_.`code_and_first_name` = ?)'
        "find_by_codeNot_in_list"   | ", values: list[str]"              | "list[Item]" | 'WHERE (item_.`code_not` IN (?))'
    }

    void "Python literal returning predicate cannot opt into a returning update"() {
        when:
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
    returning: str
    first_name: str

@JdbcRepository(dialect="POSTGRES")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def update_by_returning(self, returning: str, first_name: str) -> list[Item]: ...
""")

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains("Update methods only support void or number based return types")
    }

    void "Python literal property does not trigger vector operator inference"() {
        when:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.model.vector import FloatVector
from micronaut.data.repository import GenericRepository

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    embedding: FloatVector
    embeddingNear: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def find_by_embeddingNear(self, value: str) -> list[Item]: ...
""")
        def method = definition.executableMethods.find { it.methodName == "find_by_embeddingNear" }

        then:
        method != null
        getQuery(method) == 'SELECT item_.`id`,item_.`embedding`,item_.`embedding_near` FROM `item` item_ WHERE (item_.`embedding_near` = ?)'
    }

    void "Python camel projection preserves native underscored property for #methodName"() {
        when:
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
    created_by_name: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method) == query

        where:
        methodName                 | parameters   | returnType  | query
        "findCreated_by_name"     | ""           | "list[str]" | 'SELECT item_.`created_by_name` FROM `item` item_'
        "findCreated_by_nameById" | ", id: int"   | "str"       | 'SELECT item_.`created_by_name` FROM `item` item_ WHERE (item_.`id` = ?)'
    }

    void "Python direct property takes precedence for #methodName"() {
        when:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
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
    name: str

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    owner_name: str
    code_and_first_name: str
    code_greater_than: str
    code_order_by_first_name: str
    owner: Annotated[Owner, Relation(Relation.Kind.MANY_TO_ONE)]

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self, value: str) -> list[Item]: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method).endsWith(queryEnding)

        where:
        methodName                         | queryEnding
        "find_by_owner_name"               | 'WHERE (item_.`owner_name` = ?)'
        "findByOwner_Name"                 | 'WHERE (item_owner_.`name` = ?)'
        "find_by_code_and_first_name"      | 'WHERE (item_.`code_and_first_name` = ?)'
        "find_by_code_greater_than"        | 'WHERE (item_.`code_greater_than` = ?)'
        "find_by_code_order_by_first_name" | 'WHERE (item_.`code_order_by_first_name` = ?)'
    }

    void "Python explicit query and update annotations retain arbitrary method names"() {
        when:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity, Query, Update
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
    @Query("UPDATE item SET first_name = :value WHERE code = :code")
    @abstractmethod
    def update_first_name_by_code(self, code: str, value: str) -> int: ...

    @Update
    @abstractmethod
    def update_one_by_code(self, item: Item) -> Item: ...

    @Update
    @abstractmethod
    def custom_update(self, item: Item) -> Item: ...
""")

        then:
        getQuery(definition.executableMethods.find { it.methodName == "update_first_name_by_code" }) == 'UPDATE item SET first_name = :value WHERE code = :code'
        getQuery(definition.executableMethods.find { it.methodName == "update_one_by_code" }) == 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
        getQuery(definition.executableMethods.find { it.methodName == "custom_update" }) == 'UPDATE `item` SET `code`=?,`first_name`=? WHERE (`id` = ?)'
    }

    void "Python repository derives an IN query for #methodName"() {
        when:
        def definition = buildBeanDefinition("python", "FruitRepository\$Intercepted", """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository

@MappedEntity("fruit")
@dataclass
class Fruit:
    id: Annotated[int, Id]
    name: str

@JdbcRepository(dialect="H2")
class FruitRepository(GenericRepository[Fruit, int], ABC):
    @abstractmethod
    def ${methodName}(self, names: list[str]) -> list[Fruit]: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method) == 'SELECT fruit_.`id`,fruit_.`name` FROM `fruit` fruit_ WHERE (fruit_.`name` IN (?))'

        where:
        methodName << ["findByNameInList", "find_by_name_in_list"]
    }

    void "Python derived query preserves properties and method name for #methodName"() {
        when:
        def definition = buildBeanDefinition("python", "ItemRepository\$Intercepted", """
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
    name_in_list: str

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str
    first_name: str
    name_in_list: str
    created_by_name: str
    owner: Annotated[Owner, Relation(Relation.Kind.MANY_TO_ONE)]

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${methodName}(self${parameters}) -> ${returnType}: ...
""")
        def method = definition.executableMethods.find { it.methodName == methodName }

        then:
        method != null
        getQuery(method).endsWith(queryEnding)

        where:
        methodName                         | parameters                    | returnType    | queryEnding
        "findByCode"                       | ", code: str"                | "list[Item]"  | 'WHERE (item_.`code` = ?)'
        "findByCodeAndFirst_name"          | ", code: str, first_name: str" | "list[Item]"  | 'WHERE (item_.`code` = ? AND item_.`first_name` = ?)'
        "find_by_code"                     | ", code: str"                | "list[Item]"  | 'WHERE (item_.`code` = ?)'
        "updateByCode"                     | ", code: str, first_name: str" | "int"         | 'UPDATE `item` SET `first_name`=? WHERE (`code` = ?)'
        "update_by_code"                   | ", code: str, first_name: str" | "int"         | 'UPDATE `item` SET `first_name`=? WHERE (`code` = ?)'
        "deleteByCode"                     | ", code: str"                | "int"         | 'DELETE  FROM `item`  WHERE (`code` = ?)'
        "delete_by_code"                   | ", code: str"                | "int"         | 'DELETE  FROM `item`  WHERE (`code` = ?)'
        "findById"                         | ", id: int"                  | "Item | None" | 'WHERE (item_.`id` = ?)'
        "find_by_id"                       | ", id: int"                  | "Item | None" | 'WHERE (item_.`id` = ?)'
        "findAll"                          | ""                           | "list[Item]"  | 'FROM `item` item_'
        "find_all"                         | ""                           | "list[Item]"  | 'FROM `item` item_'
        "findByFirst_name"                 | ", first_name: str"           | "list[Item]"  | 'WHERE (item_.`first_name` = ?)'
        "find_by_first_name"               | ", first_name: str"           | "list[Item]"  | 'WHERE (item_.`first_name` = ?)'
        "findByCreated_by_name"            | ", created_by_name: str"      | "list[Item]"  | 'WHERE (item_.`created_by_name` = ?)'
        "updateByCreated_by_name"          | ", created_by_name: str, code: str" | "int"    | 'UPDATE `item` SET `code`=? WHERE (`created_by_name` = ?)'
        "find_by_created_by_name"          | ", created_by_name: str"      | "list[Item]"  | 'WHERE (item_.`created_by_name` = ?)'
        "update_by_created_by_name"        | ", created_by_name: str, code: str" | "int"    | 'UPDATE `item` SET `code`=? WHERE (`created_by_name` = ?)'
        "findByName_in_list"               | ", name_in_list: str"         | "list[Item]"  | 'WHERE (item_.`name_in_list` = ?)'
        "find_by_name_in_list"             | ", name_in_list: str"         | "list[Item]"  | 'WHERE (item_.`name_in_list` = ?)'
        "findByowner_first_name"           | ", first_name: str"           | "list[Item]"  | 'WHERE (item_owner_.`first_name` = ?)'
        "find_by_owner_first_name"         | ", first_name: str"           | "list[Item]"  | 'WHERE (item_owner_.`first_name` = ?)'
        "findByowner_name_in_list"         | ", name_in_list: str"         | "list[Item]"  | 'WHERE (item_owner_.`name_in_list` = ?)'
        "find_by_owner_name_in_list"       | ", name_in_list: str"         | "list[Item]"  | 'WHERE (item_owner_.`name_in_list` = ?)'
        "findByowner_first_nameInList"     | ", names: list[str]"          | "list[Item]"  | 'WHERE (item_owner_.`first_name` IN (?))'
        "find_by_owner_first_name_in_list" | ", names: list[str]"          | "list[Item]"  | 'WHERE (item_owner_.`first_name` IN (?))'
    }
}
