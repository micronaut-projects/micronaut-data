from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.exceptions import OptimisticLockException
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import AfterEach, Test

from example.Article import Article
from example.ArticleRepository import ArticleRepository


@MicronautTest(transactional=False)
class ArticleRepositorySpec:

    articleRepository: Annotated[ArticleRepository, Inject]

    @AfterEach
    def afterEach(self):
        self.articleRepository.deleteAll()

    @Test
    def testOptimisticLocking(self):
        # tag::optimisticLocking[]
        self.articleRepository.save(Article("graphs", "Graphs", "Ada"))  # <1>
        first = self.articleRepository.findById("graphs").get()
        second = self.articleRepository.findById("graphs").get()

        first.title = "Graph databases"
        self.articleRepository.update(first)  # <2>

        second.title = "Graphs everywhere"
        try:
            self.articleRepository.update(second)  # <3>
            locked = False
        except OptimisticLockException:
            locked = True
        # end::optimisticLocking[]

        assert locked
        stored = self.articleRepository.findById("graphs").get()
        assert stored.title == "Graph databases"
        assert stored.version == 1
        assert [a.slug for a in self.articleRepository.findByAuthor("Ada")] == ["graphs"]
