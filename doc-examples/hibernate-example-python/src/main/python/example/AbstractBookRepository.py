from jakarta.persistence import EntityManager
from micronaut.data.annotation import Repository
from micronaut.data.repository import CrudRepository

from example.Book import Book


@Repository
class AbstractBookRepository(CrudRepository[Book, int]):

    def __init__(self, entity_manager: EntityManager):
        self.entity_manager = entity_manager

    def findByTitle(self, title: str) -> list[Book]:
        return (self.entity_manager.createQuery("FROM Book book WHERE book.title = :title", Book)
                .setParameter("title", title)
                .getResultList())
