package io.micronaut.data.jdbc.postgres.vector

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder
import io.micronaut.data.model.runtime.RuntimePersistentEntity
import io.micronaut.data.model.vector.FloatVector
import io.micronaut.data.model.vector.Vector
import io.micronaut.data.model.runtime.convert.vector.impl.DefaultFloatVectorAttributeConverter
import jakarta.persistence.Column
import spock.lang.Specification

class PostgresJdbcVectorColumnLengthSpec extends Specification {

    void "builder emits vector(3) when @Column(length=3) on FloatVector property for Postgres"() {
        given:
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.POSTGRES)
        def entity = new RuntimePersistentEntity(VectorLenAnnoDoc)
        def columnDefinitionProviders = List.of(new DefaultFloatVectorAttributeConverter(Collections.emptyList()))

        when:
        def statements = builder.buildCreateTableStatements(entity, columnDefinitionProviders)

        then:
        // assert any of the emitted statements contains vector(3)
        statements.stream().anyMatch { s -> s.toLowerCase(Locale.ROOT).contains("vector(3)") }
    }
}

@MappedEntity("vector_len_anno_doc_builder")
class VectorLenAnnoDoc {
    @Id
    @GeneratedValue
    Long id

    // Drives DDL dimension for Postgres pgvector: vector(3)
    @Column(length = 3)
    FloatVector embedding

    Long getId() { return id }
    void setId(Long id) { this.id = id }

    FloatVector getEmbedding() { return embedding }
    void setEmbedding(FloatVector embedding) { this.embedding = embedding }
}
