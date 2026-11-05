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
import static io.micronaut.data.processor.visitors.TestUtils.getDataResultType

class PythonBoundedReactiveSaveOverrideSpec extends AbstractPythonTypeElementSpec {

    void "bounded type parameter is supported when overriding reactive saveAll"() {
        given:
        def definition = buildBeanDefinition('python', 'ItemRepository$Intercepted', '''
from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Annotated
from micronaut.data.annotation import Id, MappedEntity
from micronaut.data.r2dbc.annotation import R2dbcRepository
from micronaut.data.repository.reactive import ReactiveStreamsCrudRepository
from org.reactivestreams import Publisher
from java.lang import Iterable

@MappedEntity("item")
@dataclass
class Item:
    id: Annotated[int, Id]
    code: str

@R2dbcRepository(dialect="POSTGRES")
class ItemRepository(ReactiveStreamsCrudRepository[Item, int], ABC):
    @abstractmethod
    def saveAll[S: Item](self, items: Iterable[S]) -> Publisher[S]: ...
''')
        def method = definition.executableMethods.find { it.methodName == 'saveAll' }

        expect:
        method != null
        getDataInterceptor(method) == 'io.micronaut.data.intercept.reactive.SaveAllReactiveInterceptor'
        getDataResultType(method) == 'python.Item'
    }
}
