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
package io.micronaut.data.processor.sql

import io.micronaut.data.processor.visitors.AbstractDataSpec
import io.micronaut.data.tck.entities.Restaurant

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class JakartaDataBuildQuerySpec extends AbstractDataSpec {

    void "test Jakarta Data @Save on repository without base interface (single, list, array)"() {
        given:
        def repository = buildRepository('test.RestaurantRepoSave', """
import jakarta.data.repository.Repository;
import jakarta.data.repository.Save;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoSave {

    @Save
    Restaurant customSave(Restaurant entity);

    @Save
    void customSaveAll(List<Restaurant> entities);

    @Save
    void customSaveArray(Restaurant[] entities);
}
""")

        when:
        def saveOne = repository.getRequiredMethod("customSave", Restaurant)
        def saveAllList = repository.getRequiredMethod("customSaveAll", List)
        def saveAllArray = repository.getRequiredMethod("customSaveArray", Restaurant[])

        then:
        getQuery(saveOne) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        getQuery(saveAllList) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        getQuery(saveAllArray) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
    }

    void "test Jakarta Data @Insert on repository without base interface (single, list, array)"() {
        given:
        def repository = buildRepository('test.RestaurantRepoInsert', """
import jakarta.data.repository.Repository;
import jakarta.data.repository.Insert;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoInsert {

    @Insert
    Restaurant customInsert(Restaurant entity);

    @Insert
    void customInsertAll(List<Restaurant> entities);

    @Insert
    void customInsertArray(Restaurant[] entities);
}
""")

        when:
        def insertOne = repository.getRequiredMethod("customInsert", Restaurant)
        def insertAllList = repository.getRequiredMethod("customInsertAll", List)
        def insertAllArray = repository.getRequiredMethod("customInsertArray", Restaurant[])

        then:
        getQuery(insertOne) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        getQuery(insertAllList) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
        getQuery(insertAllArray) == 'INSERT INTO `restaurant` (`name`,`street`,`zip_code`,`hqaddress_street`,`hqaddress_zip_code`) VALUES (?,?,?,?,?)'
    }

    void "test Jakarta Data @Update on repository without base interface (single, list, array)"() {
        given:
        def repository = buildRepository('test.RestaurantRepoUpdate', """
import jakarta.data.repository.Repository;
import jakarta.data.repository.Update;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoUpdate {

    @Update
    Restaurant customUpdate(Restaurant entity);

    @Update
    void customUpdateAll(List<Restaurant> entities);

    @Update
    void customUpdateArray(Restaurant[] entities);
}
""")

        when:
        def updateOne = repository.getRequiredMethod("customUpdate", Restaurant)
        def updateAllList = repository.getRequiredMethod("customUpdateAll", List)
        def updateAllArray = repository.getRequiredMethod("customUpdateArray", Restaurant[])

        then:
        getQuery(updateOne) == 'UPDATE `restaurant` SET `name`=?,`street`=?,`zip_code`=?,`hqaddress_street`=?,`hqaddress_zip_code`=? WHERE (`id` = ?)'
        getQuery(updateAllList) == 'UPDATE `restaurant` SET `name`=?,`street`=?,`zip_code`=?,`hqaddress_street`=?,`hqaddress_zip_code`=? WHERE (`id` = ?)'
        getQuery(updateAllArray) == 'UPDATE `restaurant` SET `name`=?,`street`=?,`zip_code`=?,`hqaddress_street`=?,`hqaddress_zip_code`=? WHERE (`id` = ?)'
    }

    void "test Jakarta Data @Delete on repository without base interface (single, list, array)"() {
        given:
        def repository = buildRepository('test.RestaurantRepoDelete', """
import jakarta.data.repository.Repository;
import jakarta.data.repository.Delete;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.tck.entities.Restaurant;
import java.util.List;

@JdbcRepository(dialect = Dialect.H2)
@Repository
interface RestaurantRepoDelete {

    @Delete
    void customDelete(Restaurant entity);

    @Delete
    void customDeleteAll(List<Restaurant> entities);

    @Delete
    void customDeleteArray(Restaurant[] entities);
}
""")

        when:
        def deleteOne = repository.getRequiredMethod("customDelete", Restaurant)
        def deleteAllList = repository.getRequiredMethod("customDeleteAll", List)
        def deleteAllArray = repository.getRequiredMethod("customDeleteArray", Restaurant[])

        then:
        getQuery(deleteOne) == 'DELETE  FROM `restaurant`  WHERE (`id` = ?)'
        getQuery(deleteAllList) == 'DELETE  FROM `restaurant`  WHERE (`id` IN (?))'
        getQuery(deleteAllArray) == 'DELETE  FROM `restaurant`  WHERE (`id` IN (?))'
    }
}
