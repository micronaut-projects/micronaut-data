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
import io.micronaut.data.document.mongodb.entities.CharSequenceIdEntity
import io.micronaut.data.document.mongodb.entities.Customer
import io.micronaut.data.document.mongodb.entities.FieldAccessFlagEntity
import io.micronaut.data.document.mongodb.entities.FieldAndMethodAccessFlagEntity
import io.micronaut.data.document.mongodb.entities.NestedIdChild
import io.micronaut.data.document.mongodb.entities.NestedIdEmbeddable
import io.micronaut.data.document.mongodb.entities.NestedIdParent
import io.micronaut.data.document.mongodb.entities.RuntimeNamingIdEntity
import io.micronaut.data.document.mongodb.entities.SerializableIdEntity
import io.micronaut.data.document.mongodb.entities.SnakeCaseIdEntity
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonObjectId
import org.bson.BsonString
import org.bson.codecs.DecoderContext
import org.bson.codecs.EncoderContext
import org.bson.codecs.configuration.CodecRegistries
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

    void "a global naming strategy does not rename the document, which the query builder would not find"() {
        given:
        def namingContext = ApplicationContext.run(["micronaut.serde.property-naming-strategy": "SNAKE_CASE"])
        def customer = new Customer("custom-id", "Fred", "Flintstone", null)

        when:
        def document = encode(namingContext, Customer, customer)

        then:
        document.keySet() == ["_id", "firstName", "lastName"] as Set

        when:
        def decoded = decode(namingContext, Customer, document)

        then:
        decoded.id == "custom-id"
        decoded.firstName == "Fred"
        decoded.lastName == "Flintstone"

        and:"the configuration does not push the entity onto the runtime serdes"
        codec(namingContext, Customer).serializer.class.simpleName == "SerdeCustomerSerializer"
        codec(namingContext, Customer).deserializer.class.simpleName == "SerdeCustomerDeserializer"

        cleanup:
        namingContext?.close()
    }

    void "the id of an entity or embeddable inside a document is written as _id"() {
        given:
        def itemId = new ObjectId().toHexString()
        def code = new ObjectId().toHexString()
        def parent = new NestedIdParent(
                new ObjectId().toHexString(),
                new NestedIdChild(new ObjectId().toHexString(), "child"),
                [new NestedIdChild(itemId, "item"), new NestedIdChild("custom-id", "custom")],
                new NestedIdEmbeddable(code, "embeddable")
        )

        when:
        def document = encode(NestedIdParent, parent)

        then:
        document.getArray("children").get(0).asDocument().get("_id") == new BsonObjectId(new ObjectId(itemId))
        document.getArray("children").get(1).asDocument().get("_id") == new BsonString("custom-id")
        document.getDocument("embeddable") == new BsonDocument("_id", new BsonObjectId(new ObjectId(code)))
                .append("name", new BsonString("embeddable"))

        when:
        def decoded = decode(NestedIdParent, document)

        then:
        decoded.children() == parent.children()
        decoded.embeddable() == parent.embeddable()
    }

    void "an embeddable with an id is written with _id when it is encoded on its own"() {
        when:
        def document = encode(NestedIdEmbeddable, new NestedIdEmbeddable("custom-code", "embeddable"))

        then:
        document == new BsonDocument("_id", new BsonString("custom-code")).append("name", new BsonString("embeddable"))
    }

    void "the id is written as _id with a naming strategy on the entity"() {
        given:
        def id = new ObjectId().toHexString()

        when:
        def snakeCase = encode(SnakeCaseIdEntity, new SnakeCaseIdEntity(id, "Fred"))

        then:
        snakeCase == new BsonDocument("_id", new BsonObjectId(new ObjectId(id))).append("first_name", new BsonString("Fred"))
        decode(SnakeCaseIdEntity, snakeCase) == new SnakeCaseIdEntity(id, "Fred")

        when:
        def runtimeNaming = encode(RuntimeNamingIdEntity, new RuntimeNamingIdEntity(id, "Fred"))

        then:
        runtimeNaming == new BsonDocument("_id", new BsonObjectId(new ObjectId(id))).append("x_firstName", new BsonString("Fred"))
        decode(RuntimeNamingIdEntity, runtimeNaming) == new RuntimeNamingIdEntity(id, "Fred")
    }

    void "a generated id declared as a supertype of String is written as an ObjectId"() {
        given:
        def id = new ObjectId().toHexString()

        when:
        def charSequence = encode(CharSequenceIdEntity, new CharSequenceIdEntity(id, "name"))

        then:
        charSequence.get("_id") == new BsonObjectId(new ObjectId(id))
        decode(CharSequenceIdEntity, charSequence).id().toString() == id

        when:
        def serializable = encode(SerializableIdEntity, new SerializableIdEntity(id, "name"))

        then:
        serializable.get("_id") == new BsonObjectId(new ObjectId(id))
        decode(SerializableIdEntity, serializable).id() == id

        and:"a value that is not an ObjectId stays a String"
        encode(CharSequenceIdEntity, new CharSequenceIdEntity("custom-id", "name")).get("_id") == new BsonString("custom-id")
    }

    void "a boolean field with an is prefix keeps its field name with field access"() {
        given:
        def fieldAccess = new FieldAccessFlagEntity(id: "custom-id", isOdd: true)
        def fieldAndMethodAccess = new FieldAndMethodAccessFlagEntity(id: "custom-id", odd: true)

        when:
        def document = encode(FieldAccessFlagEntity, fieldAccess)

        then:
        document.keySet() == ["_id", "isOdd"] as Set
        decode(FieldAccessFlagEntity, document).isOdd

        when:
        document = encode(FieldAndMethodAccessFlagEntity, fieldAndMethodAccess)

        then:"the introspection has both properties, so the runtime serde writes both"
        document.keySet() == ["_id", "isOdd", "odd"] as Set
        decode(FieldAndMethodAccessFlagEntity, document).isOdd()
    }

    private MappedCodec codec(ApplicationContext context = this.context, Class<?> type) {
        def registry = new DataCodecRegistry(
                [type],
                context.getBean(DataSerdeRegistry),
                context.getBean(RuntimeEntityRegistry)
        )
        return (MappedCodec) CodecRegistries.fromRegistries(registry, MongoClientSettings.defaultCodecRegistry).get(type)
    }

    private <T> BsonDocument encode(ApplicationContext context = this.context, Class<T> type, T value) {
        def document = new BsonDocument()
        codec(context, type).encode(new BsonDocumentWriter(document), value, EncoderContext.builder().build())
        return document
    }

    private <T> T decode(ApplicationContext context = this.context, Class<T> type, BsonDocument document) {
        return (T) codec(context, type).decode(new BsonDocumentReader(document), DecoderContext.builder().build())
    }
}
