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

class PythonRepositoryInteropSpec extends AbstractPythonTypeElementSpec {

    void "specification method #method works"() {
        given:
        def definition = buildBeanDefinition('python', 'ItemRepository$Intercepted', """
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.repository import GenericRepository
from micronaut.data.repository.jpa.criteria import PredicateSpecification, UpdateSpecification

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str

@JdbcRepository(dialect="H2")
class ItemRepository(GenericRepository[Item, int], ABC):
    @abstractmethod
    def ${method}(self, spec: ${spec}[Item]) -> ${result}: ...
""")

        expect:
        getDataInterceptor(definition.executableMethods.find { it.methodName == method }).contains('Specification')

        where:
        method       | spec                     | result
        'findAll'    | 'PredicateSpecification' | 'list[Item]'
        'find_all'   | 'PredicateSpecification' | 'list[Item]'
        'deleteAll'  | 'PredicateSpecification' | 'int'
        'delete_all' | 'PredicateSpecification' | 'int'
        'updateAll'  | 'UpdateSpecification'    | 'int'
        'update_all' | 'UpdateSpecification'    | 'int'
    }
}
