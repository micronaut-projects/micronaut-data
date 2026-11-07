package example

import io.micronaut.data.jdbc.config.JdbcSchemaContributor
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.runtime.schema.SchemaContext
import jakarta.inject.Singleton

// tag::contributor[]
@Singleton
class BookTitlesViewContributor implements JdbcSchemaContributor { // <1>

    @Override
    List<String> buildCreateStatements(SchemaContext context, List<PersistentEntity> entities) {
        if (!hasBookTable(context)) { // <2>
            return []
        }
        return ["CREATE VIEW book_titles AS SELECT id, title FROM book"] // <3>
    }

    @Override
    List<String> buildDropStatements(SchemaContext context, List<PersistentEntity> entities) {
        if (!hasBookTable(context)) {
            return []
        }
        return ["DROP VIEW IF EXISTS book_titles"] // <4>
    }

    private boolean hasBookTable(SchemaContext context) {
        return context.entities().any { it.simpleName == "Book" }
    }
}
// end::contributor[]
