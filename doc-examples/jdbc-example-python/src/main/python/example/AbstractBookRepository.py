from jakarta.transaction import Transactional
from micronaut.data.jdbc.annotation import JdbcRepository
from micronaut.data.jdbc.runtime import JdbcOperations
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.repository import CrudRepository

from example.Book import Book


@JdbcRepository(dialect=Dialect.H2)
class AbstractBookRepository(CrudRepository[Book, int]):

    def __init__(self, jdbc_operations: JdbcOperations):
        self.jdbc_operations = jdbc_operations

    @Transactional
    def findByTitle(self, title: str) -> list[Book]:
        sql = "SELECT * FROM Book AS book WHERE book.title = ?"

        def query(statement):
            statement.setString(1, title)
            result_set = statement.executeQuery()
            return self.jdbc_operations.entityStream(result_set, Book).toList()

        return self.jdbc_operations.prepareStatement(sql, query)
