package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.data.model.vector.BinaryVector
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorShape
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.convert.DefinitionProvider
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider
import io.micronaut.data.model.vector.Vector
import jakarta.persistence.Column
import spock.lang.Specification

/**
 * Spock spec verifying Postgres (pgvector) DDL for @VectorIndex.
 */
class PostgresVectorIndexDdlSpec extends Specification {

    @MappedEntity("document_embedding")
    static class DocumentEmbeddingTestEntity {
        Long id

        @Column(length = 3)
        @VectorIndex(
            vectorIndexType = VectorIndexType.IVF,
            distanceType = VectorIndexType.DistanceType.COSINE,
            accuracy = 90
        )
        Vector embedding
    }

    @MappedEntity("document_embedding_sparse")
    static class DocumentEmbeddingSparseEntity {
        Long id

        @VectorStorage(length = 3, shape = VectorShape.SPARSE)
        @VectorIndex(
            vectorIndexType = VectorIndexType.HNSW,
            distanceType = VectorIndexType.DistanceType.COSINE,
            accuracy = 90
        )
        Vector embedding
    }

    @MappedEntity("document_embedding_sparse_ivf")
    static class DocumentEmbeddingSparseIvfEntity {
        Long id

        @VectorStorage(length = 3, shape = VectorShape.SPARSE)
        @VectorIndex(
            vectorIndexType = VectorIndexType.IVF,
            distanceType = VectorIndexType.DistanceType.COSINE,
            accuracy = 90
        )
        Vector embedding
    }

    def "generates pgvector index with cosine ops"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingTestEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        String[] statements = builder.buildCreateTableStatements(entity, providers)
        ctx.close()

        then:
        statements.any { s ->
            s.contains("CREATE INDEX") &&
            s.toLowerCase().contains("using ivfflat") &&
            s.contains("vector_cosine_ops")
        }
    }

    def "generates sparsevec column and sparsevec index ops when sparse storage is requested"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingSparseEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        String[] statements = builder.buildCreateTableStatements(entity, providers)
        ctx.close()

        then:
        statements.any { s ->
            s.toLowerCase().contains("sparsevec(3)")
        }
        statements.any { s ->
            s.contains("CREATE INDEX") &&
                s.toLowerCase().contains("using hnsw") &&
                s.contains("sparsevec_cosine_ops")
        }
    }

    def "fails for sparse vectors with ivfflat index"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingSparseIvfEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        builder.buildCreateTableStatements(entity, providers)

        then:
        def e = thrown(IllegalArgumentException)
        e.message.contains("HNSW indexes only")

        cleanup:
        ctx.close()
    }

    @MappedEntity("binary_embedding_hnsw")
    static class BinaryHnswEntity {
        Long id

        @VectorStorage(length = 16)
        @VectorIndex(vectorIndexType = VectorIndexType.HNSW)
        BinaryVector embedding
    }

    @MappedEntity("binary_embedding_jaccard")
    static class BinaryJaccardEntity {
        Long id

        @VectorStorage(length = 16)
        @VectorIndex(vectorIndexType = VectorIndexType.HNSW, distanceType = VectorIndexType.DistanceType.JACCARD)
        BinaryVector embedding
    }

    @MappedEntity("binary_embedding_jaccard_ivf")
    static class BinaryJaccardIvfEntity {
        Long id

        @VectorStorage(length = 16)
        @VectorIndex(vectorIndexType = VectorIndexType.IVF, distanceType = VectorIndexType.DistanceType.JACCARD)
        BinaryVector embedding
    }

    @MappedEntity("binary_embedding_unsized")
    static class BinaryUnsizedEntity {
        Long id

        BinaryVector embedding
    }

    def "binary vectors use bit columns and the bit operator classes"() {
        when:
        String[] statements = statementsFor(type)

        then:
        statements.any { it.contains(column) }
        statements.any { it.contains("CREATE INDEX") && it.toLowerCase().contains("using hnsw") && it.contains(operatorClass) }

        where:
        type                | column                    | operatorClass
        BinaryHnswEntity    | '"embedding" bit(16)'     | 'bit_hamming_ops'
        BinaryJaccardEntity | '"embedding" bit(16)'     | 'bit_jaccard_ops'
    }

    def "a binary vector without a length uses bit varying"() {
        expect:
        statementsFor(BinaryUnsizedEntity).any { it.contains('"embedding" bit varying') }
    }

    def "Jaccard distance needs an HNSW index"() {
        when:
        statementsFor(BinaryJaccardIvfEntity)

        then:
        IllegalArgumentException e = thrown()
        e.message.contains('HNSW')
    }

    private static String[] statementsFor(Class<?> type) {
        ApplicationContext ctx = ApplicationContext.run()
        try {
            List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
            providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
            return new SqlQueryBuilder(Dialect.POSTGRES).buildCreateTableStatements(new RuntimePersistentEntity<>(type), providers)
        } finally {
            ctx.close()
        }
    }
}
