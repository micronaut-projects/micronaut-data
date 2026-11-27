package io.micronaut.data.graph.runtime.age

import io.micronaut.json.JsonMapper
import spock.lang.Specification

class AgeParametersSpec extends Specification {

    void "write parameters as JSON with Micronaut Serialization"() {
        given:
        def jsonMapper = JsonMapper.createDefault()

        expect:
        AgeParameters.write(jsonMapper, [p0: "it's \"quoted\"", p1: 2L, p2: [1, 2], p3: null, p4: true, p5: [k: 1.5d]]) ==
            '{"p0":"it\'s \\"quoted\\"","p1":2,"p2":[1,2],"p3":null,"p4":true,"p5":{"k":1.5}}'
    }
}
