package io.micronaut.data.hibernate.entities;

import io.micronaut.data.tck.entities.Author;
import io.micronaut.data.tck.entities.Book;

import jakarta.persistence.*;
import java.util.UUID;

@NamedEntityGraph(
        name = "RatingEntityGraph",
        attributeNodes = {
                @NamedAttributeNode(value = "book", subgraph = "graph.RatingBookSubgraph"),
                @NamedAttributeNode(value = "author")
        },
        subgraphs = {
                @NamedSubgraph(
                        name = "graph.RatingBookSubgraph",
                        attributeNodes = {
                                @NamedAttributeNode(value = "pages"),
                                @NamedAttributeNode(value = "author")
                        }
                )
        }
)
@Entity
public class Rating {
    @Id
    @GeneratedValue
    @Column(columnDefinition = "uuid")
    UUID id;

    @Column(nullable = false)
    Integer rating = 1;

    @Column(nullable = true)
    String comment;

    @ManyToOne(optional = false)
    Book book;

    @ManyToOne(optional = false)
    Author author;

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public Book getBook() {
        return book;
    }

    public void setBook(Book book) {
        this.book = book;
    }

    public Author getAuthor() {
        return author;
    }

    public void setAuthor(Author author) {
        this.author = author;
    }

    @Override
    public String toString() {
        return "Rating{" +
                "id=" + id +
                ", rating='" + rating + '\'' +
                ", comment='" + comment + '\'' +
                ", book=" + book +
                ", author=" + author +
                '}';
    }
}
