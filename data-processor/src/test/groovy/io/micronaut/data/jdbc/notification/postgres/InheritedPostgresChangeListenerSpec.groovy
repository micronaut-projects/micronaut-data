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
package io.micronaut.data.jdbc.notification.postgres

import io.micronaut.annotation.processing.BeanDefinitionInjectProcessor
import io.micronaut.annotation.processing.TypeElementVisitorProcessor
import io.micronaut.core.convert.ConversionService
import io.micronaut.context.ApplicationContext
import io.micronaut.data.runtime.operations.internal.query.BindableParametersStoredQuery
import io.micronaut.core.type.Argument
import io.micronaut.data.jdbc.annotation.PostgresChangeNotification
import io.micronaut.data.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeListenerMethod
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.PreparedQuery
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.inject.BeanDefinition
import spock.lang.Specification
import spock.lang.TempDir

import javax.tools.ToolProvider
import java.nio.file.Files
import java.nio.file.Path

class InheritedPostgresChangeListenerSpec extends Specification {

    @TempDir
    Path compilationDirectory

    void "reloads a separately compiled listener (Data visitor=#runDataVisitor, generic base=#genericBase)"() {
        given:
        compile('library.Book', '''
package library;

import io.micronaut.data.annotation.*;

@MappedEntity(value = "books", schema = "sales")
public record Book(@Id Long id, @MappedProperty("book_title") String title) {
}
''', true)
        // Model a library that did not run the Data visitor on its listener source.
        compile('library.BaseBookListener', """
package library;

import io.micronaut.data.annotation.ChangeListener;
import io.micronaut.data.jdbc.annotation.*;
import io.micronaut.data.notification.ChangeEvent;

public abstract class BaseBookListener${genericBase ? '<E>' : ''} {
    @ChangeListener
    @PostgresChangeNotification(channel = "changes")
    public void changed(ChangeEvent<${genericBase ? 'E' : 'Book'}> event) {
    }
}
""", false)
        compile('application.BookListener', """
package application;

@jakarta.inject.Singleton
public class BookListener extends library.BaseBookListener${genericBase ? '<library.Book>' : ''} {
}
""", true, runDataVisitor)
        def context = ApplicationContext.run()
        def classLoader = new URLClassLoader([compilationDirectory.toUri().toURL()] as URL[], getClass().classLoader)
        Class entityType = classLoader.loadClass('library.Book')
        def beanDefinition = (BeanDefinition) classLoader.loadClass('application.$BookListener$Definition')
            .getDeclaredConstructor().newInstance()
        def method = beanDefinition.getRequiredMethod('changed', ChangeEvent)
        def operations = Mock(JdbcRepositoryOperations)
        operations.getEntity(entityType) >> new RuntimePersistentEntity<>(entityType)
        operations.conversionService >> ConversionService.SHARED
        PreparedQuery preparedQuery
        def binder = Mock(BindableParametersStoredQuery.Binder)
        operations.findOne(_ as PreparedQuery) >> { PreparedQuery query ->
            preparedQuery = query
            null
        }

        expect:
        method.hasAnnotation(PostgresChangeNotification)
        method.hasAnnotation(PostgresChangeListenerQuery) == runDataVisitor
        method.arguments[0].firstTypeVariable.orElseThrow().type == entityType

        when:
        def listener = new ChangeListenerMethod(beanDefinition, method, Argument.of(entityType))
        def definition = new PostgresChangeListenerDefinitionFactory(operations, context.environment).create(listener)
        definition.loader().reload([id: '42'])
        preparedQuery.bindParameters(binder)

        then:
        definition.schema() == 'sales'
        definition.table() == 'books'
        definition.loader().columns() == ['id'] as Set
        preparedQuery.query == 'SELECT book_."id",book_."book_title" FROM "sales"."books" book_ WHERE (book_."id" = ?)'
        1 * binder.bindOne(_, { it instanceof Long && it == 42L })

        cleanup:
        classLoader?.close()
        context?.close()

        where:
        [runDataVisitor, genericBase] << [[true, false], [false, false], [true, true], [false, true]]
    }

    private void compile(String className, String source, boolean processAnnotations, boolean runDataVisitor = true) {
        Path sourceFile = compilationDirectory.resolve(className.replace('.', '/') + '.java')
        Files.createDirectories(sourceFile.parent)
        Files.writeString(sourceFile, source)
        def compiler = ToolProvider.systemJavaCompiler
        def diagnostics = new StringWriter()
        try (def fileManager = compiler.getStandardFileManager(null, null, null)) {
            def options = [
                '-d', compilationDirectory.toString(),
                '-classpath', System.getProperty('java.class.path') + File.pathSeparator + compilationDirectory,
                '-sourcepath', '',
                processAnnotations ? '-proc:full' : '-proc:none'
            ]
            def task = compiler.getTask(diagnostics, fileManager, null, options, null,
                fileManager.getJavaFileObjects(sourceFile.toFile()))
            if (processAnnotations) {
                def processors = [new BeanDefinitionInjectProcessor()]
                if (runDataVisitor) {
                    processors.add(0, new TypeElementVisitorProcessor())
                }
                task.setProcessors(processors)
            }
            if (!task.call()) {
                throw new AssertionError(diagnostics.toString())
            }
        }
    }
}

