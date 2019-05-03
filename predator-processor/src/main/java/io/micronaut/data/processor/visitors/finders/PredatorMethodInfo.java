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
    private final OperationType operationType;

    public PredatorMethodInfo(Query query, Class<? extends PredatorInterceptor> interceptor) {
        this(query, interceptor, OperationType.QUERY);
    }

    public PredatorMethodInfo(Query query, Class<? extends PredatorInterceptor> interceptor, OperationType operationType) {
        this.query = query;
        this.interceptor = interceptor;
        this.operationType = operationType;
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

    /**
     * The operation type to execute.
     * @return The operation type
     */
    public OperationType getOperationType() {
        return operationType;
    }

    /**
     * Describes the operation type.
     */
    public enum OperationType {
        /**
         * A query operation.
         */
        QUERY,
        /**
         * An update operation.
         */
        UPDATE,
        /**
         * A delete operation.
         */
        DELETE,
        /**
         * An insert operation.
         */
        INSERT
    }
}
