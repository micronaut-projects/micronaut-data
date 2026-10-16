from java.sql import Connection
from java.util import HashMap
from java.lang import IllegalStateException, Thread, System
from micronaut.context import ApplicationContext
from micronaut.data.jdbc.operations import DefaultJdbcRepositoryOperations
from micronaut.data.model.geo import Point
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test
from typing import Callable

from example.notification.Library import Library
from example.notification.LibraryCache import LibraryCache
from example.notification.LargeLibraryCache import LargeLibraryCache
from example.notification.LibraryRepository import LibraryRepository


@MicronautTest(transactional=False)
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
                libraryCache = context.getBean(LibraryCache)

                library1 = libraryCache.find("Library 1").orElse(None)
                library2 = libraryCache.find("Library 2").orElse(None)
                library3 = libraryCache.find("Library 3").orElse(None)
                library4 = libraryCache.find("Library 4").orElse(None)

                assert library1 is not None and library1.email == "library1@example.com", "initial email was not cached"
                assert library2 is not None and library2.capacity == 9000, "initial capacity was not cached"
                assert library3 is not None and library3.capacity == 10000, "initial query-boundary capacity was not cached"
                assert library4 is not None and library4.location.x() == 20.68960 and library4.location.y() == 44.01650, "initial location was not cached"

                repository.save(Library(library1.id, "Library 1 Updated", library1.email, library1.capacity, library1.location))
                self.waitUntil(lambda: libraryCache.find("Library 1 Updated").isPresent())
                assert libraryCache.find("Library 1 Updated").orElse(None).id == library1.id, "renamed library was not refreshed"

                repository.save(Library(library2.id, library2.name, library2.email, 12000, library2.location))
                self.waitUntil(lambda: libraryCache.find("Library 2").orElse(None).capacity == 12000)
                assert libraryCache.find("Library 2").orElse(None).id == library2.id, "capacity update did not retain the cached id"

                updatedLocation = Point(20.91140, 44.81250)
                repository.save(Library(library3.id, library3.name, library3.email, library3.capacity, updatedLocation))
                self.waitUntil(lambda: libraryCache.find("Library 3").orElse(None).location.x() == updatedLocation.x()
                               and libraryCache.find("Library 3").orElse(None).location.y() == updatedLocation.y())
                assert libraryCache.find("Library 3").orElse(None).id == library3.id, "location update did not retain the cached id"

                library5 = repository.save(Library(None, "Library 5", "library5@example.com", 8000, Point(21.16560, 44.77220)))
                self.waitUntil(lambda: libraryCache.find("Library 5").isPresent())
                assert libraryCache.find("Library 5").orElse(None).id == library5.id, "inserted library was not cached"

                repository.deleteById(library5.id)
                self.waitUntil(lambda: not libraryCache.find("Library 5").isPresent())

                self.truncateLibraries(context)
                self.waitUntil(lambda: all(not libraryCache.find(name).isPresent() for name in
                                          ("Library 1 Updated", "Library 2", "Library 3", "Library 4")))
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
                libraryCache = context.getBean(LargeLibraryCache)

                assert not libraryCache.find("Library 1").isPresent()
                assert not libraryCache.find("Library 2").isPresent()
                library3 = libraryCache.find("Library 3").orElse(None)
                library4 = libraryCache.find("Library 4").orElse(None)
                assert library3.capacity == 10000
                assert library4.capacity == 15000

                library1 = next(library for library in repository.findAll() if library.name == "Library 1")
                repository.save(Library(library1.id, library1.name, library1.email, 10000, library1.location))
                self.waitUntil(lambda: libraryCache.find(library1.name).isPresent())
                assert libraryCache.find(library1.name).orElse(None).id == library1.id

                repository.save(Library(library3.id, library3.name, library3.email, 9000, library3.location))
                self.waitUntil(lambda: not libraryCache.find(library3.name).isPresent())

                repository.deleteById(library4.id)
                self.waitUntil(lambda: not libraryCache.find(library4.name).isPresent())

                self.truncateLibraries(context)
                self.waitUntil(lambda: not libraryCache.find(library1.name).isPresent())
            finally:
                context.close()
        finally:
            self.removeData()

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
        return ApplicationContext.run(javaProperties)

    def waitUntil(self, condition: Callable[[], bool]):
        end = System.currentTimeMillis() + 10000
        while System.currentTimeMillis() < end:
            Thread.sleep(50)
            if condition():
                return
        raise IllegalStateException("Condition was not fulfilled within 10 seconds")
