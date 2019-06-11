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

    @Join("author")
    List<Book> list();
    
    @Join("author")
    Book find(String title);
    
    @Join("author")
    Book findByTitle(String title);
    
    @Join("author")
    @Join("publisher")
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
