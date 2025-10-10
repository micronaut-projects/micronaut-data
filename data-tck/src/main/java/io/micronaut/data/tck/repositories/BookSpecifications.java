package io.micronaut.data.tck.repositories;

import io.micronaut.data.model.jpa.criteria.PersistentEntityFrom;
import io.micronaut.data.model.jpa.criteria.PersistentEntityJoin;
import io.micronaut.data.repository.jpa.criteria.CriteriaQueryBuilder;
import io.micronaut.data.repository.jpa.criteria.PredicateSpecification;
import io.micronaut.data.tck.entities.Book;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

public final class BookSpecifications {

    private static final String TITLE_FIELD = "title";

    private BookSpecifications() {
    }

    public static PredicateSpecification<Book> titleEquals(String title) {
        return (root, criteriaBuilder) -> criteriaBuilder.equal(root.get(TITLE_FIELD), title);
    }

    public static PredicateSpecification<Book> titleContains(String title) {
        return (root, criteriaBuilder) -> criteriaBuilder.like(criteriaBuilder.upper(root.get(TITLE_FIELD)), "%" + title.toUpperCase() + "%");
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

    public static CriteriaQueryBuilder<Book> findUsingASubquery(String name) {
        return new CriteriaQueryBuilder<Book>() {
            @Override
            public CriteriaQuery<Book> build(CriteriaBuilder criteriaBuilder) {
                var criteriaQuery = criteriaBuilder.createQuery(Book.class);
                var bookRoot = criteriaQuery.from(Book.class);
                var subquery = criteriaQuery.subquery(Long.class);
                var subqueryBookRoot = subquery.from(Book.class);
                subquery.select(subqueryBookRoot.get("id"));
                subquery.where(criteriaBuilder.equal(subqueryBookRoot.get("title"), name));
                criteriaQuery.where(
                    criteriaBuilder.in(bookRoot.<Long>get("id")).value(subquery)
                );
                return criteriaQuery;
            }
        };
    }

    public static PredicateSpecification<Book> titleAndTotalPagesEquals(String title, int totalPages) {
        return (root, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>(3);
            if (title != null) {
                predicates.add(criteriaBuilder.equal(root.get(TITLE_FIELD), title));
            }
            if (totalPages > 0) {
                predicates.add(criteriaBuilder.equal(root.get("totalPages"), totalPages));
            }
            predicates.add(criteriaBuilder.isNotNull(root.get("id")));
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static PredicateSpecification<Book> titleAndTotalPagesEqualsUsingConjunction(String title, int totalPages) {
        return (root, criteriaBuilder) -> {
            Predicate predicate = criteriaBuilder.conjunction();

            if (title != null) {
                predicate =
                    criteriaBuilder.and(
                        predicate, criteriaBuilder.equal(root.get(TITLE_FIELD), title));
            }
            if (totalPages > 0) {
                predicate =
                    criteriaBuilder.and(
                        predicate,
                        criteriaBuilder.equal(
                            root.get("totalPages"), totalPages));
            }
            predicate =
                criteriaBuilder.and(
                    predicate, criteriaBuilder.isNotNull(root.get("id")));
            return predicate;
        };
    }
}
