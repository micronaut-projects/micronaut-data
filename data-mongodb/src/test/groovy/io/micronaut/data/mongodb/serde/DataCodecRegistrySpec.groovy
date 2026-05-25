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
package io.micronaut.data.mongodb.serde

import com.mongodb.MongoClientSettings
import io.micronaut.context.ApplicationContext
import io.micronaut.data.annotation.Id
import io.micronaut.data.annotation.MappedEntity
import io.micronaut.data.document.mongodb.entities.ComplexValue
import io.micronaut.data.model.runtime.RuntimeEntityRegistry
import spock.lang.Specification

class DataCodecRegistrySpec extends Specification {

    void "data codec registry accepts matching entity names loaded by a different classloader"() {
        given:
        Class<?> scannedEntity = reloadEntityClass(ComplexValue)

        expect:
        scannedEntity.name == ComplexValue.name
        scannedEntity != ComplexValue

        when:
        def context = ApplicationContext.run()
        def dataCodecRegistry = new DataCodecRegistry(
                [scannedEntity],
                context.getBean(DataSerdeRegistry),
                context.getBean(RuntimeEntityRegistry)
        )
        def codec = dataCodecRegistry.get(ComplexValue, MongoClientSettings.defaultCodecRegistry)

        then:
        codec != null
        codec.encoderClass == ComplexValue

        cleanup:
        context?.close()
    }

    void "data codec registry accepts mapped entities when scan result is empty"() {
        when:
        def context = ApplicationContext.run()
        def dataCodecRegistry = new DataCodecRegistry(
                [],
                context.getBean(DataSerdeRegistry),
                context.getBean(RuntimeEntityRegistry)
        )
        def codec = dataCodecRegistry.get(EmptyScanEntity, MongoClientSettings.defaultCodecRegistry)

        then:
        codec != null
        codec.encoderClass == EmptyScanEntity

        cleanup:
        context?.close()
    }

    private static Class<?> reloadEntityClass(Class<?> entityClass) {
        URL location = entityClass.getProtectionDomain().getCodeSource().getLocation()
        def classLoader = new URLClassLoader(new URL[] { location }, entityClass.classLoader) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name == entityClass.name) {
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
        try {
            return classLoader.loadClass(entityClass.name)
        } finally {
            classLoader.close()
        }
    }

    @MappedEntity
    static class EmptyScanEntity {
        @Id
        String id
        String name
    }
}
