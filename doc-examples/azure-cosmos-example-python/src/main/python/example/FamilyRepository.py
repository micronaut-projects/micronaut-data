from typing import Annotated

from com.azure.cosmos.models import PartitionKey
from java.util import Date, Map, Optional
from micronaut.context.annotation import Parameter
from micronaut.data.annotation import Id, Join, Query
from micronaut.data.cosmos.annotation import CosmosRepository
from micronaut.data.repository import PageableRepository

from example.Address import Address
from example.Child import Child
from example.Family import Family
from example.GenderAware import GenderAware
from example.Pet import PetType


# TODO(python): the specification methods are declared on the repository instead of extending JpaSpecificationExecutor:
# its same-arity overloads (PredicateSpecification / QuerySpecification) are ambiguous for a Python lambda, see DISABLED_TESTS.md
@CosmosRepository
class FamilyRepository(PageableRepository[Family, str]):

    def updateRegistered(self, id: Annotated[str, Id], registered: bool) -> None: ...

    def updateAddress(self, id: Annotated[str, Id, Parameter("id")], address: Annotated[Address, Parameter("address")]) -> None: ...

    # Raw query for Cosmos update is not supported and calling this method will throw an error.
    @Query("UPDATE family f SET f.lastName=@p1 WHERE f.id=@p2")
    def updateLastName(self, id: str, lastName: str) -> None: ...

    # tag::partitionkey[]
    def queryById(self, id: str, partitionKey: PartitionKey) -> Optional[Family]: ...

    def updateRegisteredById(self, id: Annotated[str, Id], registered: bool, partitionKey: PartitionKey) -> None: ...

    def deleteByLastName(self, lastName: str, partitionKey: PartitionKey) -> None: ...

    def removeById(self, id: str, partitionKey: PartitionKey) -> None: ...
    # end::partitionkey[]

    # Raw query not supported for delete so this would throw an error
    @Query("DELETE FROM family f WHERE f.registered=@p1")
    def deleteByRegistered(self, registered: bool) -> None: ...

    def existsByIdAndRegistered(self, id: str, registered: bool) -> bool: ...

    def countByRegistered(self, registered: bool) -> int: ...

    @Query("SELECT VALUE f.lastName FROM family f ORDER BY f.lastName DESC OFFSET 0 LIMIT 1")
    def lastOrderedLastName(self) -> str: ...

    @Query("SELECT VALUE f.registeredDate FROM family f WHERE NOT IS_NULL(f.registeredDate) ORDER BY f.registeredDate DESC OFFSET 0 LIMIT 1")
    def lastOrderedRegisteredDate(self) -> Date: ...

    # tag::relations[]
    def findByAddressStateAndAddressCityOrderByAddressCity(self, state: str, city: str) -> list[Family]: ...

    def updateByAddressCounty(self, county: str, registered: bool, registeredDate: Date | None) -> None: ...

    @Join(value="children.pets", alias="pets")
    def findByChildrenPetsType(self, type: PetType) -> list[Family]: ...

    def findChildrenByChildrenPetsGivenName(self, name: str) -> list[Child]: ...
    # end::relations[]

    def findByIdIn(self, ids: list[str]) -> list[Family]: ...

    def findByIdNotIn(self, ids: list[str]) -> list[Family]: ...

    def findByLastNameLike(self, lastName: str) -> list[Family]: ...

    # tag::method_array_contains[]
    def findByTagsArrayContains(self, tag: str) -> list[Family]: ...
    # end::method_array_contains[]

    # tag::array_contains_partial[]
    @Query("SELECT DISTINCT VALUE f FROM family f WHERE ARRAY_CONTAINS(f.children, :gender, true)")
    def childrenArrayContainsGender(self, gender: Map.Entry[str, object]) -> list[Family]: ...
    # end::array_contains_partial[]


def last_name_equals(last_name: str):
    return lambda root, criteria_builder: criteria_builder.equal(root.get("lastName"), last_name)


def ids_in(*ids: str):
    return lambda root, criteria_builder: root.get("id").in_(list(ids))


def ids_not_in(*ids: str):
    return lambda root, criteria_builder: root.get("id").in_(list(ids)).not_()


# tag::predicate_array_contains[]
def tags_contain(tag: str):
    return lambda root, criteria_builder: criteria_builder.arrayContains(root.get("tags"), criteria_builder.literal(tag))
# end::predicate_array_contains[]


# tag::predicate_array_contains_partial[]
def children_array_contains_gender(gender: GenderAware):
    return lambda root, criteria_builder: criteria_builder.arrayContains(root.join("children"), criteria_builder.literal(gender))
# end::predicate_array_contains_partial[]
