/*
 * Copyright 2017-2021 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.processor.model.criteria.impl;

import io.micronaut.core.annotation.Internal;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.ParameterExpressionImpl;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ParameterElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal source implementation of {@link ParameterExpressionImpl}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class SourceParameterExpressionImpl extends ParameterExpressionImpl<Object> implements BindingParameter {

    private final Map<String, DataType> dataTypes;
    private final ParameterElement[] parameters;
    private final ParameterElement parameterElement;
    private final boolean isEntityParameter;
    private boolean isUpdate;

    public SourceParameterExpressionImpl(Map<String, DataType> dataTypes,
                                         ParameterElement[] parameters,
                                         ParameterElement parameterElement,
                                         boolean isEntityParameter) {
        super(null, parameterElement == null ? null : parameterElement.getName());
        this.dataTypes = dataTypes;
        this.parameters = parameters;
        this.parameterElement = parameterElement;
        this.isEntityParameter = isEntityParameter;
    }

    @Override
    public Class<Object> getParameterType() {
        throw notSupportedOperation();
    }

    public void setUpdate(boolean update) {
        isUpdate = update;
    }

    @Override
    public QueryParameterBinding bind(BindingContext bindingContext) {
        String bindName;
        if (bindingContext.getName() == null) {
            bindName = String.valueOf(bindingContext.getIndex());
        } else {
            bindName = bindingContext.getName();
        }
        PersistentPropertyPath incomingMethodParameterProperty = bindingContext.getIncomingMethodParameterProperty();
        PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
        PersistentPropertyPath propertyPath = outgoingQueryParameterProperty == null ? incomingMethodParameterProperty : outgoingQueryParameterProperty;
        if (propertyPath == null) {
            Objects.requireNonNull(parameterElement);
            int index = Arrays.asList(parameters).indexOf(parameterElement);
            DataType dataType = getDataType(null, parameterElement);
            boolean isExpandable = isExpandable(bindingContext, dataType);
            return new QueryParameterBinding() {
                @Override
                public String getKey() {
                    return bindName;
                }

                @Override
                public int getParameterIndex() {
                    return index;
                }

                @Override
                public DataType getDataType() {
                    return dataType;
                }

                @Override
                public boolean isExpandable() {
                    return isExpandable;
                }
            };
        }
        boolean autopopulated = propertyPath.getProperty()
                        .findAnnotation(AutoPopulated.class)
                        .flatMap(ap -> ap.booleanValue(AutoPopulated.UPDATEABLE))
                        .orElse(false);
        DataType dataType = getDataType(propertyPath, parameterElement);
        String converterClassName = ((SourcePersistentProperty) propertyPath.getProperty()).getConverterClassName();
        int index = parameterElement == null || isEntityParameter ? -1 : Arrays.asList(parameters).indexOf(parameterElement);
        String[] path = asStringPath(outgoingQueryParameterProperty.getAssociations(), outgoingQueryParameterProperty.getProperty());
        String[] parameterBindingPath = index != -1 ? getBindingPath(incomingMethodParameterProperty, outgoingQueryParameterProperty) : null;
        boolean requiresPrevValue = index == -1 && autopopulated && !isUpdate;
        boolean isExpandable = isExpandable(bindingContext, dataType);
        return new QueryParameterBinding() {
            @Override
            public String getKey() {
                return bindName;
            }

            @Override
            public DataType getDataType() {
                return dataType;
            }

            @Override
            public String getConverterClassName() {
                return converterClassName;
            }

            @Override
            public int getParameterIndex() {
                return index;
            }

            @Override
            public String[] getParameterBindingPath() {
                return parameterBindingPath;
            }

            @Override
            public String[] getPropertyPath() {
                return path;
            }

            @Override
            public boolean isAutoPopulated() {
                return autopopulated;
            }

            @Override
            public boolean isRequiresPreviousPopulatedValue() {
                return requiresPrevValue;
            }

            @Override
            public boolean isExpandable() {
                return isExpandable;
            }
        };
    }

    private boolean isExpandable(BindingContext bindingContext, DataType dataType) {
        if (bindingContext.isExpandable()) {
            return true;
        }
        if (parameterElement != null && parameterElement.isAnnotationPresent(Expandable.class)) {
            return true;
        }
        if (!dataType.isArray() && (parameterElement == null || parameterElement.getType().isAssignable(Iterable.class.getName()))) {
            return true;
        }
        return false;
    }

    private String[] getBindingPath(PersistentPropertyPath parameterProperty, PersistentPropertyPath bindedPath) {
        if (parameterProperty == null) {
            return asStringPath(bindedPath.getAssociations(), bindedPath.getProperty());
        }
        List<String> parameterPath = Arrays.asList(asStringPath(parameterProperty.getAssociations(), parameterProperty.getProperty()));
        List<String> path = new LinkedList<>(Arrays.asList(asStringPath(bindedPath.getAssociations(), bindedPath.getProperty())));
        if (path.equals(parameterPath)) {
            return null;
        }
        int fromIndex = 0;
        for (int i = 0; i < path.size() && i < parameterPath.size(); i++) {
            String pp = parameterPath.get(i);
            String p = path.get(i);
            if (pp.equals(p)) {
                fromIndex++;
                continue;
            }
            break;
        }
        return path.subList(fromIndex, path.size()).toArray(new String[0]);
    }

    private DataType getDataType(PersistentPropertyPath propertyPath, ParameterElement parameterElement) {
        if (propertyPath != null) {
            PersistentProperty property = propertyPath.getProperty();
            if (!(property instanceof Association)) {
                return property.getDataType();
            }
        }
        if (parameterElement != null) {
            DataType dataType = TypeUtils.resolveDataType(parameterElement).orElse(null);
            if (dataType != null) {
                return dataType;
            }
            ClassElement type = parameterElement.getType();
            if (TypeUtils.isContainerType(type)) {
                type = type.getFirstTypeArgument().orElse(type);
            }
            return TypeUtils.resolveDataType(type, dataTypes);
        }
        return DataType.OBJECT;
    }

    private String[] asStringPath(List<Association> associations, PersistentProperty property) {
        if (associations.isEmpty()) {
            return new String[]{property.getName()};
        }
        List<String> path = new ArrayList<>(associations.size() + 1);
        for (Association association : associations) {
            path.add(association.getName());
        }
        path.add(property.getName());
        return path.toArray(new String[0]);
    }

}
