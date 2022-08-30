package io.micronaut.data.tck.repositories;

import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Book;

public class BookSpecifications {

    private BookSpecifications() {}

    public static PredicateSpecification<Book> titleEquals(String title) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get("title"), title);
    }

}
