/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.mongodb.serde

import com.mongodb.MongoClientSettings
import io.micronaut.context.ApplicationContext
import io.micronaut.data.document.mongodb.entities.ComplexEntity
import io.micronaut.data.document.mongodb.entities.ComplexValue
import io.micronaut.data.document.mongodb.entities.Customer
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.types.ObjectId
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MappedEntityCodecSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    void "entity codecs use the source generated serdes"() {
        expect:
        codec(type).serializer.class.simpleName == "Serde${type.simpleName}Serializer"
        codec(type).deserializer.class.simpleName == "Serde${type.simpleName}Deserializer"

        where:
        type << [ComplexEntity, Customer]
    }

    void "a generated String id is written as an ObjectId under _id and read back"() {
        given:
        def id = new ObjectId().toHexString()
        def entity = new ComplexEntity(id, "simple", new ComplexValue("a", "b"))

        when:
        def document = encode(ComplexEntity, entity)

        then:
        document.keySet() == ["_id", "simpleValue", "complexValue"] as Set
        document.get("_id") == new BsonObjectId(new ObjectId(id))

        when:
        def decoded = decode(ComplexEntity, document)

        then:
        decoded == entity
    }

    void "a String id that is not an ObjectId is written as a String under _id"() {
        given:
        def customer = new Customer("custom-id", "Fred", "Flintstone", null)

        when:
        def document = encode(Customer, customer)

        then:
        document.get("_id") == new BsonString("custom-id")
        !document.containsKey("id")

        and:"null values are left out, as with the runtime serde"
        !document.containsKey("changeLogs")

        when:
        def decoded = decode(Customer, document)

        then:
        decoded.id == "custom-id"
        decoded.firstName == "Fred"
        decoded.lastName == "Flintstone"
    }

    private MappedCodec codec(Class<?> type) {
        def registry = new DataCodecRegistry(
                [type],
                context.getBean(DataSerdeRegistry),
                context.getBean(RuntimeEntityRegistry)
        )
        return (MappedCodec) registry.get(type, MongoClientSettings.defaultCodecRegistry)
    }

    private <T> BsonDocument encode(Class<T> type, T value) {
        def document = new BsonDocument()
        codec(type).encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build())
        return document
    }

    private <T> T decode(Class<T> type, BsonDocument document) {
        return (T) codec(type).decode(new BsonDocumentReader(document), DecoderContext.builder().build())
    }
}
