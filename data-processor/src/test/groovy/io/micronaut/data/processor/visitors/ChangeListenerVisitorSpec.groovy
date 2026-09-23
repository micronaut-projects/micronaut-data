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
import io.micronaut.data.jdbc.notification.ChangeEvent
import spock.lang.Unroll

class ChangeListenerVisitorSpec extends AbstractTypeElementSpec {

    void "test valid database-neutral listener compiles without Oracle configuration"() {
        when:
        def beanDefinition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    void changed(ChangeEvent<Book> event) {
    }
'''))

        then:
        beanDefinition.getRequiredMethod('changed', ChangeEvent.class) != null
    }

    @Unroll
    void "test invalid listener signature fails compilation: #description"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource(method))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(expectedMessage)

        where:
        description          | method                                                                   | expectedMessage
        'private method'     | '@ChangeListener private void changed(ChangeEvent<Book> event) {}'       | 'must be a non-private instance method'
        'static method'      | '@ChangeListener static void changed(ChangeEvent<Book> event) {}'        | 'must be a non-private instance method'
        'entity parameter'   | '@ChangeListener void changed(Book book) {}'                             | 'method argument must be ChangeEvent<E>'
        'zero arguments'     | '@ChangeListener void changed() {}'                                      | 'must declare exactly one ChangeEvent argument'
        'raw event'          | '@ChangeListener void changed(ChangeEvent event) {}'                     | 'must declare one concrete entity type'
        'wildcard event'     | '@ChangeListener void changed(ChangeEvent<?> event) {}'                  | 'must declare one concrete entity type'
        'non-entity event'   | '@ChangeListener void changed(ChangeEvent<String> event) {}'             | 'type argument must be a persistent entity'
        'multiple arguments' | '@ChangeListener void changed(ChangeEvent<Book> event, int id) {}'       | 'must declare exactly one ChangeEvent argument'
        'non-void method'    | '@ChangeListener Book changed(ChangeEvent<Book> event) { return null; }' | 'method must return void'
    }

    void "test unresolved listener entity type fails compilation"() {
        when:
        buildBeanDefinition('test.BookListener', '''
package test;

import io.micronaut.data.jdbc.annotation.ChangeListener;
import io.micronaut.data.jdbc.notification.ChangeEvent;
import jakarta.inject.Singleton;

@Singleton
class BookListener<T> {
    @ChangeListener
    void changed(ChangeEvent<T> event) {
    }
}
''')

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains('must declare one concrete entity type')
    }

    private static String listenerSource(String method) {
        """
package test;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.jdbc.annotation.ChangeListener;
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
