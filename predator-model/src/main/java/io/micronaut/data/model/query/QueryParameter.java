package io.micronaut.data.model.query;

import io.micronaut.core.naming.Named;
import io.micronaut.core.util.ArgumentUtils;

import javax.annotation.Nonnull;
import java.util.Objects;

/**
 * A parameter to a query.
 *
 * @author graemerocher
 * @since 1.0
 */
public class QueryParameter implements Named {

    private final String name;

    public QueryParameter(@Nonnull String name) {
        ArgumentUtils.requireNonNull("name", name);
        this.name = name;
    }

    @Nonnull
    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        QueryParameter that = (QueryParameter) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    /**
     * Creates a new query parameter for the given name
     * @param name The name
     * @return The parameter
     */
    public static @Nonnull QueryParameter of(@Nonnull String name) {
        return new QueryParameter(name);
    }
}
