package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindOptionalInterceptor;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;
import java.util.Optional;

public class DefaultFindOptionalInterceptor<T> extends AbstractQueryInterceptor<T, Optional<Object>> implements FindOptionalInterceptor<T> {

    public DefaultFindOptionalInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Optional<Object> intercept(MethodInvocationContext<T, Optional<Object>> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        return Optional.ofNullable(datastore.findOne(
                preparedQuery.getRootEntity(),
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        ));
    }
}
