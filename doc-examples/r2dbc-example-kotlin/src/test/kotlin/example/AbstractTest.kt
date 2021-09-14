package example

import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.AfterAll
import org.testcontainers.containers.JdbcDatabaseContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.utility.DockerImageName

abstract class AbstractTest : TestPropertyProvider {

    private var container: JdbcDatabaseContainer<*> = MySQLContainer<Nothing>(DockerImageName.parse("mysql/mysql-server:8.0")
            .asCompatibleSubstituteFor("mysql"))

    @AfterAll
    fun cleanup() {
        container.stop()
    }

    override fun getProperties(): Map<String, String> {
        container.start()
        return mapOf(
                "datasources.default.url" to container.jdbcUrl,
                "datasources.default.username" to container.username,
                "datasources.default.password" to container.password,
                "datasources.default.database" to container.databaseName,
                "r2dbc.datasources.default.host" to container.host,
                "r2dbc.datasources.default.port" to container.firstMappedPort.toString(),
                "r2dbc.datasources.default.driver" to "mysql",
                "r2dbc.datasources.default.username" to container.username,
                "r2dbc.datasources.default.password" to container.password,
                "r2dbc.datasources.default.database" to container.databaseName
        )
    }

}