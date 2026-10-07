from typing import Annotated
from threading import RLock

from micronaut.data.jdbc.notification import ChangeEvent, ChangeOperation
from micronaut.data.jdbc.notification.oracle import OracleChangeEventMetadata
from org.slf4j import LoggerFactory
from jakarta.annotation import PostConstruct
from jakarta.inject import Inject, Singleton
from java.util import Optional
from java.util.concurrent import ConcurrentHashMap
from micronaut.context.annotation import Requires
from micronaut.data.jdbc.annotation import ChangeListener, OracleChangeNotification

from example.notification.Library import Library
from example.notification.LibraryRepository import LibraryRepository


@Requires(property="query-notification.object.enabled")
# tag::listener[]
@Singleton
class LibraryCache:
    # end::listener[]

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraries = ConcurrentHashMap()
        self.lock = RLock()
        self.log = LoggerFactory.getLogger("example.notification.LibraryCache")

    @PostConstruct
    def initialize(self) -> None:
        self.refreshCache()

    def find(self, name: str) -> Optional[Library]:
        for library in self.libraries.values():
            if library.name == name:
                return Optional.of(library)
        return Optional.empty()

    # tag::listener[]
    # tag::events[]
    @ChangeListener
    @OracleChangeNotification
    def onLibraryChanged(self, event: ChangeEvent[Library]) -> None:
        # end::listener[]
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
