package example

import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne

@Entity
class Review {

    @GeneratedValue
    @Id
    Long id

    String reviewer

    String content

    @ManyToOne(fetch = FetchType.LAZY)
    Book book
}
