/*
 * Copyright 2017-2024 original authors
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
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.jpa.criteria.impl.IParameterExpression;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

import static io.micronaut.data.model.jpa.criteria.impl.CriteriaUtils.notSupportedOperation;

/**
 * The internal source implementation of {@link IParameterExpression}.
 *
 * @author Denis Stepanov
 * @since 4.8.0
 */
@Internal
public final class SourceParameterStringExpressionImpl extends IParameterExpression<Object> implements BindingParameter {

    private final PersistentProperty persistentProperty;
    private final String expression;

    public SourceParameterStringExpressionImpl(PersistentProperty persistentProperty,
                                               String expression) {
        super(null, null);
        this.persistentProperty = persistentProperty;
        this.expression = expression;
    }

    @Override
    public Class<Object> getParameterType() {
        throw notSupportedOperation();
    }

    @Override
    public QueryParameterBinding bind(BindingContext bindingContext) {
        String bindName;
        if (bindingContext.getName() == null) {
            bindName = String.valueOf(bindingContext.getIndex());
        } else {
            bindName = bindingContext.getName();
        }
        return new QueryParameterBinding() {

            @Override
            public String getName() {
                return SourceParameterStringExpressionImpl.this.getName();
            }

            @Override
            public String[] getPropertyPath() {
                return new String[]{persistentProperty.getName()};
            }

            @Override
            public String getKey() {
                return bindName;
            }

            @Override
            public DataType getDataType() {
                return persistentProperty.getDataType();
            }

            @Override
            public JsonDataType getJsonDataType() {
                return persistentProperty.getJsonDataType();
            }

            @Override
            public boolean isExpression() {
                return true;
            }

            @Override
            public Object getValue() {
                return expression;
            }
        };
    }

}
