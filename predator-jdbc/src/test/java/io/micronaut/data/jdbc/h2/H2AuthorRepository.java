package io.micronaut.data.jdbc.h2;

import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.jdbc.mapper.SqlResultConsumer;
import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.AuthorDTO;
import io.micronaut.data.tck.entities.Book;

@JdbcRepository(dialectName = "H2")
public interface H2AuthorRepository extends io.micronaut.data.tck.repositories.AuthorRepository  {
    @Query("select *, author.name as author_name, author.nick_name as author_nick_name from book as book inner join author as author where book.title = :title and book.pages > :pages")
    Book customSearch(String title, int pages, SqlResultConsumer<Book> mappingFunction);

    default Book testReadSingleProperty(String title, int pages) {
        return customSearch(title, pages, (book, context) -> {
            Author author = new Author();
            author.setName(context.readString("author_name"));
            book.setAuthor(author);
        });
    }

    default Book testReadAssociatedEntity(String title, int pages) {
        return customSearch(title, pages, (book, context) -> book.setAuthor(context.readEntity("author_", Author.class)));
    }

    default Book testReadDTO(String title, int pages) {
        return customSearch(title, pages, (book, context) ->
                {
                    AuthorDTO dto = context.readDTO("author_", Author.class, AuthorDTO.class);
                    Author author = new Author();
                    author.setName(dto.getName());
                    book.setAuthor(author);
                }
        );
    }
}
