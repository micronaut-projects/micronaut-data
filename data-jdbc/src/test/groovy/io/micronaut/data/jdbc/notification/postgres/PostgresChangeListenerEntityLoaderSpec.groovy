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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.core.convert.ConversionService
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.DataType
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery
import spock.lang.Specification
import spock.lang.Unroll

class PostgresChangeListenerEntityLoaderSpec extends Specification {

    void "reloads retain independent key values and immutable binding metadata"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(DefinitionBook) >> new RuntimePersistentEntity<>(DefinitionBook)
        operations.conversionService >> ConversionService.SHARED
        def queries = []
        operations.findOne(_) >> { arguments ->
            queries.add(arguments[0])
            null
        }
        def bindings = [new PostgresChangeListenerEntityLoader.KeyBinding('id', Long, DataType.LONG)]
        def loader = new PostgresChangeListenerEntityLoader<>(operations, DefinitionBook,
            'SELECT id FROM book WHERE id = ?', bindings)
        def firstKeys = [id: '41']
        def firstBinder = Mock(BindableParametersStoredQuery.Binder)
        def secondBinder = Mock(BindableParametersStoredQuery.Binder)

        when:
        bindings.clear()
        def first = loader.reload(firstKeys)
        firstKeys.id = '99'
        def second = loader.reload([id: '42'])
        queries[0].bindParameters(firstBinder)
        queries[1].bindParameters(secondBinder)

        then:
        first == null
        second == null
        loader.columns() == ['id'] as Set
        !queries[0].is(queries[1])
        1 * firstBinder.bindOne({ it.dataType == DataType.LONG }, { it instanceof Long && it == 41L })
        1 * secondBinder.bindOne({ it.dataType == DataType.LONG }, { it instanceof Long && it == 42L })
    }

    @Unroll
    void "reload rejects mismatched notification columns: #keys"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(DefinitionBook) >> new RuntimePersistentEntity<>(DefinitionBook)
        operations.conversionService >> ConversionService.SHARED
        def bindings = [new PostgresChangeListenerEntityLoader.KeyBinding('id', Long, DataType.LONG)]
        def loader = new PostgresChangeListenerEntityLoader<>(operations, DefinitionBook,
            'SELECT id FROM book WHERE id = ?', bindings)

        when:
        loader.reload(keys)

        then:
        def error = thrown(IllegalArgumentException)
        error.message == 'Notification keys do not match the mapped primary key'
        0 * operations.findOne(_)

        where:
        keys << [[:], [other: '41'], [id: '41', other: '42']]
    }
}
