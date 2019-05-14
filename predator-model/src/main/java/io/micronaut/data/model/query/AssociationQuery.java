package io.micronaut.data.model.query;

import io.micronaut.data.model.Association;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Extends a query and allows querying an association.
 *
 * @author graemerocher
 * @since 1.0
 */
public class AssociationQuery extends DefaultQuery implements Query.Criterion {

    private final Association association;

    public AssociationQuery(@NonNull Association association) {
        super(association.getAssociatedEntity());
        this.association = association;
    }

    /**
     * @return The association to be queried.
     */
    public Association getAssociation() {
        return association;
    }
}
