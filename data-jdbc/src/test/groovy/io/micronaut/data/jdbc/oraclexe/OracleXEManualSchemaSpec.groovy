package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.JsonRepresentation
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.QueryResult
import io.micronaut.data.annotation.TypeDef
import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.DataType
import io.micronaut.data.model.JsonType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate

import io.micronaut.data.tck.entities.SampleData
import io.micronaut.data.tck.repositories.PatientRepository
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull

import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

class OracleXEManualSchemaSpec extends AbstractManualSchemaSpec implements OracleTestPropertyProvider {

    @Override
    SchemaGenerate schemaGenerate() {
        SchemaGenerate.NONE
    }

    @Memoized
    @Override
    PatientRepository getPatientRepository() {
        return context.getBean(OracleXEPatientRepository)
    }

    @Memoized
    OracleXEJsonEntityRepository getJsonEntityRepository() {
        return context.getBean(OracleXEJsonEntityRepository)
    }

    @Memoized
    OracleXEJsonDataRepository getJsonDataRepository() {
        return context.getBean(OracleXEJsonDataRepository)
    }

    @Override
    List<String> createStatements() {
        return Arrays.asList(""" CREATE SEQUENCE "PATIENT_SEQ" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE """,
            """ CREATE TABLE "PATIENT" ("NAME" VARCHAR(255), "ID" NUMBER(19) NOT NULL PRIMARY KEY, "HISTORY" VARCHAR(1000), "DOCTOR_NOTES" VARCHAR(255)) """,
            """ CREATE TABLE "JSON_ENTITY" ("ID" NUMBER(19) NOT NULL PRIMARY KEY, "SAMPLE_DATA" JSON) """,
            """ CREATE TABLE "JSON_DATA" ("ID" NUMBER(19) NOT NULL PRIMARY KEY, "NAME" VARCHAR(100), "CREATED_DATE" TIMESTAMP (6), "DURATION" INTERVAL DAY (2) TO SECOND (6)) """)
    }

    @Override
    List<String> dropStatements() {
        return Arrays.asList(""" DROP TABLE "PATIENT" PURGE """, """ DROP SEQUENCE "PATIENT_SEQ" """, """ DROP TABLE "JSON_ENTITY" """, """ DROP TABLE "JSON_DATA" """)
    }

    @Override
    String insertStatement() {
        return """INSERT INTO "PATIENT" ("NAME", "HISTORY", "DOCTOR_NOTES", "ID") VALUES (?, ?, ?, "PATIENT_SEQ".nextval)"""
    }

    void "test JSON object retrieval"() {
        given:
        createSchema()
        def jsonEntity = new JsonEntity()
        jsonEntity.id = 1L
        def sampleData = new SampleData()
        sampleData.etag = UUID.randomUUID().toString()
        sampleData.memo = "memo".getBytes(Charset.defaultCharset())
        sampleData.uuid = UUID.randomUUID()
        sampleData.duration = Duration.ofHours(15)
        sampleData.localDateTime = LocalDateTime.now()
        sampleData.description = "sample description"
        sampleData.grade = 1
        sampleData.rating = 9.75d
        jsonEntity.sampleData = sampleData
        jsonEntityRepository.save(jsonEntity)
        when:
        def optSampleData = jsonEntityRepository.findJsonSampleDataByEntityId(jsonEntity.id)
        then:
        optSampleData.present
        def loadedSampleData = optSampleData.get()
        loadedSampleData.localDateTime == sampleData.localDateTime
        loadedSampleData.etag == sampleData.etag
        loadedSampleData.rating == sampleData.rating
        loadedSampleData.grade == sampleData.grade
        loadedSampleData.description == sampleData.description
        loadedSampleData.memo == sampleData.memo
        loadedSampleData.duration == sampleData.duration
        loadedSampleData.uuid == sampleData.uuid
        when:
        def jsonData = new JsonData()
        jsonData.id = 100L
        jsonData.name = "Custom Name"
        jsonData.createdDate = LocalDateTime.now()
        jsonData.duration = Duration.ofHours(12)
        jsonDataRepository.save(jsonData)
        def optJsonData = jsonDataRepository.getJsonDataById(100L)
        then:
        optJsonData.present
        def loadedJsonData = optJsonData.get()
        loadedJsonData.id == jsonData.id
        loadedJsonData.name == jsonData.name
        loadedJsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli() == jsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli()
        loadedJsonData.duration == jsonData.duration
        cleanup:
        dropSchema()
    }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEJsonEntityRepository extends CrudRepository<JsonEntity, Long> {

    @Query("SELECT SAMPLE_DATA AS DATA FROM JSON_ENTITY WHERE ID = :id")
    @QueryResult(type = QueryResult.Type.JSON, jsonType = JsonType.NATIVE)
    Optional<SampleData> findJsonSampleDataByEntityId(Long id)

    @NonNull
    @Override
    JsonEntity save(@Valid @NotNull @NonNull JsonEntity entity)
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEJsonDataRepository extends CrudRepository<JsonData, Long> {

    @Query(""" SELECT JSON{'id' : "ID", 'name' : "NAME", 'createdDate' : "CREATED_DATE", 'duration' : "DURATION"} AS "DATA" FROM JSON_DATA """)
    @QueryResult(type = QueryResult.Type.JSON, jsonType = JsonType.NATIVE)
    Optional<JsonData> getJsonDataById(Long id)
}

@MappedEntity
class JsonData {
    @Id
    private Long id
    private String name
    private LocalDateTime createdDate
    private Duration duration

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    LocalDateTime getCreatedDate() {
        return createdDate
    }

    void setCreatedDate(LocalDateTime createdDate) {
        this.createdDate = createdDate
    }

    Duration getDuration() {
        return duration
    }

    void setDuration(Duration duration) {
        this.duration = duration
    }
}

@MappedEntity
class JsonEntity {

    @Id
    private Long id

    @TypeDef(type = DataType.JSON)
    @JsonRepresentation(type = JsonType.NATIVE)
    @Nullable
    private SampleData sampleData

    Long getId() {
        return id
    }

    void setId(Long id) {
        this.id = id
    }

    @Nullable
    SampleData getSampleData() {
        return sampleData
    }

    void setSampleData(@Nullable SampleData sampleData) {
        this.sampleData = sampleData
    }
}
