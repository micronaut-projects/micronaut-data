package io.micronaut.data.jdbc.oraclexe.vector

import io.micronaut.core.annotation.Nullable
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
import io.micronaut.data.model.vector.ByteVector
import io.micronaut.data.model.vector.Vector
import spock.lang.Specification

import java.util.Locale

/**
 * Spock spec verifying Oracle XE VECTOR index DDL for @VectorIndex.
 */
class OracleXeVectorIndexDdlSpec extends Specification {

    @MappedEntity("document_embedding")
    static class DocumentEmbeddingTestEntity {
        Long id

        @VectorStorage(length = 3)
        @VectorIndex(
            vectorIndexType = VectorIndexType.IVF,
            distanceType = VectorIndexType.DistanceType.COSINE,
            accuracy = 90
        )
        Vector embedding
    }

    @MappedEntity("document_embedding_default")
    static class DocumentEmbeddingDefaultEntity {
        Long id
        Vector embedding

        @Nullable
        Vector nullableEmbedding
    }

    @MappedEntity("document_embedding_sparse")
    static class DocumentEmbeddingSparseEntity {
        Long id

        @VectorStorage(length = 5, shape = VectorShape.SPARSE)
        ByteVector embedding
    }

    def "generates Oracle VECTOR index with cosine and accuracy"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingTestEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        String[] statements = builder.buildCreateTableStatements(entity, providers)
        ctx.close()

        then:
        statements.any { s ->
            s.contains("CREATE VECTOR INDEX") &&
            s.contains("ORGANIZATION NEIGHBOR PARTITIONS") && // IVF -> PARTITIONS
            s.contains("DISTANCE COSINE") &&
            s.contains("WITH TARGET ACCURACY 90")
        }
    }

    def "generates Oracle VECTOR(*, FLOAT64) when length annotation is absent"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingDefaultEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        String[] statements = builder.buildCreateTableStatements(entity, providers)
        ctx.close()

        then:
        def createTable = statements.find { it.startsWith("CREATE TABLE") }
        def sql = createTable.toUpperCase(Locale.ROOT)
        sql.contains('"EMBEDDING" VECTOR(*,FLOAT64) NOT NULL')
        sql.contains('"NULLABLE_EMBEDDING" VECTOR(*,FLOAT64)')
        !sql.contains('"NULLABLE_EMBEDDING" VECTOR(*,FLOAT64) NOT NULL')
    }

    def "generates Oracle VECTOR with SPARSE storage"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.ORACLE)
        def entity = new RuntimePersistentEntity<>(DocumentEmbeddingSparseEntity.class)

        when:
        ApplicationContext ctx = ApplicationContext.run()
        List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
        String[] statements = builder.buildCreateTableStatements(entity, providers)
        ctx.close()

        then:
        statements.any { s ->
            def u = s.toUpperCase(Locale.ROOT)
            (u.contains("VECTOR(5,") && u.contains("SPARSE"))
        }
    }

    def "the vector index gets IF NOT EXISTS on Oracle 23ai, like the other schema statements"() {
        when:
        String[] statements = statementsFor(DocumentEmbeddingTestEntity, version)

        then:
        statements.findAll { it.startsWith('CREATE VECTOR INDEX ') }*.startsWith('CREATE VECTOR INDEX IF NOT EXISTS ') == [ifNotExists]

        where:
        version | ifNotExists
        null    | false
        '23.26' | true
    }

    private static String[] statementsFor(Class<?> type, String version) {
        ApplicationContext ctx = ApplicationContext.run()
        try {
            List<DefinitionProvider> providers = new ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider))
            providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider))
            return new SqlQueryBuilder(Dialect.ORACLE, version).buildCreateTableStatements(new RuntimePersistentEntity<>(type), providers)
        } finally {
            ctx.close()
        }
    }
}
