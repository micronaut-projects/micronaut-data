package example

import io.micronaut.test.support.TestPropertyProvider

abstract class AbstractTest(private var useFlyway: Boolean) : TestPropertyProvider {

    override fun getProperties(): Map<String, String> {
        val props = if (useFlyway) {
            mapOf("flyway.datasources.default.enabled:" to "true")
        } else {
            mapOf("r2dbc.datasources.default.schema-generate" to "CREATE_DROP")
        }
        return props
    }

    fun getPropertiesForCustomDB(): Map<String, String> {
        return mapOf(
                "r2dbc.datasources.custom.schema-generate" to "CREATE_DROP",
                "r2dbc.datasources.custom.dialect" to "mysql",
                "r2dbc.datasources.custom.db-type" to "mariadb"
        )
    }

}
