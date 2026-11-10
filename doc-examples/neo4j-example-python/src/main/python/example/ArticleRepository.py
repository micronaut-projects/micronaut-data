from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository

from example.Article import Article


@Neo4jRepository
class ArticleRepository(CrudRepository[Article, str]):

    def findByAuthor(self, author: str) -> list[Article]: ...
