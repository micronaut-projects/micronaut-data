from typing import Annotated

from micronaut.data.jdbc.notification import ChangeEvent
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
class CustomLibraryCache(ApplicationEventListener[StartupEvent]):

    repository: Annotated[LibraryRepository, Inject]

    def __init__(self, repository: LibraryRepository):
        self.repository = repository
        self.libraries = ConcurrentHashMap()

    def onApplicationEvent(self, event: StartupEvent) -> None:
        for library in self.repository.findByCapacityGreaterThanEquals(10000):
            self.libraries.put(library.id, library)

    def find(self, name: str) -> Optional[Library]:
        for library in self.libraries.values():
            if library.name == name:
                return Optional.of(library)
        return Optional.empty()

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
        library = event.entity().orElse(None)
        if library is None:
            return
        if library.capacity >= 10000:
            self.libraries.put(library.id, library)
        else:
            self.libraries.remove(library.id)
