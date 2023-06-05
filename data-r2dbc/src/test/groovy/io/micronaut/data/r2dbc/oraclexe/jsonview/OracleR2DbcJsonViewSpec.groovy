package io.micronaut.data.r2dbc.oraclexe.jsonview

import io.micronaut.data.exceptions.OptimisticLockException

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@MicronautTest(environments = ["oracle-jsonview"], transactional = false)
class OracleR2DbcJsonViewSpec extends Specification {

    @Inject
    OracleXEContactRepository contactRepository

    @Inject
    ContactViewRepository contactViewRepository

    def "test CRUD"() {
        when:
        def id = 10L
        def contact = new Contact()
        contact.id = id
        contact.name = "Contact" + id
        contact.age = 25
        contact.startDateTime = LocalDateTime.now().minusMonths(10)
        contactRepository.save(contact)
        def optContactView = contactViewRepository.findById(id)
        then:
        optContactView.present

        when:
        def contactView = optContactView.get()
        contactView.name = contactView.name + "-Updated"
        contactViewRepository.update(contactView)
        def optContact = contactRepository.findById(id)
        then:
        optContact.present
        optContact.get().name == contactView.name

        when:
        contactViewRepository.deleteById(id)
        then:
        !contactRepository.findById(id).present
        !contactViewRepository.findById(id).present

        when:
        id = 20L
        contactView = new ContactView()
        contactView.id = id
        contactView.name = "Contact" + id
        contactView.age = 30
        contactViewRepository.save(contactView)
        optContact = contactRepository.findById(id)
        then:
        optContact.present
        contactView.name == optContact.get().name

        when:"Find by name"
        optContactView = contactViewRepository.findByName(contactView.name)
        then:"Returns result"
        optContactView.present

        when:"Update using auto generated query"
        def startDateTime = LocalDateTime.now()
        contactViewRepository.updateAgeAndStartDateTime(contactView.id, 31, startDateTime)
        optContactView = contactViewRepository.findById(contactView.id)
        def updatedContactView = optContactView.get()
        then:
        updatedContactView
        updatedContactView.id == contactView.id
        updatedContactView.age == 31
        updatedContactView.name == contactView.name
        // Compare milliseconds
        updatedContactView.startDateTime.truncatedTo(ChronoUnit.MILLIS) == startDateTime.truncatedTo(ChronoUnit.MILLIS)

        when:"Test optimistic locking"
        contactView = contactViewRepository.findById(id).get()
        contactView.metadata.etag = UUID.randomUUID().toString()
        contactViewRepository.update(contactView)
        then:
        def e = thrown(OptimisticLockException)
        e.message.startsWith("ETAG did not match when updating record")

        when:
        contactViewRepository.deleteAll()
        then:
        !contactRepository.findById(id).present
        !contactViewRepository.findById(id).present
    }
}
