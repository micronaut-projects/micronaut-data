package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.QueryResult
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.JsonDataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.JsonData
import io.micronaut.data.tck.entities.JsonEntity
import io.micronaut.data.tck.entities.SampleData
import io.micronaut.data.tck.repositories.JsonEntityRepository
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import io.micronaut.data.tck.tests.AbstractJSONSpec

import java.nio.charset.Charset
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset

class OracleXEJSONSpec extends AbstractJSONSpec implements OracleTestPropertyProvider {

    @Override
    @Memoized
    SaleRepository getSaleRepository() {
        return applicationContext.getBean(OracleXESaleRepository)
    }

    @Memoized
    @Override
    SaleItemRepository getSaleItemRepository() {
        return applicationContext.getBean(OracleXESaleItemRepository)
    }

    @Memoized
    @Override
    JsonEntityRepository getJsonEntityRepository() {
        return applicationContext.getBean(OracleXEJsonEntityRepository)
    }

    @Memoized
    OracleXEJsonDataRepository getJsonDataRepository() {
        return applicationContext.getBean(OracleXEJsonDataRepository)
    }

    void "test SELECT JSON() from the relational table"() {
        when:
        def jsonData = new JsonData()
        jsonData.id = 100L
        jsonData.name = "Custom Name"
        jsonData.createdDate = LocalDateTime.now()
        jsonData.duration = Duration.ofHours(12)
        jsonDataRepository.insert(jsonData)
        def optJsonData = jsonDataRepository.getJsonDataById(100L)
        then:
        optJsonData.present
        def loadedJsonData = optJsonData.get()
        loadedJsonData.id == jsonData.id
        loadedJsonData.name == jsonData.name
        loadedJsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli() == jsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli()
        loadedJsonData.duration == jsonData.duration
    }

    void "test Oracle JSON_VALUE"() {
        def jsonEntity = new JsonEntity()
        jsonEntity.id = 2L
        def sampleData = new SampleData()
        sampleData.etag = UUID.randomUUID().toString()
        sampleData.memo = "memo2".getBytes(Charset.defaultCharset())
        sampleData.uuid = UUID.randomUUID()
        sampleData.duration = Duration.ofHours(10)
        sampleData.localDateTime = LocalDateTime.now()
        sampleData.description = "oracle json description"
        sampleData.grade = 2
        sampleData.rating = 8d
        jsonEntity.jsonDefault = sampleData
        jsonEntity.jsonBlob = sampleData
        jsonEntity.jsonString = sampleData
        jsonEntityRepository.insert(jsonEntity)
        when:"Load entity from JSON BLOB field"
        def optSampleDataFromJsonBlob = jsonEntityRepository.findJsonBlobById(jsonEntity.id)
        then:"Entity is retrieved and properly deserialized"
        optSampleDataFromJsonBlob.present && optSampleDataFromJsonBlob.get() == sampleData
        when:"Loaded field from JSON fields using JSON_VALUE"
        def oracleJsonEntityRepository = (OracleXEJsonEntityRepository) jsonEntityRepository
        def jsonBlobFieldDescription = oracleJsonEntityRepository.getDescriptionFromJsonBlob(jsonEntity.id).orElse(null)
        def jsonStringFieldDescription = oracleJsonEntityRepository.getDescriptionFromJsonString(jsonEntity.id).orElse(null)
        def jsonDefaultFieldDescription = oracleJsonEntityRepository.getDescriptionFromJsonDefault(jsonEntity.id).orElse(null)
        then:
        jsonBlobFieldDescription
        jsonBlobFieldDescription == sampleData.description
        jsonStringFieldDescription
        jsonStringFieldDescription == jsonBlobFieldDescription
        jsonDefaultFieldDescription
        jsonDefaultFieldDescription == jsonStringFieldDescription
        cleanup:
        jsonEntityRepository.deleteAll()
    }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEJsonDataRepository extends CrudRepository<JsonData, Long> {

    @Query(""" SELECT JSON{'id' : "ID", 'name' : "NAME", 'createdDate' : "CREATED_DATE", 'duration' : "DURATION"} AS "DATA" FROM JSON_DATA """)
    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.DEFAULT)
    Optional<JsonData> getJsonDataById(Long id)
}
