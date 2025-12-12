package io.micronaut.data.model.runtime.convert.vector;

import io.micronaut.core.naming.Named;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;

public interface VectorTypeConvertor<T> extends Named {
    Class<T> getPersistedType();
    T convert(Vector vector, Class<T> targetType);
    Vector convert(T object, Class<Vector> targetType);
    Dialect getDialect();
}
