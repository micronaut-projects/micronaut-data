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
package io.micronaut.data.nitrite.serde

import io.micronaut.core.type.Argument
import io.micronaut.serde.Decoder
import io.micronaut.serde.Encoder
import org.locationtech.jts.geom.Geometry
import org.locationtech.jts.io.WKTReader
import spock.lang.Specification

/**
 * The JTS codec encodes to WKT and reports a WKT the reader rejects as an {@link IOException},
 * so a malformed stored value surfaces as a decode failure rather than a JTS-specific exception.
 */
class GeometrySerdeSpec extends Specification {

    static final Argument<Geometry> GEOMETRY = Argument.of(Geometry)

    GeometrySerde serde = new GeometrySerde()

    void "a geometry round-trips through its WKT form"() {
        given:
        String encoded = null
        def encoder = [encodeString: { String value -> encoded = value }] as Encoder

        when:
        serde.serialize(encoder, null, GEOMETRY, new WKTReader().read("POINT (30 10)"))

        then:
        encoded == "POINT (30 10)"

        when:
        def decoder = [decodeString: { -> encoded }] as Decoder
        def decoded = serde.deserialize(decoder, null, GEOMETRY)

        then:
        decoded.coordinate.x == 30d
        decoded.coordinate.y == 10d
    }

    void "a stored value the WKT reader rejects is reported as a decode failure"() {
        given:
        def decoder = [decodeString: { -> "INVALID WKT" }] as Decoder

        when:
        serde.deserialize(decoder, null, GEOMETRY)

        then:
        def e = thrown(IOException)
        e.message == "Unable to deserialize JTS Geometry from WKT"
    }
}
