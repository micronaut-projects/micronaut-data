package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.store.Datastore;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Abstract interceptor that executes a {@link io.micronaut.data.annotation.Query}.
 * @param <T> The declaring type
 * @param <R> The return type
 * @since 1.0
 * @author graemerocher
 */
abstract class AbstractQueryInterceptor<T, R> implements PredatorInterceptor<T, R> {
    private static final String MEMBER_ROOT_MEMBER = "rootEntity";
    private static final String MEMBER_RESULT_TYPE = "resultType";
    private static final String MEMBER_ID_TYPE = "idType";
    private static final String MEMBER_PARAMETER_BINDING = "parameterBinding";
    protected final Datastore datastore;

    AbstractQueryInterceptor(@NonNull Datastore datastore) {
        ArgumentUtils.requireNonNull("datastore", datastore);
        this.datastore = datastore;
    }

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery prepareQuery(MethodInvocationContext<T, R> context) {
        String query = context.getValue(Query.class, String.class).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        AnnotationValue<PredatorMethod> annotation = context.getAnnotation(PredatorMethod.class);
        if (annotation == null) {
            // this should never happen
            throw new IllegalStateException("No predator method configured");
        }

        Class rootEntity = annotation.get(MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
        Class resultType = annotation.get(MEMBER_RESULT_TYPE, Class.class).orElse(rootEntity);
        Class idType = annotation.get(MEMBER_ID_TYPE, Class.class)
                .orElse(null);

        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(MEMBER_PARAMETER_BINDING, Property.class);
        Map<String, Object> parameterValues;
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        if (CollectionUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.get("name", String.class).orElse(null);
                String argument = annotationValue.get("value", String.class).orElse(null);
                if (name != null && argument != null) {
                    parameterValues.put(name, parameterValueMap.get(argument));
                }
            }
        } else {
            parameterValues = Collections.emptyMap();
        }

        Pageable pageable = getPageable(context);

        return new PreparedQuery(
                resultType,
                rootEntity,
                idType,
                query,
                parameterValues,
                pageable
        );
    }

    @NonNull
    protected Class getRequiredRootEntity(MethodInvocationContext context) {
        return context.getValue(PredatorMethod.class, MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
    }

    @Nullable
    protected Pageable getPageable(MethodInvocationContext context) {
        String pageableParam = context.getValue(PredatorMethod.class, "pageable", String.class).orElse(null);
        Pageable pageable = null;
        if (pageableParam != null) {
            Map<String, Object> parameterValueMap = context.getParameterValueMap();
            pageable = ConversionService.SHARED
                    .convert(parameterValueMap.get(pageableParam), Pageable.class).orElse(null);

        } else {
            int max = context.getValue(PredatorMethod.class, "max", int.class).orElse(-1);
            long offset = context.getValue(PredatorMethod.class, "offset", long.class).orElse(0L);
            boolean hasMax = max > -1;
            if (offset > 0 || hasMax) {
                if (hasMax) {
                    pageable = Pageable.from(offset, max);
                } else {
                    pageable = Pageable.from(offset);
                }
            }
        }
        return pageable;
    }

    /**
     * Return whether the metadata indicates the instance is nullable.
     * @param metadata The metadata
     * @return True if it is nullable
     */
    protected boolean isNullable(@NonNull AnnotationMetadata metadata) {
        return metadata
                .getDeclaredAnnotationNames()
                .stream()
                .anyMatch(n -> NameUtils.getSimpleName(n).equalsIgnoreCase("nullable"));
    }

    /**
     * Represents a prepared query.
     */
    protected final class PreparedQuery {
        private final @NonNull Class resultType;
        private final @NonNull Class rootEntity;
        private final @Nullable Class idType;
        private final @NonNull String query;
        private final @NonNull Map<String, Object> parameterValues;
        private final Pageable pageable;

        /**
         * The default constructor.
         * @param rootEntity The root entity of the query
         * @param query The query itself
         * @param idType The ID type
         * @param parameterValues The parameter values
         */
        PreparedQuery(
                @NonNull Class resultType,
                @NonNull Class rootEntity,
                @Nullable Class<?> idType,
                @NonNull String query,
                @Nullable Map<String, Object> parameterValues,
                @Nullable Pageable pageable) {
            this.resultType = resultType;
            this.rootEntity = rootEntity;
            this.idType = idType;
            this.query = query;
            this.parameterValues = parameterValues == null ? Collections.emptyMap() : parameterValues;
            this.pageable = pageable;
        }

        @NonNull
        public Class<?> getResultType() {
            return resultType;
        }

        @Nullable
        public Class getIdType() {
            return idType;
        }

        @NonNull
        public Class<?> getRootEntity() {
            return rootEntity;
        }

        @NonNull
        public String getQuery() {
            return query;
        }

        @NonNull
        public Map<String, Object> getParameterValues() {
            return parameterValues;
        }

        @NonNull
        public Pageable getPageable() {
            return pageable != null ? pageable : Pageable.unpaged();
        }
    }
}
