package io.micronaut.data.tck.repositories;

import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Book;

public class BookSpecifications {

    public static PredicateSpecification<Book> titleEquals(String title) {
        return (root, criteriaBuilder) ->  {
            ((PersistentEntityFrom) root).join("genre", io.micronaut.data.annotation.Join.Type.LEFT_FETCH);
            return criteriaBuilder.equal(root.get("title"), title);
        };
    }
}
