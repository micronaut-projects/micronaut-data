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
package io.micronaut.data.mongodb.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.mongodb.operations.MongoUtils;
import org.bson.BsonBinary;
import org.bson.BsonDbPointer;
import org.bson.BsonReader;
import org.bson.BsonRegularExpression;
import org.bson.BsonTimestamp;
import org.bson.BsonWriter;
import org.bson.types.Decimal128;
import org.bson.types.ObjectId;

/**
 * Writes the identity of an entity document as {@code _id}.
 *
 * <p>The serde of an entity writes the identity under its property name, so that the same serde can be source
 * generated and used outside MongoDB. This writer renames that top-level field to {@code _id} and, for a generated
 * {@link String} identity, writes a valid hex value as an {@link ObjectId}.</p>
 *
 * @author Graeme Rocher
 * @since 5.3.0
 */
@Internal
final class IdRenamingBsonWriter implements BsonWriter {

    private final BsonWriter delegate;
    private final String idName;
    private final boolean stringAsObjectId;
    private int depth;
    private boolean idValuePending;

    /**
     * @param delegate         The writer to write to
     * @param idName           The name the serde writes the identity under
     * @param stringAsObjectId Whether a {@link String} identity that is a valid object id is written as one
     */
    IdRenamingBsonWriter(BsonWriter delegate, String idName, boolean stringAsObjectId) {
        this.delegate = delegate;
        this.idName = idName;
        this.stringAsObjectId = stringAsObjectId;
    }

    private String name(String name) {
        if (depth == 1 && idName.equals(name)) {
            idValuePending = true;
            return MongoUtils.ID;
        }
        return name;
    }

    private void value() {
        idValuePending = false;
    }

    @Override
    public void writeName(String name) {
        delegate.writeName(name(name));
    }

    @Override
    public void writeString(String value) {
        if (idValuePending && stringAsObjectId && ObjectId.isValid(value)) {
            value();
            delegate.writeObjectId(new ObjectId(value));
            return;
        }
        value();
        delegate.writeString(value);
    }

    @Override
    public void writeString(String name, String value) {
        writeName(name);
        writeString(value);
    }

    @Override
    public void writeStartDocument() {
        value();
        depth++;
        delegate.writeStartDocument();
    }

    @Override
    public void writeStartDocument(String name) {
        writeName(name);
        writeStartDocument();
    }

    @Override
    public void writeEndDocument() {
        depth--;
        delegate.writeEndDocument();
    }

    @Override
    public void writeStartArray() {
        value();
        depth++;
        delegate.writeStartArray();
    }

    @Override
    public void writeStartArray(String name) {
        writeName(name);
        writeStartArray();
    }

    @Override
    public void writeEndArray() {
        depth--;
        delegate.writeEndArray();
    }

    @Override
    public void flush() {
        delegate.flush();
    }

    @Override
    public void writeBinaryData(BsonBinary binary) {
        value();
        delegate.writeBinaryData(binary);
    }

    @Override
    public void writeBinaryData(String name, BsonBinary binary) {
        writeName(name);
        writeBinaryData(binary);
    }

    @Override
    public void writeBoolean(boolean value) {
        value();
        delegate.writeBoolean(value);
    }

    @Override
    public void writeBoolean(String name, boolean value) {
        writeName(name);
        writeBoolean(value);
    }

    @Override
    public void writeDateTime(long value) {
        value();
        delegate.writeDateTime(value);
    }

    @Override
    public void writeDateTime(String name, long value) {
        writeName(name);
        writeDateTime(value);
    }

    @Override
    public void writeDBPointer(BsonDbPointer value) {
        value();
        delegate.writeDBPointer(value);
    }

    @Override
    public void writeDBPointer(String name, BsonDbPointer value) {
        writeName(name);
        writeDBPointer(value);
    }

    @Override
    public void writeDouble(double value) {
        value();
        delegate.writeDouble(value);
    }

    @Override
    public void writeDouble(String name, double value) {
        writeName(name);
        writeDouble(value);
    }

    @Override
    public void writeInt32(int value) {
        value();
        delegate.writeInt32(value);
    }

    @Override
    public void writeInt32(String name, int value) {
        writeName(name);
        writeInt32(value);
    }

    @Override
    public void writeInt64(long value) {
        value();
        delegate.writeInt64(value);
    }

    @Override
    public void writeInt64(String name, long value) {
        writeName(name);
        writeInt64(value);
    }

    @Override
    public void writeDecimal128(Decimal128 value) {
        value();
        delegate.writeDecimal128(value);
    }

    @Override
    public void writeDecimal128(String name, Decimal128 value) {
        writeName(name);
        writeDecimal128(value);
    }

    @Override
    public void writeJavaScript(String code) {
        value();
        delegate.writeJavaScript(code);
    }

    @Override
    public void writeJavaScript(String name, String code) {
        writeName(name);
        writeJavaScript(code);
    }

    @Override
    public void writeJavaScriptWithScope(String code) {
        // The scope document that follows is written through this writer
        value();
        delegate.writeJavaScriptWithScope(code);
    }

    @Override
    public void writeJavaScriptWithScope(String name, String code) {
        writeName(name);
        writeJavaScriptWithScope(code);
    }

    @Override
    public void writeMaxKey() {
        value();
        delegate.writeMaxKey();
    }

    @Override
    public void writeMaxKey(String name) {
        writeName(name);
        writeMaxKey();
    }

    @Override
    public void writeMinKey() {
        value();
        delegate.writeMinKey();
    }

    @Override
    public void writeMinKey(String name) {
        writeName(name);
        writeMinKey();
    }

    @Override
    public void writeNull() {
        value();
        delegate.writeNull();
    }

    @Override
    public void writeNull(String name) {
        writeName(name);
        writeNull();
    }

    @Override
    public void writeObjectId(ObjectId objectId) {
        value();
        delegate.writeObjectId(objectId);
    }

    @Override
    public void writeObjectId(String name, ObjectId objectId) {
        writeName(name);
        writeObjectId(objectId);
    }

    @Override
    public void writeRegularExpression(BsonRegularExpression regularExpression) {
        value();
        delegate.writeRegularExpression(regularExpression);
    }

    @Override
    public void writeRegularExpression(String name, BsonRegularExpression regularExpression) {
        writeName(name);
        writeRegularExpression(regularExpression);
    }

    @Override
    public void writeSymbol(String value) {
        value();
        delegate.writeSymbol(value);
    }

    @Override
    public void writeSymbol(String name, String value) {
        writeName(name);
        writeSymbol(value);
    }

    @Override
    public void writeTimestamp(BsonTimestamp value) {
        value();
        delegate.writeTimestamp(value);
    }

    @Override
    public void writeTimestamp(String name, BsonTimestamp value) {
        writeName(name);
        writeTimestamp(value);
    }

    @Override
    public void writeUndefined() {
        value();
        delegate.writeUndefined();
    }

    @Override
    public void writeUndefined(String name) {
        writeName(name);
        writeUndefined();
    }

    @Override
    public void pipe(BsonReader reader) {
        // A piped document is complete on its own and is not renamed
        value();
        delegate.pipe(reader);
    }
}
