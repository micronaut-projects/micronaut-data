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
package io.micronaut.data.nitrite

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.data.nitrite.model.Person
import io.micronaut.data.nitrite.repository.PersonRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import org.slf4j.LoggerFactory
import spock.lang.Specification

@MicronautTest
class NitriteLoggingSpec extends Specification {

    @Inject
    PersonRepository personRepository

    ListAppender<ILoggingEvent> appender

    void setup() {
        Logger queryLogger = (Logger) LoggerFactory.getLogger("io.micronaut.data.nitrite.runtime.DefaultNitriteRepositoryOperations")
        queryLogger.setLevel(Level.DEBUG)
        appender = new ListAppender<>()
        appender.start()
        queryLogger.addAppender(appender)
    }

    void cleanup() {
        Logger queryLogger = (Logger) LoggerFactory.getLogger("io.micronaut.data.nitrite.runtime.DefaultNitriteRepositoryOperations")
        queryLogger.detachAppender(appender)
    }

    void "test query logging with parameters"() {
        when:
        personRepository.save(new Person("Joe", 25))
        personRepository.findByName("Joe")

        then:
        def logs = appender.list.collect { it.formattedMessage }
        logs.any { it.contains("Executing Nitrite 'insert' into collection [persons] with document:") }
        logs.any { it.contains("Executing Nitrite 'find' on collection [persons] with filter: (name == Joe)") }
    }

    void "test query logging with multiple parameters"() {
        when:
        personRepository.save(new Person("Bob", 30))
        personRepository.findByNameAndAge("Bob", 30)

        then:
        def logs = appender.list.collect { it.formattedMessage }
        // Match the observed structure
        logs.any { it.contains("Executing Nitrite 'find' on collection [persons] with filter: ((name == Bob) && (age == 30))") }
    }

    void "test redundant numeric expansion"() {
        when:
        personRepository.save(new Person("Joe", 2))
        personRepository.findByAge(2)

        then:
        def logs = appender.list.collect { it.formattedMessage }
        // The fix should produce "(age == 2)" and NOT "((age == 2) || (age == 2) ...)"
        logs.any { it.contains("Executing Nitrite 'find' on collection [persons] with filter: (age == 2)") }
        !logs.any { it.contains("|| (age == 2)") }
    }
}
