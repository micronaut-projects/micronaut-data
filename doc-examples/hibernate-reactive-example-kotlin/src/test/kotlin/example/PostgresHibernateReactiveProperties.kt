package example

import io.micronaut.test.support.TestPropertyProvider
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

interface PostgresHibernateReactiveProperties : TestPropertyProvider {

    companion object {
        val CONTAINER: PostgreSQLContainer<*> = PostgreSQLContainer(DockerImageName.parse("postgres:10"))
    }

    override fun getProperties(): Map<String, String> {
        CONTAINER.start()
        return java.util.Map.of(
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop",
                "jpa.default.reactive", "true",
                "jpa.default.properties.hibernate.connection.url", CONTAINER.jdbcUrl,
                "jpa.default.properties.hibernate.connection.username", CONTAINER.username,
                "jpa.default.properties.hibernate.connection.password", CONTAINER.password
        )
    }


}
