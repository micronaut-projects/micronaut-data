package io.micronaut.data.jdbc.reload

import io.micronaut.context.ApplicationContext
import io.micronaut.context.RuntimeBeanDefinition
import io.micronaut.context.reload.ClassChange
import io.micronaut.context.reload.ClassChangeEvent
import io.micronaut.context.reload.ReloadStrategy
import io.micronaut.data.jdbc.config.SchemaGenerator
import io.micronaut.data.jdbc.operations.JdbcRepositoryOperations
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.runtime.event.EntityEventRegistry
import io.micronaut.data.runtime.event.listeners.AutoTimestampEntityEventListener
import io.micronaut.data.runtime.intercept.DataInterceptorResolver
import io.micronaut.transaction.interceptor.TransactionalInterceptor
import spock.lang.Specification

class DataReloadSpec extends Specification {

    private static final String DATA_RELOADER = 'io.micronaut.data.runtime.support.DevelopmentDataReloader'
    private static final String SCHEMA_RELOADER = 'io.micronaut.data.jdbc.config.DevelopmentSchemaReloader'

    void "in development mode a reload that retires a classloader recreates the data beans, the transaction interceptor and the schema generator, and the application works on top of the new ones"() {
        given:
        ApplicationContext context = devContext(true)
        ReloadLibrary library = context.getBean(ReloadLibrary)
        library.add('One')
        Map<String, Object> before = dataBeans(context)
        TransactionalInterceptor transactionalInterceptor = context.getBean(TransactionalInterceptor)
        SchemaGenerator generator = context.getBean(SchemaGenerator)

        expect: 'the reloaders exist only in development mode'
        context.containsBean(Class.forName(DATA_RELOADER))
        context.containsBean(Class.forName(SCHEMA_RELOADER))

        when:
        context.publishEvent(classChange([ReloadBook.classLoader] as Set, [], ReloadStrategy.RELOAD))
        Map<String, Object> after = dataBeans(context)
        ReloadLibrary recreated = context.getBean(ReloadLibrary)

        then: 'every bean that holds what was derived from the retired classes is replaced'
        before.every { name, bean -> !after[name].is(bean) }
        !context.getBean(TransactionalInterceptor).is(transactionalInterceptor)
        !context.getBean(SchemaGenerator).is(generator)

        and: 'the advised bean that received them, through its repository, is created again on top of the new ones'
        !recreated.is(library)
        !recreated.books().is(library.books())

        and: 'the schema generated again in create mode kept the table and its rows'
        recreated.count('One') == 1
        recreated.add('Two') > 0
        recreated.count('Two') == 1

        cleanup:
        context.close()
    }

    void "in development mode a class redefined in place recreates the data beans when it is an entity or a repository, or was generated for one, and nothing otherwise"() {
        given:
        ApplicationContext context = devContext(true)
        ReloadLibrary library = context.getBean(ReloadLibrary)
        library.add('One')
        TransactionalInterceptor transactionalInterceptor = context.getBean(TransactionalInterceptor)
        SchemaGenerator generator = context.getBean(SchemaGenerator)

        when: 'the application restarts: the new context creates its own beans'
        Map<String, Object> before = dataBeans(context)
        context.publishEvent(classChange([ReloadBook.classLoader] as Set, [new ClassChange(ReloadBook.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RESTART))

        then:
        same(before, dataBeans(context))
        context.getBean(ReloadLibrary).is(library)

        when: 'a class that is neither an entity nor a repository is redefined'
        context.publishEvent(classChange([] as Set, [new ClassChange(DataReloadSpec.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then:
        same(before, dataBeans(context))
        context.getBean(ReloadLibrary).is(library)

        when: 'a class is redefined: ' + changed
        context.publishEvent(classChange([] as Set, [new ClassChange(changed, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))
        Map<String, Object> after = dataBeans(context)
        ReloadLibrary recreated = context.getBean(ReloadLibrary)

        then: 'the beans derived from entities and repositories are replaced, with the beans that received them'
        before.every { name, bean -> !after[name].is(bean) }
        !recreated.is(library)

        and: 'what holds methods of other classes, and the schema, are kept: a redefinition changes method bodies only'
        context.getBean(TransactionalInterceptor).is(transactionalInterceptor)
        context.getBean(SchemaGenerator).is(generator)

        and:
        recreated.count('One') == 1
        recreated.add('Two') > 0

        cleanup:
        context.close()

        where:
        changed << [
            ReloadBook.name,
            'io.micronaut.data.jdbc.reload.$ReloadBook$Introspection',
            ReloadBookRepository.name,
            'io.micronaut.data.jdbc.reload.$ReloadBookRepository$Intercepted$Definition'
        ]
    }

    void "in development mode a repository definition added at runtime recreates the repository operations and the interceptor resolver"() {
        given:
        ApplicationContext context = devContext(true)
        ReloadLibrary library = context.getBean(ReloadLibrary)
        library.add('One')
        RuntimeEntityRegistry registry = context.getBean(RuntimeEntityRegistry)
        JdbcRepositoryOperations operations = context.getBean(JdbcRepositoryOperations)
        DataInterceptorResolver resolver = context.getBean(DataInterceptorResolver)
        def repositoryMetadata = context.getBeanDefinition(ReloadBookRepository).annotationMetadata

        when: 'a definition with the repository stereotype is registered, as a module that registers repositories at runtime does'
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(LateRepository, { -> new LateRepository() })
            .annotationMetadata(repositoryMetadata)
            .build())

        then: 'the operations, which know the repositories they serve from when they were created, are replaced'
        !context.getBean(JdbcRepositoryOperations).is(operations)
        !context.getBean(DataInterceptorResolver).is(resolver)

        and: 'the entity registry is kept'
        context.getBean(RuntimeEntityRegistry).is(registry)

        and:
        context.getBean(ReloadLibrary).count('One') == 1

        cleanup:
        context.close()
    }

    void "in development mode a context that does not track bean dependencies keeps the data beans"() {
        given:
        ApplicationContext context = devContext(false)
        ReloadLibrary library = context.getBean(ReloadLibrary)
        Map<String, Object> before = dataBeans(context)

        expect:
        context.containsBean(Class.forName(DATA_RELOADER))

        when:
        context.publishEvent(classChange([ReloadBook.classLoader] as Set, [], ReloadStrategy.RELOAD))

        then:
        same(before, dataBeans(context))
        context.getBean(ReloadLibrary).is(library)
        library.add('One') > 0

        cleanup:
        context.close()
    }

    void "outside development mode there are no reloaders, and class changes leave the data beans as they are"() {
        given:
        ApplicationContext context = ApplicationContext.run(properties())
        ReloadLibrary library = context.getBean(ReloadLibrary)
        Map<String, Object> before = dataBeans(context)
        SchemaGenerator generator = context.getBean(SchemaGenerator)

        expect:
        !context.containsBean(Class.forName(DATA_RELOADER))
        !context.containsBean(Class.forName(SCHEMA_RELOADER))

        when:
        context.publishEvent(classChange([ReloadBook.classLoader] as Set, [new ClassChange(ReloadBook.name, ClassChange.Kind.MODIFIED)], ReloadStrategy.RELOAD))

        then:
        same(before, dataBeans(context))
        context.getBean(ReloadLibrary).is(library)
        context.getBean(SchemaGenerator).is(generator)
        library.add('One') > 0

        cleanup:
        context.close()
    }

    private static ApplicationContext devContext(boolean track) {
        return ApplicationContext.builder()
            .properties(properties() + ['micronaut.dev.enabled': true])
            .beanDependencyTrackingEnabled(track)
            .start()
    }

    private static Map<String, Object> properties() {
        return [
            'datasources.default.url'            : "jdbc:h2:mem:reload${UUID.randomUUID().toString().replace('-', '')};LOCK_TIMEOUT=10000;DB_CLOSE_ON_EXIT=FALSE".toString(),
            'datasources.default.schema-generate': 'CREATE',
            'datasources.default.dialect'        : 'h2',
            'datasources.default.username'       : '',
            'datasources.default.password'       : '',
            'datasources.default.packages'       : ReloadBook.package.name,
            'datasources.default.driverClassName': 'org.h2.Driver'
        ]
    }

    private static Map<String, Object> dataBeans(ApplicationContext context) {
        return [
            entityRegistry     : context.getBean(RuntimeEntityRegistry),
            operations         : context.getBean(JdbcRepositoryOperations),
            interceptorResolver: context.getBean(DataInterceptorResolver),
            eventRegistry      : context.getBean(EntityEventRegistry),
            timestampListener  : context.getBean(AutoTimestampEntityEventListener)
        ]
    }

    private static boolean same(Map<String, Object> before, Map<String, Object> after) {
        return before.every { name, bean -> after[name].is(bean) }
    }

    private static ClassChangeEvent classChange(Set<ClassLoader> retired, List<ClassChange> changes, ReloadStrategy strategy) {
        return new ClassChangeEvent(DataReloadSpec, retired, DataReloadSpec.classLoader, changes, strategy)
    }

    static class LateRepository {
    }
}
