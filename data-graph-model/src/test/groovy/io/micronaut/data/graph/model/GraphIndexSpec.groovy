package io.micronaut.data.graph.model

import io.micronaut.data.graph.model.query.builder.entities.Catalogue
import io.micronaut.data.graph.model.query.builder.entities.Note
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import spock.lang.Specification

import java.nio.charset.StandardCharsets

class GraphIndexSpec extends Specification {

    void "a generated name past the 63 bytes of a PostgreSQL identifier is shortened with a hash"() {
        when:
        def indexes = GraphIndex.of(new RuntimePersistentEntity<>(Catalogue))
        def country = indexes.find { it.properties().size() == 3 && it.properties().contains('publisherCountryCode') }
        def region = indexes.find { it.properties().size() == 3 && it.properties().contains('publisherRegionCode') }

        then: "the names stay readable, within the limit and distinct"
        country.name() ==~ /Catalogue_firstAuthorFamilyName_firstAuthorGiven_[0-9a-f]{8}_index/
        length(country.name()) == 63
        region.name() != country.name()
        length(region.name()) <= 63

        and: "the same index gets the same name on every start"
        GraphIndex.of(new RuntimePersistentEntity<>(Catalogue)) == indexes
    }

    void "a name a character over the limit is not cut in the middle of a character"() {
        when:
        String name = GraphIndex.generatedName("Etiquettes", ["\u00e9".repeat(60)], false)

        then:
        length(name) == 62
        new String(name.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8) == name
        name.contains("\u00e9")
        !name.contains("\ufffd")
    }

    void "a generated name within the limit and a declared name are kept"() {
        expect:
        GraphIndex.of(new RuntimePersistentEntity<>(Note))*.name() == ['Note_code_unique', 'Note_text_index']
        GraphIndex.of(new RuntimePersistentEntity<>(Catalogue))*.name().contains('a_name_that_is_declared')
    }

    void "an index on the version property is created"() {
        expect:
        GraphIndex.of(new RuntimePersistentEntity<>(Catalogue)).find { it.properties() == ['version'] }?.name() == 'Catalogue_version_index'
    }

    private static int length(String name) {
        name.getBytes(StandardCharsets.UTF_8).length
    }
}
