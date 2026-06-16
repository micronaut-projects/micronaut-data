package io.micronaut.data.runtime.operations.internal.query

import io.micronaut.core.type.Argument
import io.micronaut.data.model.runtime.QueryParameterBinding
import io.micronaut.data.model.runtime.RuntimePersistentProperty
import spock.lang.Specification

class BindableParametersStoredQueryBinderDefaultsSpec extends Specification {

    def "Binder default methods return expected defaults"() {
        given:
        BindableParametersStoredQuery.Binder binder = new BindableParametersStoredQuery.Binder() {
            @Override
            Object autoPopulateRuntimeProperty(RuntimePersistentProperty<?> persistentProperty, Object previousValue) { return null }

            @Override
            Object convert(Object value, RuntimePersistentProperty<?> property) { return value }

            @Override
            Object convert(Class<?> converterClass, Object value, Argument<?> argument) { return value }

            @Override
            void bindOne(QueryParameterBinding binding, Object value) { /* no-op for test */ }

            @Override
            void bindMany(QueryParameterBinding binding, Collection<Object> values) { /* no-op for test */ }
        }

        expect:
        binder.currentIndex() == -1
    }
}
