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
package io.micronaut.data.processor.kotlin

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.data.jdbc.notification.oracle.OracleChangeListenerQuery
import io.micronaut.data.notification.ChangeEvent
import spock.lang.Unroll

class KotlinChangeListenerVisitorSpec extends AbstractKotlinCompilerSpec {

    void "generates an Oracle ROWID reload query for a Kotlin listener"() {
        when:
        def definition = buildBeanDefinition('test.BookListener', listenerSource('''
    @ChangeListener
    @OracleChangeNotification
    open fun changed(event: ChangeEvent<Book>) {}
'''))

        then:
        definition.getRequiredMethod('changed', ChangeEvent)
            .stringValue(OracleChangeListenerQuery).orElseThrow() ==
            'SELECT book_."ID",book_."BOOK_TITLE" FROM "BOOK" book_ WHERE (ROWID = ?)'
    }

    @Unroll
    void "rejects invalid Kotlin listener: #description"() {
        when:
        buildBeanDefinition('test.BookListener', listenerSource(method))

        then:
        def exception = thrown(RuntimeException)
        exception.message.contains(message)

        where:
        description        | method                                                                                                   | message
        'missing provider' | '@ChangeListener open fun changed(event: ChangeEvent<Book>) {}'                                          | 'requires a provider-specific notification annotation'
        'non-void return'  | '@ChangeListener @OracleChangeNotification open fun changed(event: ChangeEvent<Book>): Book? = null'      | 'must return void'
        'wildcard event'   | '@ChangeListener @OracleChangeNotification open fun changed(event: ChangeEvent<*>) {}'                     | 'must declare one concrete entity type'
        'non-entity'       | '@ChangeListener @OracleChangeNotification open fun changed(event: ChangeEvent<String>) {}'                | 'type argument must be a persistent entity'
        'Oracle select'    | '@ChangeListener @OracleChangeNotification(select = "id") open fun changed(event: ChangeEvent<Book>) {}'  | 'may specify select or where only when DCN_QUERY_CHANGE_NOTIFICATION is true'
        'missing listener' | '@OracleChangeNotification open fun changed(event: ChangeEvent<Book>) {}'                                 | '@OracleChangeNotification requires @ChangeListener'
    }

    private static String listenerSource(String method) {
        """
package test

import io.micronaut.data.annotation.*
import io.micronaut.data.jdbc.annotation.*
import io.micronaut.data.notification.ChangeEvent
import jakarta.inject.Singleton

@MappedEntity
class Book {
    @field:Id var id: Long? = null
    @field:MappedProperty("book_title") var title: String? = null
}

@Singleton
open class BookListener {
$method
}
"""
    }
}

