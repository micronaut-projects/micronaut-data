from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Column
from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, VectorIndex
from micronaut.data.model.vector import Vector


@MappedEntity("document_embedding")
@dataclass(frozen=True)
class DocumentEmbedding:
    id: Annotated[int | None, Id, GeneratedValue]
    embedding: Annotated[Vector, VectorIndex(vectorIndexType="IVF", distanceType="COSINE", accuracy=90), Column(length=3)]
