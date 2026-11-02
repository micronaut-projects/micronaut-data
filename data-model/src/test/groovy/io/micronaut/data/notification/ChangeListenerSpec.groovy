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
package io.micronaut.data.notification

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.annotation.Executable
import io.micronaut.data.annotation.ChangeListener

class ChangeListenerSpec extends AbstractTypeElementSpec {

    void "compiles a composed listener and its event contract without JDBC"() {
        given:
        assert getClass().classLoader.getResource('io/micronaut/data/jdbc/annotation/OracleChangeNotification.class') == null

        when:
        def definition = buildBeanDefinition('test.BookListener', '''
package test;

import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.intercept.annotation.ChangeListenerProvider;
import io.micronaut.data.notification.ChangeEvent;
import io.micronaut.data.notification.ChangeEventMetadata;
import io.micronaut.data.notification.ChangeOperation;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.Optional;

@ChangeListener("archive")
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface BookChanges {
}

@ChangeListenerProvider
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@interface StreamChanges {
}

@MappedEntity
record Book(@Id Long id) {
}

record StreamMetadata(String position) implements ChangeEventMetadata {
}

record BookEvent(ChangeOperation operation, Optional<Book> entity) implements ChangeEvent<Book> {
    @Override
    public <M extends ChangeEventMetadata> Optional<M> metadata(Class<M> type) {
        return Optional.empty();
    }
}

@Singleton
class BookListener {
    @BookChanges
    @StreamChanges
    public void changed(ChangeEvent<Book> event) {
    }
}
''')
        def method = definition.getRequiredMethod('changed', ChangeEvent)

        then:
        method.stringValue(ChangeListener, 'dataSource').orElseThrow() == 'archive'
        method.annotationMetadata.getAnnotationTypesByStereotype(Executable).contains(ChangeListener)
        method.arguments[0].firstTypeVariable.orElseThrow().type.simpleName == 'Book'
    }
}
