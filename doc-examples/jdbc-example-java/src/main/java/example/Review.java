package example;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

/**
 * A book review.
 *
 * @param id The id
 * @param reviewer The reviewer name
 * @param content The book review content
 * @param book The reviewed book
 */
@Entity
public record Review(

    @GeneratedValue
    @Id
    Long id,
    String reviewer,
    String content,
    @ManyToOne(fetch = FetchType.LAZY)
    Book book) {

    public Review(String reviewer, String content) {
        this(null, reviewer, content, null);
    }
}
