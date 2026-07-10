/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.data.nitrite.model.query.builder;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.BindingParameter;
import io.micronaut.data.model.query.builder.QueryParameterBinding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Holder for state during query translation. */
public final class NitriteQueryState {

  private final PersistentEntity persistentEntity;
    private final List<QueryParameterBinding> parameterBindings = new ArrayList<>();
  private final AtomicInteger position = new AtomicInteger(0);

  NitriteQueryState(
      final PersistentEntity persistentEntity) {
    this.persistentEntity = persistentEntity;
  }

  public PersistentEntity getEntity() {
    return persistentEntity;
  }

    public List<QueryParameterBinding> getParameterBindings() {
    return parameterBindings;
  }

  /**
   * Register a parameter and return its index for the serialized JSON placeholder.
   *
   * @param bindingParameter the parameter
   * @param bindingContext the context
   * @return the parameter index
   */
  public int pushParameter(
      @NonNull final BindingParameter bindingParameter,
      @NonNull final BindingParameter.BindingContext bindingContext) {
    int index = position.getAndIncrement();
    BindingParameter.BindingContext indexContext = bindingContext.index(index);
    parameterBindings.add(bindingParameter.bind(indexContext));
    return index;
  }
}
