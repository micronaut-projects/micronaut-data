package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.JsonView;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

@JsonView(entity = Parcel.class)
@Serdeable
public record ParcelView(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    @Nullable
    Long id,
    String label,
    @Relation(Relation.Kind.EMBEDDED)
    Parcel.Details details,
    @Relation(Relation.Kind.EMBEDDED)
    Parcel.Destination destination,
    @Relation(Relation.Kind.EMBEDDED)
    Parties parties
) {
    /**
     * Not an embedded object of {@link Parcel}: it groups the top level {@code sender} of the entity.
     */
    @Embeddable
    @Serdeable
    public record Parties(String sender) {
    }
}
