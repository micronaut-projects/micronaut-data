/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.data.hibernate6

import io.micronaut.context.annotation.Property
import io.micronaut.data.hibernate6.entities.FavoriteStudents
import io.micronaut.data.hibernate6.entities.Favorites
import io.micronaut.data.tck.entities.Student
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

@MicronautTest(packages = "io.micronaut.data.tck.entities")
@Property(name = "datasources.default.name", value = "mydb")
@Property(name = 'jpa.default.properties.hibernate.hbm2ddl.auto', value = 'create-drop')
class HibernateOptimisticLockingSpec extends Specification {

    @Shared
    @Inject
    FavoritesRepository favoritesRepository

    void "version inc from data runtime is not breaking hibernate"() {
        when:
            Favorites favorites = new Favorites(id: UUID.randomUUID())
            Student s1 = new Student(name: "xyz1")
            Student s2 = new Student(name: "xyz2")
            Student s3 = new Student(name: "xyz3")
            FavoriteStudents favs = new FavoriteStudents(id: UUID.randomUUID(), favorite: s1, students: [s1, s2, s3])
            favorites.list = [favs]
            favoritesRepository.saveAndFlush(favorites)
            favorites = favoritesRepository.findById(favorites.id).get()
            favorites.getList().remove(favorites.getList().iterator().next())
            favoritesRepository.saveAndFlush(favorites)
        then:
            noExceptionThrown()
    }

}
