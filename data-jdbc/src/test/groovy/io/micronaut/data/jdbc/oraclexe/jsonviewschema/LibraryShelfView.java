package io.micronaut.data.jdbc.oraclexe.jsonviewschema;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = LibraryShelf.class, schema = "LIBRARY", alias = "lshw")
public class LibraryShelfView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    private String comment;

    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<LibraryBookSubView> books;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public List<LibraryBookSubView> getBooks() {
        return books;
    }

    public void setBooks(List<LibraryBookSubView> books) {
        this.books = books;
    }
}
