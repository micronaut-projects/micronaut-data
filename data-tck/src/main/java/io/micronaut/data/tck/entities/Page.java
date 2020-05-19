package io.micronaut.data.tck.entities;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.ManyToOne;

@Entity
public class Page {
    @GeneratedValue
    @Id
    private Long id;
    private long num;

    @ManyToOne
    private Book book;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public long getNum() {
        return num;
    }

    public void setNum(long num) {
        this.num = num;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }
}
