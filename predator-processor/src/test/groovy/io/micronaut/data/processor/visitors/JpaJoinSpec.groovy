package io.micronaut.data.processor.visitors

import io.micronaut.data.annotation.Query
import io.micronaut.data.model.entities.Book


class JpaJoinSpec extends AbstractPredatorSpec {


    void "test join spec - list"() {
        given:
        def repository = buildRepository("test.MyInterface", '''
import io.micronaut.data.model.entities.Book;

@Repository
interface MyInterface extends GenericRepository<Book, Long> {

    @JoinSpec("author")
    List<Book> list();
    
    @JoinSpec("author")
    Book find(String title);
    
    @JoinSpec("author")
    Book findByTitle(String title);
    
    @JoinSpec("author")
    @JoinSpec("publisher")
    Book getByTitle(String title);
}
''')
        expect:
        repository.getRequiredMethod("list").synthesize(Query).value() ==
                "SELECT book FROM $Book.name AS book JOIN book.author author"

        repository.getRequiredMethod("find", String).synthesize(Query).value() ==
                "SELECT book FROM $Book.name AS book JOIN book.author author WHERE (book.title = :p1)"

        repository.getRequiredMethod("findByTitle", String).synthesize(Query).value() ==
                "SELECT book FROM $Book.name AS book JOIN book.author author WHERE (book.title = :p1)"

        repository.getRequiredMethod("getByTitle", String).synthesize(Query).value() ==
                "SELECT book FROM $Book.name AS book JOIN book.author author JOIN book.publisher publisher WHERE (book.title = :p1)"

    }
}
