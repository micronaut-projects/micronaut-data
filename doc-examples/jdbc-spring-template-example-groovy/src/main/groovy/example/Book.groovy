package example

import groovy.transform.Canonical
import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

@Canonical
@MappedEntity
class Book {
    @Id @GeneratedValue @Nullable Long id
    String title
    int pages
}
