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
package io.micronaut.data.nitrite.serde;

import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.type.Argument;
import io.micronaut.serde.Decoder;
import io.micronaut.serde.Deserializer;
import io.micronaut.serde.Encoder;
import io.micronaut.serde.Serializer;
import jakarta.inject.Singleton;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.locationtech.jts.io.WKTWriter;

import java.io.IOException;

/**
 * Micronaut Serde codec for JTS Geometry values.
 */
@Singleton
@Internal
@Requires(classes = Geometry.class)
public final class GeometrySerde implements Serializer<Geometry>, Deserializer<Geometry> {

    private final WKTReader reader = new WKTReader();
    private final WKTWriter writer = new WKTWriter();

    @Override
    public void serialize(Encoder encoder, EncoderContext context, Argument<? extends Geometry> type, Geometry value) throws IOException {
        encoder.encodeString(writer.write(value));
    }

    @Override
    public Geometry deserialize(Decoder decoder, DecoderContext context, Argument<? super Geometry> type) throws IOException {
        try {
            return reader.read(decoder.decodeString());
        } catch (ParseException e) {
            throw new IOException("Unable to deserialize JTS Geometry from WKT", e);
        }
    }
}
