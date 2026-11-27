from jakarta.inject import Singleton
from micronaut.data.jdbc.config import JdbcSchemaContributor
from micronaut.data.model import PersistentEntity
from micronaut.data.runtime.schema import SchemaContext


# tag::contributor[]
@Singleton
class BookTitlesViewContributor(JdbcSchemaContributor):  # <1>

    def buildCreateStatements(self, context: SchemaContext, entities: list[PersistentEntity]) -> list[str]:
        if not self.hasBookTable(context):  # <2>
            return []
        return ["CREATE VIEW book_titles AS SELECT id, title FROM book"]  # <3>

    def buildDropStatements(self, context: SchemaContext, entities: list[PersistentEntity]) -> list[str]:
        if not self.hasBookTable(context):
            return []
        return ["DROP VIEW IF EXISTS book_titles"]  # <4>

    def hasBookTable(self, context: SchemaContext) -> bool:
        return any(entity.getSimpleName() == "Book" for entity in context.entities())
# end::contributor[]
