from typing import Annotated
from threading import RLock

from micronaut.data.jdbc.notification import ChangeEvent, ChangeOperation
from micronaut.data.jdbc.notification.oracle import OracleChangeEventMetadata
from org.slf4j import LoggerFactory
from jakarta.inject import Inject
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from micronaut.context.annotation import Context, Requires
from micronaut.context.event import ApplicationEventListener, StartupEvent
from micronaut.data.jdbc.annotation import ChangeListener, OracleChangeNotification

from example.notification.Library import Library
from example.notification.LibraryRepository import LibraryRepository


# tag::listener[]
@Context
@Requires(property="query-notification.object.enabled")
class LibraryCache(ApplicationEventListener[StartupEvent]):

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraries = ConcurrentHashMap()
        self.lock = RLock()
        self.log = LoggerFactory.getLogger("example.notification.LibraryCache")

    def onApplicationEvent(self, event: StartupEvent) -> None:
        self.refreshCache()

    def find(self, name: str) -> Optional[Library]:
        for library in self.libraries.values():
            if library.name == name:
                return Optional.of(library)
        return Optional.empty()

    # tag::events[]
    # tag::datasource[]
    @ChangeListener(dataSource="default")
    # end::datasource[]
    @OracleChangeNotification
    def onLibraryChanged(self, event: ChangeEvent[Library]) -> None:
        with self.lock:
            metadata = event.metadata(OracleChangeEventMetadata).orElse(None)
            if metadata is not None:
                self.log.debug("Changed library ROWID: {}", metadata.rowId())
            if event.operation() in (ChangeOperation.INSERT, ChangeOperation.UPDATE):
                library = event.entity().orElse(None)
                if library is not None:
                    self.libraries.put(library.id, library)
            elif event.operation() in (ChangeOperation.DELETE, ChangeOperation.INVALIDATE):
                self.refreshCache()
    # end::events[]

    def refreshCache(self) -> None:
        with self.lock:
            currentLibraries = self.repository.findAll()
            self.libraries.clear()
            for library in currentLibraries:
                self.libraries.put(library.id, library)
# end::listener[]
