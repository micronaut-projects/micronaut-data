package io.micronaut.data.runtime.intercept.async;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.backend.Datastore;
import io.micronaut.data.intercept.async.FindAllAsyncInterceptor;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.PreparedQuery;

import java.util.Collections;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public class DefaultFindAllAsyncInterceptor<T> extends AbstractAsyncInterceptor<T, Iterable<Object>> implements FindAllAsyncInterceptor<T> {
    /**
     * Default constructor.
     *
     * @param datastore The datastore
     */
    protected DefaultFindAllAsyncInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public CompletionStage<Iterable<Object>> intercept(MethodInvocationContext<T, CompletionStage<Iterable<Object>>> context) {
//        if (context.hasAnnotation(Query.class)) {
//            PreparedQuery<?, ?> preparedQuery = prepareQuery(context);
//            CompletionStage<? extends Iterable<?>> future = asyncDatastoreOperations.findAll(preparedQuery);
//            return future.thenApply((Function<Iterable<?>, Iterable<Object>>) iterable -> {
//                Argument<CompletionStage<Iterable<Object>>> targetType = context.getReturnType().asArgument();
//                Argument<?> argument = targetType.getFirstTypeVariable().orElse(Argument.listOf(Object.class));
//                Iterable<Object> result = (Iterable<Object>) ConversionService.SHARED.convert(
//                        iterable,
//                        argument
//                ).orElse(null);
//                return result == null ? Collections.emptyList() : result;
//            });
//        } else {
//            Class rootEntity = getRequiredRootEntity(context);
//            Pageable pageable = getPageable(context);
//
//            if (pageable != null) {
//                return datastore.findAll(rootEntity, pageable);
//            } else {
//                return datastore.findAll(rootEntity);
//            }
//        }
        return null;
    }
}
