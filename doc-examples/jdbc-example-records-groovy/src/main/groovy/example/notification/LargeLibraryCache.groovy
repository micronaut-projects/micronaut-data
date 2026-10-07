package example.notification

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent
import io.micronaut.data.jdbc.notification.ChangeOperation

import java.util.concurrent.ConcurrentHashMap

@Context
@Requires(property = "query-notification.query.enabled")
class LargeLibraryCache implements ApplicationEventListener<StartupEvent> {

    private final LibraryRepository repository
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>()

    LargeLibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @Override
    void onApplicationEvent(StartupEvent event) {
        refreshCache()
    }

    Optional<Library> find(String name) {
        libraries.values().stream()
            .filter { library -> library.name() == name }
            .findFirst()
    }

    // tag::query[]
    @ChangeListener
    @OracleChangeNotification(
        select = "name",
        where = "capacity >= 10000",
        properties = @OracleChangeNotification.Property(
            name = "DCN_QUERY_CHANGE_NOTIFICATION",
            value = "true"
        )
    )
    synchronized void onLibraryChanged(ChangeEvent<Library> event) {
        switch (event.operation()) {
            case ChangeOperation.INSERT:
            case ChangeOperation.UPDATE:
                event.entity().ifPresent { library ->
                    if (library.capacity() >= 10000) {
                        libraries.put(library.id(), library)
                    } else {
                        libraries.remove(library.id())
                    }
                }
                break
            case ChangeOperation.DELETE:
            case ChangeOperation.INVALIDATE:
                refreshCache()
                break
        }
    }
    // end::query[]

    private synchronized void refreshCache() {
        def currentLibraries = repository.findByCapacityGreaterThanEquals(10000)
        libraries.clear()
        currentLibraries.forEach { library -> libraries.put(library.id(), library) }
    }
}
