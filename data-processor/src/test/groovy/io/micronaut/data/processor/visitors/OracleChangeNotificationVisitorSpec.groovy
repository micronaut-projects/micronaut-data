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
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery
import io.micronaut.data.jdbc.notification.ChangeEvent
import spock.lang.Unroll

class OracleChangeNotificationVisitorSpec extends AbstractTypeElementSpec {

    void "test valid Oracle listener generates reload query metadata"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {
    }
'''))
        def method = beanDefinition.getRequiredMethod('changed', ChangeEvent)
        def reloadQuery = method.stringValue(OracleChangeListenerQuery).orElseThrow()

        then:
        method.hasAnnotation(OracleChangeListenerQuery)
        reloadQuery.toUpperCase().contains('BOOK')
        reloadQuery.endsWith(' WHERE ROWID = ?')
        method.classValue(OracleChangeListenerQuery, 'entity').orElseThrow().name == 'test.Book'
    }

    void "test valid Oracle query notification accepts mapped column select"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "id, book_title",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)
    }

    void "test valid Oracle query notification accepts wildcard select and where"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "*",
        where = "book_title = 'Query Change Notification'",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)
    }

    void "test valid Oracle query notification accepts quoted mapped columns"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "\\\"ID\\\", \\\"BOOK_TITLE\\\"",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)
    }

    void "test after-expiration renewal ignores the overlap lead time"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        timeoutSeconds = 10,
        renewal = OracleChangeNotification.RenewalMode.AFTER_EXPIRATION,
        renewalLeadTimeSeconds = 10
    )
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)
    }

    @Unroll
    void "test invalid Oracle notification configuration fails compilation: #description"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource("""
    @ChangeListener
    $oracleAnnotation
    void changed(ChangeEvent<Book> event) {
    }
"""))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedMessage)

        where:
        description          | oracleAnnotation                                                                                                                                               | expectedMessage
        'blank select'       | '@OracleChangeNotification(select = " ")'                                                                                                                      | 'must have a non-blank select value'
        'select without QCN' | '@OracleChangeNotification(select = "id")'                                                                                                                     | 'may specify select or where only when DCN_QUERY_CHANGE_NOTIFICATION is true'
        'where without QCN'  | '@OracleChangeNotification(where = "book_title = \'Query Change Notification\'")'                                                                              | 'may specify select or where only when DCN_QUERY_CHANGE_NOTIFICATION is true'
        'nonzero change lag' | '@OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "DCN_NOTIFY_CHANGELAG", value = "1"))'                                       | 'requires DCN_NOTIFY_CHANGELAG to be 0'
        'zero timeout'       | '@OracleChangeNotification(timeoutSeconds = 0)'                                                                                                                | 'requires timeoutSeconds to be greater than 0'
        'zero lead time'     | '@OracleChangeNotification(timeoutSeconds = 60, renewalLeadTimeSeconds = 0)'                                                                                   | 'requires renewalLeadTimeSeconds to be greater than 0 and less than timeoutSeconds'
        'negative lead time' | '@OracleChangeNotification(timeoutSeconds = 60, renewalLeadTimeSeconds = -1)'                                                                                  | 'requires renewalLeadTimeSeconds to be greater than 0 and less than timeoutSeconds'
        'invalid lead time'  | '@OracleChangeNotification(timeoutSeconds = 60, renewalLeadTimeSeconds = 60)'                                                                                  | 'requires renewalLeadTimeSeconds to be greater than 0 and less than timeoutSeconds'
        'raw timeout'        | '@OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "NTF_TIMEOUT", value = "60"))'                                               | 'must configure Oracle registration timeout with timeoutSeconds'
        'aggregate select'   | '@OracleChangeNotification(select = "COUNT(*)", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))'      | 'unsupported selection [COUNT(*)]'
        'expression select'  | '@OracleChangeNotification(select = "UPPER(title)", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))'  | 'unsupported selection [UPPER(title)]'
        'aliased select'     | '@OracleChangeNotification(select = "title AS name", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))' | 'unsupported selection [title AS name]'
        'property name'      | '@OracleChangeNotification(select = "title", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))'         | 'unsupported selection [title]'
        'unmapped select'    | '@OracleChangeNotification(select = "isbn", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))'          | 'unsupported selection [isbn]'
        'empty selection'    | '@OracleChangeNotification(select = "id, , title", properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"))'   | 'unsupported selection []'
    }

    void "test Oracle configuration requires a change listener"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource('''
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('@OracleChangeNotification requires @ChangeListener')
    }

    private static String listenerSource(String method) {
        """
package test;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import jakarta.inject.Singleton;

@MappedEntity
class Book {
    @Id
    public Long id;
    @MappedProperty("book_title")
    public String title;
}

@Singleton
class BookListener {
$method
}
"""
    }
}
