package example

import io.micronaut.data.exceptions.OptimisticLockException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest(transactional = false)
class ArticleRepositorySpec extends AbstractNeo4jSpec {

    @Inject
    ArticleRepository articleRepository

    void cleanup() {
        articleRepository.deleteAll()
    }

    void "optimistic locking"() {
        when:
        // tag::optimisticLocking[]
        articleRepository.save(new Article(slug: "graphs", title: "Graphs", author: "Ada")) // <1>
        def first = articleRepository.findById("graphs").get()
        def second = articleRepository.findById("graphs").get()

        first.title = "Graph databases"
        articleRepository.update(first) // <2>

        second.title = "Graphs everywhere"
        articleRepository.update(second) // <3>
        // end::optimisticLocking[]

        then:
        thrown(OptimisticLockException)
        with(articleRepository.findById("graphs").get()) {
            title == "Graph databases"
            version == 1L
        }
        articleRepository.findByAuthor("Ada")*.slug == ["graphs"]
    }
}
