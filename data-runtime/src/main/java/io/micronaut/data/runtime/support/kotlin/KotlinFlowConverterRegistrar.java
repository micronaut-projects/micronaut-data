/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.data.runtime.support.kotlin;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.TypeConverterRegistrar;
import jakarta.inject.Singleton;
import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.reactive.ReactiveFlowKt;
import org.reactivestreams.Publisher;

import java.util.Optional;

/**
 * Type converter for {@link Flow}.
 *
 * @author Denis Stepanov
 * @since 3.1
 */
@Singleton
@Internal
@Requires(classes = {Flow.class, ReactiveFlowKt.class})
class KotlinFlowConverterRegistrar implements TypeConverterRegistrar {
    @Override
    public void register(ConversionService<?> conversionService) {
        if (conversionService.canConvert(Publisher.class, Flow.class)) {
            return;
        }
        Publishers.registerReactiveType(Flow.class);
        conversionService.addConverter(
                Publisher.class,
                Flow.class,
                (publisher, targetType, context) -> Optional.of(ReactiveFlowKt.asFlow(publisher))
        );
        conversionService.addConverter(
                Flow.class,
                Publisher.class,
                (flow, targetType, context) -> Optional.of(ReactiveFlowKt.asPublisher(flow))
        );
    }
}
