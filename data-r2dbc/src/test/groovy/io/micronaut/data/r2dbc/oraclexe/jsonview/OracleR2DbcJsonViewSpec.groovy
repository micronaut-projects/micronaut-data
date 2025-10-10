package io.micronaut.data.r2dbc.oraclexe.jsonview

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.tck.entities.Contact
import io.micronaut.data.tck.entities.ContactView
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
        def contact = new Contact()
        contact.name = "Contact1"
        contact.age = 25
        contact.startDateTime = LocalDateTime.now().minusMonths(10)
        contactRepository.save(contact)
        def optContactView = contactViewRepository.findById(contact.id)
        then:
        optContactView.present

        when:
        def contactView = optContactView.get()
        contactView.name = contactView.name + "-Updated"
        contactViewRepository.update(contactView)
        def optContact = contactRepository.findById(contact.id)
        then:
        optContact.present
        optContact.get().name == contactView.name

        when:
        contactViewRepository.deleteById(contact.id)
        then:
        !contactRepository.findById(contact.id).present
        !contactViewRepository.findById(contact.id).present

        when:
        contactView = new ContactView()
        contactView.name = "Contact2"
        contactView.age = 30
        contactViewRepository.save(contactView)
        optContact = contactRepository.findById(contactView.id)
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
        contactView = contactViewRepository.findById(contactView.id).get()
        contactView.metadata = new io.micronaut.data.tck.entities.Metadata(UUID.randomUUID().toString(), contactView.metadata.asof())
        contactViewRepository.update(contactView)
        then:
        def e = thrown(OptimisticLockException)
        e.message.startsWith("ETAG did not match when updating record")

        when:"Optimistic lock exception with invalid ETAG in batch update"
        contactViewRepository.updateAll(List.of(contactView))
        then:
        thrown(OptimisticLockException)

        when:"Save multiple at once"
        ContactView contactView1 = new ContactView()
        contactView1.name = "ContactNew1"
        contactView1.age = 59
        ContactView contactView2 = new ContactView()
        contactView2.name = "ContactNew2"
        contactView2.age = 60
        def savedEntities = contactViewRepository.saveAll(Arrays.asList(contactView1, contactView2))
        then:
        savedEntities.size() == 2
        savedEntities[0].id
        savedEntities[1].id
        when:
        contactViewRepository.deleteAll()
        then:
        !contactRepository.findById(contactView.id).present
        !contactViewRepository.findById(contactView.id).present
    }
}
