/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.core.reflect.ClassUtils;
import org.bson.codecs.ByteArrayCodec;
import org.bson.codecs.Codec;

import java.util.Map;

/**
 * The Codec utils class.
 */
@Internal
final class CodecUtils {

    private CodecUtils() {
    }

    /**
     * Determines whether a given Codec should be used based on its encoder class.
     * A Codec will not be considered for use if its encoder class is one of the following:
     * - Basic types except byte arrays
     * - Collections (maps or iterables)
     *
     * @param <T> the type parameter representing the generic type of the Codec
     * @param codec the Codec object whose compatibility needs to be checked
     * @return true if the Codec should be used, false otherwise
     */
    static <T> boolean shouldUseCodec(Codec<? extends T> codec) {
        // Eliminate codecs for basic types (except byte array) and collections
        if (codec instanceof ByteArrayCodec) {
            return true;
        } else {
            Class<? extends T> encoderClass = codec.getEncoderClass();
            return !ClassUtils.isJavaLangType(encoderClass)
                && !Map.class.isAssignableFrom(encoderClass)
                && !Iterable.class.isAssignableFrom(encoderClass);
        }
    }
}
