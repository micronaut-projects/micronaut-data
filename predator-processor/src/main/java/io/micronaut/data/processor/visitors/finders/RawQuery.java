package io.micronaut.data.processor.visitors.finders;

import io.micronaut.data.model.PersistentEntity;
import io.micronaut.data.model.query.DefaultQuery;
import io.micronaut.data.model.query.Query;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * Represents a raw query. Specified by the user.
 *
 * @author graemerocher
 * @since 1.0
 */
public class RawQuery extends DefaultQuery implements Query {

    private final Map<String, String> parameterBinding;

    /**
     * Represents a raw query provided by the user.
     * @param entity The entity
     * @param parameterBinding The parameter binding.
     */
    protected RawQuery(@NonNull PersistentEntity entity, @NonNull Map<String, String> parameterBinding) {
        super(entity);
        this.parameterBinding = parameterBinding;
    }

    public Map<String, String> getParameterBinding() {
        return this.parameterBinding;
    }
}
