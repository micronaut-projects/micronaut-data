package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.QueryResult
import io.micronaut.data.jdbc.AbstractManualSchemaSpec
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.runtime.config.SchemaGenerate
import io.micronaut.data.tck.entities.JsonEntity
import io.micronaut.data.tck.entities.SampleData
import io.micronaut.data.tck.repositories.PatientRepository

import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime

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

    @Override
    List<String> createStatements() {
        return Arrays.asList("CREATE SEQUENCE \"PATIENT_SEQ\" MINVALUE 1 START WITH 1 CACHE 100 NOCYCLE",
            "CREATE TABLE \"PATIENT\" (\"NAME\" VARCHAR(255), \"ID\" NUMBER(19) NOT NULL PRIMARY KEY, \"HISTORY\" VARCHAR(1000), \"DOCTOR_NOTES\" VARCHAR(255))",
            "CREATE TABLE \"JSON_ENTITY\" (\"ID\" NUMBER(19) NOT NULL PRIMARY KEY, \"SAMPLE_DATA\" JSON)")
    }

    @Override
    List<String> dropStatements() {
        return Arrays.asList("DROP TABLE \"PATIENT\" PURGE", "DROP SEQUENCE \"PATIENT_SEQ\"", "DROP TABLE \"JSON_ENTITY\"")
    }

    @Override
    String insertStatement() {
        return "INSERT INTO \"PATIENT\" (\"NAME\", \"HISTORY\", \"DOCTOR_NOTES\", \"ID\") VALUES (?, ?, ?, \"PATIENT_SEQ\".nextval)"
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
        cleanup:
        dropSchema()
    }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEJsonEntityRepository extends CrudRepository<JsonEntity, Long> {

    @Query("SELECT SAMPLE_DATA AS DATA FROM JSON_ENTITY WHERE ID = :id")
    @QueryResult(type = QueryResult.Type.JSON)
    Optional<SampleData> findJsonSampleDataByEntityId(Long id);

}
