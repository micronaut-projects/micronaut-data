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
package io.micronaut.data.graph.runtime.serde;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.WrappedDecoder;
import io.micronaut.serde.exceptions.SerdeException;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Optional;

/**
 * Deserializes a value the database driver returns as a native Java object, such as a temporal or spatial value, by
 * converting it with the conversion service instead of reading its text form.
 *
 * @param conversionService The conversion service
 * @param fallback          The deserializer for decoders other than {@link GraphValueDecoder}, or null
 * @param unresolved        Why the serde registry has no deserializer for the type, or null when the type is read
 *                          natively by choice. A value that cannot be read fails with it as the cause
 * @author graemerocher
 * @since 5.3.0
 */
@Internal
record NativeValueDeserializer(ConversionService conversionService,
                               @Nullable Deserializer<Object> fallback,
                               @Nullable SerdeException unresolved) implements Deserializer<Object> {

    @Override
    public Object deserialize(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        Decoder unwrapped = WrappedDecoder.unwrap(decoder);
        if (!(unwrapped instanceof GraphValueDecoder graphDecoder)) {
            if (fallback == null) {
                throw failure(null, "No deserializer for " + type.getTypeName());
            }
            return fallback.deserialize(decoder, context, type);
        }
        Object value = graphDecoder.decodeNative();
        if (value == null) {
            throw failure(graphDecoder, "Expected a value of " + type.getTypeName() + " but found null");
        }
        if (type.getType().isInstance(value)) {
            return value;
        }
        Optional<?> converted = conversionService.convert(value, type);
        if (converted.isEmpty()) {
            // The value is not in the message: it is row data
            throw failure(graphDecoder, "Cannot convert a value of type " + value.getClass().getName() + " to " + type.getTypeName());
        }
        return converted.get();
    }

    /**
     * A failure to read a value natively, naming the property being read and the reason serde could not read the type.
     *
     * @param decoder The graph decoder, which knows the property, or null
     * @param message The message
     * @return The exception, with {@link #unresolved()} as its cause
     */
    private SerdeException failure(@Nullable GraphValueDecoder decoder, String message) {
        if (unresolved == null) {
            return decoder == null ? new SerdeException(message) : decoder.error(message, null);
        }
        String described = message + ": " + unresolved.getMessage();
        return decoder == null ? new SerdeException(described, unresolved) : decoder.error(described, unresolved);
    }

    @Override
    public @Nullable Object deserializeNullable(Decoder decoder, DecoderContext context, Argument<? super Object> type) throws IOException {
        if (decoder.decodeNull()) {
            return null;
        }
        return deserialize(decoder, context, type);
    }
}
