
package example

import jakarta.persistence.*

@Entity
class Book {
    @Id
    @GeneratedValue
    Long id
    private String title
    private int pages
    @OneToMany(cascade = CascadeType.ALL, mappedBy = "book")
    private Set<Review> reviews

    Book(String title, int pages) {
        this(title, pages, Set.of())
    }

    Book(String title, int pages, Set<Review> reviews) {
        this.title = title
        this.pages = pages
        this.reviews = reviews
    }

    String getTitle() {
        return title
    }

    int getPages() {
        return pages
    }

    Set<Review> getReviews() {
        return reviews
    }

    void setReviews(Set<Review> reviews) {
        this.reviews = reviews
    }
}
