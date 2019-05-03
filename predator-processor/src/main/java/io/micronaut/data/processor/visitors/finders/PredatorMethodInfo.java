package io.micronaut.data.processor.visitors.finders;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.query.Query;

import javax.annotation.Nullable;

/**
 * The predator method info.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class PredatorMethodInfo {

    private final Query query;
    private final Class<? extends PredatorInterceptor> interceptor;

    public PredatorMethodInfo(Query query, Class<? extends PredatorInterceptor> interceptor) {
        this.query = query;
        this.interceptor = interceptor;
    }

    /**
     * The query to be executed.
     * @return The query
     */
    @Nullable
    public Query getQuery() {
        return query;
    }

    /**
     * The runtime interceptor that will handle the method.
     * @return The runtime interceptor
     */
    @Nullable public Class<? extends PredatorInterceptor> getRuntimeInterceptor() {
        return interceptor;
    }
}
