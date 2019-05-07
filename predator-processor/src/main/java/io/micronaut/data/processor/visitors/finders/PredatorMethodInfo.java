package io.micronaut.data.processor.visitors.finders;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.model.query.Query;
import io.micronaut.inject.ast.TypedElement;


/**
 * The predator method info. This class describes the pre-computed method handling for a
 * repository and is computed into a {@link io.micronaut.data.intercept.annotation.PredatorMethod} annotation
 * which is readable at runtime.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public class PredatorMethodInfo {

    private final TypedElement resultType;
    private final Query query;
    private final Class<? extends PredatorInterceptor> interceptor;
    private final OperationType operationType;

    /**
     * Creates a method info.
     * @param resultType The result type
     * @param query The query
     * @param interceptor The interceptor type to execute at runtime
     */
    public PredatorMethodInfo(
            @Nullable TypedElement resultType,
            @Nullable Query query,
            @Nullable Class<? extends PredatorInterceptor> interceptor) {
        this(resultType, query, interceptor, OperationType.QUERY);
    }

    /**
     * Creates a method info.
     * @param resultType The result type, can be null for void etc.
     * @param query The query, can be null for interceptors that don't execute queries.
     * @param interceptor The interceptor type to execute at runtime
     * @param operationType The operation type
     */
    public PredatorMethodInfo(
            @Nullable TypedElement resultType,
            @Nullable Query query,
            @Nullable Class<? extends PredatorInterceptor> interceptor,
            @NonNull OperationType operationType) {
        this.query = query;
        this.interceptor = interceptor;
        this.operationType = operationType;
        this.resultType = resultType;
    }

    /**
     * The computed result type.
     * @return The result type.
     */
    @Nullable public TypedElement getResultType() {
        return resultType;
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
    @NonNull
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
