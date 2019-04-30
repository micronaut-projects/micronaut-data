package io.micronaut.data.model.finders;

import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Implementation of dynamic finders.
 *
 * @author graeme rocher
 * @since 1.0
 */
public interface FinderMethod {
    /**
     * Whether the given method name matches this finder.
     *
     * @param methodElement The method element
     * @return true if it does
     */
    boolean isMethodMatch(MethodElement methodElement);

    /**
     * Build the query object for this finder. The method {@link #isMethodMatch(MethodElement)} should be
     * invoked and checked prior to calling this method.
     *
     * @param entity        The entity
     * @param methodElement The method element
     * @return The query or null if it cannot be built. If the query cannot be built an error will be reported to
     * the passed {@link VisitorContext}
     */
    @Nullable
    Query buildQuery(
            @Nonnull PersistentEntity entity,
            @Nonnull MethodElement methodElement,
            @Nonnull VisitorContext visitorContext
    );


    /**
     * Computes the runtime {@link PredatorInterceptor} to use from the method signature. If the computation
     * cannot be done null is returned an error reported to the passed {@link VisitorContext}.
     *
     * @param entity The entity
     * @param methodElement The method element
     * @param visitorContext The visitor context
     * @return The runtime interceptor type to use
     */
    @Nullable
    Class<? extends PredatorInterceptor> getRuntimeInterceptor(@Nonnull PersistentEntity entity,
                                                               @Nonnull MethodElement methodElement,
                                                               @Nonnull VisitorContext visitorContext);
}
