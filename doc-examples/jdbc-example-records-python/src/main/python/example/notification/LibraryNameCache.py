from typing import Annotated

from micronaut.data.notification import ChangeEvent, ChangeOperation
from jakarta.annotation import PostConstruct
from jakarta.inject import Inject, Singleton
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from java.util.concurrent.atomic import AtomicBoolean, AtomicReference
from micronaut.context.annotation import Requires
from micronaut.data.annotation import ChangeListener
from micronaut.data.jdbc.annotation import OracleChangeNotification

from example.notification.Library import Library
from example.notification.LibraryRepository import LibraryRepository


@Singleton
@Requires(property="query-notification.query.enabled")
class LibraryNameCache:

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraryNames = AtomicReference(ConcurrentHashMap())
        self.cacheEnabled = AtomicBoolean(True)

    @PostConstruct
    def initialize(self) -> None:
        self.refreshCache()

    def find(self, id: int) -> Optional[str]:
        if self.cacheEnabled.get():
            return Optional.ofNullable(self.libraryNames.get().get(id))
        library = self.repository.findById(id).orElse(None)
        return Optional.of(library.name) if library is not None and library.capacity >= 10000 else Optional.empty()

    def contains(self, name: str) -> bool:
        if self.cacheEnabled.get():
            return self.libraryNames.get().containsValue(name)
        for library in self.repository.findByCapacityGreaterThanEquals(10000):
            if library.name == name:
                return True
        return False

    # tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select="id, name", # <1>
        where="capacity >= 10000", # <2>
        properties=[
            OracleChangeNotification.Property(
                name="DCN_QUERY_CHANGE_NOTIFICATION",
                value="true" # <3>
            ),
        ]
    )
    def onLibraryChanged(self, event: ChangeEvent[Library]) -> None:
        if not self.cacheEnabled.get():
            return
        if event.operation() in (ChangeOperation.INSERT, ChangeOperation.UPDATE):
            library = event.entity().orElse(None)
            if library is not None:
                if library.capacity >= 10000:
                    self.libraryNames.get().put(library.id, library.name)
                else:
                    self.libraryNames.get().remove(library.id)
        elif event.operation() in (ChangeOperation.DELETE, ChangeOperation.INVALIDATE):
            self.refreshCache()
        elif event.operation() == ChangeOperation.TERMINATED:
            self.cacheEnabled.set(False)
            self.libraryNames.get().clear()
    # end::query[]

    def refreshCache(self) -> None:
        refreshed = ConcurrentHashMap()
        for library in self.repository.findByCapacityGreaterThanEquals(10000):
            refreshed.put(library.id, library.name)
        self.libraryNames.set(refreshed)
