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

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.core.convert.ConversionService
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.ChangeListener
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.Relation
import io.micronaut.data.annotation.Where
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.notification.ChangeListenerRegistry
import io.micronaut.data.jdbc.config.DataJdbcConfiguration
import io.micronaut.data.model.DataType
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.postgres.PostgresNotificationQuery
import io.micronaut.data.runtime.criteria.RuntimeCriteriaBuilder
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.data.model.runtime.convert.AttributeConverter
import io.micronaut.core.convert.ConversionContext
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification
import spock.lang.Unroll

import java.sql.Connection
import java.sql.ResultSet
import java.sql.Statement

class PostgresChangeListenerDefinitionSpec extends Specification {
    private ApplicationContext context = ApplicationContext.run([
            'app.table': 'ÄBooks', 'app.schema': 'ÄSales'
    ])

    void cleanup() {
        context.close()
    }


    @Unroll
    void "metadata mapping for #type.simpleName matches #changedSchema.#changedTable: #matches"() {
        given:
        def entity = new RuntimePersistentEntity<>(type)
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(type) >> entity
        operations.conversionService >> ConversionService.SHARED
        def method = Mock(ExecutableMethod)
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
                .member('channel', 'changes').build()
        method.getAnnotation(PostgresChangeListenerQuery) >> AnnotationValue.builder(PostgresChangeListenerQuery)
                .value('SELECT id FROM book WHERE id = ?')
                .member('columns', ['id'] as String[])
                .member('paths', ['id'] as String[]).build()
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(type))

        when:
        def definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)

        then:
        definition.beanDefinition().is(listener.beanDefinition())
        definition.method().is(method)
        definition.schema() == schema
        definition.table() == table
        definition.matches(changedSchema, changedTable) == matches
        0 * operations.execute(_)

        where:
        type                    | schema   | table             | changedSchema | changedTable      | matches
        QualifiedBook           | 'Sales'  | 'Book.Items'      | 'Sales'       | 'Book.Items'      | true
        PlaceholderBook         | 'ÄSales' | 'ÄBooks'          | 'ÄSales'      | 'ÄBooks'          | true
        UnquotedPlaceholderBook | 'Äsales' | 'Äbooks'          | 'Äsales'      | 'Äbooks'          | true
        QualifiedBook           | 'Sales'  | 'Book.Items'      | 'other'       | 'Book.Items'      | false
        QualifiedBook           | 'Sales'  | 'Book.Items'      | 'sales'       | 'Book.Items'      | false
        QualifiedBook           | 'Sales'  | 'Book.Items'      | 'Sales'       | 'book.items'      | false
        UnqualifiedBook         | null     | 'Book'            | 'public'      | 'Book'            | true
        UnqualifiedBook         | null     | 'Book'            | 'other'       | 'Book'            | true
        UnqualifiedBook         | null     | 'Book'            | 'public'      | 'book'            | false
        UnescapedBook           | 'sales'  | 'book'            | 'sales'       | 'book'            | true
        UnescapedBook           | 'sales'  | 'book'            | 'Sales'       | 'book'            | false
        UnescapedBook           | 'sales'  | 'book'            | 'sales'       | 'Book'            | false
        DefinitionBook          | null     | 'definition_book' | 'public'      | 'definition_book' | true
        QuotedUnicodeBook       | 'ÄSales' | 'ÄBook'           | 'ÄSales'      | 'ÄBook'           | true
        QuotedUnicodeBook       | 'ÄSales' | 'ÄBook'           | 'Äsales'      | 'Äbook'           | false
        UnquotedUnicodeBook     | 'Äsales' | 'Äbook'           | 'Äsales'      | 'Äbook'           | true
        UnquotedUnicodeBook     | 'Äsales' | 'Äbook'           | 'äsales'      | 'äbook'           | false
        QuotedLongBook          | 'S' * 62 | 'A' * 62          | 'S' * 62      | 'A' * 62          | true
        UnquotedLongBook        | 's' * 62 | 'a' * 62          | 's' * 62      | 'a' * 62          | true
    }

    @Unroll
    void "invalid generated metadata reports the listener: #description"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(DefinitionBook) >> new RuntimePersistentEntity<>(DefinitionBook)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<DefinitionBook>)'
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
                .member('channel', 'changes').build()
        method.getAnnotation(PostgresChangeListenerQuery) >> metadata
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(DefinitionBook))

        when:
        new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('void changed(ChangeEvent<DefinitionBook>)')
        error.message.contains(message)
        0 * operations.execute(_)

        where:
        description        | metadata                                                         | message
        'missing SQL'      | queryMetadata(null, ['id'], ['id'])                              | 'missing or blank generated PostgreSQL reload query'
        'blank SQL'        | queryMetadata(' ', ['id'], ['id'])                               | 'missing or blank generated PostgreSQL reload query'
        'empty keys'       | queryMetadata('SELECT id FROM book', [], [])                     | 'non-empty column and property-path arrays of equal length'
        'unequal arrays'   | queryMetadata('SELECT id FROM book', ['id'], [])                 | 'non-empty column and property-path arrays of equal length'
        'empty column'     | queryMetadata('SELECT id FROM book', [''], ['id'])               | 'empty or duplicate generated PostgreSQL key column []'
        'duplicate column' | queryMetadata('SELECT id FROM book', ['id', 'id'], ['id', 'id']) | 'empty or duplicate generated PostgreSQL key column [id]'
        'unknown path'     | queryMetadata('SELECT id FROM book', ['id'], ['missing'])        | 'unknown generated PostgreSQL key property path [missing]'
    }

    @Unroll
    void "fallback reload and schema generation share mapped keys for #type.simpleName"() {
        given:
        def entity = new RuntimePersistentEntity<>(type)
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(type) >> entity
        operations.conversionService >> ConversionService.SHARED
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void inherited(ChangeEvent<Book>)'
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
            .member('channel', 'changes').build()
        method.hasStereotype(PostgresChangeNotification) >> true
        method.stringValue(PostgresChangeNotification, 'channel') >> Optional.of('changes')
        method.stringValue(ChangeListener, 'dataSource') >> Optional.empty()
        method.getArguments() >> ([Argument.of(ChangeEvent, Argument.of(type))] as Argument[])
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(type))
        PreparedQuery prepared
        operations.findOne(_ as PreparedQuery) >> { PreparedQuery query -> prepared = query; null }
        def bound = []
        def binder = Stub(BindableParametersStoredQuery.Binder) {
            bindOne(_, _) >> { binding, value -> bound.add([binding.column(), binding.dataType, value]) }
        }

        when:
        def definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        definition.loader().reload(keys)
        prepared.bindParameters(binder)

        then:
        definition.loader().columns() == keys.keySet()
        prepared.query == sql
        bound == bindings

        when: 'schema generation obtains the same columns without repository operations'
        def registry = new ChangeListenerRegistry()
        registry.process(listener.beanDefinition(), method)
        def statements = []
        def statement = Stub(Statement) {
            executeQuery(_ as String) >> Stub(ResultSet) {
                next() >> true
                getInt(1) >> 0
            }
            execute(_ as String) >> { String command -> statements.add(command); true }
        }
        def connection = Stub(Connection) {
            getAutoCommit() >> true
            createStatement() >> statement
        }
        def configuration = new DataJdbcConfiguration('default')
        configuration.dialect = Dialect.POSTGRES
        configuration.schemaGenerate = SchemaGenerate.CREATE
        new PostgresNotificationSchemaGenerator(registry, context.environment, new PostgresNotificationConfiguration())
            .generate(connection, configuration, [entity] as PersistentEntity[])

        then:
        def arguments = (["E'changes'"] + keys.keySet().collect { "E'$it'" }).join(', ')
        statements.find { it.contains('FOR EACH ROW') }.endsWith(
            'EXECUTE FUNCTION "Sales"."micronaut_data_notify_change"(' + arguments + ')')
        0 * operations.execute(_)

        where:
        type                  | keys                                                     | sql                                                                                                                                       | bindings
        FallbackScalarBook    | [BookId: '42']                                           | 'SELECT book_."BookId",book_."book_title" FROM "Sales"."Books" book_ WHERE (book_."BookId" = ?)'                                            | [['BookId', DataType.LONG, 42L]]
        FallbackCompositeBook | [TenantId: 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67', BookId: '42'] | 'SELECT book_."TenantId",book_."BookId",book_."book_title" FROM "Sales"."Books" book_ WHERE (book_."TenantId" = ? AND book_."BookId" = ?)' | [['TenantId', DataType.UUID, UUID.fromString('b3d93d9a-1b6c-40ae-a761-f15d6010ad67')], ['BookId', DataType.LONG, 42L]]
        FallbackEmbeddedBook  | [TenantId: 'b3d93d9a-1b6c-40ae-a761-f15d6010ad67', BookId: '42'] | 'SELECT book_."TenantId",book_."BookId",book_."book_title" FROM "Sales"."Books" book_ WHERE (book_."TenantId" = ? AND book_."BookId" = ?)' | [['TenantId', DataType.UUID, UUID.fromString('b3d93d9a-1b6c-40ae-a761-f15d6010ad67')], ['BookId', DataType.LONG, 42L]]
    }

    void "fallback reload SQL resolves configured table and schema names"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(PlaceholderBook) >> new RuntimePersistentEntity<>(PlaceholderBook)
        operations.conversionService >> ConversionService.SHARED
        def method = Mock(ExecutableMethod)
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
            .member('channel', 'changes').build()
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(PlaceholderBook))
        PreparedQuery prepared
        operations.findOne(_ as PreparedQuery) >> { PreparedQuery query -> prepared = query; null }

        when:
        def definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        definition.loader().reload([id: '42'])

        then:
        prepared.query.contains('FROM "ÄSales"."ÄBooks"')
        !prepared.query.contains('${')
    }

    @Unroll
    void "fallback key columns obey PostgreSQL identifier rules for #type.simpleName"() {
        given:
        def entity = new RuntimePersistentEntity<>(type)

        expect:
        PostgresNotificationQuery.build(entity, new RuntimeCriteriaBuilder()).keys()*.column() == [column]
        PostgresNotificationQuery.keyColumns(entity) == [column]

        where:
        type                | column
        FallbackQuotedKey   | 'ÄBookId'
        FallbackUnquotedKey | 'Äbookid'
        FallbackLongKey     | 'a' * 62
    }

    void "fallback preserves constant entity filters"() {
        given:
        def entity = new RuntimePersistentEntity<>(FallbackConstantFilter)

        when:
        def reload = PostgresNotificationQuery.build(entity, new RuntimeCriteriaBuilder())

        then:
        reload.query().contains('enabled = true')
        reload.keys().collect { it.path().path } == ['id']
        reload.keys()*.column() == ['id']
    }

    @Unroll
    void "fallback rejects unsupported mapping for #type.simpleName"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(type) >> new RuntimePersistentEntity<>(type)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void inherited(ChangeEvent<Book>)'
        method.getAnnotation(PostgresChangeNotification) >> AnnotationValue.builder(PostgresChangeNotification)
            .member('channel', 'changes').build()
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(type))

        when:
        new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('void inherited(ChangeEvent<Book>)')
        error.message.contains(message)
        0 * operations.findOne(_)

        where:
        type                  | message
        FallbackMissingId     | 'requires a mapped primary key'
        FallbackAssociationId | 'does not support association or converted primary keys'
        FallbackConvertedId   | 'does not support association or converted primary keys'
        FallbackFilteredBook  | 'additional query parameters'
        FallbackDuplicateId   | 'duplicate PostgreSQL key column [BookId]'
    }

    void "a PostgreSQL datasource requires its notification annotation"() {
        given:
        def operations = Mock(JdbcRepositoryOperations)
        def method = Mock(ExecutableMethod)
        method.getDescription(true) >> 'void changed(ChangeEvent<DefinitionBook>)'
        method.getAnnotation(PostgresChangeNotification) >> null
        def listener = new ChangeListenerMethod(Mock(BeanDefinition), method, Argument.of(DefinitionBook))

        when:
        new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)

        then:
        def error = thrown(IllegalStateException)
        error.message.contains('void changed(ChangeEvent<DefinitionBook>)')
        error.message.contains('requires @PostgresChangeNotification for a PostgreSQL datasource')
        0 * operations.execute(_)
    }

    void "unresolved mapping placeholders fail rather than becoming literal identifiers"() {
        when:
        PostgresNotificationIdentifiers.resolve('${missing.table}', true, context.environment)

        then:
        thrown(io.micronaut.context.exceptions.ConfigurationException)
    }

    private static AnnotationValue<PostgresChangeListenerQuery> queryMetadata(String query, List<String> columns, List<String> paths) {
        def builder = AnnotationValue.builder(PostgresChangeListenerQuery)
                .member('columns', columns as String[]).member('paths', paths as String[])
        if (query != null) {
            builder.value(query)
        }
        builder.build()
    }

    @MappedEntity(escape = true)
    private static class FallbackQuotedKey {
        @Id @MappedProperty('ÄBookId') Long id
    }

    @MappedEntity(escape = false)
    private static class FallbackUnquotedKey {
        @Id @MappedProperty('ÄBookId') Long id
    }

    @MappedEntity(escape = false)
    private static class FallbackLongKey {
        @Id @MappedProperty('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAé') Long id
    }

    @MappedEntity
    @Where('enabled = true')
    private static class FallbackConstantFilter {
        @Id Long id
        Boolean enabled
    }

    @MappedEntity(value = 'Books', schema = 'Sales', alias = 'book_')
    private static class FallbackScalarBook {
        @Id @MappedProperty('BookId')
        Long id
        @MappedProperty('book_title')
        String title
    }

    @MappedEntity(value = 'Books', schema = 'Sales', alias = 'book_')
    private static class FallbackCompositeBook {
        @Id @MappedProperty('TenantId')
        UUID tenant
        @Id @MappedProperty('BookId')
        Long id
        @MappedProperty('book_title')
        String title
    }

    @Embeddable
    private static class FallbackBookId {
        @MappedProperty('TenantId')
        UUID tenant
        @MappedProperty('BookId')
        Long number
    }

    @MappedEntity(value = 'Books', schema = 'Sales', alias = 'book_')
    private static class FallbackEmbeddedBook {
        @EmbeddedId
        FallbackBookId id
        @MappedProperty('book_title')
        String title
    }

    @MappedEntity
    private static class FallbackMissingId {
        String title
    }

    @MappedEntity
    private static class FallbackAssociationId {
        @Id @Relation(Relation.Kind.MANY_TO_ONE)
        DefinitionBook book
    }

    @MappedEntity
    private static class FallbackConvertedId {
        @Id @MappedProperty(converter = FallbackConverter)
        Long id
    }

    private static class FallbackConverter implements AttributeConverter<Long, String> {
        @Override
        String convertToPersistedValue(Long value, ConversionContext context) { value.toString() }
        @Override
        Long convertToEntityValue(String value, ConversionContext context) { Long.valueOf(value) }
    }

    @MappedEntity
    @Where('id = :other')
    private static class FallbackFilteredBook {
        @Id Long id
    }

    @MappedEntity
    private static class FallbackDuplicateId {
        @Id @MappedProperty('BookId') Long first
        @Id @MappedProperty('BookId') Long second
    }

    @MappedEntity(value = '${app.table}', schema = '${app.schema}')
    private static class PlaceholderBook {
        @Id
        Long id
    }

    @MappedEntity(value = '${app.table}', schema = '${app.schema}', escape = false)
    private static class UnquotedPlaceholderBook {
        @Id
        Long id
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

    @MappedEntity(value = 'ÄBook', schema = 'ÄSales', escape = true)
    private static class QuotedUnicodeBook {
        @Id
        Long id
    }

    @MappedEntity(value = 'ÄBook', schema = 'ÄSales', escape = false)
    private static class UnquotedUnicodeBook {
        @Id
        Long id
    }

    @MappedEntity(value = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAé', schema = 'SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSé', escape = true)
    private static class QuotedLongBook {
        @Id
        Long id
    }

    @MappedEntity(value = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAé', schema = 'SSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSSé', escape = false)
    private static class UnquotedLongBook {
        @Id
        Long id
    }

}

@MappedEntity
class DefinitionBook {
    @Id
    Long id
}
