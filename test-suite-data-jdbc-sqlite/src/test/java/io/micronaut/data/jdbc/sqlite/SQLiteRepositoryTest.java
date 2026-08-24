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
package io.micronaut.data.jdbc.sqlite;

import io.micronaut.data.connection.jdbc.advice.DelegatingDataSource;
import io.micronaut.data.tck.repositories.AuthorRepository;
import io.micronaut.data.tck.repositories.BasicTypesRepository;
import io.micronaut.data.tck.repositories.BookDtoRepository;
import io.micronaut.data.tck.repositories.BookRepository;
import io.micronaut.data.tck.repositories.CarRepository;
import io.micronaut.data.tck.repositories.CityRepository;
import io.micronaut.data.tck.repositories.CompanyRepository;
import io.micronaut.data.tck.repositories.CountryRegionCityRepository;
import io.micronaut.data.tck.repositories.CountryRepository;
import io.micronaut.data.tck.repositories.EntityWithIdClass2Repository;
import io.micronaut.data.tck.repositories.EntityWithIdClassRepository;
import io.micronaut.data.tck.repositories.ExampleEntityRepository;
import io.micronaut.data.tck.repositories.FaceRepository;
import io.micronaut.data.tck.repositories.FoodRepository;
import io.micronaut.data.tck.repositories.GenreRepository;
import io.micronaut.data.tck.repositories.IntervalRepository;
import io.micronaut.data.tck.repositories.MealRepository;
import io.micronaut.data.tck.repositories.NoseRepository;
import io.micronaut.data.tck.repositories.PageRepository;
import io.micronaut.data.tck.repositories.PersonRepository;
import io.micronaut.data.tck.repositories.RegionRepository;
import io.micronaut.data.tck.repositories.RoleRepository;
import io.micronaut.data.tck.repositories.StudentRepository;
import io.micronaut.data.tck.repositories.TimezoneBasicTypesRepository;
import io.micronaut.data.tck.repositories.UserRepository;
import io.micronaut.data.tck.repositories.UserRoleRepository;
import io.micronaut.data.tck.tests.AbstractRepositorySpec;
import io.micronaut.inject.qualifiers.Qualifiers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;

public class SQLiteRepositoryTest extends AbstractRepositorySpec implements SQLiteTestingPropertyProvider {

    @Override
    public Map<String, String> getProperties() {
        return SQLiteTestingPropertyProvider.super.getProperties();
    }

    @Override
    public EntityWithIdClassRepository getEntityWithIdClassRepository() {
        return getApplicationContext().getBean(SQLiteEntityWithIdClassRepository.class);
    }

    @Override
    public EntityWithIdClass2Repository getEntityWithIdClass2Repository() {
        return getApplicationContext().getBean(SQLiteEntityWithIdClass2Repository.class);
    }

    @Override
    public NoseRepository getNoseRepository() {
        return getApplicationContext().getBean(SQLiteNoseRepository.class);
    }

    @Override
    public FaceRepository getFaceRepository() {
        return getApplicationContext().getBean(SQLiteFaceRepository.class);
    }

    @Override
    public PersonRepository getPersonRepository() {
        return getApplicationContext().getBean(SQLitePersonRepository.class);
    }

    @Override
    public BookRepository getBookRepository() {
        return getApplicationContext().getBean(SQLiteBookRepository.class);
    }

    @Override
    public GenreRepository getGenreRepository() {
        return getApplicationContext().getBean(SQLiteGenreRepository.class);
    }

    @Override
    public AuthorRepository getAuthorRepository() {
        return getApplicationContext().getBean(SQLiteAuthorRepository.class);
    }

    @Override
    public CompanyRepository getCompanyRepository() {
        return getApplicationContext().getBean(SQLiteCompanyRepository.class);
    }

    @Override
    public BookDtoRepository getBookDtoRepository() {
        return getApplicationContext().getBean(SQLiteBookDtoRepository.class);
    }

    @Override
    public CountryRepository getCountryRepository() {
        return getApplicationContext().getBean(SQLiteCountryRepository.class);
    }

    @Override
    public CityRepository getCityRepository() {
        return getApplicationContext().getBean(SQLiteCityRepository.class);
    }

    @Override
    public RegionRepository getRegionRepository() {
        return getApplicationContext().getBean(SQLiteRegionRepository.class);
    }

    @Override
    public CountryRegionCityRepository getCountryRegionCityRepository() {
        return getApplicationContext().getBean(SQLiteCountryRegionCityRepository.class);
    }

    @Override
    public UserRoleRepository getUserRoleRepository() {
        return getApplicationContext().getBean(SQLiteUserRoleRepository.class);
    }

    @Override
    public RoleRepository getRoleRepository() {
        return getApplicationContext().getBean(SQLiteRoleRepository.class);
    }

    @Override
    public UserRepository getUserRepository() {
        return getApplicationContext().getBean(SQLiteUserRepository.class);
    }

    @Override
    public MealRepository getMealRepository() {
        return getApplicationContext().getBean(SQLiteMealRepository.class);
    }

    @Override
    public FoodRepository getFoodRepository() {
        return getApplicationContext().getBean(SQLiteFoodRepository.class);
    }

    @Override
    public StudentRepository getStudentRepository() {
        return getApplicationContext().getBean(SQLiteStudentRepository.class);
    }

    @Override
    public CarRepository getCarRepository() {
        return getApplicationContext().getBean(SQLiteCarRepository.class);
    }

    @Override
    public BasicTypesRepository getBasicTypeRepository() {
        return getApplicationContext().getBean(SQLiteBasicTypesRepository.class);
    }

    @Override
    public TimezoneBasicTypesRepository getTimezoneBasicTypeRepository() {
        return getApplicationContext().getBean(SQLiteTimezoneBasicTypesRepository.class);
    }

    @Override
    public PageRepository getPageRepository() {
        return getApplicationContext().getBean(SQLitePageRepository.class);
    }

    @Override
    public ExampleEntityRepository getExampleEntityRepository() {
        return getApplicationContext().getBean(SQLiteExampleEntityRepository.class);
    }

    @Override
    public IntervalRepository getIntervalRepository() {
        return getApplicationContext().getBean(SQLiteIntervalRepository.class);
    }

    @Override
    public boolean isSupportsArrays() {
        return true;
    }

    @Override
    protected boolean skipQueryByDataArray() {
        return true;
    }

    @Override
    protected void cleanupBooks() {
        try (Connection connection = dataSource().getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM \"book_student\"");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clean up book_student rows", e);
        }
        super.cleanupBooks();
    }

    @Override
    protected void cleanupData() {
        getStudentRepository().deleteAll();
        super.cleanupData();
    }

    private DataSource dataSource() {
        DataSource dataSource = getApplicationContext().getBean(DataSource.class, Qualifiers.byName("default"));
        return DelegatingDataSource.unwrapDataSource(dataSource);
    }
}
