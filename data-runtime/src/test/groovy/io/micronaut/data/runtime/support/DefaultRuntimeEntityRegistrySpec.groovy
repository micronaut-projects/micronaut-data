/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.runtime.support

import io.micronaut.context.ApplicationContext
import io.micronaut.aop.MethodInvocationContext
import io.micronaut.core.convert.ConversionService
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.intercept.RepositoryMethodKey
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import io.micronaut.data.operations.RepositoryOperations
import io.micronaut.data.runtime.intercept.AbstractQueryInterceptor
import spock.lang.Specification

class DefaultRuntimeEntityRegistrySpec extends Specification {

    void "runtime entity registry resolves introspection from the entity classloader"() {
        given:
        Class<?> reloadedEntity = reloadEntityClass(ClassLoaderOnlyEntity)
        ClassLoader previousClassLoader = Thread.currentThread().contextClassLoader
        ApplicationContext context = null

        when:
        Thread.currentThread().contextClassLoader = DefaultRuntimeEntityRegistrySpec.classLoader
        context = ApplicationContext.run()
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(reloadedEntity)

        then:
        reloadedEntity.name == ClassLoaderOnlyEntity.name
        reloadedEntity != ClassLoaderOnlyEntity
        entity.introspection.beanType.is(reloadedEntity)

        cleanup:
        context?.close()
        Thread.currentThread().contextClassLoader = previousClassLoader
        closeClassLoader(reloadedEntity.classLoader)
    }

    void "query interceptor instantiates entity using runtime entity introspection"() {
        given:
        Class<?> reloadedEntity = reloadEntityClass(ClassLoaderOnlyEntity)
        ClassLoader previousClassLoader = Thread.currentThread().contextClassLoader
        ApplicationContext context = null

        when:
        Thread.currentThread().contextClassLoader = DefaultRuntimeEntityRegistrySpec.classLoader
        context = ApplicationContext.run()
        def entity = context.getBean(RuntimeEntityRegistry).getEntity(reloadedEntity)
        def operations = Stub(RepositoryOperations) {
            getApplicationContext() >> context
            getConversionService() >> ConversionService.SHARED
            getEntity(reloadedEntity) >> entity
        }
        def instance = new TestQueryInterceptor(operations).instantiate(reloadedEntity, [id: 1L, name: "R2DBC"])

        then:
        instance.class.is(reloadedEntity)
        entity.introspection.getRequiredProperty("id", Long).get(instance) == 1L
        entity.introspection.getRequiredProperty("name", String).get(instance) == "R2DBC"

        cleanup:
        context?.close()
        Thread.currentThread().contextClassLoader = previousClassLoader
        closeClassLoader(reloadedEntity.classLoader)
    }

    private static Class<?> reloadEntityClass(Class<?> entityClass) {
        URL location = entityClass.getProtectionDomain().getCodeSource().getLocation()
        def classLoader = new URLClassLoader(new URL[] { location }, entityClass.getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (shouldLoadChildFirst(name, entityClass)) {
                    Class<?> loaded = findLoadedClass(name)
                    if (loaded == null) {
                        loaded = findClass(name)
                    }
                    if (resolve) {
                        resolveClass(loaded)
                    }
                    return loaded
                }
                return super.loadClass(name, resolve)
            }
        }
        return classLoader.loadClass(entityClass.name)
    }

    private static boolean shouldLoadChildFirst(String name, Class<?> entityClass) {
        String entityName = entityClass.name
        String packageName = entityClass.packageName
        String simpleName = entityName.substring(packageName.length() + 1)
        String generatedPrefix = packageName + '.$' + simpleName
        return name == entityName || name.startsWith(entityName + '$') || name.startsWith(generatedPrefix + '$')
    }

    private static void closeClassLoader(ClassLoader classLoader) {
        if (classLoader instanceof Closeable) {
            classLoader.close()
        }
    }

    @MappedEntity
    static class ClassLoaderOnlyEntity {
        @Id
        Long id
        String name
    }

    private static final class TestQueryInterceptor extends AbstractQueryInterceptor<Object, Object> {

        TestQueryInterceptor(RepositoryOperations operations) {
            super(operations)
        }

        Object instantiate(Class<?> rootEntity, Map<String, Object> parameterValues) {
            instantiateEntity(rootEntity, parameterValues)
        }

        @Override
        Object intercept(RepositoryMethodKey methodKey, MethodInvocationContext<Object, Object> context) {
            throw new UnsupportedOperationException()
        }
    }
}
