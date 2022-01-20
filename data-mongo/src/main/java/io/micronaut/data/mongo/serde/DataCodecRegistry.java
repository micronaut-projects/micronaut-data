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
package io.micronaut.data.mongo.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import jakarta.inject.Singleton;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecConfigurationException;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The Micronaut Data codec registry.
 *
 * @author Denis Stepanov
 * @since 3.3
 */
@Internal
@Singleton
final class DataCodecRegistry implements CodecRegistry {

    private final DataSerdeRegistry dataSerdeRegistry;
    private final RuntimeEntityRegistry runtimeEntityRegistry;
    private final Map<Class, Codec> codecs = new ConcurrentHashMap<>();

    /**
     * Default constructor.
     *
     * @param dataSerdeRegistry     The data serde registry
     * @param runtimeEntityRegistry The runtime entity registry
     */
    DataCodecRegistry(DataSerdeRegistry dataSerdeRegistry,
                      RuntimeEntityRegistry runtimeEntityRegistry) {
        this.dataSerdeRegistry = dataSerdeRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz) {
        throw new CodecConfigurationException("Not supported");
    }

    @Override
    public <T> Codec<T> get(Class<T> clazz, CodecRegistry registry) {
        Codec codec = codecs.get(clazz);
        if (codec != null) {
            return codec;
        }
        if (BeanIntrospector.SHARED.findIntrospection(clazz).isPresent()) {
            RuntimePersistentEntity<T> entity = runtimeEntityRegistry.getEntity(clazz);
            if (entity.isAnnotationPresent(MappedEntity.class)) {
                codec = new MappedEntityCodec<>(dataSerdeRegistry, entity, clazz, registry);
            } else {
                // Embedded
                codec = new MappedCodec<>(dataSerdeRegistry, entity, clazz, registry);
            }
            codecs.put(clazz, codec);
            return codec;
        }
        return null;
    }

}
