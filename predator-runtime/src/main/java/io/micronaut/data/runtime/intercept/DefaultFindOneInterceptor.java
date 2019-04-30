package io.micronaut.data.runtime.intercept;

import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.intercept.FindOneInterceptor;
import io.micronaut.data.intercept.annotation.PredatorMethod;
import io.micronaut.data.store.Datastore;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DefaultFindOneInterceptor<T> implements FindOneInterceptor<T> {

    private final Datastore datastore;

    public DefaultFindOneInterceptor(Datastore datastore) {
        this.datastore = datastore;
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        String query = context.getValue(Query.class, String.class).orElseThrow(() ->
                new IllegalStateException("No query present in method")
        );
        AnnotationValue<PredatorMethod> annotation = context.getAnnotation(PredatorMethod.class);
        if (annotation == null) {
            // this should never happen
            throw new IllegalStateException("No predator method configured");
        }

        Class rootEntity = annotation.get("rootEntity", Class.class)
                                .orElseThrow(() -> new IllegalStateException("No root entity present in method"));

        List<AnnotationValue<Property>> parameterData = annotation.getAnnotations("parameterBinding", Property.class);
        Map<String, Object> parameterValues;
        if (CollectionUtils.isNotEmpty(parameterData)) {
            Map<String, Object> parameterValueMap = context.getParameterValueMap();
            parameterValues = new HashMap<>(parameterData.size());
            for (AnnotationValue<Property> annotationValue : parameterData) {
                String name = annotationValue.get("name", String.class).orElse(null);
                String argument = annotationValue.get("value", String.class).orElse(null);
                if (name != null && argument != null) {
                    parameterValues.put(name.substring(1), parameterValueMap.get(argument));
                }
            }
        } else {
            parameterValues = Collections.emptyMap();
        }
        return datastore.findOne(
                rootEntity,
                query,
                parameterValues
        );
    }
}
