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
package io.micronaut.data.jdbc.notification

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.micronaut.context.ApplicationContext
import io.micronaut.data.notification.ChangeOperation
import io.micronaut.inject.ExecutableMethod
import org.slf4j.LoggerFactory
import spock.lang.Specification

class ChangeListenerExceptionHandlerSpec extends Specification {

    void "serialization retains diagnostic details without live listener context"() {
        given:
        def original = new IllegalStateException('listener failure')
        def failure = new ChangeListenerException('Failed listener for datasource [inventory]', 'inventory',
            Mock(ExecutableMethod), new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), original)
        def bytes = new ByteArrayOutputStream()

        when:
        new ObjectOutputStream(bytes).withCloseable { it.writeObject(failure) }
        def restored = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).withCloseable {
            (ChangeListenerException) it.readObject()
        }

        then:
        restored.message == failure.message
        restored.dataSourceName == 'inventory'
        restored.cause.message == original.message
        restored.listenerMethod == null
        restored.event == null
    }

    void "the default handler logs the failure message and original cause"() {
        given:
        def context = ApplicationContext.run()
        def handler = context.getBean(ChangeListenerExceptionHandler)
        def logger = (Logger) LoggerFactory.getLogger(DefaultChangeListenerExceptionHandler)
        def previousLevel = logger.level
        logger.level = Level.ERROR
        def appender = new ListAppender<ILoggingEvent>()
        appender.start()
        logger.addAppender(appender)
        def original = new IllegalStateException('listener failure')
        def failure = new ChangeListenerException('Failed listener for datasource [inventory]', 'inventory',
            Mock(ExecutableMethod), new DefaultChangeEvent<>(ChangeOperation.INVALIDATE, null, null), original)

        when:
        handler.handle(failure)

        then:
        handler instanceof DefaultChangeListenerExceptionHandler
        appender.list.size() == 1
        appender.list[0].formattedMessage == failure.message
        appender.list[0].throwableProxy.throwable.is(original)

        cleanup:
        logger.detachAppender(appender)
        logger.level = previousLevel
        appender.stop()
        context.close()
    }

    void "an application handler replaces default logging without a Replaces annotation"() {
        given:
        def handler = Mock(ChangeListenerExceptionHandler)
        def context = ApplicationContext.builder().build()
        context.registerSingleton(ChangeListenerExceptionHandler, handler)

        when:
        context.start()

        then:
        context.getBean(ChangeListenerExceptionHandler).is(handler)
        context.getBeansOfType(ChangeListenerExceptionHandler).size() == 1
        !context.containsBean(DefaultChangeListenerExceptionHandler)

        cleanup:
        context.close()
    }
}
