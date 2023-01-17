package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate6.entities.Children
import io.micronaut.data.hibernate6.entities.ChildrenId
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.hibernate.ObjectNotFoundException
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

import jakarta.inject.Inject
import javax.validation.ConstraintViolationException

@MicronautTest(rollback = false, packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
@Stepwise
class LoadSpec extends Specification {

    @Inject
    @Shared
    ChildrenRepository childrenRepository

    @Shared
    static final ChildrenId existingId = new ChildrenId(1, 1)

    def setupSpec() {
        Children existing = new Children(existingId)
        existing.setName("Mitch")
        childrenRepository.save(existing)
    }

    def "can retrieve uninitialized proxy from repository for existing entity"() {
        when: "Loading a proxy for the existing entity"
        Children proxy = childrenRepository.load(existingId)

        then: "The proxy fields are loaded"
        proxy.getName() == "Mitch"
    }

    def "retrieving a proxy for a non-existing entity does not throw an exception, but accessing fields does"() {
        given: "A non-existent ID"
        ChildrenId id = new ChildrenId(99, 99)

        when: "Loading a proxy for the non-existent ID"
        Children proxy = childrenRepository.load(id)

        then:
        noExceptionThrown()

        when: "When accessing a field"
        proxy.getName()

        then:
        ObjectNotFoundException ex = thrown()

        ex.getMessage().contains("No row with the given identifier exists")
    }

    def "retrieving a proxy with null id throws an exception"() {
        when:
        childrenRepository.load(null)

        then:
        thrown(ConstraintViolationException)
    }

}
