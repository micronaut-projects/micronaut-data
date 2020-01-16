package io.micronaut.data.spring.jpa.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.RepositoryMethodKey;
import io.micronaut.data.jpa.operations.JpaRepositoryOperations;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.query.QueryUtils;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.*;
import java.util.List;

/**
 * Runtime implementation of {@code Page find(Specification, Pageable)} for Spring Data JPA.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class FindPageSpecificationInterceptor extends AbstractQueryInterceptor<Object, Object> {
    private final JpaRepositoryOperations jpaOperations;

    /**
     * Default constructor.
     *
     * @param operations The operations
     */
    protected FindPageSpecificationInterceptor(@NonNull RepositoryOperations operations) {
        super(operations);
        if (operations instanceof JpaRepositoryOperations) {
            this.jpaOperations = (JpaRepositoryOperations) operations;
        } else {
            throw new IllegalStateException("Repository operations must be na instance of JpaRepositoryOperations");
        }
    }

    @Override
    public Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
        final Object[] parameterValues = context.getParameterValues();
        if (parameterValues.length != 2) {
            throw new IllegalStateException("Expected exactly 2 arguments to method");
        }
        final Object parameterValue = parameterValues[0];
        final Object pageableObject = parameterValues[1];
        if (parameterValue instanceof Specification) {
            Specification specification = (Specification) parameterValue;
            final EntityManager entityManager = jpaOperations.getCurrentEntityManager();
            final CriteriaBuilder criteriaBuilder = entityManager.getCriteriaBuilder();
            final CriteriaQuery<Object> query = criteriaBuilder.createQuery((Class<Object>) getRequiredRootEntity(context));
            final Root<Object> root = query.from((Class<Object>) getRequiredRootEntity(context));
            final Predicate predicate = specification.toPredicate(root, query, criteriaBuilder);
            query.where(predicate);
            query.select(root);

            if (pageableObject instanceof Pageable) {
                Pageable pageable = (Pageable) pageableObject;
                final Sort sort = pageable.getSort();
                if (sort.isSorted()) {
                    final List<Order> orders = QueryUtils.toOrders(sort, root, criteriaBuilder);
                    query.orderBy(orders);
                }
                final TypedQuery<Object> typedQuery = entityManager
                        .createQuery(query);
                if (pageable.isUnpaged()) {
                    return new PageImpl<>(
                        typedQuery
                                .getResultList()
                    );
                } else {
                    typedQuery.setFirstResult((int) pageable.getOffset());
                    typedQuery.setMaxResults(pageable.getPageSize());
                    final List<Object> results = typedQuery.getResultList();
                    final CriteriaQuery<Long> countQuery = criteriaBuilder.createQuery(Long.class);
                    final Root<?> countRoot = countQuery.from(getRequiredRootEntity(context));
                    final Predicate countPredicate = specification.toPredicate(root, query, criteriaBuilder);
                    countQuery.where(countPredicate);
                    countQuery.select(criteriaBuilder.count(countRoot));

                    return new PageImpl<>(
                            results,
                            pageable,
                            entityManager.createQuery(countQuery).getSingleResult()
                    );
                }

            } else {
                return new PageImpl<>(
                        entityManager
                                .createQuery(query)
                                .getResultList()
                );
            }
        } else {
            throw new IllegalArgumentException("Argument must be an instance of: " + Specification.class);
        }
    }
}
