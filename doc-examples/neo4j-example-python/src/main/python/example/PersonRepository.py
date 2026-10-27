from typing import Annotated

from java.util import UUID
from micronaut.data.graph.annotation import Direction, PathDirection, PathLimit
from micronaut.data.graph.model import GraphPath, GraphRelationship
from micronaut.data.neo4j.annotation import Neo4jRepository
from micronaut.data.repository import CrudRepository

from example.Person import Person


@Neo4jRepository
class PersonRepository(CrudRepository[Person, UUID]):

    def findByName(self, name: str) -> Person | None: ...
    def findByKnowsName(self, name: str) -> list[Person]: ...

    # tag::paths[]
    def findShortestPath(self, start: Person, end: Person) -> GraphPath[Person] | None: ...  # <1>

    def findShortestPathByFollows(self, start: UUID, end: UUID, maxDepth: Annotated[int, PathLimit]) -> GraphPath[Person] | None: ...  # <2>

    @PathLimit(4)
    def findAllPathsByFollows(self, start: Person, end: Person) -> list[GraphPath[Person]]: ...  # <3>

    def existsPathByFollows(self, start: Person, end: Person) -> bool: ...  # <4>
    # end::paths[]

    # tag::pathOptions[]
    def findShortestPathByFollowsBetweenName(self, start: str, end: str, maxDepth: Annotated[int, PathLimit]) -> GraphPath[Person] | None: ...  # <1>

    @PathLimit(4)
    @PathDirection(Direction.UNDIRECTED)  # <2>
    def existsPathByFollowsBetweenName(self, start: str, end: str) -> bool: ...
    # end::pathOptions[]

    # tag::pathDirection[]
    @PathLimit(4)
    def hasPathByFollows(self, start: Person, end: Person, direction: Annotated[Direction, PathDirection]) -> bool: ...  # <1>
    # end::pathDirection[]

    # tag::relationships[]
    def findRelationships(self, start: Person, end: Person) -> list[GraphRelationship[Person, Person]]: ...

    def countRelationshipsByFollows(self) -> int: ...
    # end::relationships[]
