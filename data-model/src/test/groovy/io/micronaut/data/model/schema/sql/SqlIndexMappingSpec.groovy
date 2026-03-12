package io.micronaut.data.model.schema.sql

import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider
import io.micronaut.data.model.schema.sql.metadata.VectorIndexMetadata
import spock.lang.Specification

class SqlIndexMappingSpec extends Specification {

    void "constructors populate optional fields"() {
        given:
        def provider = Stub(SqlIndexDefinitionProvider)

        when:
        def plain = new SqlIndexMapping('idx_plain', false, ['a'] as String[])
        def providerOnly = new SqlIndexMapping('idx_provider', true, ['b'] as String[], provider)
        def vector = new SqlIndexMapping('idx_vector', false, ['c'] as String[], provider,
            new VectorIndexMetadata(VectorIndexType.HNSW, VectorIndexType.DistanceType.COSINE, 90, true))

        then:
        plain.sqlIndexDefinitionProvider() == null
        plain.vectorIndexMetadata() == null
        providerOnly.sqlIndexDefinitionProvider().is(provider)
        providerOnly.vectorIndexMetadata() == null
        vector.vectorIndexMetadata().sparse()
    }

    void "equals and hashCode compare arrays by content"() {
        given:
        def metadata = new VectorIndexMetadata(VectorIndexType.HNSW, VectorIndexType.DistanceType.L2_EUCLIDEAN, 80, false)

        expect:
        new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata) ==
            new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata)
        new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata).hashCode() ==
            new SqlIndexMapping('idx', true, ['a', 'b'] as String[], null, metadata).hashCode()
    }

    void "toString contains key fields"() {
        expect:
        new SqlIndexMapping('idx_name', true, ['c1', 'c2'] as String[]).toString().contains("name='idx_name'")
    }
}
