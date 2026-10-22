from typing import Annotated, Callable

from jakarta.inject import Inject
from javax.sql import DataSource
from java.lang import System, Thread
from micronaut.data.connection.jdbc.advice import DelegatingDataSource
from micronaut.http.client import HttpClient
from micronaut.http.client.annotation import Client
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.Book import Book
from example.BookCatalog import BookCatalog


@MicronautTest(transactional=False)
class BookNotificationTest:
    dataSource: Annotated[DataSource, Inject]
    catalog: Annotated[BookCatalog, Inject]
    client: Annotated[HttpClient, Inject, Client("/")]

    @Test
    def testMigrationsAndExternalChangesMaintainTheCatalog(self):
        assert self.catalog.find(1).orElseThrow().title == "Present before startup"
        assert self.client.toBlocking().retrieve("/books/1", Book).title == "Present before startup"
        connection = DelegatingDataSource.unwrapDataSource(self.dataSource).getConnection()
        try:
            statement = connection.createStatement()
            try:
                statement.executeUpdate("INSERT INTO public.book VALUES (2, 'Inserted externally')")
                self.awaitChange(lambda: self.hasTitle(2, "Inserted externally"))
                assert self.client.toBlocking().retrieve("/books/2", Book).title == "Inserted externally"
                statement.executeUpdate("UPDATE public.book SET id = 3, title = 'Updated externally' WHERE id = 2")
                self.awaitChange(lambda: self.catalog.find(2).isEmpty() and self.hasTitle(3, "Updated externally"))
                statement.executeUpdate("DELETE FROM public.book WHERE id = 3")
                self.awaitChange(lambda: self.catalog.find(3).isEmpty())
                statement.execute("TRUNCATE public.book")
                self.awaitChange(lambda: self.catalog.find(1).isEmpty())
            finally:
                statement.close()
        finally:
            connection.close()

    def hasTitle(self, id: int, title: str) -> bool:
        book = self.catalog.find(id).orElse(None)
        return book is not None and book.title == title

    def awaitChange(self, condition: Callable[[], bool]) -> None:
        deadline = System.currentTimeMillis() + 10000
        while not condition() and System.currentTimeMillis() < deadline:
            Thread.sleep(20)
        assert condition(), "Catalog did not reflect the committed change"
