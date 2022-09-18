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
package io.micronaut.data.cosmos.operations;

import io.micronaut.core.type.Argument;
import io.micronaut.data.document.serde.OneRelationDeserializer;
import io.micronaut.serde.Decoder;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Default {@link io.micronaut.data.document.serde.OneRelationDeserializer} implementation.
 */
@Singleton
final class DefaultOneRelationDeserializer implements OneRelationDeserializer {

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        return null;
    }
}
