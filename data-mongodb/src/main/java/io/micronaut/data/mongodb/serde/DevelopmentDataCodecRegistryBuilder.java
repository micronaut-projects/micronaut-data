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

import io.micronaut.configuration.mongo.core.AbstractMongoConfiguration;
import io.micronaut.configuration.mongo.core.CodecRegistryBuilder;
import io.micronaut.context.BeanProvider;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.condition.Condition;
import io.micronaut.context.condition.ConditionContext;
import io.micronaut.context.env.DevelopmentActive;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.data.model.runtime.RuntimeEntityRegistry;
import org.bson.codecs.Codec;
import org.bson.codecs.configuration.CodecRegistry;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Type;
import java.util.List;
import java.util.function.Supplier;

/**
 * The codec registry builder of Micronaut Data in development mode, with Micronaut MongoDB's development support,
 * which retains the driver clients across restarts and applies the codec registry of each generation per operation.
 *
 * <p>It builds the same registry as {@link DataCodecRegistryBuilder}, but resolves the entity registry and the data
 * serde registry through providers, when a codec is first asked for, rather than receiving them. The MongoDB configurations receive the
 * builder, and the retained clients are built from the configurations: a builder that received the entity registry
 * made them dependents of it, so that the development reloader of Micronaut Data, which recreates the entity registry
 * when an entity changes in place, destroyed the configurations and the retained driver clients with it. Through
 * providers the configurations and the retained clients are kept; Micronaut MongoDB's reloader recreates the client
 * beans of the generation, which build their codec registry again with this builder, from the current entity registry,
 * when a document class changes.</p>
 *
 * <p>Without Micronaut MongoDB's development support, a driver client is built with the codec registry itself, and
 * caches codecs by class. The production builder is then kept, so that recreating the entity registry recreates the
 * clients built from it.</p>
 *
 * @author graemerocher
 * @since 5.3.0
 */
@Prototype
@Internal
@DevelopmentActive
@Requires(condition = DevelopmentDataCodecRegistryBuilder.RetainedMongoClients.class)
@Replaces(DataCodecRegistryBuilder.class)
final class DevelopmentDataCodecRegistryBuilder implements CodecRegistryBuilder {

    private final Environment environment;
    private final BeanProvider<DataSerdeRegistry> dataSerdeRegistry;
    private final BeanProvider<RuntimeEntityRegistry> runtimeEntityRegistry;

    DevelopmentDataCodecRegistryBuilder(Environment environment,
                                        BeanProvider<DataSerdeRegistry> dataSerdeRegistry,
                                        BeanProvider<RuntimeEntityRegistry> runtimeEntityRegistry) {
        this.environment = environment;
        this.dataSerdeRegistry = dataSerdeRegistry;
        this.runtimeEntityRegistry = runtimeEntityRegistry;
    }

    @Override
    public CodecRegistry build(AbstractMongoConfiguration configuration) {
        return new DeferredCodecRegistry(() -> new DataCodecRegistryBuilder(environment, dataSerdeRegistry.get(), runtimeEntityRegistry.get()).build(configuration));
    }

    /**
     * The registry that the production builder builds, built when a codec is first asked for, rather than as the
     * configuration builds its settings. A retained client is created from those settings, and what is resolved
     * while it is created is recorded as a dependency of it: the entity registry would then keep the retained client
     * from being retained across a restart, and recreating the entity registry in place would destroy it. A retained
     * client never asks this registry for a codec, since it uses the default registry of the driver; the client of a
     * generation asks it on its first operation.
     */
    private static final class DeferredCodecRegistry implements CodecRegistry {

        private final Supplier<CodecRegistry> supplier;
        private volatile @Nullable CodecRegistry delegate;

        DeferredCodecRegistry(Supplier<CodecRegistry> supplier) {
            this.supplier = supplier;
        }

        private CodecRegistry delegate() {
            CodecRegistry registry = delegate;
            if (registry == null) {
                synchronized (this) {
                    registry = delegate;
                    if (registry == null) {
                        registry = supplier.get();
                        delegate = registry;
                    }
                }
            }
            return registry;
        }

        @Override
        public <T> Codec<T> get(Class<T> clazz) {
            return delegate().get(clazz);
        }

        @Override
        public <T> Codec<T> get(Class<T> clazz, List<Type> typeArguments) {
            return delegate().get(clazz, typeArguments);
        }

        @Override
        public <T> @Nullable Codec<T> get(Class<T> clazz, CodecRegistry registry) {
            return delegate().get(clazz, registry);
        }

        @Override
        public <T> @Nullable Codec<T> get(Class<T> clazz, List<Type> typeArguments, CodecRegistry registry) {
            return delegate().get(clazz, typeArguments, registry);
        }
    }

    /**
     * Whether Micronaut MongoDB retains the driver clients in development mode, and applies the codec registry of each
     * generation per operation.
     */
    static final class RetainedMongoClients implements Condition {

        private static final String GENERATION_CODEC_REGISTRY = "io.micronaut.configuration.mongo.core.dev.GenerationCodecRegistry";

        @Override
        public boolean matches(ConditionContext context) {
            return ClassUtils.isPresent(GENERATION_CODEC_REGISTRY, context.getBeanContext().getClassLoader());
        }
    }
}
