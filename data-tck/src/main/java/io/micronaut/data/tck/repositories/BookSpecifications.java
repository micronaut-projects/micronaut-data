package io.micronaut.data.tck.repositories;

import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityJoin;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Book;

public class BookSpecifications {

    private static final String TITLE_FIELD = "title";

    private BookSpecifications() {}

    public static PredicateSpecification<Book> titleEquals(String title) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(TITLE_FIELD), title);
    }

    public static PredicateSpecification<Book> titleEqualsWithJoin(String title) {
        return (root, criteriaBuilder) -> {
            ((PersistentEntityFrom<?, ?>) root).join("genre", io.micronaut.data.annotation.Join.Type.LEFT_FETCH);
            return criteriaBuilder.equal(root.get(TITLE_FIELD), title);
        };
    }

    public static PredicateSpecification<Book> hasChapter(String chapterTitle) {
        return (root, criteriaBuilder) -> {
            PersistentEntityJoin<?, ?> join = ((PersistentEntityFrom<?, ?>) root).join("chapters");
            return criteriaBuilder.equal(join.get(TITLE_FIELD), chapterTitle);
        };
    }

}
