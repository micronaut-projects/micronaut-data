package io.micronaut.data.r2dbc.oraclexe.jsonview

import io.micronaut.context.ApplicationContext
import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.tck.entities.Contact
import io.micronaut.data.tck.entities.ContactView
import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.OracleContainer
import org.testcontainers.utility.DockerImageName
import spock.lang.AutoCleanup
import spock.lang.IgnoreIf
import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

@IgnoreIf({ env["GITHUB_WORKFLOW"] })
class OracleR2DbcJsonViewSpec extends Specification implements TestPropertyProvider {

    @AutoCleanup("stop")
    @Shared
    OracleContainer container = createContainer()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    OracleXEContactRepository getContactRepository() {
        return context.getBean(OracleXEContactRepository)
    }

    ContactViewRepository getContactViewRepository() {
        return context.getBean(ContactViewRepository)
    }

    @Override
    Map<String, String> getProperties() {
        if (container == null) {
            container = createContainer()
        }
        container.start()
        def prefix = 'r2dbc.datasources.default'
        def dbType = 'oracle'
        return [
                (prefix + '.url')               : "r2dbc:${dbType}://${container.getHost()}:${container.getFirstMappedPort()}/test",
                (prefix + '.username')          : container.getUsername(),
                (prefix + '.password')          : container.getPassword(),
                // Cannot create JSON view during schema creation, works via init script
                (prefix + '.schema-generate')   : 'CREATE',
                (prefix + '.dialect')           : Dialect.ORACLE,
                (prefix + '.connectTimeout')    : Duration.ofMinutes(1).toString(),
                (prefix + '.statementTimeout')  : Duration.ofMinutes(1).toString(),
                (prefix + '.lockTimeout')       : Duration.ofMinutes(1).toString()
        ] as Map<String, String>
    }

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

    static OracleContainer createContainer() {
        return new OracleContainer(DockerImageName.parse("gvenzl/oracle-free:latest-faststart").asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                .withDatabaseName("test").withInitScript("./oracle-json-view-init.sql")
    }
}
