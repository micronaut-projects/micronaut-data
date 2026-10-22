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
package io.micronaut.data.processor.visitors

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.data.intercept.annotation.PostgresChangeListenerQuery
import io.micronaut.data.jdbc.notification.ChangeEvent
import spock.lang.Unroll

class PostgresChangeNotificationVisitorSpec extends AbstractTypeElementSpec {

    @Unroll
    void "valid channel is retained exactly: #channel"() {
        when:
        def definition = buildBeanDefinition('test.BookListener', source(channel))

        then:
        definition.getRequiredMethod('changed', ChangeEvent).stringValue(
                'io.micronaut.data.jdbc.annotation.PostgresChangeNotification', 'channel').orElseThrow() == channel

        where:
        channel << ['Book Changes', 'a' * 63, ('é' * 31) + 'a']
    }

    @Unroll
    void "invalid channel fails compilation: #description"() {
        when:
        buildBeanDefinition('test.BookListener', source(channel))

        then:
        def e = thrown(RuntimeException)
        e.message.contains('channel must be non-blank, contain no NUL characters, and occupy at most 63 UTF-8 bytes')

        where:
        description | channel
        'empty'     | ''
        'blank'     | '   '
        'long'      | 'a' * 64
        'UTF-8'     | 'é' * 32
        'NUL'       | 'book\\000changes'
    }

    void "Postgres annotation requires the generic listener contract"() {
        when:
        buildBeanDefinition('test.BookListener', source('books').replace('@ChangeListener', ''))

        then:
        def e = thrown(RuntimeException)
        e.message.contains('@PostgresChangeNotification requires @ChangeListener')
    }

    void "generates bound reload SQL and primary key metadata"() {
        when:
        def method = buildBeanDefinition('test.BookListener', source('books')).getRequiredMethod('changed', ChangeEvent)
        def query = method.getAnnotation(PostgresChangeListenerQuery)

        then:
        query.stringValue().orElseThrow() == 'SELECT book_."id" FROM "book" book_ WHERE (book_."id" = ?)'
        query.stringValues('columns') as List == ['id']
        query.stringValues('paths') as List == ['id']
    }

    void "composite mapped keys retain query binding order"() {
        given:
        def input = source('books').replace('class Book {', """class Book {
    @Id @io.micronaut.data.annotation.MappedProperty("TenantId") private java.util.UUID tenant;
    public java.util.UUID getTenant() { return tenant; }
    public void setTenant(java.util.UUID tenant) { this.tenant = tenant; }
""")

        when:
        def query = buildBeanDefinition('test.BookListener', input).getRequiredMethod('changed', ChangeEvent)
                .getAnnotation(PostgresChangeListenerQuery)

        then:
        query.stringValues('columns') as List == ['TenantId', 'id']
        query.stringValues('paths') as List == ['tenant', 'id']
        query.stringValue().orElseThrow().contains('book_."TenantId" = ? AND book_."id" = ?')
    }

    void "embedded keys generate flattened property bindings"() {
        given:
        def input = source('books').replaceAll(/(?s)@MappedEntity\nclass Book \{.*?\n\}/, """
@MappedEntity
record Book(@io.micronaut.data.annotation.EmbeddedId BookId id) {}
@io.micronaut.data.annotation.Embeddable
record BookId(Long tenant, Long number) {}
""")

        when:
        def query = buildBeanDefinition('test.BookListener', input).getRequiredMethod('changed', ChangeEvent)
                .getAnnotation(PostgresChangeListenerQuery)

        then:
        query.stringValues('paths') as List == ['id.tenant', 'id.number']
        query.stringValues('columns').size() == 2
        query.stringValue().orElseThrow().count('?') == 2
    }

    void "missing primary key fails compilation"() {
        when:
        buildBeanDefinition('test.BookListener', source('books').replace('@Id private', 'private'))

        then:
        def e = thrown(RuntimeException)
        e.message.contains('requires a mapped primary key')
    }

    void "association primary keys fail compilation"() {
        given:
        def input = source('books').replaceAll(/(?s)@MappedEntity\nclass Book \{.*?\n\}/, """
@MappedEntity
record Book(@Id @io.micronaut.data.annotation.Relation(io.micronaut.data.annotation.Relation.Kind.MANY_TO_ONE) Author author) {}
@MappedEntity
record Author(@Id Long id) {}
""")

        when:
        buildBeanDefinition('test.BookListener', input)

        then:
        def e = thrown(RuntimeException)
        e.message.contains('does not support association or converted primary keys')
    }

    void "converted primary keys fail compilation"() {
        given:
        def input = source('books').replaceAll(/(?s)@MappedEntity\nclass Book \{.*?\n\}/, """
@MappedEntity
record Book(@Id @io.micronaut.data.annotation.MappedProperty(converter = BookIdConverter.class) Long id) {}
class BookIdConverter implements io.micronaut.data.model.runtime.convert.AttributeConverter<Long, String> {
    @Override
    public String convertToPersistedValue(Long value, io.micronaut.core.convert.ConversionContext context) {
        return value.toString();
    }
    @Override
    public Long convertToEntityValue(String value, io.micronaut.core.convert.ConversionContext context) {
        return Long.valueOf(value);
    }
}
""")

        when:
        buildBeanDefinition('test.BookListener', input)

        then:
        def e = thrown(RuntimeException)
        e.message.contains('does not support association or converted primary keys')
    }

    @Unroll
    void "mapped key column names respect PostgreSQL identifiers: #name, escape=#escape"() {
        given:
        def input = source('books')
                .replace('@MappedEntity\nclass Book', "@MappedEntity(escape = $escape)\nclass Book")
                .replace('@Id private', '@Id @io.micronaut.data.annotation.MappedProperty("' + name + '") private')

        when:
        def query = buildBeanDefinition('test.BookListener', input).getRequiredMethod('changed', ChangeEvent)
                .getAnnotation(PostgresChangeListenerQuery)

        then:
        query.stringValues('columns') as List == [column]
        query.stringValues('paths') as List == ['id']
        query.stringValue().orElseThrow().contains(escape ? 'book_."' + name + '" = ?' : 'book_.' + name + ' = ?')

        where:
        escape | name             | column
        true   | 'BookId'         | 'BookId'
        false  | 'BookId'         | 'bookid'
        true   | 'ÄBookId'        | 'ÄBookId'
        false  | 'ÄBookId'        | 'Äbookid'
        true   | 'A' * 62 + 'é'   | 'A' * 62
        false  | 'A' * 62 + 'é'   | 'a' * 62
    }

    void "parameterized entity filters fail compilation"() {
        when:
        buildBeanDefinition('test.BookListener', source('books').replace('@MappedEntity\nclass Book',
                '@MappedEntity\n@io.micronaut.data.annotation.Where("id = :other")\nclass Book'))

        then:
        def e = thrown(RuntimeException)
        e.message.contains('additional query parameters')
        e.message.contains('parameterized entity @Where clauses are not supported')
    }

    private static String source(String channel) {
        """
package test;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import jakarta.inject.Singleton;
@MappedEntity
class Book {
    @Id private Long id;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
}
@Singleton
class BookListener {
    @ChangeListener
    @PostgresChangeNotification(channel = "$channel")
    void changed(ChangeEvent<Book> event) {}
}
"""
    }
}
