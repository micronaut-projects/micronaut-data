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
package io.micronaut.data.processor.groovy

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery
import io.micronaut.data.jdbc.notification.ChangeEvent
import spock.lang.Unroll

class GroovyChangeListenerVisitorSpec extends AbstractBeanDefinitionSpec {

    void "generates an Oracle ROWID reload query for a Groovy listener"() {
        when:
        def definition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {}
'''))

        then:
        definition.getRequiredMethod('changed', ChangeEvent)
            .stringValue(OracleChangeListenerQuery).orElseThrow() ==
            'SELECT book_."ID",book_."BOOK_TITLE" FROM "BOOK" book_ WHERE (ROWID = ?)'
    }

    @Unroll
    void "rejects invalid Groovy listener: #description"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource(method))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(message)

        where:
        description         | method                                                                                              | message
        'missing provider'  | '@ChangeListener void changed(ChangeEvent<Book> event) {}'                                           | 'requires a provider-specific notification annotation'
        'non-void return'   | '@ChangeListener @OracleChangeNotification Book changed(ChangeEvent<Book> event) { null }'            | 'must return void'
        'raw event'         | '@ChangeListener @OracleChangeNotification void changed(ChangeEvent event) {}'                         | 'must declare one concrete entity type'
        'non-entity'        | '@ChangeListener @OracleChangeNotification void changed(ChangeEvent<String> event) {}'                 | 'type argument must be a persistent entity'
        'Oracle select'     | '@ChangeListener @OracleChangeNotification(select = "id") void changed(ChangeEvent<Book> event) {}'   | 'may specify select or where only when DCN_QUERY_CHANGE_NOTIFICATION is true'
        'missing listener'  | '@OracleChangeNotification void changed(ChangeEvent<Book> event) {}'                                  | '@OracleChangeNotification requires @ChangeListener'
    }

    private static String listenerSource(String method) {
        """
package test

import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.*
import io.micronaut.data.jdbc.notification.ChangeEvent
import jakarta.inject.Singleton

@MappedEntity
class Book {
    @Id Long id
    @MappedProperty("book_title") String title
}

@Singleton
class BookListener {
$method
}
"""
    }
}

