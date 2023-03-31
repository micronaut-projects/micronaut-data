/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.model.query;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.naming.Named;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.data.model.Association;
import io.micronaut.data.model.DataType;
import io.micronaut.data.model.JsonDataType;
import io.micronaut.data.model.PersistentProperty;
import io.micronaut.data.model.PersistentPropertyPath;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A parameter to a query.
 *
 * @author graemerocher
 * @since 1.0
 */
public class QueryParameter implements Named, BindingParameter {

    private final String name;

    /**
     * Default constructor.
     *
     * @param name The parameter name
     */
    public QueryParameter(@NonNull String name) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QueryParameter that = (QueryParameter) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Creates a new query parameter for the given name.
     *
     * @param name The name
     * @return The parameter
     */
    public static @NonNull
    QueryParameter of(@NonNull String name) {
        return new QueryParameter(name);
    }

    @Override
    public QueryParameterBinding bind(BindingContext bindingContext) {
        String name = bindingContext.getName() == null ? String.valueOf(bindingContext.getIndex()) : bindingContext.getName();
        PersistentPropertyPath outgoingQueryParameterProperty = bindingContext.getOutgoingQueryParameterProperty();
        return new QueryParameterBinding() {
            @Override
            public String getKey() {
                return name;
            }

            @Override
            public String[] getPropertyPath() {
                return asStringPath(outgoingQueryParameterProperty.getAssociations(), outgoingQueryParameterProperty.getProperty());
            }

            @Override
            public DataType getDataType() {
                return outgoingQueryParameterProperty.getProperty().getDataType();
            }

            @Override
            public JsonDataType getJsonDataType() {
                return outgoingQueryParameterProperty.getProperty().getJsonDataType();
            }

            @Override
            public boolean isExpandable() {
                return bindingContext.isExpandable();
            }
        };
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
