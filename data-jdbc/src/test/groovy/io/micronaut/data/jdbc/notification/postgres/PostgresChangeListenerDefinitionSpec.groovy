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

import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.intercept.annotation.PostgresChangeListenerQuery
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification
import spock.lang.Unroll

class PostgresChangeListenerDefinitionSpec extends Specification {

    @Unroll
    void "metadata mapping for #type.simpleName matches #changedSchema.#changedTable: #matches"() {
        given:
        def entity = new RuntimePersistentEntity<>(type)
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(type) >> entity
        operations.conversionService >> ConversionService.SHARED
        def method = Mock(ExecutableMethod)
        method.stringValue(PostgresChangeNotification, 'channel') >> Optional.of('changes')
        method.getAnnotation(PostgresChangeListenerQuery) >> AnnotationValue.builder(PostgresChangeListenerQuery)
                .value('SELECT id FROM book WHERE id = ?')
                .member('columns', ['id'] as String[])
                .member('paths', ['id'] as String[]).build()
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(type))

        when:
        def definition = new PostgresChangeListenerDefinitionFactory(operations).create(listener)

        then:
        definition.schema() == schema
        definition.table() == table
        definition.matches(changedSchema, changedTable) == matches
        0 * operations.execute(_)

        where:
        type            | schema  | table             | changedSchema | changedTable      | matches
        QualifiedBook   | 'Sales' | 'Book.Items'      | 'Sales'       | 'Book.Items'      | true
        QualifiedBook   | 'Sales' | 'Book.Items'      | 'other'       | 'Book.Items'      | false
        QualifiedBook   | 'Sales' | 'Book.Items'      | 'sales'       | 'Book.Items'      | false
        QualifiedBook   | 'Sales' | 'Book.Items'      | 'Sales'       | 'book.items'      | false
        UnqualifiedBook | null    | 'Book'            | 'public'      | 'Book'            | true
        UnqualifiedBook | null    | 'Book'            | 'other'       | 'Book'            | true
        UnqualifiedBook | null    | 'Book'            | 'public'      | 'book'            | false
        UnescapedBook   | 'sales' | 'book'            | 'sales'       | 'book'            | true
        UnescapedBook   | 'sales' | 'book'            | 'Sales'       | 'book'            | false
        UnescapedBook   | 'sales' | 'book'            | 'sales'       | 'Book'            | false
        DefinitionBook  | null    | 'definition_book' | 'public'      | 'definition_book' | true
    }

    @Unroll
    void "invalid generated metadata reports the listener: #description"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(DefinitionBook) >> new RuntimePersistentEntity<>(DefinitionBook)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<DefinitionBook>)'
        method.stringValue(PostgresChangeNotification, 'channel') >> Optional.of('changes')
        method.getAnnotation(PostgresChangeListenerQuery) >> metadata
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(DefinitionBook))

        when:
        new PostgresChangeListenerDefinitionFactory(operations).create(listener)

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('void changed(ChangeEvent<DefinitionBook>)')
        error.message.contains(message)
        0 * operations.execute(_)

        where:
        description       | metadata                                                                  | message
        'missing metadata'| null                                                                      | 'missing its generated PostgreSQL primary-key reload query'
        'missing SQL'     | queryMetadata(null, ['id'], ['id'])                                         | 'missing or blank generated PostgreSQL reload query'
        'blank SQL'       | queryMetadata(' ', ['id'], ['id'])                                          | 'missing or blank generated PostgreSQL reload query'
        'empty keys'      | queryMetadata('SELECT id FROM book', [], [])                                | 'non-empty column and property-path arrays of equal length'
        'unequal arrays'  | queryMetadata('SELECT id FROM book', ['id'], [])                            | 'non-empty column and property-path arrays of equal length'
        'empty column'    | queryMetadata('SELECT id FROM book', [''], ['id'])                          | 'empty or duplicate generated PostgreSQL key column []'
        'duplicate column'| queryMetadata('SELECT id FROM book', ['id', 'id'], ['id', 'id'])             | 'empty or duplicate generated PostgreSQL key column [id]'
        'unknown path'    | queryMetadata('SELECT id FROM book', ['id'], ['missing'])                   | 'unknown generated PostgreSQL key property path [missing]'
    }

    void "a PostgreSQL datasource requires its notification annotation"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<DefinitionBook>)'
        method.stringValue(PostgresChangeNotification, 'channel') >> Optional.empty()
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(DefinitionBook))

        when:
        new PostgresChangeListenerDefinitionFactory(operations).create(listener)

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('void changed(ChangeEvent<DefinitionBook>)')
        error.message.contains('requires @PostgresChangeNotification for a PostgreSQL datasource')
        0 * operations.execute(_)
    }

    private static AnnotationValue<PostgresChangeListenerQuery> queryMetadata(String query, List<String> columns, List<String> paths) {
        def builder = AnnotationValue.builder(PostgresChangeListenerQuery)
            .member('columns', columns as String[]).member('paths', paths as String[])
        if (query != null) {
            builder.value(query)
        }
        builder.build()
    }

    @MappedEntity(value = 'Book.Items', schema = 'Sales')
    private static class QualifiedBook {
        @Id
        Long id
    }

    @MappedEntity('Book')
    private static class UnqualifiedBook {
        @Id
        Long id
    }

    @MappedEntity(value = 'Book', schema = 'Sales', escape = false)
    private static class UnescapedBook {
        @Id
        Long id
    }

}

@MappedEntity
class DefinitionBook {
    @Id
    Long id
}
