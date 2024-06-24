package io.micronaut.data.jdbc.h2

import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.DateCreated
import io.micronaut.data.annotation.DateUpdated
import io.micronaut.data.annotation.Embeddable
import io.micronaut.data.annotation.EmbeddedId
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.MappedProperty
import io.micronaut.data.annotation.event.PrePersist
import io.micronaut.data.annotation.event.PreUpdate
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import jakarta.persistence.Embedded
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant

@MicronautTest
@H2DBProperties
class H2AutoPopulateSpec extends Specification {

    @Inject
    @Shared
    H2ExampleRepository exampleRepository

    void "test save and read entity"() {
        when:"an entity is saved"
        def exampleId = new ExampleId(null, "data1")
        def exampleEntity = new ExampleEntity(exampleId, "Test 1")
        exampleRepository.save(exampleEntity)

        then:
        exampleEntity.id.otherProp == "data1"
        exampleEntity.id.uuid
        exampleEntity.guid

        when:"An entity is retrieved by ID"
        exampleEntity = exampleRepository.findById(exampleEntity.id).orElse(null)

        then:"The entity is correct"
        exampleEntity.id.otherProp == "data1"
        exampleEntity.id.uuid
        exampleEntity.name == "Test 1"
        exampleEntity.audit
        exampleEntity.audit.dateCreated
        exampleEntity.audit.dateUpdated
        exampleEntity.audit.recordId
    }
}

@MappedEntity
class ExampleEntity {
    @MappedProperty("")
    @EmbeddedId
    private ExampleId id
    @MappedProperty("")
    @Embedded
    private Audit audit
    private String name
    @AutoPopulated
    UUID guid

    ExampleEntity(ExampleId id, String name) {
        this.id = id
        this.name = name
        this.audit = new Audit()
    }

    ExampleId getId() {
        return id
    }

    void setId(ExampleId id) {
        this.id = id
    }

    Audit getAudit() {
        return audit
    }

    void setAudit(Audit audit) {
        this.audit = audit
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    UUID getGuid() {
        return guid
    }

    void setGuid(UUID guid) {
        this.guid = guid
    }

    @PrePersist
    void prePersist() {
        if (id.uuid == null) {
            id.uuid = UUID.randomUUID()
        }
        if (audit == null) {
            audit = new Audit()
        }
        if (audit.recordId == null) {
            audit.recordId = UUID.randomUUID()
        }
        if (audit.dateCreated == null) {
            audit.dateCreated = Instant.now()
        }
        if (audit.dateUpdated == null) {
            audit.dateUpdated = Instant.now()
        }
    }

    @PreUpdate
    void preUpdate() {
        audit.dateUpdated = Instant.now()
    }
}

@Embeddable
class ExampleId {
    @AutoPopulated
    private UUID uuid
    private String otherProp

    ExampleId(UUID uuid, String otherProp) {
        this.uuid = uuid
        this.otherProp = otherProp
    }

    UUID getUuid() {
        return uuid
    }

    void setUuid(UUID uuid) {
        this.uuid = uuid
    }

    String getOtherProp() {
        return otherProp
    }

    void setOtherProp(String otherProp) {
        this.otherProp = otherProp
    }
}

@Embeddable
class Audit {
    @DateCreated
    private Instant dateCreated
    @DateUpdated
    private Instant dateUpdated
    @AutoPopulated
    private UUID recordId

    Instant getDateCreated() {
        return dateCreated
    }

    void setDateCreated(Instant dateCreated) {
        this.dateCreated = dateCreated
    }

    Instant getDateUpdated() {
        return dateUpdated
    }

    void setDateUpdated(Instant dateUpdated) {
        this.dateUpdated = dateUpdated
    }

    UUID getRecordId() {
        return recordId
    }

    void setRecordId(UUID recordId) {
        this.recordId = recordId
    }
}

@JdbcRepository(dialect = Dialect.H2)
public interface H2ExampleRepository extends CrudRepository<ExampleEntity, ExampleId> {
}
