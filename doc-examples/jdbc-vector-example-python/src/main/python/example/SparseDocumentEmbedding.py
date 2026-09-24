from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, VectorStorage
from micronaut.data.model.vector import Vector


@MappedEntity("sparse_document_embedding")
@dataclass(frozen=True)
class SparseDocumentEmbedding:
    id: Annotated[int | None, Id, GeneratedValue]
    embedding: Annotated[Vector, VectorStorage(length=5, shape="SPARSE")]
