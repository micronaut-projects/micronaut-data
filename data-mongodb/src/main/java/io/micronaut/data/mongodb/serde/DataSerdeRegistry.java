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
import io.micronaut.core.annotation.Order;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.order.Ordered;
import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.IdPropertyNamingStrategy;
import io.micronaut.data.document.serde.IdSerializer;
import io.micronaut.data.document.serde.ManyRelationSerializer;
import io.micronaut.data.document.serde.OneRelationSerializer;
import io.micronaut.data.model.runtime.AttributeConverterRegistry;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.data.mongodb.conf.MongoDataConfiguration;
import io.micronaut.data.mongodb.operations.MongoUtils;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.SerdeRegistry;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.config.naming.PropertyNamingStrategy;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.support.DefaultSerdeRegistry;
import jakarta.inject.Singleton;
import org.bson.codecs.configuration.CodecRegistry;

import java.io.IOException;
import java.util.Collection;

/**
 * Micronaut Data serde registry.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Singleton
@Order(Ordered.HIGHEST_PRECEDENCE)
@Internal
final class DataSerdeRegistry implements SerdeRegistry {

    public static final IdPropertyNamingStrategy ID_PROPERTY_NAMING_STRATEGY = element -> MongoUtils.ID;

    private final DefaultSerdeRegistry defaultSerdeRegistry;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final AttributeConverterRegistry attributeConverterRegistry;

    private final MongoDataConfiguration mongoDataConfiguration;

    /**
     * Default constructor.
     *
     * @param defaultSerdeRegistry       The DefaultSerdeRegistry
     * @param runtimeEntityRegistry      The runtimeEntityRegistry
     * @param attributeConverterRegistry The attributeConverterRegistry
     * @param mongoDataConfiguration     The Mongo configuration
     */
    public DataSerdeRegistry(DefaultSerdeRegistry defaultSerdeRegistry,
                             RuntimeEntityRegistry runtimeEntityRegistry,
                             AttributeConverterRegistry attributeConverterRegistry,
                             MongoDataConfiguration mongoDataConfiguration) {
        this.defaultSerdeRegistry = defaultSerdeRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
        this.attributeConverterRegistry = attributeConverterRegistry;
        this.mongoDataConfiguration = mongoDataConfiguration;
    }

    public Serializer.EncoderContext newEncoderContext(Class<?> view,
                                                       Argument argument,
                                                       RuntimePersistentEntity<?> runtimePersistentEntity,
                                                       CodecRegistry codecRegistry) {
        return new DataEncoderContext(mongoDataConfiguration, attributeConverterRegistry, argument, (RuntimePersistentEntity<Object>) runtimePersistentEntity, newEncoderContext(view), codecRegistry);
    }

    public Deserializer.DecoderContext newDecoderContext(Class<?> view,
                                                         Argument argument,
                                                         RuntimePersistentEntity<?> runtimePersistentEntity,
                                                         CodecRegistry codecRegistry) {
        return new DataDecoderContext(mongoDataConfiguration, attributeConverterRegistry, argument, (RuntimePersistentEntity<Object>) runtimePersistentEntity, newDecoderContext(view), codecRegistry);
    }

    @Override
    public Serializer.EncoderContext newEncoderContext(Class<?> view) {
        if (view != null) {
            return new DefaultEncoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    for (Class<?> candidate : views) {
                        if (candidate.isAssignableFrom(view)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return new DefaultEncoderContext(this);
    }

    @Override
    public Deserializer.DecoderContext newDecoderContext(Class<?> view) {
        if (view != null) {
            return new DefaultDecoderContext(this) {
                @Override
                public boolean hasView(Class<?>... views) {
                    for (Class<?> candidate : views) {
                        if (candidate.isAssignableFrom(view)) {
                            return true;
                        }
                    }
                    return false;
                }
            };
        }
        return new DefaultDecoderContext(this);
    }

    @Override
    public <T, D extends Serializer<? extends T>> D findCustomSerializer(Class<? extends D> serializerClass) throws SerdeException {
        if (serializerClass == OneRelationSerializer.class) {
            OneRelationSerializer oneRelationSerializer = new OneRelationSerializer() {

                @Override
                public Serializer<Object> createSpecific(EncoderContext encoderContext, Argument<?> type) throws SerdeException {
                    RuntimePersistentEntity entity = runtimeEntityRegistry.getEntity(type.getType());
                    if (entity.getIdentity() == null) {
                        throw new SerdeException("Cannot find ID of entity type: " + type);
                    }
                    BeanProperty property = entity.getIdentity().getProperty();
                    Argument<?> idType = entity.getIdentity().getArgument();
                    Serializer<Object> idSerializer = encoderContext.findCustomSerializer(IdSerializer.class).createSpecific(encoderContext, idType);
                    return new Serializer<Object>() {
                        @Override
                        public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value) throws IOException {
                            Object id = property.get(value);
                            if (id == null) {
                                encoder.encodeNull();
                            } else {
                                Encoder en = encoder.encodeObject(type);
                                en.encodeKey(MongoUtils.ID);
                                idSerializer.serialize(en, context, idType, id);
                                en.finishStructure();
                            }
                        }
                    };
                }

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Object> type, Object value) throws IOException {
                    throw new IllegalStateException("Create specific call is required!");
                }

            };
            return (D) oneRelationSerializer;
        }
        if (serializerClass == ManyRelationSerializer.class) {
            ManyRelationSerializer manyRelationSerializer = new ManyRelationSerializer() {

                @Override
                public void serialize(Encoder encoder, EncoderContext context, Argument<?> type, Object value) throws IOException {
                    encoder.encodeNull();
                }
            };
            return (D) manyRelationSerializer;
        }
        return defaultSerdeRegistry.findCustomSerializer(serializerClass);
    }

    @Override
    public <T> Serializer<? super T> findSerializer(Argument<? extends T> forType) throws SerdeException {
        return defaultSerdeRegistry.findSerializer(forType);
    }

    @Override
    public <T, D extends Deserializer<? extends T>> D findCustomDeserializer(Class<? extends D> deserializerClass) throws SerdeException {
        return defaultSerdeRegistry.findCustomDeserializer(deserializerClass);
    }

    @Override
    public <T> Deserializer<? extends T> findDeserializer(Argument<? extends T> type) throws SerdeException {
        return defaultSerdeRegistry.findDeserializer(type);
    }

    @Override
    public <T> Collection<BeanIntrospection<? extends T>> getDeserializableSubtypes(Class<T> superType) {
        return defaultSerdeRegistry.getDeserializableSubtypes(superType);
    }

    @Override
    public <D extends PropertyNamingStrategy> D findNamingStrategy(Class<? extends D> namingStrategyClass) throws SerdeException {
        return defaultSerdeRegistry.findNamingStrategy(namingStrategyClass);
    }

    @Override
    public ConversionService getConversionService() {
        return defaultSerdeRegistry.getConversionService();
    }
}
