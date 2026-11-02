from typing import Annotated
from threading import RLock

from micronaut.data.notification import ChangeEvent, ChangeOperation
from jakarta.annotation import PostConstruct
from jakarta.inject import Inject, Singleton
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from java.util.concurrent.atomic import AtomicReference
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
        self.cacheEnabled = True
        self.lock = RLock()

    @PostConstruct
    def initialize(self) -> None:
        self.refreshCache()

    def find(self, id: int) -> Optional[str]:
        with self.lock:
            if self.cacheEnabled:
                return Optional.ofNullable(self.libraryNames.get().get(id))
            library = self.repository.findById(id).orElse(None)
            return Optional.of(library.name) if library is not None and library.capacity >= 10000 else Optional.empty()

    def contains(self, name: str) -> bool:
        with self.lock:
            if self.cacheEnabled:
                return self.libraryNames.get().containsValue(name)
            for library in self.repository.findByCapacityGreaterThanEquals(10000):
                if library.name == name:
                    return True
            return False

    # tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select="id, name",
        where="capacity >= 10000",
        properties=[
            OracleChangeNotification.Property(
                name="DCN_QUERY_CHANGE_NOTIFICATION",
                value="true"
            ),
        ]
    )
    def onLibraryChanged(self, event: ChangeEvent[Library]) -> None:
        with self.lock:
            if not self.cacheEnabled:
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
                self.cacheEnabled = False
                self.libraryNames.get().clear()
    # end::query[]

    def refreshCache(self) -> None:
        with self.lock:
            refreshed = ConcurrentHashMap()
            for library in self.repository.findByCapacityGreaterThanEquals(10000):
                refreshed.put(library.id, library.name)
            self.libraryNames.set(refreshed)
