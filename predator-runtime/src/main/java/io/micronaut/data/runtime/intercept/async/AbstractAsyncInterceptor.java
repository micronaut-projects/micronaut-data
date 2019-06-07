package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.operations.RepositoryOperations;
import io.micronaut.data.operations.async.AsyncCapableRepository;
import io.micronaut.data.operations.async.AsyncRepositoryOperations;
import io.micronaut.data.exceptions.DataAccessException;
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor;

import java.util.concurrent.CompletionStage;

/**
 * Abstract asynchronous interceptor implementation.
 * @param <T> The declaring type
 * @param <R> The result type.
 * @author graemerocher
 * @since 1.0.0
 */
public abstract class AbstractAsyncInterceptor<T, R> extends AbstractQueryInterceptor<T, CompletionStage<R>> {

    @NonNull
    protected final AsyncRepositoryOperations asyncDatastoreOperations;

    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected AbstractAsyncInterceptor(@NonNull RepositoryOperations datastore) {
        super(datastore);
        if (datastore instanceof AsyncCapableRepository) {
            this.asyncDatastoreOperations = ((AsyncCapableRepository) datastore).async();
        } else {
            throw new DataAccessException("Datastore of type [" + datastore.getClass() + "] does not support asynchronous operations");
        }
    }

    /**
     * Convert a number argument if necessary.
     * @param number The number
     * @param argument The argument
     * @return The result
     */
    protected @Nullable Number convertNumberIfNecessary(Number number, Argument<CompletionStage<Number>> argument) {
        Argument<?> firstTypeVar = argument.getFirstTypeVariable().orElse(Argument.of(Long.class));
        Class<?> type = firstTypeVar.getType();
        if (type == Object.class || type == Void.class) {
            return null;
        }
        if (number == null) {
            number = 0;
        }
        if (!type.isInstance(number)) {
            return (Number) ConversionService.SHARED.convert(number, firstTypeVar)
                    .orElseThrow(() -> new IllegalStateException("Unsupported number type for return type: " + firstTypeVar));
        } else {
            return number;
        }
    }
}
