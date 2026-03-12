package io.micronaut.data.model.runtime.convert

import spock.lang.Specification

class DefinitionTypeSpec extends Specification {

    void "definition type enum exposes expected constants"() {
        expect:
        DefinitionType.values().toList() == [DefinitionType.COLUMN, DefinitionType.INDEX]
        DefinitionType.valueOf('COLUMN') == DefinitionType.COLUMN
        DefinitionType.valueOf('INDEX') == DefinitionType.INDEX
    }
}
