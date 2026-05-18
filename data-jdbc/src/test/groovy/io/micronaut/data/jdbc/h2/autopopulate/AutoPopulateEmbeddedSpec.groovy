package io.micronaut.data.jdbc.h2.autopopulate

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Relation
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.jdbc.h2.H2TestPropertyProvider
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.GenericRepository
import io.micronaut.serde.annotation.Serdeable
import jakarta.inject.Inject
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.time.LocalDateTime

class AutoPopulateEmbeddedSpec extends Specification implements H2TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getProperties())

    @Shared
    @Inject
    MyAuditableEntityRepository myAuditableEntityRepository = applicationContext.getBean(MyAuditableEntityRepository)

    def "test embeddable fields auto populated"() {
        when:
        def saved = myAuditableEntityRepository.insert(new MyAuditableEntity(id: "id1", firstName: "Peter"))
        def loaded = myAuditableEntityRepository.findById(saved.id).orElse(null)
        then:
        loaded
        loaded.id == saved.id
        loaded.firstName == "Peter"
        loaded.createdAt
        loaded.updatedAt
        loaded.guid
        loaded.auditFields
        loaded.auditFields.innerCreatedAt
        loaded.auditFields.innerUpdatedAt
        loaded.auditFields.innerGuid
        loaded.auditFields.innerFields
        loaded.auditFields.innerFields.subInnerCreatedAt
        loaded.auditFields.innerFields.subInnerGuid
        // Currently embedded entity without default constructor cannot be created
        // in order to populate fields in timestamp and uuid entity event listeners
        !loaded.otherAuditFields
    }

}

@Embeddable
class AuditFields {

    @DateCreated
    LocalDateTime innerCreatedAt

    @DateUpdated
    LocalDateTime innerUpdatedAt

    @AutoPopulated
    UUID innerGuid

    @Relation(value = Relation.Kind.EMBEDDED)
    InnerFields innerFields
}

@Embeddable
class OtherAuditFields {

    @DateCreated
    LocalDateTime otherInnerCreatedAt

    @DateUpdated
    LocalDateTime otherInnerUpdatedAt

    @AutoPopulated
    UUID otherInnerGuid

    OtherAuditFields(LocalDateTime otherInnerCreatedAt, LocalDateTime otherInnerUpdatedAt, UUID otherInnerGuid) {
        this.otherInnerCreatedAt = otherInnerCreatedAt
        this.otherInnerUpdatedAt = otherInnerUpdatedAt
        this.otherInnerGuid = otherInnerGuid
    }
}

@Serdeable
@MappedEntity(value = "my_auditable_entity")
class MyAuditableEntity {
    @Id
    String id

    String firstName

    @DateCreated
    LocalDateTime createdAt

    @DateUpdated
    LocalDateTime updatedAt

    @AutoPopulated
    UUID guid

    @Relation(value = Relation.Kind.EMBEDDED)
    AuditFields auditFields

    @Relation(value = Relation.Kind.EMBEDDED)
    OtherAuditFields otherAuditFields
}

@JdbcRepository(dialect = Dialect.H2)
interface MyAuditableEntityRepository extends GenericRepository<MyAuditableEntity, String> {

    MyAuditableEntity insert(MyAuditableEntity entity)

    Optional<MyAuditableEntity> findById(String id)
}
