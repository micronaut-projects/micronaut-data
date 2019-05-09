package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindAllInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

import java.util.Collections;

public class DefaultFindAllInterceptor<T, R> extends AbstractQueryInterceptor<T,Iterable<R>> implements FindAllInterceptor<T, R> {

    protected DefaultFindAllInterceptor(Datastore datastore) {
        super(datastore);
    }

    @Override
    public Iterable<R> intercept(MethodInvocationContext<T, Iterable<R>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            Iterable<?> iterable = datastore.findAll(
                    preparedQuery.getResultType(),
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    preparedQuery.getPageable()
            );
            return ConversionService.SHARED.convert(
                    iterable,
                    context.getReturnType().asArgument()
            ).orElse(Collections.emptyList());
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);

            if (pageable != null) {
                return datastore.findAll(rootEntity, pageable);
            } else {
                return datastore.findAll(rootEntity);
            }
        }
    }

}
