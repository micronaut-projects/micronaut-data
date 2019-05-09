package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.ReturnType;
import io.micronaut.data.exceptions.EmptyResultException;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.runtime.datastore.Datastore;

import javax.annotation.Nonnull;

/**
 * Default implementation of the {@link FindOneInterceptor} interface.
 *
 * @param <T> The declaring type.
 */
public class DefaultFindOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements FindOneInterceptor<T> {

    /**
     * The default constructor.
     * @param datastore The datastore
     */
    protected DefaultFindOneInterceptor(@Nonnull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        PreparedQuery preparedQuery = prepareQuery(context);
        Class<?> resultType = preparedQuery.getResultType();

        Object result = datastore.findOne(
                resultType,
                preparedQuery.getQuery(),
                preparedQuery.getParameterValues()
        );
        if (result != null) {
            ReturnType<Object> returnType = context.getReturnType();
            if (!returnType.getType().isInstance(result)) {
                return ConversionService.SHARED.convert(result, returnType.asArgument())
                            .orElseThrow(() -> new IllegalStateException("Unexpected return type: " + result));
            } else {
                return result;
            }
        } else {
            if (!isNullable(context.getAnnotationMetadata())) {
                throw new EmptyResultException();
            }
        }
        return result;
    }

}
