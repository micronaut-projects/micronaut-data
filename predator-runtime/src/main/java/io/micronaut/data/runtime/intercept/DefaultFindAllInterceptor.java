package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

public class DefaultFindAllInterceptor<T, R> extends AbstractQueryInterceptor<T,Iterable<R>> implements FindAllInterceptor<T, R> {

    protected DefaultFindAllInterceptor(Datastore datastore) {
        super(datastore);
    }

    @Override
    public Iterable<R> intercept(MethodInvocationContext<T, Iterable<R>> context) {
        Class rootEntity = getRequiredRootEntity(context);
        Pageable pageable = getPageable(context);

        if (pageable != null) {
            return datastore.findAll(rootEntity, pageable);
        } else {
            return datastore.findAll(rootEntity);
        }
    }

}
