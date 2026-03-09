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
package io.micronaut.data.nitrite.tck

import io.micronaut.data.document.tck.AbstractDocumentRepositorySpec
import io.micronaut.data.document.tck.repositories.*
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import org.slf4j.LoggerFactory

@MicronautTest(transactional = false)
class NitriteDocumentRepositorySpec extends AbstractDocumentRepositorySpec {

    def setupSpec() {
        Logger queryLogger = (Logger) LoggerFactory.getLogger("io.micronaut.data.query")
        queryLogger.setLevel(Level.INFO)
    }

    @Inject NitriteBasicTypesRepository basicTypesRepository
    @Inject NitritePersonRepository personRepository
    @Inject NitriteBookRepository bookRepository
    @Inject NitriteAuthorRepository authorRepository
    @Inject NitriteStudentRepository studentRepository
    @Inject NitriteSaleRepository saleRepository
    @Inject NitriteDomainEventsRepository eventsRepository
    @Inject NitriteDocumentRepository documentRepository

    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return basicTypesRepository
    }

    @Override
    PersonRepository getPersonRepository() {
        return personRepository
    }

    @Override
    BookRepository getBookRepository() {
        return bookRepository
    }

    @Override
    AuthorRepository getAuthorRepository() {
        return authorRepository
    }

    @Override
    StudentRepository getStudentRepository() {
        return studentRepository
    }

    @Override
    SaleRepository getSaleRepository() {
        return saleRepository
    }

    @Override
    DomainEventsRepository getEventsRepository() {
        return eventsRepository
    }

    @Override
    DocumentRepository getDocumentRepository() {
        return documentRepository
    }

    def setup() {
        basicTypeRepository.deleteAll()
        eventsRepository.deleteAll()
        saleRepository.deleteAll()
        studentRepository.deleteAll()
        documentRepository.deleteAll()
    }
}
