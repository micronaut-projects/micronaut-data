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
    @Id public Long id;
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
