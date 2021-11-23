
package example


import io.micronaut.serde.annotation.Serdeable

@Serdeable
class BookDTO {

    String title
    int pages
}
