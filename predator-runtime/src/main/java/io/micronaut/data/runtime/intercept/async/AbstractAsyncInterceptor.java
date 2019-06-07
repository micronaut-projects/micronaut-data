package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
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
}
