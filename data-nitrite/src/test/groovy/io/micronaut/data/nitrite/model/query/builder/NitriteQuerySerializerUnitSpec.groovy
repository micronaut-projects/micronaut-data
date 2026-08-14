package io.micronaut.data.nitrite.model.query.builder

import spock.lang.Specification

class NitriteQuerySerializerUnitSpec extends Specification {

    void "serializer escapes backslashes before quote characters"() {
        given:
        def expected = "'" + "C:" + "\\" + "\\" + "Users" + "\\" + "\\" + "O" + "\\" + "'" + "Reilly" + "'"

        expect:
        NitriteQuerySerializer.toJsonString("C:\\Users\\O'Reilly") == expected
    }
}
