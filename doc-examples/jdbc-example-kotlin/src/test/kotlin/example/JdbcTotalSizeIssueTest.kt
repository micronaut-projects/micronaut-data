package example

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.micronaut.data.model.Pageable
import io.micronaut.data.model.Sort
import io.micronaut.data.model.Sort.Order
import io.micronaut.data.runtime.criteria.get
import io.micronaut.data.runtime.criteria.where
import io.micronaut.test.annotation.Sql
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import jakarta.persistence.criteria.JoinType

@Sql("classpath:init.sql")
@MicronautTest
class JdbcTotalSizeIssueTest(
    private val authorRepository: AuthorRepository
) : StringSpec({

    "test totalSize should match the number of authors" {
        val criteria = where<Author> {
            val genresJoin = root.joinList<Author, Genre>("genres", JoinType.LEFT)

            or {
                genresJoin[Genre::name] eq "Horror"
                genresJoin[Genre::name] eq "Thriller"
            }
        }
        val all = authorRepository.findAll().toMutableList()
        all.sortBy { it.name }
        all.size shouldBe 3
        all[0].name shouldBe "Dan Brown"
        all[0].genres.map { it.name }.sorted() shouldBe listOf("Mystery", "Thriller")
        all[1].name shouldBe "Stephen King"
        all[1].genres.map { it.name }.sorted() shouldBe listOf("Horror", "Thriller")
        all[2].name shouldBe "William Shakespeare"
        all[2].genres.map { it.name }.sorted() shouldBe listOf("Comedy")

        var page = authorRepository.findAll(criteria, Pageable.from(0, 10, Sort.of(Order.asc("name"))))
        page.totalSize shouldBe page.content.size
        page.totalSize shouldBe 2
        page.content.map { it.name } shouldBe listOf("Dan Brown", "Stephen King")
        page.content[0].genres.map { it.name }.sorted() shouldBe listOf("Mystery", "Thriller")
        page.content[1].genres.map { it.name }.sorted() shouldBe listOf("Horror", "Thriller")

        var pageable = Pageable.from(0, 1, Sort.of(Order.asc("name")))
        page = authorRepository.findAll(criteria, pageable)
        page.totalSize shouldBe 2
        page.content.size shouldBe 1
        page.content[0].name shouldBe "Dan Brown"
        page.content[0].genres.map { it.name }.sorted() shouldBe listOf("Mystery", "Thriller")

        pageable = pageable.next()
        page = authorRepository.findAll(criteria, pageable)
        page.totalSize shouldBe 2
        page.content.size shouldBe 1
        page.content[0].name shouldBe "Stephen King"
        page.content[0].genres.map { it.name }.sorted() shouldBe listOf("Horror", "Thriller")

        pageable = pageable.next()
        page = authorRepository.findAll(criteria, pageable)
        page.totalSize shouldBe 2
        page.content.size shouldBe 0

        pageable = pageable.previous()
        page = authorRepository.findAll(criteria, pageable)
        page.totalSize shouldBe 2
        page.content.size shouldBe 1
        page.content[0].name shouldBe "Stephen King"
        page.content[0].genres.map { it.name }.sorted() shouldBe listOf("Horror", "Thriller")
    }
})
