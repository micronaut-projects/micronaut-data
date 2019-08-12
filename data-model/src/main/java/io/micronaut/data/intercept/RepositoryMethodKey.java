package io.micronaut.data.intercept;

import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.ExecutableMethod;

import java.util.Objects;

/**
 * Key used to cache results for repository method invocations.
 *
 * @author graemerocher
 * @since 1.0
 */
@Internal
public final class RepositoryMethodKey {
    private final Object repository;
    private final ExecutableMethod method;
    private final int hashCode;

    /**
     * Default constructor.
     * @param repository The repository
     * @param method The method
     */
    public RepositoryMethodKey(Object repository, ExecutableMethod method) {
        this.repository = repository;
        this.method = method;
        this.hashCode = Objects.hash(repository, method);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        RepositoryMethodKey that = (RepositoryMethodKey) o;
        return repository.equals(that.repository) &&
                method.equals(that.method);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }
}
