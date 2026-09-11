package example

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity

// tag::page[]
@MappedEntity
class Page {
    @Id
    @GeneratedValue
    var id: String? = null

    var pageNumber: Int = 0

    var content: String = ""

    constructor()

    constructor(pageNumber: Int, content: String) {
        this.pageNumber = pageNumber
        this.content = content
    }
}
// end::page[]
