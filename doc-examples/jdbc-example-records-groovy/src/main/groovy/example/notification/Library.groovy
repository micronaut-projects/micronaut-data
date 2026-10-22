package example.notification

import groovy.transform.ImmutableOptions
import groovy.transform.RecordType
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.Index
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.Srid
import io.micronaut.data.model.geo.Point

@MappedEntity
@RecordType
@ImmutableOptions(knownImmutableClasses = [Point])
class Library {
    @Id @GeneratedValue @Nullable Long id
    String name
    String email
    int capacity
    @Srid(4326) @Index(columns = "location") Point location
}
