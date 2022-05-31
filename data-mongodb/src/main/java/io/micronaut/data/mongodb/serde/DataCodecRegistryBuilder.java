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

import com.mongodb.MongoClientSettings;
import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.CodecRegistryBuilder;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import io.micronaut.serde.annotation.Serdeable;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.bson.codecs.configuration.CodecRegistries.fromCodecs;
import static org.bson.codecs.configuration.CodecRegistries.fromRegistries;

/**
 * Default builder.
 *
 * @author Denis Stepanov
 * @since 3.5.0
 */
@Prototype
@Internal
final class DataCodecRegistryBuilder implements CodecRegistryBuilder {

    private final Environment environment;
    private final DataSerdeRegistry dataSerdeRegistry;
    private final RuntimeEntityRegistry runtimeEntityRegistry;

    DataCodecRegistryBuilder(Environment environment, DataSerdeRegistry dataSerdeRegistry, RuntimeEntityRegistry runtimeEntityRegistry) {
        this.environment = environment;
        this.dataSerdeRegistry = dataSerdeRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public CodecRegistry build(AbstractMongoConfiguration configuration) {
        List<CodecRegistry> codecRegistries = new ArrayList<>();
        DataCodecRegistry dataCodecRegistry;
        Collection<String> packageNames = configuration.getPackageNames();
        if (CollectionUtils.isNotEmpty(packageNames)) {
            Collection<Class<?>> entities = Stream.concat(
                    environment.scan(Serdeable.Serializable.class, packageNames.toArray(new String[0])),
                    environment.scan(Serdeable.Deserializable.class, packageNames.toArray(new String[0]))
            ).collect(Collectors.toSet());
            dataCodecRegistry = new DataCodecRegistry(entities, dataSerdeRegistry, runtimeEntityRegistry);
        } else {
            dataCodecRegistry = new DataCodecRegistry(null, dataSerdeRegistry, runtimeEntityRegistry);
        }
        codecRegistries.add(dataCodecRegistry);

        codecRegistries.add(MongoClientSettings.getDefaultCodecRegistry());
        List<CodecRegistry> configuredCodecRegistries = configuration.getCodecRegistries();
        if (configuredCodecRegistries != null) {
            codecRegistries.addAll(configuredCodecRegistries);
        }
        List<Codec<?>> codecList = configuration.getCodecs();
        if (codecList != null) {
            codecRegistries.add(fromCodecs(codecList));
        }
        return fromRegistries(codecRegistries);
    }
}
