package io.micronaut.data.model.query;

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.model.Association;

import java.util.Objects;

/**
 * A join path represents a path, association and join type for performing a join with a query.
 *
 * @author graemerocher
 * @see QueryModel
 * @since 1.0.0
 */
public class JoinPath {

    private final String path;
    private final Association association;
    private final Join.Type joinType;


    /**
     * Default constructor.
     * @param path The path
     * @param association The association
     * @param joinType The join type
     */
    JoinPath(@NonNull String path, @NonNull Association association, @NonNull Join.Type joinType) {
        this.path = path;
        this.association = association;
        this.joinType = joinType;
    }

    /**
     * @return The association
     */
    public @NonNull Association getAssociation() {
        return association;
    }

    /**
     * @return The association path
     */
    public @NonNull String getPath() {
        return path;
    }

    /**
     * @return The join type
     */
    public @NonNull Join.Type getJoinType() {
        return joinType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JoinPath joinPath = (JoinPath) o;
        return path.equals(joinPath.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }
}
