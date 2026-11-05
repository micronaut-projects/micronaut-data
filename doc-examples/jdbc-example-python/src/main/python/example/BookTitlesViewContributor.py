from jakarta.inject import Singleton
from micronaut.data.jdbc.config import JdbcSchemaContributor
from micronaut.data.model import PersistentEntity
from micronaut.data.runtime.schema import SchemaContext


# tag::contributor[]
@Singleton
class BookTitlesViewContributor(JdbcSchemaContributor):  # <1>

    def buildCreateStatements(self, context: SchemaContext, entities: list[PersistentEntity]) -> list[str]:
        if not any(entity.getSimpleName() == "Book" for entity in context.entities()):  # <2>
            return []
        return ["CREATE VIEW book_titles AS SELECT id, title FROM book"]  # <3>

    def buildDropStatements(self, context: SchemaContext, entities: list[PersistentEntity]) -> list[str]:
        return ["DROP VIEW IF EXISTS book_titles"]  # <4>
# end::contributor[]
