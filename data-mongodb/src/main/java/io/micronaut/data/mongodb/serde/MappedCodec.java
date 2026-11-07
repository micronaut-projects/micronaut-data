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

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Relation;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.RuntimePersistentProperty;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.LimitingStream;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.BsonWriterEncoder;
import io.micronaut.serde.config.annotation.SerdeConfig;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
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
    @Nullable
    private final String identitySerializedName;
    private final boolean stringIdAsObjectId;

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
            if (persistentEntity.hasIdentity()) {
                RuntimePersistentProperty<T> identity = persistentEntity.getIdentity();
                this.identitySerializedName = identitySerializedName(identity);
                this.stringIdAsObjectId = identity.isAnnotationPresent(GeneratedValue.class)
                    && identity.getType().isAssignableFrom(String.class);
            } else {
                this.identitySerializedName = null;
                this.stringIdAsObjectId = false;
            }
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
                serializer.serialize(new BsonWriterEncoder(documentWriter(writer), LimitingStream.DEFAULT_LIMITS), this.encoderContext, argument, value);
            } else {
                // To-many relations are represented by join collections, so the relation serializer writes them
                // as null in the owning document. Do not persist those explicit nulls: serde's strict null
                // contract rejects null BSON values for non-null collection constructor arguments, while an
                // absent field lets immutable entities use their default relation value.
                BsonDocument document = new BsonDocument();
                serializer.serialize(new BsonWriterEncoder(documentWriter(new BsonDocumentWriter(document)), LimitingStream.DEFAULT_LIMITS), this.encoderContext, argument, value);
                removeNullManyAssociations(document);
                new BsonDocumentCodec(codecRegistry).encode(writer, document, encoderContext);
            }
        } catch (IOException e) {
            throw new DataAccessException("Cannot serialize: " + value, e);
        }
    }

    /**
     * The serializer of this type as a value inside another document. The writer of that document only renames
     * its own identity, so the identity of this value is renamed here.
     *
     * @return The serializer
     */
    Serializer<? super T> nestedSerializer() {
        if (identitySerializedName == null) {
            return serializer;
        }
        return new NestedIdentitySerializer<>(serializer, identitySerializedName, stringIdAsObjectId);
    }

    private BsonWriter documentWriter(BsonWriter writer) {
        if (identitySerializedName == null) {
            return writer;
        }
        return new IdRenamingBsonWriter(writer, identitySerializedName, stringIdAsObjectId);
    }

    /**
     * The name the serde writes the identity under: an explicit name, which includes a naming strategy resolved
     * during annotation processing, or else the result of a naming strategy looked up at runtime.
     */
    private String identitySerializedName(RuntimePersistentProperty<T> identity) throws SerdeException {
        AnnotationMetadata annotationMetadata = identity.getAnnotationMetadata();
        String explicitName = annotationMetadata.stringValue(SerdeConfig.class, SerdeConfig.PROPERTY).orElse(null);
        if (explicitName != null) {
            return explicitName;
        }
        Class<? extends PropertyNamingStrategy> namingStrategy = annotationMetadata.classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING)
            .or(() -> persistentEntity.getAnnotationMetadata().classValue(SerdeConfig.class, SerdeConfig.RUNTIME_NAMING))
            .map(type -> type.asSubclass(PropertyNamingStrategy.class))
            .orElse(null);
        if (namingStrategy == null) {
            return identity.getName();
        }
        return encoderContext.findNamingStrategy(namingStrategy).translate(identity.getProperty());
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

    /**
     * Writes a value through a writer that renames its identity.
     *
     * @param <T> The value type
     */
    private static final class NestedIdentitySerializer<T> implements Serializer<T> {

        private final Serializer<T> delegate;
        private final String identityName;
        private final boolean stringIdAsObjectId;

        @SuppressWarnings("unchecked")
        NestedIdentitySerializer(Serializer<? super T> delegate, String identityName, boolean stringIdAsObjectId) {
            this.delegate = (Serializer<T>) delegate;
            this.identityName = identityName;
            this.stringIdAsObjectId = stringIdAsObjectId;
        }

        @Override
        public Serializer<T> createSpecific(EncoderContext context, Argument<? extends T> type) throws SerdeException {
            Serializer<T> specific = delegate.createSpecific(context, type);
            if (type.getAnnotationMetadata().hasAnnotation(SerdeConfig.SerUnwrapped.class)) {
                // Written into the fields of the enclosing document, which needs the serializer to do that
                return specific;
            }
            return specific == delegate ? this : new NestedIdentitySerializer<>(specific, identityName, stringIdAsObjectId);
        }

        @Override
        public void serialize(Encoder encoder, EncoderContext context, Argument<? extends T> type, T value) throws IOException {
            if (encoder instanceof BsonWriterEncoder bsonEncoder) {
                BsonWriter writer = new IdRenamingBsonWriter(bsonEncoder.getBsonWriter(), identityName, stringIdAsObjectId);
                delegate.serialize(new BsonWriterEncoder(writer, LimitingStream.DEFAULT_LIMITS), context, type, value);
            } else {
                delegate.serialize(encoder, context, type, value);
            }
        }

        @Override
        public boolean isEmpty(EncoderContext context, @Nullable T value) {
            return delegate.isEmpty(context, value);
        }

        @Override
        public boolean isAbsent(EncoderContext context, @Nullable T value) {
            return delegate.isAbsent(context, value);
        }

        @Override
        public boolean isDefault(EncoderContext context, T value) {
            return delegate.isDefault(context, value);
        }
    }
}
