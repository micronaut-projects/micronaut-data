package io.micronaut.data.jdbc.mariadb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.micronaut.data.tck.tests.AbstractSchemaSpec

import javax.sql.DataSource

class MariaSchemaSpec extends AbstractSchemaSpec implements MariaTestPropertyProvider {

    void 'validate manually created table(s)'() {
        given:
        def props = properties
        props["datasources.default.schema-generate"] = "none"
        def initialContext = ApplicationContext.run(props)
        def dataSource = DelegatingDataSource.unwrapDataSource(initialContext.getBean(DataSource))
        def connection = dataSource.connection
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity").executeUpdate()
        connection.prepareStatement("CREATE TABLE uuid_maria_schema_entity (id BIGINT NOT NULL PRIMARY KEY, uuid_field uuid)").executeUpdate()
        // create table which does not have mapped entity which will be just skipped
        connection.prepareStatement("CREATE TABLE user_account_maria_schema (id BIGINT NOT NULL PRIMARY KEY, name VARCHAR(30))").executeUpdate()
        when:"Manually created table mapped to an entity"
        def schemaValidateProperties = props
        schemaValidateProperties["datasources.default.schema-generate"] =  "validate"
        schemaValidateProperties["datasources.default.packages"] = "io.micronaut.data.jdbc.mariadb"
        def validationContext = ApplicationContext.run(schemaValidateProperties)
        then:"Schema validation against entity works"
        noExceptionThrown()
        when:"Verify save and read works for manually created table"
        def repository = validationContext.getBean(UuidEntityRepository)
        def uuidEntity = repository.insert(new UuidEntity(id: 1L, uuidField: UUID.randomUUID()))
        def foundEntity = repository.findById(1L).orElse(null)
        then:"Save and read works without errors"
        foundEntity
        foundEntity.id == 1L
        foundEntity.uuidField == uuidEntity.uuidField
        when:"Table not found"
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity").executeUpdate()
        connection.prepareStatement("CREATE TABLE uuid_maria_schema_entity_other (id BIGINT NOT NULL PRIMARY KEY, uuid_field uuid)").executeUpdate()
        ApplicationContext.run(schemaValidateProperties)
        then:"Schema validation throws an error"
        def exception = thrown(Exception)
        exception.message.contains('Schema validation failed. Expected table [uuid_maria_schema_entity] not found')
        when:"Column not found"
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity_other").executeUpdate()
        connection.prepareStatement("CREATE TABLE uuid_maria_schema_entity (id BIGINT NOT NULL PRIMARY KEY, uuid_field_new uuid)").executeUpdate()
        ApplicationContext.run(schemaValidateProperties)
        then:"Schema validation throws an error"
        exception = thrown(Exception)
        exception.message.contains('Schema validation failed. Column [uuid_field] not found in the table [uuid_maria_schema_entity]')
        when:"Column type not matching"
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity").executeUpdate()
        connection.prepareStatement("CREATE TABLE uuid_maria_schema_entity (id BIGINT NOT NULL PRIMARY KEY, uuid_field VARCHAR(50))").executeUpdate()
        validationContext = ApplicationContext.run(schemaValidateProperties)
        then:"Schema validation throws an error"
        exception = thrown(Exception)
        exception.message.contains('Schema validation failed. Column [uuid_field] in table [uuid_maria_schema_entity] of type [VARCHAR] is mapped to [UUID]')
        cleanup:
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity").executeUpdate()
        connection.prepareStatement("DROP TABLE IF EXISTS uuid_maria_schema_entity_other").executeUpdate()
        connection.prepareStatement("DROP TABLE IF EXISTS user_account_maria_schema").executeUpdate()
        if (initialContext) {
            initialContext.close()
        }
        if (validationContext) {
            validationContext.close()
        }
    }
}

@MappedEntity("uuid_maria_schema_entity")
class UuidEntity {

    @Id
    Long id

    UUID uuidField
}

@JdbcRepository(dialect = Dialect.MYSQL)
interface UuidEntityRepository extends CrudRepository<UuidEntity, Long> {
}
