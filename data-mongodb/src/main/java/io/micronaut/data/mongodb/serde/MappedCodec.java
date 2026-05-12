/*
 * Copyright 2017-2022 original authors
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
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import io.micronaut.serde.config.annotation.SerdeConfig;
import org.bson.BsonDocument;
import org.bson.BsonDocumentWriter;
import org.bson.BsonReader;
import org.bson.BsonWriter;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.DecoderContext;
import org.bson.codecs.EncoderContext;
import org.bson.codecs.configuration.CodecRegistry;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistent entity implementation of {@link Codec}.
 *
 * @param <T> The entity type
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
class MappedCodec<T> implements Codec<T> {

    protected final DataSerdeRegistry dataSerdeRegistry;
    protected final RuntimePersistentEntity<T> persistentEntity;
    protected final Class<T> type;
    protected final Argument<T> argument;
    protected final Serializer<? super T> serializer;
    protected final Deserializer<? extends T> deserializer;
    protected final CodecRegistry codecRegistry;
    private final Deserializer.DecoderContext decoderContext;
    private final Serializer.EncoderContext encoderContext;
    private final List<String> manyAssociationProperties;

    /**
     * Default constructor.
     *
     * @param dataSerdeRegistry The data serde registry
     * @param persistentEntity  The persistent entity
     * @param type              The type
     * @param codecRegistry     The codec registry
     */
    MappedCodec(DataSerdeRegistry dataSerdeRegistry,
                RuntimePersistentEntity<T> persistentEntity,
                Class<T> type,
                CodecRegistry codecRegistry) {
        this.dataSerdeRegistry = dataSerdeRegistry;
        this.persistentEntity = persistentEntity;
        this.type = type;
        this.argument = Argument.of(type);
        this.codecRegistry = codecRegistry;
        this.decoderContext = dataSerdeRegistry.newDecoderContext(type, codecRegistry);
        this.encoderContext = dataSerdeRegistry.newEncoderContext(type, codecRegistry);
        this.manyAssociationProperties = findManyAssociationProperties(persistentEntity);
        try {
            this.serializer = dataSerdeRegistry.findSerializer(argument).createSpecific(encoderContext, argument);
            this.deserializer = dataSerdeRegistry.findDeserializer(argument).createSpecific(decoderContext, argument);
        } catch (IOException e) {
            throw new DataAccessException("Cannot find serialize/deserializer for type: " + type + ". " + e.getMessage(), e);
        }
    }

    @Override
    @Nullable
    public T decode(BsonReader reader, DecoderContext decoderContext) {
        try {
            return deserializer.deserialize(new BsonReaderDecoder(reader, LimitingStream.DEFAULT_LIMITS), this.decoderContext, argument);
        } catch (IOException e) {
            throw new DataAccessException("Cannot deserialize: " + type, e);
        }
    }

    @Override
    public void encode(BsonWriter writer, T value, EncoderContext encoderContext) {
        try {
            if (manyAssociationProperties.isEmpty()) {
                serializer.serialize(new BsonWriterEncoder(writer, LimitingStream.DEFAULT_LIMITS), this.encoderContext, argument, value);
            } else {
                BsonDocument document = new BsonDocument();
                serializer.serialize(new BsonWriterEncoder(new BsonDocumentWriter(document), LimitingStream.DEFAULT_LIMITS), this.encoderContext, argument, value);
                removeNullManyAssociations(document);
                new BsonDocumentCodec(codecRegistry).encode(writer, document, encoderContext);
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot serialize: " + value, e);
        }
    }

    @Override
    public Class<T> getEncoderClass() {
        return type;
    }

    private static List<String> findManyAssociationProperties(RuntimePersistentEntity<?> persistentEntity) {
        List<String> propertyNames = new ArrayList<>(2);
        for (Association association : persistentEntity.getAssociations()) {
            Relation.Kind kind = association.getKind();
            if (kind == Relation.Kind.ONE_TO_MANY || kind == Relation.Kind.MANY_TO_MANY) {
                propertyNames.add(
                    association.getAnnotationMetadata()
                        .stringValue(SerdeConfig.class, SerdeConfig.PROPERTY)
                        .orElseGet(association::getName)
                );
            }
        }
        return List.copyOf(propertyNames);
    }

    private void removeNullManyAssociations(BsonDocument document) {
        for (String propertyName : manyAssociationProperties) {
            if (document.containsKey(propertyName) && document.get(propertyName).isNull()) {
                document.remove(propertyName);
            }
        }
    }
}
