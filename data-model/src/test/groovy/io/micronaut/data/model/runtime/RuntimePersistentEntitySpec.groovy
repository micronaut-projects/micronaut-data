package io.micronaut.data.model.runtime

import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.type.Argument
import io.micronaut.data.annotation.AutoPopulated
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.annotation.NamingStrategy
import spock.lang.Specification

class RuntimePersistentEntitySpec extends Specification {

    def "test properties"() {
        given:
            def rtpe = new RuntimePersistentEntity(Test)
        expect:
            rtpe.getPersistentPropertyNames().contains('id')
    }

    def "test naming strategy from the thread context classloader"() {
        given:
            def originalClassLoader = Thread.currentThread().contextClassLoader
            def firstApplicationClassLoader = new GroovyClassLoader(this.class.classLoader)
            def secondApplicationClassLoader = new GroovyClassLoader(this.class.classLoader)
            def firstStrategyType = firstApplicationClassLoader.parseClass('''
                package example
                class ChildNamingStrategy implements io.micronaut.data.model.naming.NamingStrategy {
                    String mappedName(String name) { "first_" + name }
                }
            ''')
            def secondStrategyType = secondApplicationClassLoader.parseClass('''
                package example
                class ChildNamingStrategy implements io.micronaut.data.model.naming.NamingStrategy {
                    String mappedName(String name) { "second_" + name }
                }
            ''')
            AnnotationMetadata annotationMetadata = Mock {
                stringValue(NamingStrategy.class) >> Optional.of(firstStrategyType.name)
                stringValue(MappedEntity.class, _) >> Optional.empty()
            }
            BeanIntrospection introspection = Mock {
                getAnnotationMetadata() >> annotationMetadata
                getBeanProperties() >> []
                getConstructorArguments() >> Argument.ZERO_ARGUMENTS
                getBeanType() >> Test
            }

        when:
            Thread.currentThread().contextClassLoader = firstApplicationClassLoader
            def firstEntity = new RuntimePersistentEntity(introspection)
            Thread.currentThread().contextClassLoader = secondApplicationClassLoader
            def secondEntity = new RuntimePersistentEntity(introspection)

        then:
            firstEntity.namingStrategy.class == firstStrategyType
            firstEntity.namingStrategy.mappedName('name') == 'first_name'
            secondEntity.namingStrategy.class == secondStrategyType
            secondEntity.namingStrategy.mappedName('name') == 'second_name'

        cleanup:
            Thread.currentThread().contextClassLoader = originalClassLoader
            firstApplicationClassLoader.close()
            secondApplicationClassLoader.close()
    }

    def "test naming strategy falls back to the defining classloader for context classloader #contextClassLoader"() {
        given:
            def originalClassLoader = Thread.currentThread().contextClassLoader
            Thread.currentThread().contextClassLoader = contextClassLoader
            AnnotationMetadata annotationMetadata = Mock {
                stringValue(NamingStrategy.class) >> Optional.of(io.micronaut.data.model.naming.NamingStrategies.Raw.name)
                stringValue(MappedEntity.class, _) >> Optional.empty()
            }
            BeanIntrospection introspection = Mock {
                getAnnotationMetadata() >> annotationMetadata
                getBeanProperties() >> []
                getConstructorArguments() >> Argument.ZERO_ARGUMENTS
                getBeanType() >> Test
            }

        when:
            def entity = new RuntimePersistentEntity(introspection)

        then:
            entity.namingStrategy.class == io.micronaut.data.model.naming.NamingStrategies.Raw
            entity.namingStrategy.mappedName('camelCase') == 'camelCase'

        cleanup:
            Thread.currentThread().contextClassLoader = originalClassLoader

        where:
            contextClassLoader << [ClassLoader.platformClassLoader, null]
    }

}

@MappedEntity
class Test {
    @Id
    @AutoPopulated
    UUID id

    String name
}
