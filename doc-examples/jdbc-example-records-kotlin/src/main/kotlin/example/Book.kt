package example

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.*
import java.util.*

@MappedEntity // (1)
data class Book(@Id
                @field:Id @GeneratedValue var id: Long?, // (2)
                @DateCreated @Nullable var dateCreated: Date? = null,
                var title: String,
                var pages: Int = 0) {
    constructor(title: String, pages: Int) : this(null, null, title, pages)
}
