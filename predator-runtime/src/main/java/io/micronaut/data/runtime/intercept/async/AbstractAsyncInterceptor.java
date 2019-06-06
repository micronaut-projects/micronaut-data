package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.backend.async.AsyncCapableDatastore;
import io.micronaut.data.backend.async.AsyncDatastoreOperations;
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
    protected final AsyncDatastoreOperations asyncDatastoreOperations;

    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected AbstractAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
        if (datastore instanceof AsyncCapableDatastore) {
            this.asyncDatastoreOperations = ((AsyncCapableDatastore) datastore).async();
        } else {
            throw new DataAccessException("Datastore of type [" + datastore.getClass() + "] does not support asynchronous operations");
        }
    }
}
