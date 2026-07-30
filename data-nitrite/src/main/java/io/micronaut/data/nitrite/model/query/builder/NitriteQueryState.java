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

  /**
   * Retrieves the root persistent entity associated with this query context.
   * This is used during AST traversal to resolve property paths and document mappings
   * strictly against the root entity of the query.
   *
   * @return The root persistent entity being queried.
   */
  public PersistentEntity getEntity() {
    return persistentEntity;
  }

  /**
   * Retrieves the ordered list of parameter bindings collected during query translation.
   * At compile-time or runtime query construction, any non-literal method parameters
   * are pushed to this list, returning an index used for positional JSON parameter binding in Nitrite filters.
   *
   * @return The list of parameter bindings.
   */
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
