package io.micronaut.data.runtime.mapper.sql

import io.micronaut.data.model.vector.search.ScoringFunction
import io.micronaut.data.runtime.mapper.ResultReader
import spock.lang.Specification

class SearchResultsMapperSpec extends Specification {

    def "maps normalized similarity when scoring function is provided"() {
        given:
        def entityMapper = Mock(SqlTypeMapper<Map, String>)
        def resultReader = Mock(ResultReader<Map, String>)
        def mapper = new SearchResultsMapper<Map, String>(entityMapper, resultReader, "mn_score", ScoringFunction.COSINE)

        and:
        entityMapper.hasNext(_ as Map) >>> [true, false]
        entityMapper.map(_ as Map, String) >> "doc-1"
        resultReader.getRequiredValue(_ as Map, "mn_score", Double) >> 0d

        when:
        def results = mapper.mapAll([:] as Map, String)

        then:
        results.results().size() == 1
        results.results().first().score().value() == 0d
        results.results().first().similarity() != null
        results.results().first().similarity().value() == 1d
    }

    def "keeps similarity null when scoring function is absent"() {
        given:
        def entityMapper = Mock(SqlTypeMapper<Map, String>)
        def resultReader = Mock(ResultReader<Map, String>)
        def mapper = new SearchResultsMapper<Map, String>(entityMapper, resultReader, "mn_score")

        and:
        entityMapper.hasNext(_ as Map) >>> [true, false]
        entityMapper.map(_ as Map, String) >> "doc-1"
        resultReader.getRequiredValue(_ as Map, "mn_score", Double) >> 0.25d

        when:
        def results = mapper.mapAll([:] as Map, String)

        then:
        results.results().size() == 1
        results.results().first().score().value() == 0.25d
        results.results().first().similarity() == null
    }

    def "null score is treated as zero before normalization"() {
        given:
        def entityMapper = Mock(SqlTypeMapper<Map, String>)
        def resultReader = Mock(ResultReader<Map, String>)
        def mapper = new SearchResultsMapper<Map, String>(entityMapper, resultReader, "mn_score", ScoringFunction.L2_EUCLIDEAN)

        and:
        entityMapper.hasNext(_ as Map) >>> [true, false]
        entityMapper.map(_ as Map, String) >> "doc-1"
        resultReader.getRequiredValue(_ as Map, "mn_score", Double) >> null

        when:
        def results = mapper.mapAll([:] as Map, String)

        then:
        results.results().first().score().value() == 0d
        results.results().first().similarity() != null
        results.results().first().similarity().value() == 1d
    }
}
