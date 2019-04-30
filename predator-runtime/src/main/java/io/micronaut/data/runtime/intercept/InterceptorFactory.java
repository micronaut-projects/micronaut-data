package io.micronaut.data.runtime.intercept;

import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.store.Datastore;

/**
 * Factory for creating the different types of interceptors.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class InterceptorFactory {

    @EachBean(Datastore.class)
    protected FindOneInterceptor findOneInterceptor(Datastore datastore) {
        return new DefaultFindOneInterceptor(datastore);
    }
}
