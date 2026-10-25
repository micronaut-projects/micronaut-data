package io.micronaut.data.search.elasticsearch

import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.search.document.init.SearchIndexDefinitions
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
        body.contains('"title":{"type":"text","analyzer":"folded","fielddata":true}')
        body.contains('"isbn":{"type":"keyword","ignore_above":64}')
        body.contains('"id":{"type":"keyword"}')
    }

    void "an autocomplete field keeps the keyword sub-field that exact matches and sorts use"() {
        when:
        def body = SearchIndexDefinitions.mappingFor(new RuntimePersistentEntity<io.micronaut.data.search.elasticsearch.memory.Recipe>(io.micronaut.data.search.elasticsearch.memory.Recipe), null)

        then:
        body.contains('"name":{"type":"search_as_you_type","analyzer":"standard","fields":{"keyword":{')
        body.contains('"cuisine":{"type":"keyword"}')
    }
}
