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

import io.micronaut.context.BeanContext
import io.micronaut.data.document.tck.AbstractDocumentRepositorySpec
import io.micronaut.data.document.tck.repositories.*
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class NitriteDocumentRepositorySpec extends AbstractDocumentRepositorySpec {

    @Inject
    NitriteBasicTypesRepository basicTypeRepository
    @Inject
    NitriteDomainEventsRepository eventsRepository
    @Inject
    NitriteSaleRepository saleRepository
    @Inject
    NitriteStudentRepository studentRepository
    @Inject
    NitriteDocumentRepository documentRepository
    @Inject
    NitriteBookRepository bookRepository
    @Inject
    NitriteAuthorRepository authorRepository
    @Inject
    NitritePersonRepository personRepository
    @Inject
    BeanContext beanContext

    @Override
    PersonRepository getPersonRepository() {
        return personRepository
    }

    @Override
    BasicTypesRepository getBasicTypeRepository() {
        return basicTypeRepository
    }

    @Override
    DomainEventsRepository getEventsRepository() {
        return eventsRepository
    }

    @Override
    SaleRepository getSaleRepository() {
        return saleRepository
    }

    @Override
    StudentRepository getStudentRepository() {
        return studentRepository
    }

    @Override
    DocumentRepository getDocumentRepository() {
        return documentRepository
    }

    def setup() {
        authorRepository.deleteAll()
        bookRepository.deleteAll()
        basicTypeRepository.deleteAll()
        eventsRepository.deleteAll()
        saleRepository.deleteAll()
        studentRepository.deleteAll()
        documentRepository.deleteAll()
    }
}
