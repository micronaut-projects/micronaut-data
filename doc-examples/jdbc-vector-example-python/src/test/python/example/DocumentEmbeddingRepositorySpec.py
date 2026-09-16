from typing import Annotated

from jakarta.inject import Inject
from micronaut.data.model.vector import Vector
from micronaut.data.model.vector.search import Score, ScoringFunction
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.DocumentEmbeddingRepository import DocumentEmbeddingRepository


@MicronautTest(transactional=False)
class DocumentEmbeddingRepositorySpec:

    repository: Annotated[DocumentEmbeddingRepository, Inject]

    @Test
    def saveAndFetchEmbedding(self):
        self.repository.deleteAll()

        # tag::create_vector[]
        vec = Vector.of(0.1, 0.2, 0.3)
        saved = self.repository.save(vec)
        # end::create_vector[]

        assert saved is not None
        assert saved.id is not None

        reloaded = self.repository.findById(saved.id).orElse(None)
        assert reloaded is not None
        assert reloaded.id == saved.id
        assert [round(v, 6) for v in reloaded.embedding.toDoubleArray()] == [0.1, 0.2, 0.3]

        self.repository.save(Vector.of(0.15, 0.2, 0.25))
        self.repository.save(Vector.of(0.9, 0.1, 0.1))

        near = self.repository.findTop2ByEmbeddingNear(vec, 2.0)
        assert len(near) == 2
        assert any(it.id == saved.id for it in near)

        scored = self.repository.searchByEmbeddingNear(vec, Score(2.0), ScoringFunction.COSINE)
        assert not scored.results().isEmpty()
