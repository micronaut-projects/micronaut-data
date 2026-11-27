from typing import Annotated

from jakarta.inject import Inject
from java.sql import Connection
from micronaut.data.model.query.builder.sql import Dialect
from micronaut.data.runtime.config import SchemaGenerate
from micronaut.data.runtime.schema import SchemaContext
from micronaut.test.extensions.junit5.annotation import MicronautTest
from micronaut.transaction import TransactionOperations
from org.junit.jupiter.api import Test

from example.Book import Book
from example.BookRepository import BookRepository
from example.BookTitlesViewContributor import BookTitlesViewContributor


@MicronautTest(transactional=False)
class BookTitlesViewTest:

    bookRepository: Annotated[BookRepository, Inject]
    transactionOperations: Annotated[TransactionOperations[Connection], Inject]
    contributor: Annotated[BookTitlesViewContributor, Inject]

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

    @Test
    def theContributorDoesNotApplyToADataSourceWithoutTheBookEntity(self):
        other = SchemaContext("other", Dialect.H2, None, SchemaGenerate.CREATE_DROP, [])

        assert len(self.contributor.buildCreateStatements(other, [])) == 0
        assert len(self.contributor.buildDropStatements(other, [])) == 0
