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
import io.micronaut.data.document.tck.repositories.AuthorRepository
import io.micronaut.data.document.tck.repositories.BasicTypesRepository
import io.micronaut.data.document.tck.repositories.BookRepository
import io.micronaut.data.document.tck.repositories.DocumentRepository
import io.micronaut.data.document.tck.repositories.DomainEventsRepository
import io.micronaut.data.document.tck.repositories.PersonRepository
import io.micronaut.data.document.tck.repositories.SaleRepository
import io.micronaut.data.document.tck.repositories.StudentRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class NitriteDocumentRepositorySpec extends AbstractDocumentRepositorySpec {

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

    Map<String, String> getProperties() {
        return Collections.singletonMap("nitrite.db-path", "memory")
    }
}
