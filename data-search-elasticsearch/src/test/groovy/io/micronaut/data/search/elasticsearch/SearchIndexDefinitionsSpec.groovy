package io.micronaut.data.search.elasticsearch

import io.micronaut.core.type.Argument
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.search.document.init.SearchIndexDefinitions
import io.micronaut.json.JsonMapper
import spock.lang.Specification

/**
 * The create-index body of an entity with {@code @SearchIndexSettings} and {@code @SearchMapping}.
 */
class SearchIndexDefinitionsSpec extends Specification {

    void "the declared settings and field mappings replace the generated ones"() {
        when:
        def body = SearchIndexDefinitions.mappingFor(new RuntimePersistentEntity<TunedBook>(TunedBook), null)

        then:
        body.contains('"settings":{"number_of_shards":2,"analysis":{"analyzer":{"folded":{"tokenizer":"standard","filter":["lowercase","asciifolding"]}}}}')
        // the keyword sub-field the derived equality, range and sort queries address is kept
        mappedFields(body).title == [type: "text", analyzer: "folded", fielddata: true, fields: [keyword: [type: "keyword", ignore_above: 8191]]]
        body.contains('"isbn":{"type":"keyword","ignore_above":64}')
        body.contains('"id":{"type":"keyword"}')
    }

    void "a declared mapping of an analysed field gets the keyword sub-field unless it declares one"() {
        when:
        def body = SearchIndexDefinitions.mappingFor(new RuntimePersistentEntity<KeywordMappedBook>(KeywordMappedBook), null)

        then: "a keyword sub-field is added next to the declared sub-fields"
        mappedFields(body).title == [type: "text", fields: [raw: [type: "keyword"], keyword: [type: "keyword", ignore_above: 8191]]]

        and: "a declared keyword sub-field is kept as it is"
        mappedFields(body).subtitle == [type: "text", fields: [keyword: [type: "keyword", ignore_above: 100]]]

        and: "a keyword search field has no sub-field, as it is matched itself"
        mappedFields(body).code == [type: "keyword", ignore_above: 64]
    }

    private static Map mappedFields(String body) {
        Map<String, Object> parsed = JsonMapper.createDefault().readValue(body, Argument.mapOf(String, Object))
        return ((Map<String, Object>) parsed.get("mappings")).get("properties") as Map
    }

    void "declared settings are merged into the store's defaults object by object"() {
        given:
        def transport = Stub(io.micronaut.data.search.document.runtime.SearchTransport) {
            indexSettings() >> [analysis: [filter: [shingles: [type: "shingle"]]]]
        }

        when:
        def body = SearchIndexDefinitions.mappingFor(new RuntimePersistentEntity<TunedBook>(TunedBook), transport)

        then:
        body.contains('"settings":{"analysis":{"filter":{"shingles":{"type":"shingle"}},"analyzer":{"folded":{"tokenizer":"standard","filter":["lowercase","asciifolding"]}}},"number_of_shards":2}')
    }
}
