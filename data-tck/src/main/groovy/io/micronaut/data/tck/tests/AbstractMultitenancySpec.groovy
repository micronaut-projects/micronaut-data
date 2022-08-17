/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.tck.entities.Book
import io.micronaut.data.tck.repositories.BookRepository
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import spock.lang.Specification

import java.util.stream.Collectors

abstract class AbstractMultitenancySpec extends Specification {

    abstract Map<String, String> getExtraProperties()

    Map<String, String> getDataSourceProperties() {
        return [:]
    }

    Map<String, String> getDataSourceProperties(String dataSourceName) {
        return getDataSourceProperties().collectEntries { [sourcePrefix() + '.' + dataSourceName + '.' + it.key, it.value] }
    }

    boolean supportsSchemaMultitenancy() {
        return true
    }

    abstract String sourcePrefix();

    def "test schema multitenancy"() {
        if (!supportsSchemaMultitenancy()) {
            return
        }
        setup:
            EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, properties + getExtraProperties() + getDataSourceProperties('default') + [
                    'spec.name'                                               : 'multitenancy',
                    'micronaut.data.multi-tenancy.mode'                       : 'SCHEMA',
                    'micronaut.multitenancy.tenantresolver.httpheader.enabled': 'true',
                    (sourcePrefix() + '.default.schema-generate-names[0]')    : 'foo',
                    (sourcePrefix() + '.default.schema-generate-names[1]')    : 'bar'
            ], Environment.TEST)
            def context = embeddedServer.applicationContext
            FooBookClient fooBookClient = context.getBean(FooBookClient)
            BarBookClient barBookClient = context.getBean(BarBookClient)
            fooBookClient.deleteAll()
            barBookClient.deleteAll()
        when: 'A book created in FOO tenant'
            BookDto book = fooBookClient.save("The Stand", 1000)
        then: 'The book exists in FOO tenant'
            book.id
        when:
            book = fooBookClient.findOne(book.getId()).orElse(null)
        then:
            book
            book.getTitle() == "The Stand"
        and: 'There is one book'
            fooBookClient.findAll().size() == 1
        and: 'There is no books in BAR tenant'
            barBookClient.findAll().size() == 0
        and: 'JDBC client validates previous steps'
            getSchemaBooksCount(context, "foo") == 1
            getSchemaBooksCount(context, "bar") == 0

        when: 'Delete all BARs'
            barBookClient.deleteAll()
        then: "FOOs aren't deletes"
            fooBookClient.findAll().size() == 1

        when: 'Delete all FOOs'
            fooBookClient.deleteAll()
        then: "All FOOs are deleted"
            fooBookClient.findAll().size() == 0
        cleanup:
            embeddedServer?.stop()
    }

    def "test datasource multitenancy"() {
        setup:
            Map<String, String> dataSourceProperties = getDataSourceProperties('foo') + getDataSourceProperties('bar')
            EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, properties + getExtraProperties() + dataSourceProperties + [
                    'spec.name'                                               : 'multitenancy',
                    'micronaut.data.multi-tenancy.mode'                       : 'DATASOURCE',
                    'micronaut.multitenancy.tenantresolver.httpheader.enabled': 'true'
            ], Environment.TEST)
            def context = embeddedServer.applicationContext
            FooBookClient fooBookClient = context.getBean(FooBookClient)
            BarBookClient barBookClient = context.getBean(BarBookClient)
            fooBookClient.deleteAll()
            barBookClient.deleteAll()
        when: 'A book created in FOO tenant'
            BookDto book = fooBookClient.save("The Stand", 1000)
        then: 'The book exists in FOO tenant'
            book.id
        when:
            book = fooBookClient.findOne(book.getId()).orElse(null)
        then:
            book
            book.getTitle() == "The Stand"
        and: 'There is one book'
            fooBookClient.findAll().size() == 1
        and: 'There is no books in BAR tenant'
            barBookClient.findAll().size() == 0
        and: 'JDBC client validates previous steps'
            getDataSourceBooksCount(context, "foo") == 1
            getDataSourceBooksCount(context, "bar") == 0

        when: 'Delete all BARs'
            barBookClient.deleteAll()
        then: "FOOs aren't deletes"
            fooBookClient.findAll().size() == 1

        when: 'Delete all FOOs'
            fooBookClient.deleteAll()
        then: "All FOOs are deleted"
            fooBookClient.findAll().size() == 0
        cleanup:
            embeddedServer?.stop()
    }

    protected abstract long getDataSourceBooksCount(BeanContext beanContext, String ds);

    protected abstract long getSchemaBooksCount(BeanContext beanContext, String schemaName);

}

@Requires(property = "spec.name", value = "multitenancy")
@ExecuteOn(TaskExecutors.IO)
@Controller("/books")
class BookController {

    private final BookRepository bookRepository

    BookController(ApplicationContext beanContext) {
        this.bookRepository = beanContext.getBean(Class.forName(beanContext.getProperty("bookRepositoryClass", String).get())) as BookRepository
    }

    @Post
    BookDto save(String title, int pages) {
        def newBook = new Book()
        newBook.title = title
        newBook.totalPages = pages
        def book = bookRepository.save(newBook)
        return new BookDto(id: book.id, title: book.title)
    }

    @Get("/{id}")
    Optional<BookDto> findOne(Long id) {
        return bookRepository.findById(id).map(BookDto::new)
    }

    @Get
    List<BookDto> findAll() {
        return bookRepository.findAll().stream().map(BookDto::new).collect(Collectors.toList())
    }

    @Delete
    void deleteAll() {
        bookRepository.deleteAll()
    }

}


@Introspected
class BookDto {
    String id
    String title

    BookDto() {
    }

    BookDto(Book book) {
        id = book.id.toString()
        title = book.title
    }

}

@Requires(property = "spec.name", value = "multitenancy")
@Client("/books")
interface BookClient {

    @Post
    BookDto save(String title, int pages);

    @Get("/{id}")
    Optional<BookDto> findOne(String id);

    @Get
    List<BookDto> findAll();

    @Delete
    void deleteAll();
}


@Requires(property = "spec.name", value = "multitenancy")
@Header(name = "tenantId", value = "foo")
@Client("/books")
interface FooBookClient extends BookClient {
}

@Requires(property = "spec.name", value = "multitenancy")
@Header(name = "tenantId", value = "bar")
@Client("/books")
interface BarBookClient extends BookClient {
}
