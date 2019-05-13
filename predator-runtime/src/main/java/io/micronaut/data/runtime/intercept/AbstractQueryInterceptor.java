package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.beans.BeanIntrospector;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.TypeRole;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.PredatorInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.model.Pageable;
import io.micronaut.data.model.query.Sort;
import io.micronaut.data.runtime.datastore.Datastore;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

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
    private final ConcurrentMap<Class, Class> lastUpdatedTypes = new ConcurrentHashMap<>(10);

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

        Map<String, Object> parameterValues = buildParameterBinding(context, annotation, rootEntity);

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

    /**
     * Prepares a query for the given context.
     * @param context The context
     * @return The query
     */
    protected final PreparedQuery prepareCountQuery(MethodInvocationContext<T, R> context) {
        String query = context.getValue(Query.class, PredatorMethod.MEMBER_COUNT_QUERY, String.class).orElseThrow(() ->
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

        @SuppressWarnings("ConstantConditions") Map<String, Object> parameterValues = buildParameterBinding(
                context,
                context.getAnnotation(Query.class),
                PredatorMethod.MEMBER_COUNT_PARAMETERS,
                rootEntity
        );

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
    private Map<String, Object> buildParameterBinding(
            @NonNull MethodInvocationContext<T, R> context,
            @NonNull AnnotationValue<PredatorMethod> annotation,
            @NonNull Class<?> rootEntity) {
        return buildParameterBinding(context, annotation, MEMBER_PARAMETER_BINDING, rootEntity);
    }

    /**
     * Builds the parameter data.
     * @param context The context
     * @param annotation The predator annotation
     * @param parameterBindingMember The member that holds the parameter binding
     * @param rootEntity The root entity
     * @return The parameter data
     */
    private Map<String, Object> buildParameterBinding(
            @NonNull MethodInvocationContext<T, R> context,
            @NonNull AnnotationValue<?> annotation,
            String parameterBindingMember,
            @NonNull Class<?> rootEntity) {
        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations(parameterBindingMember,
                Property.class);
        Map<String, Object> parameterValues;
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        if (CollectionUtils.isNotEmpty(parameterData)) {
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.get("name", String.class).orElse(null);
                String argument = annotationValue.get("value", String.class).orElse(null);
                if (name != null && argument != null) {
                    if (parameterValueMap.containsKey(argument)) {
                        parameterValues.put(name, parameterValueMap.get(argument));
                    } else {
                        String v = context.getValue(PredatorMethod.class, TypeRole.LAST_UPDATED_PROPERTY, String.class).orElse(null);
                        if (v != null && v.equals(argument)) {
                            Class<?> lastUpdatedType = getLastUpdatedType(rootEntity, v);
                            if (lastUpdatedType == null) {
                                throw new IllegalStateException("Could not establish last updated time for entity: " + rootEntity);
                            }
                            Object timestamp = ConversionService.SHARED.convert(OffsetDateTime.now(), lastUpdatedType).orElse(null);
                            if (timestamp == null) {
                                throw new IllegalStateException("Unsupported date type: " + lastUpdatedType);
                            }
                            parameterValues.put(name, timestamp);
                        } else {
                            throw new IllegalArgumentException("Missing query arguments: " + argument);
                        }
                    }
                }
            }
        } else {
            parameterValues = Collections.emptyMap();
        }
        return parameterValues;
    }

    private Class<?> getLastUpdatedType(Class<?> rootEntity, String property) {
        Class<?> type = lastUpdatedTypes.get(rootEntity);
        if (type == null) {
            type = BeanIntrospector.SHARED
                    .findIntrospection(rootEntity)
                    .flatMap(bp -> bp.getProperty(property))
                    .map(BeanProperty::getType).orElse(null);
            if (type != null) {
                lastUpdatedTypes.put(rootEntity, type);
            }
        }
        return type;
    }

    @NonNull
    protected Class getRequiredRootEntity(MethodInvocationContext context) {
        return context.getValue(PredatorMethod.class, MEMBER_ROOT_MEMBER, Class.class)
                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));
    }

    @Nullable
    protected Pageable getPageable(MethodInvocationContext context) {
        String pageableParam = context.getValue(PredatorMethod.class, TypeRole.PAGEABLE, String.class).orElse(null);
        Pageable pageable = null;
        if (pageableParam != null) {
            Map<String, Object> parameterValueMap = context.getParameterValueMap();
            pageable = ConversionService.SHARED
                    .convert(parameterValueMap.get(pageableParam), Pageable.class).orElse(null);

        } else {
            Sort sortParam = context.getValue(PredatorMethod.class, TypeRole.SORT, Sort.class).orElse(null);
            if (sortParam != null) {
                pageable = Pageable.unpaged();
                for (Sort.Order order : sortParam.getOrderBy()) {
                    pageable.getSort().order(order);
                }
                return pageable;
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
     * Looks up the entity to persist from the execution context, or throws an exception.
     * @param context The context
     * @return The entity
     */
    protected @NonNull Object getRequiredEntity(MethodInvocationContext<T, Object> context) {
        String entityParam = context.getValue(PredatorMethod.class, TypeRole.ENTITY, String.class)
                .orElseThrow(() -> new IllegalStateException("No entity parameter specified"));

        Object o = context.getParameterValueMap().get(entityParam);
        if (o == null) {
            throw new IllegalArgumentException("Entity argument cannot be null");
        }
        return o;
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
