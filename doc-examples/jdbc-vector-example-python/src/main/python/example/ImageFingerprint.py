from dataclasses import dataclass
from typing import Annotated

from micronaut.data.annotation import GeneratedValue, Id, MappedEntity, VectorIndex, VectorStorage
from micronaut.data.model.vector import BinaryVector


@MappedEntity("image_fingerprint")
@dataclass(frozen=True)
class ImageFingerprint:
    id: Annotated[int | None, Id, GeneratedValue]
    name: str
    # VECTOR(16, BINARY): 16 bits
    hash: Annotated[BinaryVector, VectorStorage(length=16), VectorIndex(vectorIndexType="IVF", distanceType="HAMMING")]
