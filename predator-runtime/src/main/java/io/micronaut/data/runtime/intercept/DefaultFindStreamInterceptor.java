package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindStreamInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

import java.util.stream.Stream;

public class DefaultFindStreamInterceptor<T> extends AbstractQueryInterceptor<T, Stream<Object>> implements FindStreamInterceptor<T> {

    public DefaultFindStreamInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Stream<Object> intercept(MethodInvocationContext<T, Stream<Object>> context) {
        if (context.hasAnnotation(Query.class)) {
            PreparedQuery preparedQuery = prepareQuery(context);
            return datastore.findStream(
                    (Class<Object>) preparedQuery.getResultType(),
                    preparedQuery.getQuery(),
                    preparedQuery.getParameterValues(),
                    preparedQuery.getPageable()
            );
        } else {
            Class rootEntity = getRequiredRootEntity(context);
            Pageable pageable = getPageable(context);

            if (pageable != null) {
                return datastore.findStream(rootEntity, pageable);
            } else {
                return datastore.findStream(rootEntity);
            }
        }
    }
}
