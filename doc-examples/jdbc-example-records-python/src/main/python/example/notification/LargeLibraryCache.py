from typing import Annotated
from threading import RLock

from micronaut.data.jdbc.notification import ChangeEvent, ChangeOperation
from jakarta.inject import Inject
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from micronaut.context.annotation import Context, Requires
from micronaut.context.event import ApplicationEventListener, StartupEvent
from micronaut.data.jdbc.annotation import ChangeListener, OracleChangeNotification

from example.notification.Library import Library
from example.notification.LibraryRepository import LibraryRepository


@Context
@Requires(property="query-notification.query.enabled")
class LargeLibraryCache(ApplicationEventListener[StartupEvent]):

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraries = ConcurrentHashMap()
        self.lock = RLock()

    def onApplicationEvent(self, event: StartupEvent) -> None:
        self.refreshCache()

    def find(self, name: str) -> Optional[Library]:
        for library in self.libraries.values():
            if library.name == name:
                return Optional.of(library)
        return Optional.empty()

    # tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select="name",
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
            if event.operation() in (ChangeOperation.INSERT, ChangeOperation.UPDATE):
                library = event.entity().orElse(None)
                if library is not None:
                    if library.capacity >= 10000:
                        self.libraries.put(library.id, library)
                    else:
                        self.libraries.remove(library.id)
            elif event.operation() in (ChangeOperation.DELETE, ChangeOperation.INVALIDATE):
                self.refreshCache()
    # end::query[]

    def refreshCache(self) -> None:
        with self.lock:
            currentLibraries = self.repository.findByCapacityGreaterThanEquals(10000)
            self.libraries.clear()
            for library in currentLibraries:
                self.libraries.put(library.id, library)
