package io.micronaut.data.jdbc.oraclexe.jsonview;

import io.micronaut.data.annotation.Embeddable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Relation;
import io.micronaut.serde.annotation.Serdeable;

/**
 * The embedded {@code details} has no prefix, so {@code details.label} is the column {@code label}, while the top level
 * {@code label} is the column {@code PARCEL_LABEL}. The embedded {@code destination} has the prefix {@code dest}, so
 * {@code destination.city} is the column {@code dest_city}. {@link ParcelView} groups {@code sender} into an embedded
 * object that only the view has.
 */
@MappedEntity(value = "TBL_PARCEL", alias = "pc")
public record Parcel(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,
    @MappedProperty("PARCEL_LABEL")
    String label,
    @Relation(Relation.Kind.EMBEDDED)
    Details details,
    @MappedProperty("dest")
    @Relation(Relation.Kind.EMBEDDED)
    Destination destination,
    String sender
) {
    @Embeddable
    @Serdeable
    public record Details(String label) {
    }

    @Embeddable
    @Serdeable
    public record Destination(String city) {
    }
}
