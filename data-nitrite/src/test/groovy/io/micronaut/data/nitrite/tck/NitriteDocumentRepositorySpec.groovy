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
