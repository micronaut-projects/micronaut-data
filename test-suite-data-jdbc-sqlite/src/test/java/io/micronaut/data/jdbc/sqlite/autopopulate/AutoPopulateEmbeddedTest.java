package io.micronaut.data.jdbc.sqlite.autopopulate;

import io.micronaut.context.ApplicationContext;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.DateCreated;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.GenericRepository;
import io.micronaut.serde.annotation.Serdeable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class AutoPopulateEmbeddedTest {

    @Test
    void testEmbeddableFieldsAutoPopulated() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            MyAuditableEntityRepository repository = applicationContext.getBean(MyAuditableEntityRepository.class);

            MyAuditableEntity entity = new MyAuditableEntity();
            entity.setId("id1");
            entity.setFirstName("Peter");

            MyAuditableEntity saved = repository.save(entity);
            MyAuditableEntity loaded = repository.findById(saved.getId()).orElse(null);

            assertNotNull(loaded);
            assertEquals(saved.getId(), loaded.getId());
            assertEquals("Peter", loaded.getFirstName());
            assertNotNull(loaded.getCreatedAt());
            assertNotNull(loaded.getUpdatedAt());
            assertNotNull(loaded.getGuid());
            assertNotNull(loaded.getAuditFields());
            assertNotNull(loaded.getAuditFields().getInnerCreatedAt());
            assertNotNull(loaded.getAuditFields().getInnerUpdatedAt());
            assertNotNull(loaded.getAuditFields().getInnerGuid());
            assertNotNull(loaded.getAuditFields().getInnerFields());
            assertNotNull(loaded.getAuditFields().getInnerFields().subInnerCreatedAt());
            assertNotNull(loaded.getAuditFields().getInnerFields().subInnerGuid());
            assertNull(loaded.getOtherAuditFields());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("autopopulateembedded", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "ANSI");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite.autopopulate");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@Embeddable
class AuditFields {

    @DateCreated
    private LocalDateTime innerCreatedAt;

    @DateUpdated
    private LocalDateTime innerUpdatedAt;

    @AutoPopulated
    private UUID innerGuid;

    @Relation(Relation.Kind.EMBEDDED)
    private InnerFields innerFields;

    LocalDateTime getInnerCreatedAt() {
        return innerCreatedAt;
    }

    void setInnerCreatedAt(LocalDateTime innerCreatedAt) {
        this.innerCreatedAt = innerCreatedAt;
    }

    LocalDateTime getInnerUpdatedAt() {
        return innerUpdatedAt;
    }

    void setInnerUpdatedAt(LocalDateTime innerUpdatedAt) {
        this.innerUpdatedAt = innerUpdatedAt;
    }

    UUID getInnerGuid() {
        return innerGuid;
    }

    void setInnerGuid(UUID innerGuid) {
        this.innerGuid = innerGuid;
    }

    InnerFields getInnerFields() {
        return innerFields;
    }

    void setInnerFields(InnerFields innerFields) {
        this.innerFields = innerFields;
    }
}

@Embeddable
class OtherAuditFields {

    @DateCreated
    private LocalDateTime otherInnerCreatedAt;

    @DateUpdated
    private LocalDateTime otherInnerUpdatedAt;

    @AutoPopulated
    private UUID otherInnerGuid;

    OtherAuditFields(LocalDateTime otherInnerCreatedAt, LocalDateTime otherInnerUpdatedAt, UUID otherInnerGuid) {
        this.otherInnerCreatedAt = otherInnerCreatedAt;
        this.otherInnerUpdatedAt = otherInnerUpdatedAt;
        this.otherInnerGuid = otherInnerGuid;
    }

    LocalDateTime getOtherInnerCreatedAt() {
        return otherInnerCreatedAt;
    }

    LocalDateTime getOtherInnerUpdatedAt() {
        return otherInnerUpdatedAt;
    }

    UUID getOtherInnerGuid() {
        return otherInnerGuid;
    }
}

@Serdeable
@MappedEntity("my_auditable_entity")
class MyAuditableEntity {
    @Id
    private String id;
    private String firstName;

    @DateCreated
    private LocalDateTime createdAt;

    @DateUpdated
    private LocalDateTime updatedAt;

    @AutoPopulated
    private UUID guid;

    @Relation(Relation.Kind.EMBEDDED)
    private AuditFields auditFields;

    @Relation(Relation.Kind.EMBEDDED)
    private OtherAuditFields otherAuditFields;

    String getId() {
        return id;
    }

    void setId(String id) {
        this.id = id;
    }

    String getFirstName() {
        return firstName;
    }

    void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    LocalDateTime getCreatedAt() {
        return createdAt;
    }

    void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    UUID getGuid() {
        return guid;
    }

    void setGuid(UUID guid) {
        this.guid = guid;
    }

    AuditFields getAuditFields() {
        return auditFields;
    }

    void setAuditFields(AuditFields auditFields) {
        this.auditFields = auditFields;
    }

    OtherAuditFields getOtherAuditFields() {
        return otherAuditFields;
    }

    void setOtherAuditFields(OtherAuditFields otherAuditFields) {
        this.otherAuditFields = otherAuditFields;
    }
}

@JdbcRepository(dialect = Dialect.ANSI)
interface MyAuditableEntityRepository extends GenericRepository<MyAuditableEntity, String> {

    MyAuditableEntity save(MyAuditableEntity entity);

    Optional<MyAuditableEntity> findById(String id);
}
