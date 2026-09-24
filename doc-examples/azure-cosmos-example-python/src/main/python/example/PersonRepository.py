from java.util import Optional
from micronaut.data.cosmos.annotation import CosmosRepository
from micronaut.data.model import Page, Pageable, Sort
from micronaut.data.repository import CrudRepository
from micronaut.data.repository.jpa.criteria import PredicateSpecification, UpdateSpecification

from example.Person import Person


# TODO(python): the specification methods are declared on the repository instead of extending JpaSpecificationExecutor:
# with that base type a Python lambda passed to deleteAll/updateAll is ambiguous again between its
# PredicateSpecification and DeleteSpecification/UpdateSpecification overloads (TypeError: invalid
# instantiation of foreign object), and Python cannot declare the findOne/findAll overload pairs anyway
# tag::repository[]
@CosmosRepository
class PersonRepository(CrudRepository[Person, str]):
    # end::repository[]

    # tag::find[]
    def findOne(self, spec: PredicateSpecification[Person]) -> Optional[Person]: ...

    def findAll(self, spec: PredicateSpecification[Person] | None) -> list[Person]: ...

    def findAllSorted(self, spec: PredicateSpecification[Person], sort: Sort) -> list[Person]: ...

    def findPage(self, spec: PredicateSpecification[Person], pageable: Pageable) -> Page[Person]: ...
    # end::find[]

    # tag::count[]
    def count(self, spec: PredicateSpecification[Person]) -> int: ...
    # end::count[]

    # tag::update[]
    def updateAll(self, spec: UpdateSpecification[Person]) -> int: ...
    # end::update[]

    # tag::delete[]
    def deleteAll(self, spec: PredicateSpecification[Person]) -> int: ...
    # end::delete[]


# tag::specifications[]
# tag::allSpecifications[]
def name_equals(name: str):
    return lambda root, criteria_builder: criteria_builder.equal(root.get("name"), name)


def age_is_less_than(age: int):
    return lambda root, criteria_builder: criteria_builder.lessThan(root.get("age"), age)


# `and`, `or` and `not` are Python keywords: the trailing underscore maps to the CriteriaBuilder method
def and_(spec, other):
    return lambda root, criteria_builder: criteria_builder.and_(spec(root, criteria_builder), other(root, criteria_builder))


def or_(spec, other):
    return lambda root, criteria_builder: criteria_builder.or_(spec(root, criteria_builder), other(root, criteria_builder))


def not_(spec):
    return lambda root, criteria_builder: criteria_builder.not_(spec(root, criteria_builder))


# end::specifications[]
def set_new_name(new_name: str, where):
    def specification(root, query, criteria_builder):
        # tag::setUpdate[]
        query.set(root.get("name"), new_name)
        # end::setUpdate[]
        return where(root, criteria_builder)
    return specification


# end::allSpecifications[]
