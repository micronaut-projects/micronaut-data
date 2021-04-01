package example

import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import io.micronaut.test.support.TestPropertyProvider
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.MySQLContainer
import javax.inject.Inject

@MicronautTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SomeEntityRepositoryTest() : TestPropertyProvider {
    @Inject
    lateinit var repository: SomeEntityRepository
    var container: MySQLContainer<*>? = null

    @Test
    fun testInsertImmutableWithNullValue() {
        val result = repository.save(SomeEntity()).block()

        assertNotNull(result)
        assertNotNull(result?.id)
    }

    override fun getProperties(): Map<String, String> {
        container = MySqlServer.start()
        return mapOf(
            "datasources.default.url" to container!!.jdbcUrl,
            "datasources.default.username" to container!!.username,
            "datasources.default.password" to container!!.password,
            "datasources.default.database" to container!!.databaseName,
            "r2dbc.datasources.default.host" to container!!.host,
            "r2dbc.datasources.default.port" to container!!.firstMappedPort.toString(),
            "r2dbc.datasources.default.driver" to "mysql",
            "r2dbc.datasources.default.username" to container!!.username,
            "r2dbc.datasources.default.password" to container!!.password,
            "r2dbc.datasources.default.database" to container!!.databaseName
        )
    }
}