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
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.document.serde.CustomConverterDeserializer;
import io.micronaut.data.document.serde.IdDeserializer;
import io.micronaut.data.document.serde.IdPropertyNamingStrategy;
import io.micronaut.data.document.serde.OneRelationDeserializer;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.model.runtime.convert.AttributeConverter;
import io.micronaut.data.mongodb.conf.MongoDataConfiguration;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.bson.BsonReaderDecoder;
import io.micronaut.serde.bson.custom.CodecBsonDecoder;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.reference.PropertyReference;
import org.bson.BsonDocument;
import org.bson.codecs.BsonDocumentCodec;
import org.bson.codecs.Codec;
import org.bson.codecs.IterableCodec;
import org.bson.codecs.configuration.CodecRegistry;
import org.bson.types.ObjectId;

import java.io.IOException;
import java.util.Collection;

/**
 * The Micronaut Data's Serde's {@link io.micronaut.serde.Deserializer.DecoderContext}.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
final class DataDecoderContext implements Deserializer.DecoderContext {

    private final Argument<ObjectId> OBJECT_ID = Argument.of(ObjectId.class);

    private final MongoDataConfiguration mongoDataConfiguration;
    private final AttributeConverterRegistry attributeConverterRegistry;
    private final Argument argument;
    private final RuntimePersistentEntity<Object> runtimePersistentEntity;
    private final Deserializer.DecoderContext parent;
    private final CodecRegistry codecRegistry;

    /**
     * Default constructor.
     *
     * @param mongoDataConfiguration     The Mongo data configuration
     * @param attributeConverterRegistry The attributeConverterRegistry
     * @param argument                   The argument
     * @param runtimePersistentEntity    The runtime persistent entity
     * @param parent                     The parent context
     * @param codecRegistry              The codec registry
     */
    DataDecoderContext(MongoDataConfiguration mongoDataConfiguration,
                       AttributeConverterRegistry attributeConverterRegistry,
                       Argument argument,
                       RuntimePersistentEntity<Object> runtimePersistentEntity,
                       Deserializer.DecoderContext parent,
                       CodecRegistry codecRegistry) {
        this.mongoDataConfiguration = mongoDataConfiguration;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.argument = argument;
        this.runtimePersistentEntity = runtimePersistentEntity;
        this.parent = parent;
        this.codecRegistry = codecRegistry;
    }

    @Override
    public <B, P> PropertyReference<B, P> resolveReference(PropertyReference<B, P> reference) {
        return parent.resolveReference(reference);
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        if (deserializerClass == OneRelationDeserializer.class) {
            OneRelationDeserializer oneRelationDeserializer = new OneRelationDeserializer() {

                @Override
                public Deserializer<Object> createSpecific(DecoderContext decoderContext, Argument<? super Object> type) throws SerdeException {
                    Deserializer<?> relationDeser = findDeserializer(type);
                    return new Deserializer<Object>() {
                        @Override
                        public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
                            if (decoder.decodeNull()) {
                                return null;
                            }
                            CodecBsonDecoder<BsonDocument> codecBsonDecoder = new CodecBsonDecoder<>(new BsonDocumentCodec(codecRegistry));
                            BsonDocument document = codecBsonDecoder.deserialize(decoder, decoderContext, type);
                            if (document == null || document.size() <= 1) {
                                return null;
                            }
                            return relationDeser.deserialize(new BsonReaderDecoder(document.asBsonReader()), decoderContext, type);
                        }
                    };
                }

                @Override
                public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
                    throw new IllegalStateException("Create specific call is required!");
                }
            };
            return (D) oneRelationDeserializer;
        }
        if (deserializerClass == IdDeserializer.class) {
            IdDeserializer idDeserializer = new IdDeserializer() {

                @Override
                public Deserializer<Object> createSpecific(DecoderContext decoderContext, Argument<? super Object> type) throws SerdeException {
                    if (type.getType().isAssignableFrom(String.class) && type.isAnnotationPresent(GeneratedValue.class)) {
                        Deserializer<? extends ObjectId> deserializer = findDeserializer(OBJECT_ID);
                        return (decoder, decoderContext2, objectIdType) -> {
                            ObjectId objectId = deserializer.deserialize(decoder, decoderContext2, OBJECT_ID);
                            return objectId == null ? null : objectId.toHexString();
                        };
                    }
                    Deserializer<? extends Object> deserializer = findDeserializer(type);
                    return (Deserializer<Object>) deserializer.createSpecific(decoderContext, type);
                }

                @Override
                public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
                    throw new IllegalStateException("Create specific call is required!");
                }
            };
            return (D) idDeserializer;
        }
        if (deserializerClass == CustomConverterDeserializer.class) {
            CustomConverterDeserializer customConverterDeserializer = new CustomConverterDeserializer() {

                @Override
                public Deserializer<Object> createSpecific(DecoderContext decoderContext, Argument<? super Object> type) throws SerdeException {
                    Class<?> converterClass = type.getAnnotationMetadata().classValue(MappedProperty.class, "converter")
                            .orElseThrow(IllegalStateException::new);
                    Class<Object> converterPersistedType = type.getAnnotationMetadata().classValue(MappedProperty.class, "converterPersistedType")
                            .orElseThrow(IllegalStateException::new);
                    Argument<Object> convertedType = Argument.of(converterPersistedType);
                    AttributeConverter<Object, Object> converter = attributeConverterRegistry.getConverter(converterClass);
                    Deserializer<?> deserializer = findDeserializer(convertedType);
                    return new Deserializer<Object>() {
                        @Override
                        public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
                            if (decoder.decodeNull()) {
                                return null;
                            }
                            Object deserialized = deserializer.deserialize(decoder, decoderContext, convertedType);
                            return converter.convertToEntityValue(deserialized, ConversionContext.of(convertedType));
                        }
                    };
                }

                @Override
                public Object deserialize(Decoder decoder, DecoderContext decoderContext, Argument<? super Object> type) throws IOException {
                    throw new IllegalStateException("Create specific call is required!");
                }
            };
            return (D) customConverterDeserializer;
        }
        return parent.findCustomDeserializer(deserializerClass);
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        Codec<? extends T> codec = codecRegistry.get(type.getType(), codecRegistry);
        if (codec instanceof MappedCodec) {
            return ((MappedCodec<? extends T>) codec).deserializer;
        }
        if (codec != null && !(codec instanceof IterableCodec)) {
            return new CodecBsonDecoder<T>((Codec<T>) codec);
        }
        return parent.findDeserializer(type);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        if (namingStrategyClass == IdPropertyNamingStrategy.class) {
            return (D) DataSerdeRegistry.ID_PROPERTY_NAMING_STRATEGY;
        }
        return parent.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return parent.getDeserializableSubtypes(superType);
    }

    @Override
    public <B, P> void pushManagedRef(PropertyReference<B, P> reference) {
        parent.pushManagedRef(reference);
    }

    @Override
    public void popManagedRef() {
        parent.popManagedRef();
    }

    @Override
    public boolean hasView(Class<?>... views) {
        return this.mongoDataConfiguration.isIgnoreJsonViews() || parent.hasView(views);
    }

}
