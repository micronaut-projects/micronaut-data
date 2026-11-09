package io.micronaut.data.search.elasticsearch.memory

import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.search.TextSearchSupport
import io.micronaut.data.search.document.init.SearchIndexDefinitions
import spock.lang.Specification

class SearchLanguageMappingSpec extends Specification {

    void "a document store takes a custom analyzer name as the entity language"() {
        given:
        def entity = new RuntimePersistentEntity<MappingOnlyEntities.FoldedNote>(MappingOnlyEntities.FoldedNote)

        expect: "only the SQL dialects restrict the name, as they render it inside the statement"
        TextSearchSupport.resolveLanguage(entity) == "my-folding"
        SearchIndexDefinitions.mappingFor(entity).contains('"text":{"type":"text","analyzer":"my-folding"')
    }
}
