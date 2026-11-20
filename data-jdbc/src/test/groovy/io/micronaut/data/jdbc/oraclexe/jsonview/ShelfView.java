package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;

import java.util.List;

@JsonView(entity = Shelf.class, alias = "shw")
public class ShelfView {
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    private Long id;

    @Relation(Relation.Kind.ONE_TO_MANY)
    private List<BookSubView> books;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<BookSubView> getBooks() {
        return books;
    }

    public void setBooks(List<BookSubView> books) {
        this.books = books;
    }
}
