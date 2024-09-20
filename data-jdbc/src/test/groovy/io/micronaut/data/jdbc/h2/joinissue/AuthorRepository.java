package io.micronaut.data.jdbc.h2.joinissue;

import io.micronaut.data.annotation.Join;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.H2)
@Join(value = "books", type = Join.Type.LEFT_FETCH)
public interface AuthorRepository extends CrudRepository<Author, Long> {

    Optional<Author> queryByName(String name);

    List<Author> queryByNameContains(String partialName);

    Optional<Author> findByNameContains(String partialName); //findByNameContainsIgnoreCase has the same issue

    //Note: findFirstByNameContains returns only the first row and therefore only one book.

    /*
     SELECT author_.`id`,
            author_.`name`,
            author_books_.`id` AS books_id,
            author_books_.`title` AS books_title,
            author_books_.`author` AS books_author
     FROM (
       SELECT id,name FROM author
       WHERE (`name` LIKE CONCAT('%',:partialName,'%'))
       LIMIT 1) author_
     LEFT JOIN book author_books_ ON author_.id=author_books_.author;
    */
    @Query("SELECT author_.`id`,author_.`name`,author_books_.`id` AS books_id,author_books_.`title` AS books_title,author_books_.`author` AS books_author FROM (SELECT id,name FROM ji_author WHERE (`name` LIKE CONCAT('%',:partialName,'%')) LIMIT 1) author_ LEFT JOIN ji_book author_books_ ON author_.id=author_books_.author;")
    Optional<Author> getOneByNameContains(String partialName);

}
