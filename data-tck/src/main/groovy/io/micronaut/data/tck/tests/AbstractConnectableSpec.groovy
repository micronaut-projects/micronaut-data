package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.ConnectionOperations
import io.micronaut.data.connection.ConnectionSynchronization
import io.micronaut.data.connection.SynchronousConnectionManager
import io.micronaut.data.tck.repositories.SimpleBookRepository
import io.micronaut.data.tck.services.AbstractBookService
import io.micronaut.data.tck.services.ConnectionBookService
import io.micronaut.test.support.TestPropertyProvider
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
abstract class AbstractConnectableSpec extends Specification implements TestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.builder()
            .properties(getProperties() + [(AbstractBookService.BOOK_REPOSITORY_CLASS_PROPERTY): getBookRepositoryClass().name])
            .packages(getPackages())
            .start()

    abstract Class<? extends SimpleBookRepository> getBookRepositoryClass();

    String[] getPackages() {
        return null
    }

    protected abstract ConnectionOperations<?> getConnectionOperations();

    protected abstract SynchronousConnectionManager<?> getSynchronousConnectionManager();

    ConnectionBookService getBookService() {
        return context.getBean(ConnectionBookService)
    }

    void cleanup() {
        connectionOperations.executeWrite {
            bookService.cleanup()
        }
    }

    void "test SynchronousConnectionManager propagated context"() {
        when:
            def connectionStatus = synchronousConnectionManager.getConnection(ConnectionDefinition.DEFAULT)
        then:
            connectionOperations.findConnectionStatus().get() == connectionStatus
        when:
            synchronousConnectionManager.complete(connectionStatus)
        then:
            connectionOperations.findConnectionStatus().isEmpty()
    }

    void "test callbacks execution"() {
        given:
            List<String> events = new ArrayList<>();
        when:
            connectionOperations.execute(ConnectionDefinition.DEFAULT, status -> {
                status.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    void executionComplete() {
                        events.add("con1 executionComplete1")
                    }

                    @Override
                    void beforeClosed() {
                        events.add("con1 beforeClosed1")
                    }

                    @Override
                    void afterClosed() {
                        events.add("con1 afterClosed1")
                    }
                })

                status.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    void executionComplete() {
                        events.add("con1 executionComplete2")
                    }

                    @Override
                    void beforeClosed() {
                        events.add("con1 beforeClosed2")
                    }

                    @Override
                    void afterClosed() {
                        events.add("con1 afterClosed2")
                    }
                })

                connectionOperations.execute(ConnectionDefinition.DEFAULT, status2 -> {
                    status2.registerSynchronization(new ConnectionSynchronization() {
                        @Override
                        void executionComplete() {
                            events.add("con2 executionComplete1")
                        }

                        @Override
                        void beforeClosed() {
                            events.add("con2 beforeClosed1")
                        }

                        @Override
                        void afterClosed() {
                            events.add("con2 afterClosed1")
                        }
                    })

                    status2.registerSynchronization(new ConnectionSynchronization() {
                        @Override
                        void executionComplete() {
                            events.add("con2 executionComplete2")
                        }

                        @Override
                        void beforeClosed() {
                            events.add("con2 beforeClosed2")
                        }

                        @Override
                        void afterClosed() {
                            events.add("con2 afterClosed2")
                        }
                    })
                })

            })
        then:
            events == ["con2 executionComplete2", "con2 executionComplete1", "con1 executionComplete2", "con1 executionComplete1", "con1 beforeClosed2", "con1 beforeClosed1", "con1 afterClosed2", "con1 afterClosed1"]
    }

    void "test callbacks execution 2"() {
        given:
            List<String> events = new ArrayList<>();
        when:
            def status = synchronousConnectionManager.getConnection(ConnectionDefinition.DEFAULT)

            status.registerSynchronization(new ConnectionSynchronization() {
                @Override
                void executionComplete() {
                    events.add("con1 executionComplete1")
                }

                @Override
                void beforeClosed() {
                    events.add("con1 beforeClosed1")
                }

                @Override
                void afterClosed() {
                    events.add("con1 afterClosed1")
                }
            })

            status.registerSynchronization(new ConnectionSynchronization() {
                @Override
                void executionComplete() {
                    events.add("con1 executionComplete2")
                }

                @Override
                void beforeClosed() {
                    events.add("con1 beforeClosed2")
                }

                @Override
                void afterClosed() {
                    events.add("con1 afterClosed2")
                }
            })

            connectionOperations.execute(ConnectionDefinition.DEFAULT, status2 -> {
                status2.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    void executionComplete() {
                        events.add("con2 executionComplete1")
                    }

                    @Override
                    void beforeClosed() {
                        events.add("con2 beforeClosed1")
                    }

                    @Override
                    void afterClosed() {
                        events.add("con2 afterClosed1")
                    }
                })

                status2.registerSynchronization(new ConnectionSynchronization() {
                    @Override
                    void executionComplete() {
                        events.add("con2 executionComplete2")
                    }

                    @Override
                    void beforeClosed() {
                        events.add("con2 beforeClosed2")
                    }

                    @Override
                    void afterClosed() {
                        events.add("con2 afterClosed2")
                    }
                })
            })

            synchronousConnectionManager.complete(status)

        then:
            events == ["con2 executionComplete2", "con2 executionComplete1", "con1 executionComplete2", "con1 executionComplete1", "con1 beforeClosed2", "con1 beforeClosed1", "con1 afterClosed2", "con1 afterClosed1"]
    }

    void "test mandatory connection missing"() {
        when:
            bookService.mandatoryConnection()
        then:
            def e = thrown(Exception)
            e.message == "No existing connection found for connection marked with propagation 'mandatory'"
    }

    void "test book is added in mandatory connection"() {
        when:
            bookService.bookAddedInMandatoryConnection()
        then:
            bookService.countBooks() == 1
    }

    void "test inner connection with suppressed exception"() {
        when:
            bookService.innerConnectionHasSuppressedException()
        then:
            bookService.countBooks() == 1
    }

    void "test inner requires new connection with suppressed exception"() {
        when:
            bookService.innerRequiresNewConnectionHasSuppressedException()
        then:
            bookService.countBooks() == 1
    }

    void "test book is added in another requires new connection"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewConnection()
        then:
            bookService.countBooks() == 1
    }

    void "test book is added in another requires new connection which if failing"() {
        when:
            bookService.bookIsAddedInAnotherRequiresNewConnectionWhichIsFailing()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooks() == 1
    }

    void "test book is added in the main TX and another requires new TX is failing"() {
        when:
            bookService.bookIsAddedAndAnotherRequiresNewConnectionIsFailing()
        then:
            def e = thrown(IllegalStateException)
            e.message == "Big fail!"
        and:
            bookService.countBooks() == 1
    }

    void "test that connections are never exhausted"() {
        when:
            int i = 0
            bookService.bookIsAddedInConnectionMethod()
            400.times { i += bookService.countBooks() }
        then:
            i == 400
    }

    void "test connection managed"() {
        when:
            assert connectionOperations.findConnectionStatus().isEmpty()
            bookService.checkInConnection({
                assert connectionOperations.findConnectionStatus().isPresent()
            })
            assert connectionOperations.findConnectionStatus().isEmpty()
        then:
            noExceptionThrown()
    }

}
