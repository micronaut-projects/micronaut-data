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
import org.jspecify.annotations.Nullable;
import io.micronaut.data.annotation.AutoPopulated;
import io.micronaut.data.annotation.Expandable;
import io.micronaut.data.annotation.JsonRepresentation;
import io.micronaut.data.annotation.TypeDef;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.jpa.criteria.impl.IParameterExpression;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryParameterBinding;
import io.micronaut.data.processor.model.SourcePersistentProperty;
import io.micronaut.data.processor.visitors.finders.TypeUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ParameterElement;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal source implementation of {@link IParameterExpression}.
 *
 * @author Denis Stepanov
 * @since 3.2
 */
@Internal
public final class SourceParameterExpressionImpl extends IParameterExpression<Object> implements BindingParameter {

    private final Map<String, DataType> dataTypes;
    @Nullable
    private final ClassElement expressionType;
    private final ParameterElement @Nullable [] parameters;
    @Nullable
    private final ParameterElement parameterElement;
    private final boolean isEntityParameter;
    private boolean isUpdate;
    @Nullable
    private final PersistentPropertyPath parameterPropertyPath;

    public SourceParameterExpressionImpl(Map<String, DataType> dataTypes,
                                         ParameterElement[] parameters,
                                         @Nullable
                                         ParameterElement parameterElement,
                                         boolean isEntityParameter,
                                         @Nullable
                                         PersistentPropertyPath parameterPropertyPath) {
        this(parameterElement == null ? null : parameterElement.getName(),
            dataTypes, null, parameters, parameterElement, isEntityParameter, false, parameterPropertyPath);
    }

    public SourceParameterExpressionImpl(Map<String, DataType> dataTypes,
                                         String name,
                                         @Nullable
                                         ClassElement expressionType,
                                         @Nullable
                                         PersistentPropertyPath parameterPropertyPath) {
        this(name, dataTypes, expressionType, null, null, false, false, parameterPropertyPath);
    }

    private SourceParameterExpressionImpl(@Nullable String name,
                                          Map<String, DataType> dataTypes,
                                          @Nullable
                                          ClassElement expressionType,
                                          ParameterElement @Nullable [] parameters,
                                          @Nullable
                                          ParameterElement parameterElement,
                                          boolean isEntityParameter,
                                          boolean isUpdate,
                                          @Nullable
                                          PersistentPropertyPath parameterPropertyPath) {
        super(new ClassElementExpressionType<>(getSourceExpressionType(expressionType, parameterElement, parameterPropertyPath)), name);
        this.dataTypes = dataTypes;
        this.expressionType = expressionType;
        this.parameters = parameters;
        this.parameterElement = parameterElement;
        this.isEntityParameter = isEntityParameter;
        this.isUpdate = isUpdate;
        this.parameterPropertyPath = parameterPropertyPath;
    }

    public static ClassElement getSourceExpressionType(@Nullable
                                                       ClassElement expressionType,
                                                       @Nullable
                                                       ParameterElement parameterElement,
                                                       @Nullable
                                                       PersistentPropertyPath parameterPropertyPath) {
        if (expressionType != null) {
            return expressionType;
        }
        if (parameterPropertyPath != null) {
            SourcePersistentProperty property = (SourcePersistentProperty) parameterPropertyPath.getProperty();
            return property.getType();
        }
        if (parameterElement != null) {
            return parameterElement.getType();
        }
        return ClassElement.of(Object.class);
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
        if (incomingMethodParameterProperty == null) {
            incomingMethodParameterProperty = parameterPropertyPath;
        }
        PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
        PersistentPropertyPath propertyPath = outgoingQueryParameterProperty == null ? incomingMethodParameterProperty : outgoingQueryParameterProperty;
        if (propertyPath == null) {
            if (parameterElement != null) {
                Objects.requireNonNull(parameters);
                int index = Arrays.asList(parameters).indexOf(parameterElement);
                DataType dataType = getDataType(null, parameterElement, expressionType);
                JsonDataType jsonDataType = dataType == DataType.JSON ? getJsonDataType(null, parameterElement, expressionType) : JsonDataType.DEFAULT;
                String converter = parameterElement.stringValue(TypeDef.class, "converter").orElseGet(() -> {
                    ClassElement t = expressionType != null ? expressionType : parameterElement.getType();
                    return t.stringValue(TypeDef.class, "converter").orElse(null);
                });
                boolean isExpandable = isExpandable(bindingContext, dataType);
                return new QueryParameterBinding() {

                    @Override
                    @Nullable
                    public String getName() {
                        return SourceParameterExpressionImpl.this.getName();
                    }

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
                    public JsonDataType getJsonDataType() {
                        return jsonDataType;
                    }

                    @Override
                    @Nullable
                    public String getConverterClassName() {
                        return converter;
                    }

                    @Override
                    public boolean isExpandable() {
                        return isExpandable;
                    }

                };
            }
            Objects.requireNonNull(expressionType);
            DataType dataType = getDataType(null, null, expressionType);
            JsonDataType jsonDataType = dataType == DataType.JSON ? getJsonDataType(null, null, expressionType) : JsonDataType.DEFAULT;
            boolean isExpandable = isExpandable(bindingContext, dataType);
            return new QueryParameterBinding() {

                @Nullable
                @Override
                public String getName() {
                    return SourceParameterExpressionImpl.this.getName();
                }

                @Override
                public String getKey() {
                    return bindName;
                }

                @Override
                public DataType getDataType() {
                    return dataType;
                }

                @Override
                public JsonDataType getJsonDataType() {
                    return jsonDataType;
                }

                @Override
                public boolean isExpandable() {
                    return isExpandable;
                }

                @Override
                public boolean isExpression() {
                    return true;
                }
            };

        }
        boolean autopopulated = propertyPath.getProperty()
            .findAnnotation(AutoPopulated.class)
            .map(ap -> ap.getRequiredValue(AutoPopulated.UPDATABLE, Boolean.class))
            .orElse(false);
        DataType dataType = getDataType(propertyPath, parameterElement, expressionType);
        JsonDataType jsonDataType = getJsonDataType(propertyPath, parameterElement, expressionType);
        String converterClassName = ((SourcePersistentProperty) propertyPath.getProperty()).getConverterClassName();
        int index = parameterElement == null || isEntityParameter ? -1 : Arrays.asList(Objects.requireNonNull(parameters)).indexOf(parameterElement);
        boolean requiresPrevValue = index == -1 && autopopulated && !isUpdate;
        boolean isExpandable = isExpandable(bindingContext, dataType);
        String[] parameterBindingPath;
        if (bindingContext.getParameterBindingPath() == null) {
            if (incomingMethodParameterProperty != null && outgoingQueryParameterProperty != null) {
                parameterBindingPath = getParameterBindingPath(incomingMethodParameterProperty, outgoingQueryParameterProperty);
            } else {
                parameterBindingPath = null;
            }
        } else {
            parameterBindingPath = bindingContext.getParameterBindingPath().getArrayPath();
        }
        return new QueryParameterBinding() {

            @Nullable
            @Override
            public String getName() {
                return SourceParameterExpressionImpl.this.getName();
            }

            @Override
            public String getKey() {
                return bindName;
            }

            @Override
            public DataType getDataType() {
                return dataType;
            }

            @Override
            public JsonDataType getJsonDataType() {
                return jsonDataType;
            }

            @Nullable
            @Override
            public String getConverterClassName() {
                return converterClassName;
            }

            @Override
            public int getParameterIndex() {
                return index;
            }

            @Override
            public String @Nullable[] getParameterBindingPath() {
                return parameterBindingPath;
            }

            @Override
            public String[] getPropertyPath() {
                return propertyPath.getArrayPath();
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

            @Override
            public boolean isExpression() {
                return expressionType != null;
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
        if (dataType == DataType.JSON) {
            return false;
        }
        return !dataType.isArray() && (parameterElement != null && parameterElement.getType().isAssignable(Iterable.class.getName()));
    }

    private String @Nullable [] getParameterBindingPath(PersistentPropertyPath incomingMethodParameterPropertyPath,
                                             PersistentPropertyPath outgoingQueryParameterPropertyPath) {
        List<String> parameterPath = List.of(incomingMethodParameterPropertyPath.getArrayPath());
        List<String> path = List.of(outgoingQueryParameterPropertyPath.getArrayPath());
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

    private DataType getDataType(@Nullable PersistentPropertyPath propertyPath, @Nullable ParameterElement parameterElement, @Nullable ClassElement type) {
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
            if (type == null) {
                type = parameterElement.getType();
            }
        }
        if (type != null) {
            if (TypeUtils.isContainerType(type)) {
                type = type.getFirstTypeArgument().orElse(type);
            }
            return TypeUtils.resolveDataType(type, dataTypes);
        }
        return DataType.OBJECT;
    }

    private JsonDataType getJsonDataType(@Nullable PersistentPropertyPath propertyPath, @Nullable ParameterElement parameterElement, @Nullable ClassElement type) {
        if (propertyPath != null) {
            PersistentProperty property = propertyPath.getProperty();
            if (!(property instanceof Association)) {
                return property.getJsonDataType();
            }
        }
        Element element;
        if (type == null && parameterElement != null) {
            element = parameterElement;
        } else {
            element = type;
        }
        if (element != null) {
            return element.enumValue(JsonRepresentation.class, "type", JsonDataType.class).orElse(JsonDataType.DEFAULT);
        }
        // default
        return JsonDataType.DEFAULT;
    }

}
