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
package io.micronaut.data.tck.tests

import io.micronaut.context.ApplicationContext
import io.micronaut.data.model.geo.Geometry
import io.micronaut.data.model.geo.GeometryCollection
import io.micronaut.data.model.geo.LineString
import io.micronaut.data.model.geo.MultiLineString
import io.micronaut.data.model.geo.MultiPoint
import io.micronaut.data.model.geo.MultiPolygon
import io.micronaut.data.model.geo.Point
import io.micronaut.data.model.geo.Polygon
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverJson
import io.micronaut.data.tck.jdbc.entities.geo.DeliveryDriverWkt
import io.micronaut.data.tck.jdbc.entities.geo.District
import io.micronaut.data.tck.jdbc.entities.geo.GeometryEntityJson
import io.micronaut.data.tck.jdbc.entities.geo.GeometryEntityWkt
import io.micronaut.data.tck.jdbc.entities.geo.HotelJson
import io.micronaut.data.tck.jdbc.entities.geo.HotelWkt
import io.micronaut.data.tck.jdbc.entities.geo.Location
import io.micronaut.data.tck.jdbc.entities.geo.School
import io.micronaut.data.tck.repositories.DeliveryDriverJsonRepository
import io.micronaut.data.tck.repositories.DeliveryDriverWktRepository
import io.micronaut.data.tck.repositories.DistrictRepository
import io.micronaut.data.tck.repositories.GeometryEntityJsonRepository
import io.micronaut.data.tck.repositories.GeometryEntityWktRepository
import io.micronaut.data.tck.repositories.HotelJsonRepository
import io.micronaut.data.tck.repositories.HotelWktRepository
import io.micronaut.data.tck.repositories.SchoolRepository
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assumptions.assumeTrue

abstract class AbstractGeoSpec extends Specification {

    abstract GeometryEntityJsonRepository getGeometryEntityJsonRepository()

    abstract GeometryEntityWktRepository getGeometryEntityWktRepository()

    abstract SchoolRepository getSchoolRepository()

    abstract HotelJsonRepository getHotelJsonRepository()

    abstract HotelWktRepository getHotelWktRepository()

    abstract DeliveryDriverJsonRepository getDeliveryDriverJsonRepository()

    abstract DeliveryDriverWktRepository getDeliveryDriverWktRepository()

    abstract DistrictRepository getDistrictRepository()

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run(properties)

    void cleanup() {
        getGeometryEntityJsonRepository()?.deleteAll()
        getGeometryEntityWktRepository()?.deleteAll()
        getSchoolRepository()?.deleteAll()
        getHotelJsonRepository()?.deleteAll()
        getHotelWktRepository()?.deleteAll()
        getDeliveryDriverJsonRepository()?.deleteAll()
        getDeliveryDriverWktRepository()?.deleteAll()
        getDistrictRepository()?.deleteAll()
    }

    void "test creates, reads, and updates embedded geometry with JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        Location location1 = new Location()
        location1.setPoint(new Point(2.0, 2.5))
        School school = new School()
        school.setName("school1")
        school.setLocation(location1)

        when:
        School savedSchool = getSchoolRepository().insert(school)

        then:
        savedSchool.id > 0

        when:
        Optional<School> foundSchool = getSchoolRepository().findById(savedSchool.id)

        then:
        foundSchool.isPresent()
        with (foundSchool.get()) {
            it.getName() == "school1"
            it.getLocation().getPoint().x() == 2.0d
            it.getLocation().getPoint().y() == 2.5d
        }

        when:
        Location location2 = new Location()
        location2.setPoint(new Point(3.0, 3.5))
        school.setLocation(location2)
        getSchoolRepository().update(school)
        foundSchool = getSchoolRepository().findById(savedSchool.id)

        then:
        foundSchool.isPresent()
        with (foundSchool.get()) {
            it.getName() == "school1"
            it.getLocation().getPoint().x() == 3.0d
            it.getLocation().getPoint().y() == 3.5d
        }
    }

    void "test creates, reads, and updates geometry with JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        GeometryEntityJson entity = createGeometryEntityJson(1, 3)

        when:
        GeometryEntityJson savedEntity = getGeometryEntityJsonRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityJson> foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
            assertPolygon(it.getPolygon(), 1)
            assertMultiPolygon(it.getMultiPolygon(), 1)
            assertGeometryCollection(it.getGeometryCollection(), 3)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        entity.setPolygon(createPolygon(2))
        entity.setMultiPolygon(createMultiPolygon(2))
        entity.setGeometryCollection(createGeometryCollection(4))
        getGeometryEntityJsonRepository().update(entity)
        foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
            assertPolygon(it.getPolygon(), 2)
            assertMultiPolygon(it.getMultiPolygon(), 2)
            assertGeometryCollection(it.getGeometryCollection(), 4)
        }
    }

    void "test updates geometry to null with JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())
        assumeTrue(supportsDeletingGeometryTypes())

        given:
        GeometryEntityJson entity = createGeometryEntityJson(5, 8)

        when:
        GeometryEntityJson savedEntity = getGeometryEntityJsonRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityJson> foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 5)
            assertMultiPoint(it.getMultiPoint(), 5)
            assertLineString(it.getLineString(), 5)
            assertMultiLineString(it.getMultiLineString(), 5)
            assertPolygon(it.getPolygon(), 5)
            assertMultiPolygon(it.getMultiPolygon(), 5)
            assertGeometryCollection(it.getGeometryCollection(), 8)
        }

        when:
        entity.setMultiLineString(null)
        entity.setPolygon(null)
        entity.setMultiPolygon(null)
        entity.setGeometryCollection(null)
        getGeometryEntityJsonRepository().update(entity)
        foundEntity = getGeometryEntityJsonRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 5)
            assertMultiPoint(it.getMultiPoint(), 5)
            assertLineString(it.getLineString(), 5)
            assertNull(it.getMultiLineString())
            assertNull(it.getPolygon())
            assertNull(it.getMultiPolygon())
            assertNull(it.getGeometryCollection())
        }
    }

    void "test creates, reads, updates, and clears geometry with WKT conversion"() {
        given:
        GeometryEntityWkt entity = new GeometryEntityWkt()
        entity.setPoint(createPoint(1))
        entity.setMultiPoint(createMultiPoint(1))
        entity.setLineString(createLineString(1))
        entity.setMultiLineString(createMultiLineString(1))
        entity.setPolygon(createPolygon(1))
        entity.setMultiPolygon(createMultiPolygon(1))
        entity.setGeometryCollection(createGeometryCollection(3))

        when:
        GeometryEntityWkt savedEntity = getGeometryEntityWktRepository().insert(entity)

        then:
        savedEntity.id > 0

        when:
        Optional<GeometryEntityWkt> foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        foundEntity.isPresent()
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 1)
            assertMultiPoint(it.getMultiPoint(), 1)
            assertLineString(it.getLineString(), 1)
            assertMultiLineString(it.getMultiLineString(), 1)
            assertPolygon(it.getPolygon(), 1)
            assertMultiPolygon(it.getMultiPolygon(), 1)
            assertGeometryCollection(it.getGeometryCollection(), 3)
        }

        when:
        entity.setPoint(createPoint(2))
        entity.setMultiPoint(createMultiPoint(2))
        entity.setLineString(createLineString(2))
        entity.setMultiLineString(createMultiLineString(2))
        entity.setPolygon(createPolygon(2))
        entity.setMultiPolygon(createMultiPolygon(2))
        entity.setGeometryCollection(createGeometryCollection(4))
        getGeometryEntityWktRepository().update(entity)
        foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertMultiLineString(it.getMultiLineString(), 2)
            assertPolygon(it.getPolygon(), 2)
            assertMultiPolygon(it.getMultiPolygon(), 2)
            assertGeometryCollection(it.getGeometryCollection(), 4)
        }

        when:
        entity.setMultiLineString(null)
        entity.setPolygon(null)
        entity.setMultiPolygon(null)
        entity.setGeometryCollection(null)
        getGeometryEntityWktRepository().update(entity)
        foundEntity = getGeometryEntityWktRepository().findById(savedEntity.id)

        then:
        with (foundEntity.get()) {
            assertPoint(it.getPoint(), 2)
            assertMultiPoint(it.getMultiPoint(), 2)
            assertLineString(it.getLineString(), 2)
            assertNull(it.getMultiLineString())
            assertNull(it.getPolygon())
            assertNull(it.getMultiPolygon())
            assertNull(it.getGeometryCollection())
        }
    }

    void "test findByLocationGeoWithin with JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson inside1 = new HotelJson("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelJson inside2 = new HotelJson("Sunset Resort", new Point(12.0, 12.0))
        HotelJson outside = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        Polygon city = new Polygon([
                new LineString([
                        new Point(9.0, 9.0),
                        new Point(9.0, 15.0),
                        new Point(15.0, 15.0),
                        new Point(15.0, 9.0),
                        new Point(9.0, 9.0)
                ])
        ])

        when:
        getHotelJsonRepository().saveAll(List.of(inside1, inside2, outside))
        List<HotelJson> result = getHotelJsonRepository().findByLocationGeoWithin(city)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoIntersects with JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson onRoute1 = new HotelJson("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelJson onRoute2 = new HotelJson("Sunset Resort", new Point(12.0, 12.0))
        HotelJson outside = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        LineString busRoute = new LineString([
                new Point(9.0, 9.0),
                new Point(15.0, 15.0)
        ])

        when:
        getHotelJsonRepository().saveAll(List.of(onRoute1, onRoute2, outside))
        List<HotelJson> result = getHotelJsonRepository().findByLocationGeoIntersects(busRoute)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoWithin with WKT conversion"() {
        given:
        HotelWkt inside1 = new HotelWkt("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelWkt inside2 = new HotelWkt("Sunset Resort", new Point(12.0, 12.0))
        HotelWkt outside = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        Polygon city = new Polygon([
                new LineString([
                        new Point(9.0, 9.0),
                        new Point(9.0, 15.0),
                        new Point(15.0, 15.0),
                        new Point(15.0, 9.0),
                        new Point(9.0, 9.0)
                ])
        ])

        when:
        getHotelWktRepository().saveAll(List.of(inside1, inside2, outside))
        List<HotelWkt> result = getHotelWktRepository().findByLocationGeoWithin(city)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationGeoIntersects with WKT conversion"() {
        given:
        HotelWkt onRoute1 = new HotelWkt("Grand Plaza Hotel", new Point(10.0, 10.0))
        HotelWkt onRoute2 = new HotelWkt("Sunset Resort", new Point(12.0, 12.0))
        HotelWkt outside = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        LineString busRoute = new LineString([
                new Point(9.0, 9.0),
                new Point(15.0, 15.0)
        ])

        when:
        getHotelWktRepository().saveAll(List.of(onRoute1, onRoute2, outside))
        List<HotelWkt> result = getHotelWktRepository().findByLocationGeoIntersects(busRoute)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear with projected CRS and JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        HotelJson nearby1 = new HotelJson("Grand Plaza Hotel", new Point(11.0, 11.0))
        HotelJson nearby2 = new HotelJson("Sunset Resort", new Point(12.0, 10.0))
        HotelJson farAway = new HotelJson("Mountain View Hotel", new Point(30.0, 30.0))

        Point center = new Point(10.0, 10.0)

        when:
        getHotelJsonRepository().saveAll(List.of(nearby1, nearby2, farAway))
        List<HotelJson> result = getHotelJsonRepository().findByLocationNear(center, 3d)
        List<String> names = result.stream()
                .map(HotelJson::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear with projected CRS and WKT conversion"() {
        given:
        HotelWkt nearby1 = new HotelWkt("Grand Plaza Hotel", new Point(11.0, 11.0))
        HotelWkt nearby2 = new HotelWkt("Sunset Resort", new Point(12.0, 10.0))
        HotelWkt farAway = new HotelWkt("Mountain View Hotel", new Point(30.0, 30.0))

        Point center = new Point(10.0, 10.0)

        when:
        getHotelWktRepository().saveAll(List.of(nearby1, nearby2, farAway))
        List<HotelWkt> result = getHotelWktRepository().findByLocationNear(center, 3d)
        List<String> names = result.stream()
                .map(HotelWkt::getName)
                .toList()

        then:
        names.size() == 2
        names.contains("Grand Plaza Hotel")
        names.contains("Sunset Resort")
    }

    void "test findByLocationNear with geographic CRS and JSON conversion"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        DeliveryDriverJson nearby = new DeliveryDriverJson("Nearby Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverJson closest = new DeliveryDriverJson("Closest Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverJson busy = new DeliveryDriverJson("Busy Driver", DeliveryDriverJson.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverJson far = new DeliveryDriverJson("Far Driver", DeliveryDriverJson.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverJsonRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverJson> candidates = getDeliveryDriverJsonRepository().findByStatusAndLocationNear(
                DeliveryDriverJson.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }

    void "test findByLocationNear with geographic CRS and WKT conversion"() {
        given:
        DeliveryDriverWkt nearby = new DeliveryDriverWkt("Nearby Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9757d, 40.7554d))
        DeliveryDriverWkt closest = new DeliveryDriverWkt("Closest Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9827d, 40.7504d))
        DeliveryDriverWkt busy = new DeliveryDriverWkt("Busy Driver", DeliveryDriverWkt.Status.BUSY, new Point(-73.9850d, 40.7488d))
        DeliveryDriverWkt far = new DeliveryDriverWkt("Far Driver", DeliveryDriverWkt.Status.AVAILABLE, new Point(-73.9000d, 40.8000d))

        Point orderLocation = new Point(-73.9857, 40.7484)

        when:
        getDeliveryDriverWktRepository().saveAll(List.of(nearby, closest, busy, far))
        List<DeliveryDriverWkt> candidates = getDeliveryDriverWktRepository().findByStatusAndLocationNear(
                DeliveryDriverWkt.Status.AVAILABLE,
                orderLocation,
                5_000d
        )
        List<String> names = candidates.collect { it.name() }

        then:
        names.size() == 2
        names.contains("Nearby Driver")
        names.contains("Closest Driver")
    }

    void "test district mappings, fetch joins, and geospatial predicates"() {
        assumeTrue(supportsGeometryJsonConversion())

        given:
        Polygon downtownArea = square(0.0d, 10.0d)
        Polygon searchArea = square(-1.0d, 11.0d)
        Polygon hotelSearchArea = square(2.5d, 3.5d)
        Polygon outskirtsArea = square(20.0d, 30.0d)
        Polygon emptyArea = square(40.0d, 50.0d)
        LineString downtownRoute = new LineString([
                new Point(4.5d, 5.0d),
                new Point(5.5d, 5.0d)
        ])

        GeometryEntityJson geometryEntityJson = getGeometryEntityJsonRepository().save(createGeometryEntityJson(5))
        GeometryEntityWkt geometryEntityWkt = getGeometryEntityWktRepository().save(createGeometryEntityWkt(5))
        District downtown = getDistrictRepository().save(new District(null, "Downtown", downtownArea, geometryEntityJson, geometryEntityWkt))
        District outskirts = getDistrictRepository().save(new District(null, "Outskirts", outskirtsArea, geometryEntityJson, geometryEntityWkt))
        District emptyDistrict = getDistrictRepository().save(new District(null, "Empty", emptyArea, geometryEntityJson, geometryEntityWkt))

        addSchool(downtown, "Downtown Primary", new Point(1.0d, 1.0d))
        addSchool(downtown, "Downtown Secondary", new Point(2.0d, 2.0d))
        addHotelJson(downtown, "Downtown JSON Hotel", new Point(3.0d, 3.0d))
        addHotelJson(downtown, "Downtown JSON Inn", new Point(4.0d, 4.0d))
        addHotelWkt(downtown, "Downtown WKT Hotel", new Point(5.0d, 5.0d))
        addHotelWkt(downtown, "Downtown WKT Inn", new Point(6.0d, 6.0d))

        addHotelJson(outskirts, "Outskirts JSON Hotel", new Point(22.0d, 22.0d))

        when:
        District fetchedDowntown = getDistrictRepository().findByName("Downtown")
        District fetchedOutskirts = getDistrictRepository().findByName("Outskirts")
        District fetchedEmptyDistrict = getDistrictRepository().findByName("Empty")
        List<District> districtsWithin = getDistrictRepository().findByAreaGeoWithin(searchArea)
        List<District> districtsIntersecting = getDistrictRepository().findByAreaGeoIntersects(downtownRoute)
        List<District> districtsWithJsonHotelsWithin = getDistrictRepository().findByHotelsJsonLocationGeoWithin(hotelSearchArea)
        List<District> districtsWithOutskirtsJsonHotel = getDistrictRepository().findByHotelsJsonLocationGeoWithin(outskirtsArea)
        List<District> districtsWithWktHotelsIntersecting = getDistrictRepository().findByHotelsWktLocationGeoIntersects(downtownRoute)

        then:
        getSchoolRepository().findByDistrictId(downtown.id)*.name.sort() == ["Downtown Primary", "Downtown Secondary"]
        getHotelJsonRepository().findByDistrictId(downtown.id)*.name.sort() == ["Downtown JSON Hotel", "Downtown JSON Inn"]
        getHotelWktRepository().findByDistrictId(downtown.id)*.name.sort() == ["Downtown WKT Hotel", "Downtown WKT Inn"]

        fetchedDowntown != null
        fetchedDowntown.id == downtown.id
        fetchedDowntown.schools*.name.sort() == ["Downtown Primary", "Downtown Secondary"]
        fetchedDowntown.hotelsJson*.name.sort() == ["Downtown JSON Hotel", "Downtown JSON Inn"]
        fetchedDowntown.hotelsJson*.location.toSet() == [new Point(3.0d, 3.0d), new Point(4.0d, 4.0d)] as Set
        fetchedDowntown.hotelsWkt*.name.sort() == ["Downtown WKT Hotel", "Downtown WKT Inn"]
        fetchedDowntown.hotelsWkt*.location.toSet() == [new Point(5.0d, 5.0d), new Point(6.0d, 6.0d)] as Set
        fetchedDowntown.geometryEntityJson.id == geometryEntityJson.id
        with(fetchedDowntown.geometryEntityJson) {
            assertPoint(it.point, 5)
            assertMultiPoint(it.multiPoint, 5)
            assertLineString(it.lineString, 5)
            assertMultiLineString(it.multiLineString, 5)
            assertPolygon(it.polygon, 5)
            assertMultiPolygon(it.multiPolygon, 5)
            assertGeometryCollection(it.geometryCollection, 5)
        }
        fetchedDowntown.geometryEntityWkt.id == geometryEntityWkt.id
        with(fetchedDowntown.geometryEntityWkt) {
            assertPoint(it.point, 5)
            assertMultiPoint(it.multiPoint, 5)
            assertLineString(it.lineString, 5)
            assertMultiLineString(it.multiLineString, 5)
            assertPolygon(it.polygon, 5)
            assertMultiPolygon(it.multiPolygon, 5)
            assertGeometryCollection(it.geometryCollection, 5)
        }

        and: "left fetch returns populated and partially populated districts"
        fetchedOutskirts != null
        fetchedOutskirts.id == outskirts.id
        !fetchedOutskirts.schools
        fetchedOutskirts.hotelsJson*.name == ["Outskirts JSON Hotel"]
        !fetchedOutskirts.hotelsWkt
        fetchedOutskirts.geometryEntityJson.id == geometryEntityJson.id
        fetchedOutskirts.geometryEntityWkt.id == geometryEntityWkt.id

        and: "left fetch keeps a district with no collection rows"
        fetchedEmptyDistrict != null
        fetchedEmptyDistrict.id == emptyDistrict.id
        !fetchedEmptyDistrict.schools
        !fetchedEmptyDistrict.hotelsJson
        !fetchedEmptyDistrict.hotelsWkt

        and: "GeoWithin and GeoIntersects filter district geometry"
        districtsWithin*.id == [downtown.id]
        districtsIntersecting*.id == [downtown.id]

        and: "GeoWithin and GeoIntersects also filter through joined hotel geometries"
        districtsWithJsonHotelsWithin*.id == [downtown.id]
        districtsWithOutskirtsJsonHotel*.id == [outskirts.id]
        districtsWithWktHotelsIntersecting*.id == [downtown.id]

    }

    void "test WKT district fetch joins without JSON conversion"() {
        given:
        GeometryEntityWkt geometryEntityWkt = getGeometryEntityWktRepository().save(createGeometryEntityWkt(5))
        District district = getDistrictRepository().save(new District(null, "WKT District", null, null, geometryEntityWkt))
        addHotelWkt(district, "First WKT Hotel", new Point(3.0d, 3.0d))
        addHotelWkt(district, "Second WKT Hotel", new Point(4.0d, 4.0d))

        when:
        District fetchedDistrict = getDistrictRepository().findByName("WKT District")

        then:
        fetchedDistrict != null
        fetchedDistrict.id == district.id
        fetchedDistrict.geometryEntityWkt.id == geometryEntityWkt.id
        with(fetchedDistrict.geometryEntityWkt) {
            assertPoint(it.point, 5)
            assertMultiPoint(it.multiPoint, 5)
            assertLineString(it.lineString, 5)
            assertMultiLineString(it.multiLineString, 5)
            assertPolygon(it.polygon, 5)
            assertMultiPolygon(it.multiPolygon, 5)
            assertGeometryCollection(it.geometryCollection, 5)
        }
        fetchedDistrict.hotelsWkt*.name.toSet() == ["First WKT Hotel", "Second WKT Hotel"] as Set
        fetchedDistrict.hotelsWkt.find { it.name == "First WKT Hotel" }.location == new Point(3.0d, 3.0d)
        fetchedDistrict.hotelsWkt.find { it.name == "Second WKT Hotel" }.location == new Point(4.0d, 4.0d)
    }

    private void addSchool(District district, String name, Point point) {
        Location location = new Location()
        location.setPoint(point)
        School school = new School()
        school.setName(name)
        school.setLocation(location)
        school.setDistrict(district)
        getSchoolRepository().save(school)
    }

    private void addHotelJson(District district, String name, Point point) {
        HotelJson hotel = new HotelJson(name, point)
        hotel.setDistrict(district)
        getHotelJsonRepository().save(hotel)
    }

    private void addHotelWkt(District district, String name, Point point) {
        HotelWkt hotel = new HotelWkt(name, point)
        hotel.setDistrict(district)
        getHotelWktRepository().save(hotel)
    }

    private Polygon square(double min, double max) {
        return new Polygon([
                new LineString([
                        new Point(min, min),
                        new Point(min, max),
                        new Point(max, max),
                        new Point(max, min),
                        new Point(min, min)
                ])
        ])
    }

    private GeometryEntityJson createGeometryEntityJson(int n) {
        return createGeometryEntityJson(n, n)
    }

    private GeometryEntityJson createGeometryEntityJson(int n, int geometryCollectionN) {
        GeometryEntityJson entity = new GeometryEntityJson()
        entity.setPoint(createPoint(n))
        entity.setMultiPoint(createMultiPoint(n))
        entity.setLineString(createLineString(n))
        entity.setMultiLineString(createMultiLineString(n))
        entity.setPolygon(createPolygon(n))
        entity.setMultiPolygon(createMultiPolygon(n))
        entity.setGeometryCollection(createGeometryCollection(geometryCollectionN))
        return entity
    }

    private GeometryEntityWkt createGeometryEntityWkt(int n) {
        GeometryEntityWkt entity = new GeometryEntityWkt()
        entity.setPoint(createPoint(n))
        entity.setMultiPoint(createMultiPoint(n))
        entity.setLineString(createLineString(n))
        entity.setMultiLineString(createMultiLineString(n))
        entity.setPolygon(createPolygon(n))
        entity.setMultiPolygon(createMultiPolygon(n))
        entity.setGeometryCollection(createGeometryCollection(n))
        return entity
    }

    protected boolean supportsGeometryJsonConversion() {
        return true
    }

    protected boolean supportsDeletingGeometryTypes() {
        return true
    }

    Point createPoint(double x) {
        return new Point(x, x + 0.5)
    }

    void assertPoint(Point point, double x) {
        assert point != null
        assert point.x() == x
        assert point.y() == x + 0.5
    }

    MultiPoint createMultiPoint(int n) {
        return new MultiPoint([createPoint(n), createPoint(n + 1)])
    }

    void assertMultiPoint(MultiPoint multiPoint, int n) {
        assert multiPoint != null
        def points = multiPoint.points()
        assertPoint(points.get(0), n)
        assertPoint(points.get(1), n + 1)
    }

    LineString createLineString(int n) {
        return new LineString([createPoint(n), createPoint(n + 1)])
    }

    void assertLineString(LineString lineString, int n) {
        assert lineString != null
        def points = lineString.points()
        assertPoint(points.get(0), n)
        assertPoint(points.get(1), n + 1)
    }

    MultiLineString createMultiLineString(int n) {
        return new MultiLineString([createLineString(n + 10), createLineString(n + 20)])
    }

    void assertMultiLineString(MultiLineString multiLineString, int n) {
        assert multiLineString != null
        def lineStrings = multiLineString.lineStrings()
        assertLineString(lineStrings.get(0), n + 10)
        assertLineString(lineStrings.get(1), n + 20)
    }

    Polygon createPolygon(int n) {
        return new Polygon([
                new LineString([
                        new Point(n + 0.0, n + 0.0),
                        new Point(n + 4.0, n + 0.0),
                        new Point(n + 4.0, n + 3.0),
                        new Point(n + 0.0, n + 3.0),
                        new Point(n + 0.0, n + 0.0)
                ]),
                new LineString([
                        new Point(n + 0.5, n + 0.5),
                        new Point(n + 2.5, n + 0.5),
                        new Point(n + 2.5, n + 2.5),
                        new Point(n + 0.5, n + 0.5)
                ])
        ])
    }

    void assertPolygon(Polygon polygon, int n) {
        def lineStrings = polygon.lineStrings()
        assert lineStrings.size() == 2

        def points1 = lineStrings.get(0).points()
        assert points1*.x() == [n + 0.0d, n + 4.0d, n + 4.0d, n + 0.0d, n + 0.0d]
        assert points1*.y() == [n + 0.0d, n + 0.0d, n + 3.0d, n + 3.0d, n + 0.0d]

        def points2 = lineStrings.get(1).points()
        assert points2*.x() == [n + 0.5d, n + 2.5d, n + 2.5d, n + 0.5d]
        assert points2*.y() == [n + 0.5d, n + 0.5d, n + 2.5d, n + 0.5d]
    }

    MultiPolygon createMultiPolygon(int n) {
        return new MultiPolygon([createPolygon(n + 10), createPolygon(n + 20)])
    }

    void assertMultiPolygon(MultiPolygon multiPolygon, int n) {
        def polygons = multiPolygon.polygons()
        assertPolygon(polygons.get(0), n + 10)
        assertPolygon(polygons.get(1), n + 20)
    }

    protected GeometryCollection createGeometryCollection(int n) {
        return new GeometryCollection([
                createPoint(n),
                createMultiPoint(n),
                createLineString(n),
                createMultiLineString(n),
                createPolygon(n),
                createMultiPolygon(n)
        ] as List<Geometry>)
    }

    protected void assertGeometryCollection(GeometryCollection geometryCollection, int n) {
        def geometries = geometryCollection.geometries()
        assertPoint((Point) geometries.get(0), n)
        assertMultiPoint((MultiPoint) geometries.get(1), n)
        assertLineString((LineString) geometries.get(2), n)
        assertMultiLineString((MultiLineString) geometries.get(3), n)
        assertPolygon((Polygon) geometries.get(4), n)
        assertMultiPolygon((MultiPolygon) geometries.get(5), n)
    }
}
