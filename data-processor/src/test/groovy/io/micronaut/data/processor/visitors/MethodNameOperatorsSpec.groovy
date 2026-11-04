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
package io.micronaut.data.processor.visitors

import static io.micronaut.data.processor.visitors.TestUtils.getQuery

class MethodNameOperatorsSpec extends AbstractDataSpec {

    private static final String PHONE = '''
@MappedEntity
class Phone {
    @Id
    private Long id;
    private String name;
    private String brand;
    private String origin;
    private String androidVersion;
    private Integer order;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getOrigin() { return origin; }
    public void setOrigin(String origin) { this.origin = origin; }
    public String getAndroidVersion() { return androidVersion; }
    public void setAndroidVersion(String androidVersion) { this.androidVersion = androidVersion; }
    public Integer getOrder() { return order; }
    public void setOrder(Integer order) { this.order = order; }
}
'''

    void "test And/Or operators are not split inside property names"() {
        given:
        def repository = buildRepository('test.PhoneRepository', """
@Repository
interface PhoneRepository extends GenericRepository<Phone, Long> {

    List<Phone> findByNameOrOrigin(String name, String origin);

    List<Phone> findByOriginOrName(String origin, String name);

    List<Phone> findByBrandAndAndroidVersion(String brand, String androidVersion);

    List<Phone> findByAndroidVersionAndBrand(String androidVersion, String brand);

    List<Phone> findByNameAndOrder(String name, Integer order);

    List<Phone> findByOrderOrAndroidVersion(Integer order, String androidVersion);

    List<Phone> findByBrandAndNameOrOrigin(String brand, String name, String origin);

    List<Phone> findByAndroidVersionOrOriginAndOrder(String androidVersion, String origin, Integer order);

    List<String> findAndroidVersionByBrand(String brand);

    List<Phone> findByBrandOrderByAndroidVersion(String brand);

    List<Phone> findByBrandOrderByOriginAndAndroidVersion(String brand);
}

$PHONE
""")

        expect:
        getQuery(repository.getRequiredMethod("findByNameOrOrigin", String, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.name = :p1 OR phone_.origin = :p2)'
        getQuery(repository.getRequiredMethod("findByOriginOrName", String, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.origin = :p1 OR phone_.name = :p2)'
        getQuery(repository.getRequiredMethod("findByBrandAndAndroidVersion", String, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.brand = :p1 AND phone_.androidVersion = :p2)'
        getQuery(repository.getRequiredMethod("findByAndroidVersionAndBrand", String, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.androidVersion = :p1 AND phone_.brand = :p2)'
        getQuery(repository.getRequiredMethod("findByNameAndOrder", String, Integer)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.name = :p1 AND phone_.order = :p2)'
        getQuery(repository.getRequiredMethod("findByOrderOrAndroidVersion", Integer, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.order = :p1 OR phone_.androidVersion = :p2)'
        getQuery(repository.getRequiredMethod("findByBrandAndNameOrOrigin", String, String, String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.brand = :p1 AND (phone_.name = :p2 OR phone_.origin = :p3))'
        getQuery(repository.getRequiredMethod("findByAndroidVersionOrOriginAndOrder", String, String, Integer)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE ((phone_.androidVersion = :p1 OR phone_.origin = :p2) AND phone_.order = :p3)'
        getQuery(repository.getRequiredMethod("findAndroidVersionByBrand", String)) ==
            'SELECT phone_.androidVersion FROM test.Phone AS phone_ WHERE (phone_.brand = :p1)'
        getQuery(repository.getRequiredMethod("findByBrandOrderByAndroidVersion", String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.brand = :p1) ORDER BY phone_.androidVersion ASC'
        getQuery(repository.getRequiredMethod("findByBrandOrderByOriginAndAndroidVersion", String)) ==
            'SELECT phone_ FROM test.Phone AS phone_ WHERE (phone_.brand = :p1) ORDER BY phone_.origin ASC,phone_.androidVersion ASC'
    }

    void "test IgnoreCase before the restriction removes the IgnoreCase suffix of the property"() {
        given:
        def repository = buildRepository('test.PhoneRepository', """
@Repository
interface PhoneRepository extends GenericRepository<Phone, Long> {

    List<Phone> findByNameIgnoreCaseContains(String name);

    List<Phone> findByNameContainsIgnoreCase(String name);

    List<Phone> findByOriginIgnoreCaseStartsWith(String origin);
}

$PHONE
""")

        expect:
        getQuery(repository.getRequiredMethod("findByNameIgnoreCaseContains", String)) ==
            "SELECT phone_ FROM test.Phone AS phone_ WHERE (LOWER(phone_.name) LIKE CONCAT('%',LOWER(:p1),'%'))"
        getQuery(repository.getRequiredMethod("findByNameIgnoreCaseContains", String)) ==
            getQuery(repository.getRequiredMethod("findByNameContainsIgnoreCase", String))
        getQuery(repository.getRequiredMethod("findByOriginIgnoreCaseStartsWith", String)) ==
            "SELECT phone_ FROM test.Phone AS phone_ WHERE (LOWER(phone_.origin) LIKE CONCAT(LOWER(:p1),'%'))"
    }

    void "test incompatible parameter type is reported as a compilation error of the method"() {
        when:
        buildRepository('test.PhoneRepository', """
@Repository
interface PhoneRepository extends GenericRepository<Phone, Long> {

    List<Phone> findByOrder(String order);
}

$PHONE
""")

        then:
        def e = thrown(RuntimeException)
        e.message.contains('Unable to implement Repository method: test.PhoneRepository.findByOrder(String order). Parameter [java.lang.String order] is not compatible with property [java.lang.Integer order] of entity: test.Phone')
        !e.message.contains('Exception occurred while processing')
    }
}
