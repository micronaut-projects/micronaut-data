package io.micronaut.data.jdbc.oraclexe.binaryvector

import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.VectorIndex
import io.micronaut.data.annotation.VectorIndexType
import io.micronaut.data.annotation.VectorStorage
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.runtime.convert.DefinitionProvider
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider
import io.micronaut.data.model.vector.BinaryVector
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class OracleBinaryVectorDdlSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run()

    @MappedEntity("binary_hamming_doc")
    static class HammingDoc {
        Long id

        @VectorStorage(length = 1024)
        @VectorIndex(vectorIndexType = VectorIndexType.HNSW, distanceType = VectorIndexType.DistanceType.HAMMING)
        BinaryVector embedding
    }

    @MappedEntity("binary_jaccard_doc")
    static class JaccardDoc {
        Long id

        @VectorIndex(distanceType = VectorIndexType.DistanceType.JACCARD)
        BinaryVector embedding
    }

    @MappedEntity("binary_default_index_doc")
    static class DefaultIndexDoc {
        Long id

        @VectorStorage(length = 16)
        @VectorIndex
        BinaryVector embedding
    }

    @MappedEntity("binary_plain_doc")
    static class PlainDoc {
        Long id

        @VectorStorage(length = 64)
        BinaryVector embedding
    }

    void "generates a BINARY vector column and a HAMMING index"() {
        when:
        String[] statements = createStatements(Dialect.ORACLE, HammingDoc)

        then:
        statements.find { it.startsWith("CREATE TABLE") }.contains('"EMBEDDING" VECTOR(1024,BINARY) NOT NULL')
        statements.any { it.contains("CREATE VECTOR INDEX") && it.contains("DISTANCE HAMMING") }
    }

    void "a vector index without a distance uses HAMMING for a binary vector"() {
        when:
        String[] statements = createStatements(Dialect.ORACLE, DefaultIndexDoc)

        then: "Oracle rejects COSINE for binary vectors with ORA-51910"
        statements.any { it.contains("CREATE VECTOR INDEX") && it.contains("DISTANCE HAMMING") }
        !statements.any { it.contains("DISTANCE COSINE") }
    }

    void "generates an unsized BINARY vector column and a JACCARD index"() {
        when:
        String[] statements = createStatements(Dialect.ORACLE, JaccardDoc)

        then:
        statements.find { it.startsWith("CREATE TABLE") }.contains('"EMBEDDING" VECTOR(*,BINARY) NOT NULL')
        statements.any { it.contains("CREATE VECTOR INDEX") && it.contains("DISTANCE JACCARD") }
    }

    void "falls back to VARCHAR(255) on databases other than Oracle, like other vector types"() {
        when:
        String createTable = createStatements(dialect, PlainDoc).find { it.startsWith("CREATE TABLE") }

        then:
        createTable.contains("VARCHAR(255)")
        !createTable.contains("BINARY")

        where:
        dialect << [Dialect.POSTGRES, Dialect.MYSQL, Dialect.H2]
    }

    private String[] createStatements(Dialect dialect, Class<?> entityType) {
        List<DefinitionProvider> providers = new ArrayList<>(context.getBeansOfType(SqlColumnDefinitionProvider))
        providers.addAll(context.getBeansOfType(SqlIndexDefinitionProvider))
        return new SqlQueryBuilder(dialect).buildCreateTableStatements(new RuntimePersistentEntity<>(entityType), providers)
    }
}
