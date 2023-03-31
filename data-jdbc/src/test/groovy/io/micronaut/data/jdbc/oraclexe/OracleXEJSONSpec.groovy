package io.micronaut.data.jdbc.oraclexe

import groovy.transform.Memoized
import io.micronaut.data.annotation.Query
import io.micronaut.data.annotation.QueryResult
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.JsonDataType
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.entities.JsonData
import io.micronaut.data.tck.repositories.JsonEntityRepository
import io.micronaut.data.tck.repositories.SaleItemRepository
import io.micronaut.data.tck.repositories.SaleRepository
import io.micronaut.data.tck.tests.AbstractJSONSpec

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
        jsonDataRepository.save(jsonData)
        def optJsonData = jsonDataRepository.getJsonDataById(100L)
        then:
        optJsonData.present
        def loadedJsonData = optJsonData.get()
        loadedJsonData.id == jsonData.id
        loadedJsonData.name == jsonData.name
        loadedJsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli() == jsonData.createdDate.toInstant(ZoneOffset.UTC).toEpochMilli()
        loadedJsonData.duration == jsonData.duration
    }
}

@JdbcRepository(dialect = Dialect.ORACLE)
interface OracleXEJsonDataRepository extends CrudRepository<JsonData, Long> {

    @Query(""" SELECT JSON{'id' : "ID", 'name' : "NAME", 'createdDate' : "CREATED_DATE", 'duration' : "DURATION"} AS "DATA" FROM JSON_DATA """)
    @QueryResult(type = QueryResult.Type.JSON, jsonDataType = JsonDataType.NATIVE)
    Optional<JsonData> getJsonDataById(Long id)
}
