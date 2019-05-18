/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.intercept;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.data.intercept.SaveOneInterceptor;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.runtime.datastore.Datastore;

import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link SaveOneInterceptor}.
 *
 * @param <T> The declaring type
 * @author graemerocher
 * @since 1.0.0
 */
public class DefaultSaveOneInterceptor<T> extends AbstractQueryInterceptor<T, Object> implements SaveOneInterceptor<T> {

    /**
     * Default constructor.
     * @param datastore The datastore
     */
    protected DefaultSaveOneInterceptor(@NonNull Datastore datastore) {
        super(datastore);
    }

    @Override
    public Object intercept(MethodInvocationContext<T, Object> context) {
        Class<?> rootEntity = getRequiredRootEntity(context);
        PersistentEntity entity = PersistentEntity.of(rootEntity);
        BeanIntrospection<?> introspection = BeanIntrospection.getIntrospection(rootEntity);
        Argument<?>[] constructorArguments = introspection.getConstructorArguments();
        Map<String, Object> parameterValueMap = context.getParameterValueMap();
        Object instance;
        if (ArrayUtils.isNotEmpty(constructorArguments)) {

            Object[] arguments = new Object[constructorArguments.length];
            for (int i = 0; i < constructorArguments.length; i++) {
                Argument<?> argument = constructorArguments[i];

                Object v = parameterValueMap.get(argument.getName());
                if (v == null && !PersistentProperty.isNullable(argument.getAnnotationMetadata())) {
                    throw new IllegalArgumentException("Argument [" + argument.getName() + "] cannot be null");
                }
                arguments[i] = v;
            }
            instance = introspection.instantiate(arguments);
        } else {
            instance = introspection.instantiate();
        }

        BeanWrapper<Object> wrapper = BeanWrapper.getWrapper(instance);
        List<PersistentProperty> persistentProperties = entity.getPersistentProperties();
        for (PersistentProperty prop : persistentProperties) {
            if (prop.isReadOnly() || prop.isGenerated()) {
                continue;
            } else {
                String propName = prop.getName();
                Object v = parameterValueMap.get(propName);
                if (v == null && !prop.isNullable()) {
                    throw new IllegalArgumentException("Argument [" + propName + "] cannot be null");
                }
                wrapper.setProperty(propName, v);
            }
        }

        return datastore.persist(instance);
    }
}
