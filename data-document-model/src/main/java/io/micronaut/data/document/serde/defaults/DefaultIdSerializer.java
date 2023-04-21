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
package io.micronaut.data.document.serde.defaults;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.IdSerializer;
import io.micronaut.serde.Serializer;
import io.micronaut.serde.exceptions.SerdeException;
import io.micronaut.serde.util.CustomizableSerializer;
import jakarta.inject.Singleton;

/**
 * Default {@link IdSerializer} implementation.
 *
 * @author radovanradic
 * @author Denis Stepanov
 * @since 3.9.0
 */
@Internal
@Singleton
final class DefaultIdSerializer implements IdSerializer, CustomizableSerializer<Object> {

    @Override
    public Serializer<Object> createSpecific(EncoderContext context, Argument<?> type) throws SerdeException {
        Serializer<? super Object> serializer = context.findSerializer(type);
        return serializer.createSpecific(context, type);
    }
}
