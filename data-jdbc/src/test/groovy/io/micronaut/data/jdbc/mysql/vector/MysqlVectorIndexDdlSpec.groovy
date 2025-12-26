package io.micronaut.data.jdbc.mysql.vector

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
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
 * Spock spec verifying MySQL HeatWave DDL for @VectorIndex.
 */
class MysqlVectorIndexDdlSpec extends Specification {

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

    def "generates MySQL vector index on embedding"() {
        given:
        def builder = new SqlQueryBuilder(Dialect.MYSQL)
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
            s.toLowerCase().contains("embedding")
        }
    }
}
