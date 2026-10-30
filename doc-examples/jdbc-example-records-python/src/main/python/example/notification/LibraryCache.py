from typing import Annotated
from threading import RLock

from micronaut.data.notification import ChangeEvent, ChangeOperation
from micronaut.data.jdbc.notification.oracle import OracleChangeEventMetadata
from org.slf4j import LoggerFactory
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


@Requires(property="query-notification.object.enabled")
# tag::listener[]
@Singleton
class LibraryCache:
    # end::listener[]

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraries = AtomicReference(ConcurrentHashMap())
        self.cacheEnabled = True
        self.lock = RLock()
        self.log = LoggerFactory.getLogger("example.notification.LibraryCache")

    @PostConstruct
    def initialize(self) -> None:
        self.refreshCache()

    def find(self, name: str) -> Optional[Library]:
        with self.lock:
            currentLibraries = self.libraries.get().values() if self.cacheEnabled else self.repository.findAll()
            for library in currentLibraries:
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
            self.log.debug("Changed library table: {}.{}, ROWID: {}",
                metadata.schema().orElse("<unknown>"), metadata.table(), metadata.rowId())
        with self.lock:
            if not self.cacheEnabled:
                return
            if event.operation() in (ChangeOperation.INSERT, ChangeOperation.UPDATE):
                library = event.entity().orElse(None)
                if library is not None:
                    self.libraries.get().put(library.id, library)
            elif event.operation() in (ChangeOperation.DELETE, ChangeOperation.INVALIDATE):
                self.refreshCache()
            elif event.operation() == ChangeOperation.TERMINATED:
                self.cacheEnabled = False
                self.libraries.get().clear()
    # end::events[]

    def refreshCache(self) -> None:
        with self.lock:
            currentLibraries = self.repository.findAll()
            refreshed = ConcurrentHashMap()
            for library in currentLibraries:
                refreshed.put(library.id, library)
            self.libraries.set(refreshed)
