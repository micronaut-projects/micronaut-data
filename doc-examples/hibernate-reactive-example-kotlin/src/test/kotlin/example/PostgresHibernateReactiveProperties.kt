package example

import io.micronaut.test.support.TestPropertyProvider

interface PostgresHibernateReactiveProperties : TestPropertyProvider {

    override fun getProperties(): Map<String, String> {
        return java.util.Map.of(
                "jpa.default.properties.hibernate.hbm2ddl.auto", "create-drop",
                "jpa.default.reactive", "true",
                "jpa.default.properties.hibernate.connection.db-type", "postgres"
        )
    }


}
