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
package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.DefaultQuery;
import io.micronaut.data.model.query.QueryModel;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

import java.util.List;

/**
 * Represents a raw query. Specified by the user.
 *
 * @author graemerocher
 * @since 1.0
 */
public class RawQuery extends DefaultQuery implements QueryModel {

    private final List<QueryParameterBinding> parameterBinding;
    private final boolean encodeEntityParameters;

    /**
     * Represents a raw query provided by the user.
     * @param entity The entity
     * @param parameterBinding The parameter binding.
     * @param encodeEntityParameters The encodeEntityParameters.
     */
    protected RawQuery(@NonNull PersistentEntity entity, @NonNull List<QueryParameterBinding> parameterBinding, boolean encodeEntityParameters) {
        super(entity);
        this.parameterBinding = parameterBinding;
        this.encodeEntityParameters = encodeEntityParameters;
    }

    /**
     * @return The parameter binding to use for the raw query.
     */
    public List<QueryParameterBinding> getParameterBinding() {
        return this.parameterBinding;
    }

    /**
     * @return should encode entity parameters
     */
    public boolean isEncodeEntityParameters() {
        return encodeEntityParameters;
    }
}
