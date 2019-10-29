package io.micronaut.data.tck.entities;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Shelf {
    @GeneratedValue
    @Id
    private Long id;
    private String shelfName;

    @OneToMany(cascade = CascadeType.ALL)
    private List<Book> books = new ArrayList<>();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getShelfName() {
        return shelfName;
    }

    public void setShelfName(String shelfName) {
        this.shelfName = shelfName;
    }

    public List<Book> getBooks() {
        return books;
    }

    public void setBooks(List<Book> books) {
        this.books = books;
    }
}
