from typing import Annotated

from jakarta.inject import Inject
from javax.sql import DataSource
from java.util.concurrent import TimeUnit
from micronaut.context.annotation import Property
from micronaut.data.connection.jdbc.advice import DelegatingDataSource
from micronaut.data.jdbc.notification.postgres import PostgresChangeEventMetadata
from micronaut.data.notification import ChangeOperation
from micronaut.test.extensions.junit5.annotation import MicronautTest, TestResourcesScope
from org.junit.jupiter.api import Test

from example.ChangeEventRecorder import ChangeEventRecorder


@MicronautTest(transactional=False)
@TestResourcesScope("postgres-statement-migration-python")
@Property(name="liquibase.datasources.default.change-log", value="classpath:db/changelog/db.changelog-statement.xml")
class StatementNotificationTest:
    dataSource: Annotated[DataSource, Inject]
    events: Annotated[ChangeEventRecorder, Inject]

    @Test
    def testStatementMigrationReplacesRowTriggers(self):
        connection = DelegatingDataSource.unwrapDataSource(self.dataSource).getConnection()
        try:
            statement = connection.createStatement()
            try:
                for sql in [
                    "INSERT INTO public.book VALUES (2, 'Second'), (3, 'Third')",
                    "UPDATE public.book SET title = 'Updated'",
                    "DELETE FROM public.book WHERE id IN (2, 3)",
                    "UPDATE public.book SET title = 'No matching rows' WHERE id = 999",
                    "TRUNCATE public.book"
                ]:
                    statement.execute(sql)
                    event = self.events.poll(10, TimeUnit.SECONDS)
                    assert event is not None, sql
                    assert event.operation() == ChangeOperation.INVALIDATE
                    assert event.entity().isEmpty()
                    metadata = event.metadata(PostgresChangeEventMetadata).orElseThrow()
                    assert metadata.channel() == "book_changes"
                    assert metadata.schema() == "public"
                    assert metadata.table() == "book"
                    assert metadata.oldKeys().isEmpty()
                    assert metadata.newKeys().isEmpty()
                    assert self.events.poll(200, TimeUnit.MILLISECONDS) is None, "Unexpected additional notification: " + sql
            finally:
                statement.close()
        finally:
            connection.close()
