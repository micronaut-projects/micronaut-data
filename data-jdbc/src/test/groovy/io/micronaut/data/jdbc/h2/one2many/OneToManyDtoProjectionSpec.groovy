package io.micronaut.data.jdbc.h2.one2many

import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2DBProperties
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import jakarta.inject.Inject

@H2DBProperties
class OneToManyDtoProjectionSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    DtoContactRepository contactRepository = applicationContext.getBean(DtoContactRepository)

    @Shared
    @Inject
    DtoPhoneRepository phoneRepository = applicationContext.getBean(DtoPhoneRepository)

    void "DTO list property with same name as entity association keeps DTO element type"() {
        given:
        DtoContact contact = contactRepository.save(new DtoContact(firstName: "Sergio", lastName: "del Amo", phones: []))
        phoneRepository.save(new DtoPhone(phone: "111-111-1111", contact: contact))

        when:
        DtoContactComplete complete = contactRepository.findCompleteById(contact.id).orElse(null)

        then:
        complete != null
        complete.id == contact.id
        complete.firstName == "Sergio"
        complete.lastName == "del Amo"
        complete.phones == ["111-111-1111"]
    }
}

@JdbcRepository(dialect = Dialect.H2)
interface DtoContactRepository extends CrudRepository<DtoContact, Long> {

    @Query("""
        select c.id, c.first_name, c.last_name, group_concat(p.phone) as phones
        from dto_contact c
        left outer join dto_phone p on c.id = p.contact_id
        where c.id = :id
        group by c.id
        """)
    Optional<DtoContactComplete> findCompleteById(Long id)
}

@JdbcRepository(dialect = Dialect.H2)
interface DtoPhoneRepository extends CrudRepository<DtoPhone, Long> {
}

@MappedEntity("dto_contact")
class DtoContact {
    @Id
    @GeneratedValue
    Long id
    String firstName
    String lastName
    @Relation(value = Relation.Kind.ONE_TO_MANY, mappedBy = "contact")
    List<DtoPhone> phones
}

@MappedEntity("dto_phone")
class DtoPhone {
    @Id
    @GeneratedValue
    Long id
    String phone
    @Relation(value = Relation.Kind.MANY_TO_ONE)
    DtoContact contact
}

@Introspected
class DtoContactComplete {
    Long id
    String firstName
    String lastName
    List<String> phones
}
