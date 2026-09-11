package io.micronaut.data.nitrite.model;

import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Version;

/**
 * An immutable entity carrying an assigned identity. Its initial version cannot be written into
 * the instance the caller passed in, so the provider has to hand back a different instance
 * carrying it, which is what Jakarta Data requires of the entity an insert returns.
 *
 * <p>The identity is assigned rather than generated because a generated identity is written
 * through a setter, which a record has none of.
 */
@MappedEntity
public record ImmutableVersionedPerson(
    @Id String id,
    String name,
    @Version Long version) {

    public ImmutableVersionedPerson(String id, String name) {
        this(id, name, null);
    }
}
