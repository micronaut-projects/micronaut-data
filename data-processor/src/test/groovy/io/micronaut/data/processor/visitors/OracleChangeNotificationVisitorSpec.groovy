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
import io.micronaut.context.annotation.Executable
import io.micronaut.data.intercept.annotation.OracleChangeListenerQuery
import io.micronaut.data.jdbc.annotation.ChangeListener
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
        reloadQuery.endsWith(' WHERE (ROWID = ?)')
        method.classValue(OracleChangeListenerQuery, 'entity').orElseThrow().name == 'test.Book'
    }

    void "test ROWID predicate composes with entity-level Where"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {
    }
''', '@Where("enabled = 1")'))
        def method = beanDefinition.getRequiredMethod('changed', ChangeEvent)
        def reloadQuery = method.stringValue(OracleChangeListenerQuery).orElseThrow()

        then:
        reloadQuery.contains('enabled = 1')
        reloadQuery.contains('ROWID = ?')
        reloadQuery.contains(' AND ')
        reloadQuery.findAll(/\bWHERE\b/).size() == 1
        reloadQuery.count('?') == 1
    }

    void "test parameterized entity-level Where fails when reloading by ROWID"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {
    }
''', '@Where("enabled = :enabled")'))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('parameterized entity @Where clauses are not supported')
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

    void "test quoted select column must match Oracle's rendered identifier exactly"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "\\\"book_title\\\"",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('unsupported selection ["book_title"]')
    }

    void "test select recognizes embedded and association columns but not their Java property names"() {
        given:
        String source = listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "detail_code, category_id",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
''').replace('public boolean enabled;', '''public boolean enabled;
    @Relation(Relation.Kind.EMBEDDED)
    @MappedProperty("detail")
    public BookDetails details;
    @Relation(Relation.Kind.MANY_TO_ONE)
    public Category category;
    public BookDetails getDetails() { return details; }
    public Category getCategory() { return category; }''').replace('@Singleton\nclass BookListener', '''@Embeddable
class BookDetails {
    public String code;
    public String getCode() { return code; }
}

@MappedEntity
class Category {
    @Id public Long id;
    public Long getId() { return id; }
}

@Singleton
class BookListener''').replace('import io.micronaut.data.annotation.Id;', '''import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.annotation.Embeddable;''')

        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', source)

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)

        when:
        buildBeanDefinition('test.BookListener', source.replace('detail_code, category_id', 'details, category'))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('unsupported selection [details]')
    }

    void "test select rejects a transient field that is not a physical column"() {
        given:
        String source = listenerSource('''
    @ChangeListener
    @OracleChangeNotification(
        select = "temporary",
        properties = @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true")
    )
    void changed(ChangeEvent<Book> event) {
    }
''').replace('public boolean enabled;', '''public boolean enabled;
    @Transient public String temporary;''').replace('import io.micronaut.data.annotation.Id;', '''import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Transient;''')

        when:
        buildBeanDefinition('test.BookListener', source)

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('unsupported selection [temporary]')
    }

    void "test composed ChangeListener is accepted with Oracle configuration"() {
        given:
        String source = listenerSource('''
    @BookChanges
    @OracleChangeNotification
    void changed(ChangeEvent<Book> event) {
    }
''').replace('@Singleton\nclass BookListener', '''@ChangeListener(dataSource = "archive")
@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
@java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
@interface BookChanges {
}

@Singleton
class BookListener''')

        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', source)
        def method = beanDefinition.getRequiredMethod('changed', ChangeEvent)

        then:
        method.hasStereotype(ChangeListener)
        method.hasAnnotation(OracleChangeListenerQuery)
        method.stringValue(ChangeListener, 'dataSource').orElse('default') == 'archive'
        method.annotationMetadata.getAnnotationTypesByStereotype(Executable).contains(ChangeListener)
    }

    @Unroll
    void "test unsupported Oracle option #propertyName fails compilation"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource("""
    @ChangeListener
    @OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "$propertyName", value = "$propertyValue"))
    void changed(ChangeEvent<Book> event) {
    }
"""))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedMessage)

        where:
        propertyName               | propertyValue             | expectedMessage
        'DCN_NOTIFY_ROWIDS'        | 'false'                   | 'requires DCN_NOTIFY_ROWIDS to be true'
        'DCN_CLIENT_INIT_REGID'    | '0'                       | 'DCN_CLIENT_INIT_REGID: reusing an existing reliable DCN registration is not supported'
        'NTF_GROUPING_CLASS'       | 'NTF_GROUPING_CLASS_TIME' | 'NTF_GROUPING_CLASS: notification grouping is not supported'
        'NTF_GROUPING_VALUE'       | '30'                      | 'NTF_GROUPING_VALUE: notification grouping is not supported'
        'NTF_GROUPING_TYPE'        | 'NTF_GROUPING_TYPE_LAST'  | 'NTF_GROUPING_TYPE: notification grouping is not supported'
        'NTF_GROUPING_REPEAT_TIME' | '2'                       | 'NTF_GROUPING_REPEAT_TIME: notification grouping is not supported'
        'NTF_GROUPING_START_TIME'  | 'tomorrow'                | 'NTF_GROUPING_START_TIME: notification grouping is not supported'
        'DCN_PULL_NOTIFICATIONS'   | 'true'                    | 'DCN_PULL_NOTIFICATIONS [true] is not supported'
        'DCN_PULL_QUEUE_NAME'      | 'CHANGES'                 | 'DCN_PULL_QUEUE_NAME is not supported'
    }

    void "test last query notification property enables query fragments"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(select = "id", properties = {
        @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "false"),
        @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"),
        @OracleChangeNotification.Property(name = "NTF_QOS_RELIABLE", value = "false")
    })
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)

    }

    @Unroll
    void "test last query notification property disables query fragments (#queryFragment)"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource("""
    @ChangeListener
    @OracleChangeNotification($queryFragment, properties = {
        @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "true"),
        @OracleChangeNotification.Property(name = "DCN_QUERY_CHANGE_NOTIFICATION", value = "false")
    })
    void changed(ChangeEvent<Book> event) {
    }
"""))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('may specify select or where only when DCN_QUERY_CHANGE_NOTIFICATION is true')

        where:
        queryFragment << ['select = "id"', 'where = "id > 0"']
    }

    void "test explicitly disabled grouping and pull options compile"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(properties = {
        @OracleChangeNotification.Property(name = "NTF_GROUPING_CLASS", value = "NTF_GROUPING_CLASS_NONE"),
        @OracleChangeNotification.Property(name = "DCN_PULL_NOTIFICATIONS", value = "false"),
        @OracleChangeNotification.Property(name = "DCN_NOTIFY_ROWIDS", value = "true")
    })
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent).hasAnnotation(OracleChangeListenerQuery)
    }

    void "test finite registration timeout compiles"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "NTF_TIMEOUT", value = "60"))
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
        'negative timeout'   | '@OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "NTF_TIMEOUT", value = "-1"))'                                               | 'NTF_TIMEOUT must be a non-negative integer number of seconds'
        'invalid timeout'    | '@OracleChangeNotification(properties = @OracleChangeNotification.Property(name = "NTF_TIMEOUT", value = "invalid"))'                                          | 'NTF_TIMEOUT must be a non-negative integer number of seconds'
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

    private static String listenerSource(String method, String entityAnnotation = '') {
        """
package test;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Where;
import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.OracleChangeNotification;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import jakarta.inject.Singleton;

@MappedEntity
$entityAnnotation
class Book {
    @Id
    public Long id;
    @MappedProperty("book_title")
    public String title;
    public boolean enabled;
    public Long getId() { return id; }
    public String getTitle() { return title; }
    public boolean isEnabled() { return enabled; }
}

@Singleton
class BookListener {
$method
}
"""
    }
}
