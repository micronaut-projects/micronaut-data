package io.micronaut.data.model.finders;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;

/**
 * Implementation of dynamic finders.
 *
 * @author graeme rocher
 * @since 1.0
 */
public interface FinderMethod {

    /**
     * Build the query object for this finder
     * @param entity The entity
     * @param methodElement The method element
     * @return The query
     */
    Query buildQuery(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            VisitorContext visitorContext
    );

    /**
     * Whether the given method name matches this finder
     * @param methodName The method name
     * @return true if it does
     */
    boolean isMethodMatch(String methodName);
}
