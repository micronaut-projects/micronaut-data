package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

import java.util.Map;

import static io.micronaut.data.runtime.intercept.AbstractQueryInterceptor.MEMBER_ROOT_MEMBER;

public class DefaultFindAllInterceptor<T, R> implements FindAllInterceptor<T, R> {
    private final Datastore datastore;

    protected DefaultFindAllInterceptor(Datastore datastore) {
        this.datastore = datastore;
    }
    @Override
    public Iterable<R> intercept(MethodInvocationContext<T, Iterable<R>> context) {
        Class rootEntity = context.getValue(PredatorMethod.class, MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));

        String pageableParam = context.getValue(PredatorMethod.class, "pageable", String.class).orElse(null);
        if (pageableParam != null) {
            Map<String, Object> parameterValueMap = context.getParameterValueMap();
            Pageable pageable = ConversionService.SHARED
                    .convert(parameterValueMap.get(pageableParam), Pageable.class).orElse(null);

            if (pageable != null) {
                return datastore.findAll(rootEntity, pageable);
            }
        }
        return datastore.findAll(rootEntity);
    }
}
