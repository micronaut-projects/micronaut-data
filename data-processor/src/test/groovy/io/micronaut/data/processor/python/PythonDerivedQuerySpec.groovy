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
