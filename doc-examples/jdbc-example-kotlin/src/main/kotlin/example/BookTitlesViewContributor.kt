package example

import io.micronaut.data.jdbc.config.JdbcSchemaContributor
import io.micronaut.data.model.PersistentEntity
import io.micronaut.data.runtime.schema.SchemaContext
import jakarta.inject.Singleton

// tag::contributor[]
@Singleton
class BookTitlesViewContributor : JdbcSchemaContributor { // <1>

    override fun buildCreateStatements(context: SchemaContext, entities: List<PersistentEntity>): List<String> {
        if (context.entities().none { it.simpleName == "Book" }) { // <2>
            return emptyList()
        }
        return listOf("CREATE VIEW book_titles AS SELECT id, title FROM book") // <3>
    }

    override fun buildDropStatements(context: SchemaContext, entities: List<PersistentEntity>): List<String> =
        listOf("DROP VIEW IF EXISTS book_titles") // <4>
}
// end::contributor[]
