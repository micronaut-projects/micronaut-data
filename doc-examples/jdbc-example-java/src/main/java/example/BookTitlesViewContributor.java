package example;

import io.micronaut.data.jdbc.config.JdbcSchemaContributor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.runtime.schema.SchemaContext;
import jakarta.inject.Singleton;

import java.util.List;

// tag::contributor[]
@Singleton
public class BookTitlesViewContributor implements JdbcSchemaContributor { // <1>

    @Override
    public List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
        if (context.entities().stream().noneMatch(entity -> entity.getSimpleName().equals("Book"))) { // <2>
            return List.of();
        }
        return List.of("CREATE VIEW book_titles AS SELECT id, title FROM book"); // <3>
    }

    @Override
    public List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
        return List.of("DROP VIEW IF EXISTS book_titles"); // <4>
    }
}
// end::contributor[]
