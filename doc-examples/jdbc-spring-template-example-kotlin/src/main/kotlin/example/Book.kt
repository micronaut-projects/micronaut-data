package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::book[]
@MappedEntity
data class Book(@Id
                @GeneratedValue
                var id: Long?,
                var title: String,
                var pages: Int = 0)
