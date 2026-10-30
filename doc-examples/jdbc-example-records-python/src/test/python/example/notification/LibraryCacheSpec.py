from java.sql import Connection
from java.util import HashMap
from java.util.concurrent import TimeUnit
from micronaut.context import ApplicationContext
from micronaut.data.jdbc.operations import DefaultJdbcRepositoryOperations
from micronaut.data.model.geo import Point
from micronaut.data.notification import ChangeEvent, ChangeOperation
from micronaut.data.jdbc.notification.oracle import OracleChangeEventMetadata
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.notification.Library import Library
from example.notification.ChangeEventRecorder import ChangeEventRecorder
from example.notification.LibraryCache import LibraryCache
from example.notification.LibraryNameCache import LibraryNameCache
from example.notification.LibraryRepository import LibraryRepository


@MicronautTest(transactional=False, environments=["oracle-cqn"])
class LibraryCacheSpec:

    @Test
    def testObjectChangeNotifications(self):
        self.grantChangeNotificationPrivilege()
        self.prepareData()
        try:
            context = self.startContext({
                "query-notification.object.enabled": "true",
                "datasources.default.schema-generate": "NONE",
            })
            try:
                repository = context.getBean(LibraryRepository)
                cache = context.getBean(LibraryCache)
                events = context.getBean(ChangeEventRecorder)
                library1 = cache.find("Library 1").orElse(None)
                library2 = cache.find("Library 2").orElse(None)
                library3 = cache.find("Library 3").orElse(None)
                library4 = cache.find("Library 4").orElse(None)
                assert library1.email == "library1@example.com"
                assert library2.capacity == 9000
                assert library3.capacity == 10000
                assert library4.location.x() == 20.68960 and library4.location.y() == 44.01650

                renamed = Library(library1.id, "Library 1 Updated", library1.email, library1.capacity, library1.location)
                repository.save(renamed)
                self.assertRowEvent(events, ChangeOperation.UPDATE, renamed)
                self.assertLibrary(cache.find(renamed.name).orElse(None), renamed)

                expanded = Library(library2.id, library2.name, library2.email, 12000, library2.location)
                repository.save(expanded)
                self.assertRowEvent(events, ChangeOperation.UPDATE, expanded)
                self.assertLibrary(cache.find(expanded.name).orElse(None), expanded)

                relocated = Library(library3.id, library3.name, library3.email, library3.capacity, Point(20.91140, 44.81250))
                repository.save(relocated)
                self.assertRowEvent(events, ChangeOperation.UPDATE, relocated)
                self.assertLibrary(cache.find(relocated.name).orElse(None), relocated)

                inserted = repository.save(Library(None, "Library 5", "library5@example.com", 8000, Point(21.16560, 44.77220)))
                insert = self.assertRowEvent(events, ChangeOperation.INSERT, inserted)
                self.assertLibrary(cache.find(inserted.name).orElse(None), inserted)

                repository.deleteById(inserted.id)
                delete = self.assertRowEvent(events, ChangeOperation.DELETE, None)
                assert insert.metadata(OracleChangeEventMetadata).orElse(None).rowId() == delete.metadata(OracleChangeEventMetadata).orElse(None).rowId()
                assert not cache.find(inserted.name).isPresent()

                self.truncateLibraries(context)
                self.assertInvalidation(events)
                for name in (renamed.name, expanded.name, relocated.name, "Library 4"):
                    assert not cache.find(name).isPresent()
            finally:
                context.close()
        finally:
            self.removeData()

    @Test
    def testQueryChangeNotifications(self):
        self.grantChangeNotificationPrivilege()
        self.prepareData()
        try:
            context = self.startContext({
                "query-notification.query.enabled": "true",
                "datasources.default.schema-generate": "NONE",
            })
            try:
                repository = context.getBean(LibraryRepository)
                cache = context.getBean(LibraryNameCache)
                events = context.getBean(ChangeEventRecorder)
                assert not cache.contains("Library 1")
                assert not cache.contains("Library 2")
                assert cache.contains("Library 3")
                library3 = next(library for library in repository.findAll() if library.name == "Library 3")
                assert cache.contains("Library 4")
                library4 = next(library for library in repository.findAll() if library.name == "Library 4")
                library1 = next(library for library in repository.findAll() if library.name == "Library 1")

                entered = Library(library1.id, library1.name, library1.email, 10000, library1.location)
                repository.save(entered)
                self.assertRowEvent(events, ChangeOperation.UPDATE, entered)
                assert cache.contains(entered.name)
                assert cache.find(entered.id).orElse(None) == entered.name

                # The event reloads the complete entity; the cache retains only its ID and name.
                updated = Library(library4.id, "Library 4 Updated", "updated@example.com", 16000, Point(20.91140, 44.81250))
                repository.save(updated)
                self.assertRowEvent(events, ChangeOperation.UPDATE, updated)
                assert cache.contains(updated.name)
                assert not cache.contains(library4.name)
                assert cache.find(updated.id).orElse(None) == updated.name

                inserted = repository.save(Library(None, "Library 5", "library5@example.com", 12000, Point(21.16560, 44.77220)))
                self.assertRowEvent(events, ChangeOperation.INSERT, inserted)
                assert cache.contains(inserted.name)

                left = Library(library3.id, library3.name, library3.email, 9000, library3.location)
                repository.save(left)
                self.assertRowEvent(events, ChangeOperation.UPDATE, left)
                assert not cache.contains(left.name)
                assert not cache.find(left.id).isPresent()

                repository.deleteById(updated.id)
                self.assertRowEvent(events, ChangeOperation.DELETE, None)
                assert not cache.contains(updated.name)

                self.truncateLibraries(context)
                self.assertInvalidation(events)
                assert not cache.contains(entered.name)
                assert not cache.contains(inserted.name)
            finally:
                context.close()
        finally:
            self.removeData()

    @Test
    def testQueryIgnoresChangesOutsideFilter(self):
        self.grantChangeNotificationPrivilege()
        self.prepareData()
        try:
            context = self.startContext({
                "query-notification.query.enabled": "true",
                "datasources.default.schema-generate": "NONE",
            })
            try:
                repository = context.getBean(LibraryRepository)
                cache = context.getBean(LibraryNameCache)
                events = context.getBean(ChangeEventRecorder)
                assert cache.contains("Library 3")
                included = next(library for library in repository.findAll() if library.name == "Library 3")
                control = Library(included.id, "Library 3 Included", included.email, included.capacity, included.location)
                repository.save(control)
                self.assertRowEvent(events, ChangeOperation.UPDATE, control)

                excluded = next(library for library in repository.findAll() if library.name == "Library 1")
                repository.save(Library(excluded.id, "Library 1 Still Excluded", excluded.email, 9999, excluded.location))
                assert events.poll(1, TimeUnit.SECONDS) is None, "An update outside the registered query must not invoke the listener"
                assert not cache.contains("Library 1 Still Excluded")

                repository.save(Library(None, "Excluded Insert", "excluded@example.com", 9999, Point(20, 44)))
                assert events.poll(1, TimeUnit.SECONDS) is None, "An insert outside the registered query must not invoke the listener"
                assert not cache.contains("Excluded Insert")
            finally:
                context.close()
        finally:
            self.removeData()

    def nextEvent(self, events: ChangeEventRecorder, operation: ChangeOperation) -> ChangeEvent[Library]:
        event = events.poll(10, TimeUnit.SECONDS)
        assert event is not None, "Expected " + str(operation) + " notification"
        assert event.operation() == operation
        return event

    def assertRowEvent(self, events: ChangeEventRecorder, operation: ChangeOperation, entity: Library | None) -> ChangeEvent[Library]:
        event = self.nextEvent(events, operation)
        loaded = event.entity().orElse(None)
        if entity is None:
            assert loaded is None
        else:
            self.assertLibrary(loaded, entity)
        metadata = event.metadata(OracleChangeEventMetadata).orElse(None)
        assert metadata is not None and str(metadata.rowId()).strip()
        return event

    def assertLibrary(self, actual: Library | None, expected: Library) -> None:
        assert actual is not None
        assert actual.id == expected.id
        assert actual.name == expected.name
        assert actual.email == expected.email
        assert actual.capacity == expected.capacity
        assert actual.location.x() == expected.location.x()
        assert actual.location.y() == expected.location.y()

    def assertInvalidation(self, events: ChangeEventRecorder) -> None:
        event = self.nextEvent(events, ChangeOperation.INVALIDATE)
        assert not event.entity().isPresent()
        assert not event.metadata(OracleChangeEventMetadata).isPresent()

    def truncateLibraries(self, context: ApplicationContext):
        operations = context.getBean(DefaultJdbcRepositoryOperations)
        operations.execute(lambda connection: self.truncateTable(connection))

    def truncateTable(self, connection: Connection) -> bool:
        statement = connection.createStatement()
        try:
            statement.execute("TRUNCATE TABLE LIBRARY")
        finally:
            statement.close()
        return True

    def grantChangeNotificationPrivilege(self):
        context = self.startContext({
            "datasources.default.username": "system",
            "datasources.default.password": "test",
            "datasources.default.schema-generate": "NONE",
        })
        try:
            operations = context.getBean(DefaultJdbcRepositoryOperations)
            operations.execute(lambda connection: self.grantPrivilege(connection))
        finally:
            context.close()

    def grantPrivilege(self, connection: Connection) -> bool:
        statement = connection.createStatement()
        try:
            statement.execute("GRANT CHANGE NOTIFICATION TO test")
        finally:
            statement.close()
        return True

    def prepareData(self):
        context = self.startContext({"datasources.default.schema-generate": "CREATE"})
        try:
            repository = context.getBean(LibraryRepository)
            repository.save(Library(None, "Library 1", "library1@example.com", 5000, Point(20.46513, 44.80401)))
            repository.save(Library(None, "Library 2", "library2@example.com", 9000, Point(19.83355, 45.26714)))
            repository.save(Library(None, "Library 3", "library3@example.com", 10000, Point(21.89830, 43.32090)))
            repository.save(Library(None, "Library 4", "library4@example.com", 15000, Point(20.68960, 44.01650)))
        finally:
            context.close()

    def removeData(self):
        context = self.startContext({"datasources.default.schema-generate": "NONE"})
        try:
            context.getBean(LibraryRepository).deleteAll()
        finally:
            context.close()

    def startContext(self, properties: dict[str, str]) -> ApplicationContext:
        javaProperties = HashMap()
        for key, value in properties.items():
            javaProperties.put(key, value)
        return ApplicationContext.run(javaProperties, "oracle-cqn")
