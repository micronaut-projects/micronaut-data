# tag::entity[]
# tag::entitywithprocedures[]
from dataclasses import dataclass
from typing import Annotated

from jakarta.persistence import Entity, GeneratedValue, Id, ManyToOne, NamedStoredProcedureQuery, StoredProcedureParameter
# end::entity[]
from java.lang import Long

from example.Manufacturer import Manufacturer


@NamedStoredProcedureQuery(name="calculateSum",
                           procedureName="calculateSumInternal",
                           parameters=[
                               StoredProcedureParameter(name="productId", mode="IN", type=Long),
                               StoredProcedureParameter(name="result", mode="OUT", type=Long)
                           ])
# tag::entity[]
@Entity
@dataclass
class Product:
    # end::entitywithprocedures[]
    name: str | None = None
    manufacturer: Annotated[Manufacturer | None, ManyToOne(optional=False, fetch="LAZY")] = None
    id: Annotated[int | None, Id, GeneratedValue] = None
# end::entity[]
