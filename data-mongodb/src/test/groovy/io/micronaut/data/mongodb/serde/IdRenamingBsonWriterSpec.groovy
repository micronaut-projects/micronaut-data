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

import org.bson.BsonArray
import org.bson.BsonBinary
import org.bson.BsonBoolean
import org.bson.BsonDateTime
import org.bson.BsonDbPointer
import org.bson.BsonDecimal128
import org.bson.BsonDocument
import org.bson.BsonDocumentReader
import org.bson.BsonDocumentWriter
import org.bson.BsonDouble
import org.bson.BsonInt32
import org.bson.BsonInt64
import org.bson.BsonJavaScript
import org.bson.BsonJavaScriptWithScope
import org.bson.BsonMaxKey
import org.bson.BsonMinKey
import org.bson.BsonNull
import org.bson.BsonObjectId
import org.bson.BsonRegularExpression
import org.bson.BsonString
import org.bson.BsonSymbol
import org.bson.BsonTimestamp
import org.bson.BsonUndefined
import org.bson.BsonWriter
import org.bson.types.Decimal128
import org.bson.types.ObjectId
import spock.lang.Specification

class IdRenamingBsonWriterSpec extends Specification {

    void "a top level identity is renamed and a valid hex String becomes an ObjectId"() {
        given:
        def id = new ObjectId()

        when:
        def document = write("id", true) { BsonWriter writer ->
            writer.writeString("id", id.toHexString())
            writer.writeStartDocument("nested")
            writer.writeString("id", id.toHexString())
            writer.writeEndDocument()
            writer.writeStartArray("list")
            writer.writeStartDocument()
            writer.writeString("id", "inner")
            writer.writeEndDocument()
            writer.writeEndArray()
        }

        then:
        document == new BsonDocument()
                .append("_id", new BsonObjectId(id))
                .append("nested", new BsonDocument("id", new BsonString(id.toHexString())))
                .append("list", new BsonArray([new BsonDocument("id", new BsonString("inner"))]))
    }

    void "a String identity stays a String when it is not a generated ObjectId"() {
        when:
        def document = write("id", stringAsObjectId) { BsonWriter writer ->
            writer.writeName("id")
            writer.writeString(value)
            writer.writeString("other", new ObjectId().toHexString())
        }

        then:
        document.get("_id") == new BsonString(value)
        document.getString("other").value.length() == 24

        where:
        stringAsObjectId | value
        false            | new ObjectId().toHexString()
        true             | "not-an-object-id"
    }

    void "the identity is renamed whatever the type of its value"() {
        when:
        def document = write("key", true) { BsonWriter writer ->
            writer.writeInt64("key", 10L)
            writer.writeString("name", new ObjectId().toHexString())
        }

        then:
        document.keySet() == ["_id", "name"] as Set
        document.get("_id") == new BsonInt64(10L)
        document.get("name") instanceof BsonString
    }

    void "every value is passed to the delegate under its name"() {
        given:
        def binary = new BsonBinary([1, 2] as byte[])
        def pointer = new BsonDbPointer("ns", new ObjectId())
        def decimal = Decimal128.parse("1.5")
        def objectId = new ObjectId()
        def regex = new BsonRegularExpression("a.*", "i")
        def timestamp = new BsonTimestamp(1, 2)

        when:
        def document = write("id", true) { BsonWriter writer ->
            writer.writeBinaryData("binary", binary)
            writer.writeBoolean("boolean", true)
            writer.writeDateTime("dateTime", 5L)
            writer.writeDBPointer("pointer", pointer)
            writer.writeDouble("double", 1.5d)
            writer.writeInt32("int32", 3)
            writer.writeInt64("int64", 4L)
            writer.writeDecimal128("decimal", decimal)
            writer.writeJavaScript("javaScript", "f()")
            writer.writeJavaScriptWithScope("scoped", "g()")
            writer.writeStartDocument()
            writer.writeInt32("x", 1)
            writer.writeEndDocument()
            writer.writeMaxKey("max")
            writer.writeMinKey("min")
            writer.writeNull("null")
            writer.writeObjectId("objectId", objectId)
            writer.writeRegularExpression("regex", regex)
            writer.writeSymbol("symbol", "s")
            writer.writeTimestamp("timestamp", timestamp)
            writer.writeUndefined("undefined")
            writer.flush()
        }

        then:
        document == new BsonDocument()
                .append("binary", binary)
                .append("boolean", BsonBoolean.TRUE)
                .append("dateTime", new BsonDateTime(5L))
                .append("pointer", pointer)
                .append("double", new BsonDouble(1.5d))
                .append("int32", new BsonInt32(3))
                .append("int64", new BsonInt64(4L))
                .append("decimal", new BsonDecimal128(decimal))
                .append("javaScript", new BsonJavaScript("f()"))
                .append("scoped", new BsonJavaScriptWithScope("g()", new BsonDocument("x", new BsonInt32(1))))
                .append("max", new BsonMaxKey())
                .append("min", new BsonMinKey())
                .append("null", BsonNull.VALUE)
                .append("objectId", new BsonObjectId(objectId))
                .append("regex", regex)
                .append("symbol", new BsonSymbol("s"))
                .append("timestamp", timestamp)
                .append("undefined", new BsonUndefined())
    }

    void "values written after a separate name are passed to the delegate"() {
        given:
        def id = new ObjectId()

        when:
        def document = write("id", true) { BsonWriter writer ->
            writer.writeName("id")
            writer.writeObjectId(id)
            writer.writeName("binary")
            writer.writeBinaryData(new BsonBinary([1] as byte[]))
            writer.writeName("boolean")
            writer.writeBoolean(false)
            writer.writeName("dateTime")
            writer.writeDateTime(1L)
            writer.writeName("pointer")
            writer.writeDBPointer(new BsonDbPointer("ns", id))
            writer.writeName("double")
            writer.writeDouble(2d)
            writer.writeName("int32")
            writer.writeInt32(1)
            writer.writeName("decimal")
            writer.writeDecimal128(Decimal128.parse("2"))
            writer.writeName("javaScript")
            writer.writeJavaScript("f()")
            writer.writeName("max")
            writer.writeMaxKey()
            writer.writeName("min")
            writer.writeMinKey()
            writer.writeName("null")
            writer.writeNull()
            writer.writeName("regex")
            writer.writeRegularExpression(new BsonRegularExpression("b"))
            writer.writeName("symbol")
            writer.writeSymbol("t")
            writer.writeName("timestamp")
            writer.writeTimestamp(new BsonTimestamp(3, 4))
            writer.writeName("undefined")
            writer.writeUndefined()
            writer.writeName("hex")
            writer.writeString(id.toHexString())
        }

        then:
        document.get("_id") == new BsonObjectId(id)
        document.size() == 17
        document.get("hex") == new BsonString(id.toHexString())
    }

    void "a piped document is written as it is"() {
        given:
        def nested = new BsonDocument("id", new BsonString(new ObjectId().toHexString()))

        when:
        def document = write("id", true) { BsonWriter writer ->
            writer.writeName("nested")
            writer.pipe(new BsonDocumentReader(nested))
        }

        then:
        document == new BsonDocument("nested", nested)
    }

    private static BsonDocument write(String idName, boolean stringAsObjectId, Closure<?> body) {
        def document = new BsonDocument()
        def writer = new IdRenamingBsonWriter(new BsonDocumentWriter(document), idName, stringAsObjectId)
        writer.writeStartDocument()
        body.call(writer)
        writer.writeEndDocument()
        return document
    }
}
