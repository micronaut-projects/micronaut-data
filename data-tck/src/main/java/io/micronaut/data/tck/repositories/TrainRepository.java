/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.data.tck.repositories;

import io.micronaut.data.annotation.By;
import io.micronaut.data.annotation.Join;
import io.micronaut.data.tck.entities.Train;
import io.micronaut.data.tck.entities.TrainCZ;
import io.micronaut.data.tck.entities.TrainCZProjection;
import io.micronaut.data.tck.entities.TrainNameCapacityDto;
import io.micronaut.data.tck.entities.TrainNameModelDto;
import jakarta.data.Order;
import jakarta.data.Sort;
import jakarta.data.constraint.AtLeast;
import jakarta.data.constraint.AtMost;
import jakarta.data.constraint.EqualTo;
import jakarta.data.constraint.GreaterThan;
import jakarta.data.constraint.In;
import jakarta.data.constraint.LessThan;
import jakarta.data.constraint.Like;
import jakarta.data.constraint.NotEqualTo;
import jakarta.data.constraint.NotIn;
import jakarta.data.constraint.NotLike;
import jakarta.data.page.CursoredPage;
import jakarta.data.page.Page;
import jakarta.data.page.PageRequest;
import jakarta.data.repository.CrudRepository;
import jakarta.data.repository.Find;
import jakarta.data.repository.First;
import jakarta.data.repository.Is;
import jakarta.data.repository.OrderBy;
import jakarta.data.repository.Select;
import jakarta.data.restrict.Restriction;

import java.util.List;

public interface TrainRepository extends CrudRepository<Train, Long> {

    void deleteAll();

    @Find
    TrainCZProjection projection(@By(By.ID) Long id);

    @Find
    @Select("speed")
    @Select("model")
    @Select("electric")
    TrainCZ methodProjection(@By(By.ID) Long id);

    List<Train> findByName(String name);

    @Find
    List<Train> findByNameEqualsConstrain(@By("name") @Is(EqualTo.class) String name);

    @Find
    List<Train> findByNameEqualsConstrain(@By("name") EqualTo<String> equalTo);

    @Find
    List<Train> findByNameEqualsConstrainAndElectricConstraint(@By("name") EqualTo<String> equalTo, EqualTo<Boolean> electric);

    @Find
    List<Train> findByNameEqualsConstrainWithRestriction(@By("name") EqualTo<String> equalTo, Restriction<Train> restriction);

    @Find
    List<Train> findByNameNotEqualsConstrain(@By("name") @Is(NotEqualTo.class) String name);

    @Find
    List<Train> findByCapacityAtLeast(@By("capacity") @Is(AtLeast.class) int capacity);

    @Find
    List<Train> findBySpeedAtMost(@By("speed") @Is(AtMost.class) double speed);

    @Find
    List<Train> findByCapacityGreaterThanConstrain(@By("capacity") @Is(GreaterThan.class) int capacity);

    @Find
    List<Train> findByModelInConstrain(@By("model") @Is(In.class) List<String> models);

    @Find
    List<Train> findBySpeedLessThanConstrain(@By("speed") @Is(LessThan.class) double speed);

    @Find
    List<Train> findByNameLikeConstrain(@By("name") @Is(Like.class) String pattern);

    @Find
    List<Train> findByNameNotInConstrain(@By("name") @Is(NotIn.class) List<String> names);

    @Find
    List<Train> findByModelNotLikeConstrain(@By("model") @Is(NotLike.class) String pattern);

    List<Train> findByModelNot(String model);

    List<Train> findByCapacityGreaterThan(int capacity);

    List<Train> findBySpeedLessThan(double speed);

    List<Train> findByCapacityGreaterThanEquals(int capacity);

    List<Train> findBySpeedLessThanEquals(double speed);

    List<Train> findByCapacityBetween(int minCapacity, int maxCapacity);

    List<Train> findBySpeedNotBetween(double minSpeed, double maxSpeed);

    List<Train> findByModelIn(List<String> models);

    List<Train> findByNameNotIn(List<String> names);

    List<Train> findByModelIsNull();

    List<Train> findByModelIsNotNull();

    List<Train> findByNameLike(String pattern);

    List<Train> findByModelNotLike(String pattern);

    List<Train> findTrains(Restriction<Train> restriction);

    Train findTrain(Restriction<Train> restriction);

    @Join("manufacturer")
    List<Train> findTrains$joinedManufacturer(Restriction<Train> restriction);

    // Test methods with @Find

    @Find
    List<Train> trains(Restriction<Train> restriction);

    @Find
    @Join("manufacturer")
    List<Train> findTrainsWithJoinedManufacturer(Restriction<Train> restriction);

    // Methods for testing Restriction + Order combinations
    List<Train> findTrainsWithOrder(Restriction<Train> restriction, Order<Train> order);

    //  TODO: fix postponed compilation @OrderBy(_Train.NAME)
    @OrderBy("name")
    List<Train> findTrainsWithOrderByName(Restriction<Train> restriction);

    @Find
    List<Train> findTrainsWithSorts(Restriction<Train> restriction, Sort<Train>... sorts);

    @Find
    List<Train> findTrainsWithSorts(Restriction<Train> restriction, Sort<Train> sort);

    @Join("manufacturer")
    @Find
    List<Train> findTrainsWithOrderWithJoinedManufacturer(Restriction<Train> restriction, Order<Train> order);

    @Join("manufacturer")
    @Find
    List<Train> findTrainsWithSortsWithJoinedManufacturer(Restriction<Train> restriction, Sort<Train>... sorts);

    @Find
    Page<Train> trainsPaged(Restriction<Train> restriction, PageRequest pageRequest);

    @Find
    Page<Train> trainsPaged(Restriction<Train> restriction, PageRequest pageRequest, Order<Train> order);

    @Find
    Page<Train> trainsPaged(Restriction<Train> restriction, PageRequest pageRequest, Sort<Train> sorts);

    @Find
    Page<Train> trainsPaged2(PageRequest pageRequest, Restriction<Train> restriction);

    @Find
    CursoredPage<Train> trainsCursoredPaged(Restriction<Train> restriction, PageRequest pageRequest);

    @Find
    CursoredPage<Train> trainsCursoredPaged(Restriction<Train> restriction, PageRequest pageRequest, Order<Train> order);

    @Find
    CursoredPage<Train> trainsCursoredPaged(Restriction<Train> restriction, PageRequest pageRequest, Sort<Train> sorts);

    @Find
    CursoredPage<Train> trainsCursoredPaged2(PageRequest pageRequest, Restriction<Train> restriction);

    // Methods for testing @First annotation
    @Find
    @First(2)
    List<Train> findFirst2Trains(Restriction<Train> restriction);

    @Find
    @First(1)
    List<Train> findFirstTrain(Restriction<Train> restriction);

    @Find
    @First(3)
    List<Train> findFirst3Trains(Restriction<Train> restriction);

    // Methods for testing @Find @OrderBy @First @Select combinations
    @Find
    @OrderBy("name")
    @First(2)
    @Select("name")
    List<String> findFirst2TrainNamesOrderedByName(Restriction<Train> restriction);

    @Find
    @OrderBy("capacity")
    @First(3)
    @Select("name")
    @Select("capacity")
    List<TrainNameCapacityDto> findFirst3TrainsOrderedByCapacity(Restriction<Train> restriction);

    @Find
    @OrderBy("speed")
    @First(1)
    List<Train> findFirstTrainOrderedBySpeed(Restriction<Train> restriction);

    @Find
    @OrderBy("name")
    @First(4)
    @Select("name")
    @Select("model")
    List<TrainNameModelDto> findFirst4TrainsOrderedByName(Restriction<Train> restriction);

    // Methods for testing @Find @OrderBy @First @Select combinations without restrictions
    @Find
    @OrderBy("name")
    @First(2)
    @Select("name")
    List<String> findFirst2TrainNamesOrderedByName();

    @Find
    @OrderBy("capacity")
    @First(3)
    List<Train> findFirst3TrainsOrderedByCapacity();

    @Find
    @OrderBy("speed")
    @First(1)
    List<Train> findFirstTrainOrderedBySpeed();

    @Find
    @OrderBy("name")
    @First(4)
    List<Train> findFirst4TrainsOrderedByName();
}
