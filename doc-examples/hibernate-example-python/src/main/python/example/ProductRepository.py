from io.reactivex.rxjava3.core import Maybe, Single
from jakarta.transaction import Transactional
from java.util.concurrent import CompletableFuture
from micronaut.data.annotation import Join, Repository
from micronaut.data.annotation.sql import Procedure
from micronaut.data.jpa.annotation import EntityGraph
from micronaut.data.repository import CrudRepository
from micronaut.data.repository.jpa.criteria import QuerySpecification

from example.Product import Product


# TODO(python): the specification methods are declared on the repository instead of extending JpaSpecificationExecutor:
# with that base type a Python lambda passed to deleteAll/updateAll is ambiguous again between its
# PredicateSpecification and DeleteSpecification/UpdateSpecification overloads (TypeError: invalid
# instantiation of foreign object), and Python cannot declare the findOne/findAll overload pairs anyway
# tag::join[]
# tag::async[]
# tag::specifications[]
# tag::procedure[]
@Repository
class ProductRepository(CrudRepository[Product, int]):
    # end::join[]
    # end::async[]
    # end::specifications[]
    # end::procedure[]

    # tag::join[]
    @Join(value="manufacturer", type="FETCH")  # <1>
    def list(self) -> list[Product]: ...
    # end::join[]

    # tag::entitygraph[]
    @EntityGraph(attributePaths=["manufacturer", "title"])  # <1>
    def findAll(self) -> list[Product]: ...
    # end::entitygraph[]

    # tag::async[]
    @Join("manufacturer")
    def findByNameContains(self, str: str) -> CompletableFuture[Product]: ...

    def countByManufacturerName(self, name: str) -> CompletableFuture[int]: ...
    # end::async[]

    # tag::reactive[]
    @Join("manufacturer")
    def queryByNameContains(self, str: str) -> Maybe[Product]: ...

    def countDistinctByManufacturerName(self, name: str) -> Single[int]: ...
    # end::reactive[]

    # tag::procedure[]
    @Procedure(named="calculateSum")
    def calculateSum(self, productId: int) -> int: ...  # <1>

    @Procedure("calculateSumInternal")
    def calculateSumCustom(self, productId: int) -> int: ...  # <2>
    # end::procedure[]

    # tag::specifications[]
    def findAllBySpecification(self, specification: QuerySpecification[Product]) -> list[Product]: ...

    @Transactional
    def find_by_name(self, name: str, case_insensitive: bool, include_blank: bool) -> list[Product]:
        if case_insensitive:
            specification = name_equals_case_insensitive(name)
        else:
            specification = name_equals(name)
        if include_blank:
            specification = or_(specification, name_equals(""))
        return self.findAllBySpecification(specification)


# tag::spec[]
def name_equals(name: str):
    return lambda root, query, criteria_builder: criteria_builder.equal(root.get("name"), name)


def name_equals_case_insensitive(name: str):
    return lambda root, query, criteria_builder: criteria_builder.equal(criteria_builder.lower(root.get("name")), name.lower())


def or_(specification, other):  # `or` is a Python keyword: the trailing underscore maps to CriteriaBuilder.or
    return lambda root, query, criteria_builder: criteria_builder.or_(
        specification(root, query, criteria_builder), other(root, query, criteria_builder))
# end::spec[]
# end::specifications[]
