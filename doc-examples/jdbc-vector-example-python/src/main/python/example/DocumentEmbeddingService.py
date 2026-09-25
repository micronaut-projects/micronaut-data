from jakarta.inject import Singleton
from micronaut.data.model.vector import Vector

from example.DocumentEmbeddingRepository import DocumentEmbeddingRepository


@Singleton
class DocumentEmbeddingService:

    def __init__(self, repository: DocumentEmbeddingRepository):
        self.repository = repository

    def save_one(self) -> None:
        # tag::create_vector[]
        vec = Vector.of(0.1, 0.2, 0.3)
        self.repository.save(vec)
        # end::create_vector[]
