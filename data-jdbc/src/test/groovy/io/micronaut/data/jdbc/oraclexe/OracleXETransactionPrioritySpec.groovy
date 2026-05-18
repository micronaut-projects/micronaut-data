package io.micronaut.data.jdbc.oraclexe

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.junit5.annotation.TestResourcesScope
import io.micronaut.data.tck.entities.Author
import io.micronaut.data.tck.entities.Book
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

/**
 * Tests Oracle Transaction Priority with older database which does not support it.
 * The test should not fail as we catch Oracle exception when setting unsupported session attribute
 * in DataSourceTransactionManager.
 */
@TestResourcesScope("oracle-xe-transaction-priority")
class OracleXETransactionPrioritySpec extends Specification implements OracleXE21TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    @Shared
    OracleXEBookRepository bookRepository = context.getBean(OracleXEBookRepository)

    @Shared
    OracleXEAuthorRepository authorRepository = context.getBean(OracleXEAuthorRepository)

    void setup() {
        bookRepository.deleteAll()
        authorRepository.deleteAll()
    }

    void "repository save with transaction priority succeeds on Oracle XE"() {
        given:
        def author = authorRepository.save(new Author(name: "Oracle XE Author"))
        def book = new Book(title: "Oracle XE Priority", totalPages: 123, author: author)

        when:
        def saved = bookRepository.save(book)

        then:
        saved.id != null
        saved.title == "Oracle XE Priority"
        bookRepository.findById(saved.id).present
    }
}
