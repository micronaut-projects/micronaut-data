
package example;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Book {
    @Id
    @GeneratedValue
    private Long id;
    private String title;
    private int pages;
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    private Set<Review> reviews;

    public Book(String title, int pages) {
        this(title, pages, Set.of());
    }

    public Book(String title, int pages, Set<Review> reviews) {
        this.title = title;
        this.pages = pages;
        this.reviews = reviews;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public int getPages() {
        return pages;
    }

    public Set<Review> getReviews() {
        return reviews;
    }

    public void setReviews(Set<Review> reviews) {
        this.reviews = reviews;
    }
}
