package io.micronaut.data.jdbc.sqlite;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.annotation.DateUpdated;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.NamingStrategy;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.naming.NamingStrategies;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SQLiteDtoTest {

    @Test
    void testDtos() {
        try (ApplicationContext applicationContext = ApplicationContext.run(createProperties())) {
            ThingRepository thingRepository = applicationContext.getBean(ThingRepository.class);

            Thing thing = new Thing();
            thing.setName("Test");
            thing.setScore(123);
            thing.setSite("XYZ");

            Thing saved = thingRepository.save(thing);
            assertNotNull(saved.getId());

            List<ThingDto> things = thingRepository.findThingDTOsByThingId(saved.getId());

            assertEquals(1, things.size());
            ThingDto dto = things.get(0);
            assertEquals(saved.getId().intValue(), dto.getThingId());
            assertEquals("Test", dto.getThingName());
            assertNotNull(dto.getThingUpdatedAt());
            assertNotNull(dto.getThingUpdatedAtTime());
            assertFalse(dto.getThingUpdatedAtTime().toString().isEmpty());
        }
    }

    private static Map<String, Object> createProperties() {
        try {
            var databaseFile = Files.createTempFile("sqlitedto", ".sqlite").toFile();
            databaseFile.deleteOnExit();
            Map<String, Object> properties = new HashMap<>();
            properties.put("datasources.default.url", "jdbc:sqlite:" + databaseFile.getAbsolutePath());
            properties.put("datasources.default.schema-generate", "CREATE");
            properties.put("datasources.default.dialect", "SQLITE");
            properties.put("datasources.default.db-type", "sqlite");
            properties.put("datasources.default.username", "");
            properties.put("datasources.default.password", "");
            properties.put("datasources.default.packages", "io.micronaut.data.jdbc.sqlite,io.micronaut.data.tck.entities,io.micronaut.data.tck.jdbc.entities");
            properties.put("datasources.default.driverClassName", "org.sqlite.JDBC");
            return properties;
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to create SQLite test database", e);
        }
    }
}

@JdbcRepository(dialect = Dialect.SQLITE)
interface ThingRepository extends CrudRepository<Thing, Long> {

    @Query("""
      SELECT thing.id AS thingId,
             thing.name AS thingName,
             replace(strftime('%Y-%m-%dT%H:%M:%f', thing.updatedAt / 1000.0, 'unixepoch'), '.000', '') AS thingUpdatedAt,
             strftime('%H:%M:%f', thing.updatedAt / 1000.0, 'unixepoch') AS thingUpdatedAtTime
      FROM the_things thing
      WHERE thing.id = :id
    """)
    List<ThingDto> findThingDTOsByThingId(Long id);
}

@MappedEntity(value = "the_things", namingStrategy = NamingStrategies.Raw.class)
class Thing {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    private Integer score;
    private String site;

    @DateUpdated
    private LocalDateTime updatedAt;

    Long getId() {
        return id;
    }

    void setId(Long id) {
        this.id = id;
    }

    String getName() {
        return name;
    }

    void setName(String name) {
        this.name = name;
    }

    Integer getScore() {
        return score;
    }

    void setScore(Integer score) {
        this.score = score;
    }

    String getSite() {
        return site;
    }

    void setSite(String site) {
        this.site = site;
    }

    LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

@Introspected
@NamingStrategy(NamingStrategies.Raw.class)
class ThingDto {

    private Integer thingId;
    private String thingName;
    private LocalDateTime thingUpdatedAt;
    private LocalTime thingUpdatedAtTime;

    Integer getThingId() {
        return thingId;
    }

    void setThingId(Integer thingId) {
        this.thingId = thingId;
    }

    String getThingName() {
        return thingName;
    }

    void setThingName(String thingName) {
        this.thingName = thingName;
    }

    LocalDateTime getThingUpdatedAt() {
        return thingUpdatedAt;
    }

    void setThingUpdatedAt(LocalDateTime thingUpdatedAt) {
        this.thingUpdatedAt = thingUpdatedAt;
    }

    LocalTime getThingUpdatedAtTime() {
        return thingUpdatedAtTime;
    }

    void setThingUpdatedAtTime(LocalTime thingUpdatedAtTime) {
        this.thingUpdatedAtTime = thingUpdatedAtTime;
    }
}
