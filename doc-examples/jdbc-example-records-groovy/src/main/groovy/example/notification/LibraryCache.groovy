package example.notification

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import io.micronaut.data.jdbc.annotation.ChangeListener
import io.micronaut.data.jdbc.annotation.OracleChangeNotification
import io.micronaut.data.jdbc.notification.ChangeEvent

import java.util.concurrent.ConcurrentHashMap

@Context
@Requires(property = "query-notification.object.enabled")
class LibraryCache implements ApplicationEventListener<StartupEvent> {

    private final LibraryRepository repository
    private final Map<Long, Library> libraries = new ConcurrentHashMap<>()

    LibraryCache(LibraryRepository repository) {
        this.repository = repository
    }

    @Override
    void onApplicationEvent(StartupEvent event) {
        repository.findAll().forEach { library -> libraries.put(library.id(), library) }
    }

    Optional<Library> find(String name) {
        libraries.values().stream()
            .filter { library -> library.name() == name }
            .findFirst()
    }

    @ChangeListener
    @OracleChangeNotification
    void onLibraryChanged(ChangeEvent<Library> event) {
        event.entity().ifPresent { library -> libraries.put(library.id(), library) }
    }
}
