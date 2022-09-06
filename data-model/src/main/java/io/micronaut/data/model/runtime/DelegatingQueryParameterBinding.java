package io.micronaut.data.model.runtime;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.model.DataType;

/**
 * Delegating {@link QueryParameterBinding}. Intended for overriding some of the {@link QueryParameterBinding}'s properties.
 *
 * @author Denis Stepanov
 * @since 3.8.0
 */
@Internal
public abstract class DelegatingQueryParameterBinding implements QueryParameterBinding {

    private final QueryParameterBinding delegate;

    public DelegatingQueryParameterBinding(QueryParameterBinding delegate) {
        this.delegate = delegate;
    }

    @Nullable
    public String getName() {
        return delegate.getName();
    }

    @NonNull
    public String getRequiredName() {
        return delegate.getRequiredName();
    }

    public DataType getDataType() {
        return delegate.getDataType();
    }

    public Class<?> getParameterConverterClass() {
        return delegate.getParameterConverterClass();
    }

    public int getParameterIndex() {
        return delegate.getParameterIndex();
    }

    public String[] getParameterBindingPath() {
        return delegate.getParameterBindingPath();
    }

    public String[] getPropertyPath() {
        return delegate.getPropertyPath();
    }

    public String[] getRequiredPropertyPath() {
        return delegate.getRequiredPropertyPath();
    }

    public boolean isAutoPopulated() {
        return delegate.isAutoPopulated();
    }

    public boolean isRequiresPreviousPopulatedValue() {
        return delegate.isRequiresPreviousPopulatedValue();
    }

    public QueryParameterBinding getPreviousPopulatedValueParameter() {
        return delegate.getPreviousPopulatedValueParameter();
    }

    public boolean isExpandable() {
        return delegate.isExpandable();
    }

    public Object getValue() {
        return delegate.getValue();
    }

}
