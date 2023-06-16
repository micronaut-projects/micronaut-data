package example

import io.micronaut.test.support.TestPropertyProvider
import org.bson.UuidRepresentation

abstract class AbstractMongoSpec : TestPropertyProvider {

    override fun getProperties(): Map<String, String> {
        return mapOf(
                "mongodb" to "",
                "mongodb.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }

    fun getPropertiesForDefaultCustomDB(): Map<String, String> {
        return mapOf(
                "mongodb.servers.default.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }

    fun getPropertiesForCustomDB(): Map<String, String> {
        return mapOf(
                "mongodb.servers.custom.uuid-representation" to UuidRepresentation.STANDARD.name
        )
    }
}
