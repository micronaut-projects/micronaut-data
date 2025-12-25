package example;

import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.query.builder.sql.SqlQueryBuilder;
import io.micronaut.data.model.runtime.RuntimePersistentEntity;
import io.micronaut.context.ApplicationContext;
import io.micronaut.data.model.runtime.convert.DefinitionProvider;
import io.micronaut.data.model.runtime.convert.SqlColumnDefinitionProvider;
import io.micronaut.data.model.runtime.convert.SqlIndexDefinitionProvider;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the Oracle DDL generated for the DocumentEmbedding entity includes
 * the VECTOR index type and the provided options (algorithm + parameters).
 */
class DocumentEmbeddingOracleDdlSpec {

    @Test
    void oracleVectorIndexOptionsPresentInDdl() {
        // Build DDL for the entity using the SQL query builder with Oracle dialect
        SqlQueryBuilder builder = new SqlQueryBuilder(Dialect.ORACLE);
        RuntimePersistentEntity<DocumentEmbedding> entity = new RuntimePersistentEntity<>(DocumentEmbedding.class);
        try (ApplicationContext ctx = ApplicationContext.run()) {
            java.util.List<DefinitionProvider> providers = new java.util.ArrayList<>(ctx.getBeansOfType(SqlColumnDefinitionProvider.class));
            providers.addAll(ctx.getBeansOfType(SqlIndexDefinitionProvider.class));
            String[] statements = builder.buildCreateTableStatements(entity, providers);

            // Expect an index using Oracle vector syntax with our configured options
            assertTrue(
                Arrays.stream(statements).anyMatch(s ->
                    s.contains("CREATE VECTOR INDEX")
                        && s.contains("ORGANIZATION NEIGHBOR PARTITIONS")
                        && s.contains("DISTANCE COSINE")
                        && s.contains("WITH TARGET ACCURACY 90")
                ),
                "Expected Oracle VECTOR index with HNSW options in generated DDL, but not found. Statements were: " + String.join("\n", statements)
            );
        }
    }
}
