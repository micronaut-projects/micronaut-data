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
package io.micronaut.data.document.processor

import org.bson.BsonArray

class BuildMongoEmbeddedJoinSpec extends AbstractDataSpec {

    void "test nested joins through an embedded property"() {
        given:
        def repository = buildRepository('test.OrderRepository', """
import io.micronaut.data.mongodb.annotation.MongoRepository;
import org.bson.types.ObjectId;

@MongoRepository
interface OrderRepository extends GenericRepository<Order, ObjectId> {

    @Join("details.customer")
    List<Order> findByDetailsNote(String note);

    @Join("details.customer")
    @Join("details.customer.address")
    List<Order> findByDetailsNoteIsNotNull();
}

@MappedEntity
class Order {
    @Id
    @GeneratedValue
    private ObjectId id;
    @Relation(Relation.Kind.EMBEDDED)
    private Details details;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public Details getDetails() { return details; }
    public void setDetails(Details details) { this.details = details; }
}

@Embeddable
class Details {
    private String note;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Customer customer;

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
}

@MappedEntity
class Customer {
    @Id
    @GeneratedValue
    private ObjectId id;
    private String name;
    @Relation(Relation.Kind.MANY_TO_ONE)
    private Address address;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Address getAddress() { return address; }
    public void setAddress(Address address) { this.address = address; }
}

@MappedEntity
class Address {
    @Id
    @GeneratedValue
    private ObjectId id;
    private String city;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
}
"""
        )

        when:
        String singleJoin = TestUtils.getQuery(repository.getRequiredMethod("findByDetailsNote", String))
        String nestedJoin = TestUtils.getQuery(repository.getRequiredMethod("findByDetailsNoteIsNotNull"))

        then:
        singleJoin == "[{\$lookup:{from:'customer',localField:'details.customer._id',foreignField:'_id',as:'details.customer'}}," +
                "{\$unwind:{path:'\$details.customer',preserveNullAndEmptyArrays:true}}," +
                "{\$match:{'details.note':{\$eq:{\$mn_qp:0}}}}]"
        nestedJoin == "[{\$lookup:{from:'customer',localField:'details.customer._id',foreignField:'_id'," +
                "pipeline:[{\$lookup:{from:'address',localField:'address._id',foreignField:'_id',as:'address'}}," +
                "{\$unwind:{path:'\$address',preserveNullAndEmptyArrays:true}}],as:'details.customer'}}," +
                "{\$unwind:{path:'\$details.customer',preserveNullAndEmptyArrays:true}}," +
                "{\$match:{'details.note':{\$ne:null}}}]"

        and: "the pipeline is valid JSON"
        BsonArray.parse(nestedJoin).size() == 3
    }
}
