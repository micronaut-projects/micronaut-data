from typing import Annotated

from jakarta.inject import Inject
from java.sql import Connection
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.transaction import TransactionOperations
from org.junit.jupiter.api import Test

from example.Book import Book
from example.BookRepository import BookRepository


@MicronautTest(transactional=False)
class BookTitlesViewTest:

    bookRepository: Annotated[BookRepository, Inject]
    transactionOperations: Annotated[TransactionOperations[Connection], Inject]

    @Test
    def theContributedViewIsCreatedOverTheBookTable(self):
        self.bookRepository.save(Book("The Left Hand of Darkness", 300))

        def select(status):
            ps = status.getConnection().prepareStatement("SELECT title FROM book_titles")
            try:
                rs = ps.executeQuery()
                try:
                    titles = []
                    while rs.next():
                        titles.append(rs.getString(1))
                    return titles
                finally:
                    rs.close()
            finally:
                ps.close()

        assert "The Left Hand of Darkness" in self.transactionOperations.executeRead(select)
