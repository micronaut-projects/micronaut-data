package example

import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.PostgreSQLContainer

abstract class AbstractTest(private var useFlyway: Boolean) : TestPropertyProvider {

    var container1: JdbcDatabaseContainer<*> = PostgreSQLContainer<Nothing>("postgres:10")
    var container2: JdbcDatabaseContainer<*> = PostgreSQLContainer<Nothing>("postgres:10")

    @AfterAll
    fun cleanup() {
        container1.stop()
        container2.stop()
    }

    override fun getProperties(): Map<String, String> {
        container1.start()

        val props = if (useFlyway) {
            mapOf(
                    "datasources.flyway.url" to container1.jdbcUrl,
                    "datasources.flyway.username" to container1.username,
                    "datasources.flyway.password" to container1.password,
                    "datasources.flyway.database" to container1.databaseName
            )
        } else {
            mapOf(
                    "r2dbc.datasources.default.schema-generate" to "CREATE_DROP",
                    "r2dbc.datasources.default.dialect" to "POSTGRES",
            )
        }
        return props + mapOf(
                "r2dbc.datasources.default.host" to container1.host,
                "r2dbc.datasources.default.port" to container1.firstMappedPort.toString(),
                "r2dbc.datasources.default.driver" to "postgres",
                "r2dbc.datasources.default.username" to container1.username,
                "r2dbc.datasources.default.password" to container1.password,
                "r2dbc.datasources.default.database" to container1.databaseName
        )
    }

    fun getPropertiesForCustomDB(): Map<String, String> {
        container2.start()
        return mapOf(
                "r2dbc.datasources.custom.schema-generate" to "CREATE_DROP",
                "r2dbc.datasources.custom.dialect" to "POSTGRES",
                "r2dbc.datasources.custom.host" to container2.host,
                "r2dbc.datasources.custom.port" to container2.firstMappedPort.toString(),
                "r2dbc.datasources.custom.driver" to "postgres",
                "r2dbc.datasources.custom.username" to container2.username,
                "r2dbc.datasources.custom.password" to container2.password,
                "r2dbc.datasources.custom.database" to container2.databaseName
        )
    }

}