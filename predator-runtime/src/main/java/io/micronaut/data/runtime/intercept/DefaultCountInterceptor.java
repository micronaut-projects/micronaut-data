package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.CountAllInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

import javax.annotation.Nonnull;

public class DefaultCountInterceptor<T> extends AbstractQueryInterceptor<T, Number> implements CountAllInterceptor<T> {

    DefaultCountInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Number intercept(MethodInvocationContext<T, Number> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        Pageable pageable = getPageable(context);
        long result;
        if (pageable != null) {
            result = datastore.count(rootEntity, pageable);
        } else {
            result = datastore.count(rootEntity);
        }

        return ConversionService.SHARED.convert(
                result,
                context.getReturnType().asArgument()
        ).orElseThrow(() -> new IllegalStateException("Unsupported number type: " + context.getReturnType().getType()));
    }
}
