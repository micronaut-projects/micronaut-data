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

import org.bson.BsonDocument

class BuildMongoLiteralSpec extends AbstractDataSpec {

    void "test a string literal with quotes and backslashes is escaped in the query"() {
        given:
        def repository = buildRepository('test.AccountRepository', """
import io.micronaut.data.annotation.TenantId;
import io.micronaut.data.annotation.WithTenantId;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import org.bson.types.ObjectId;

@MongoRepository
interface AccountRepository extends GenericRepository<Account, ObjectId> {

    @WithTenantId("C:\\\\temp \\"main\\" O'Brien")
    List<Account> findAll();
}

@MappedEntity
class Account {
    @Id
    @GeneratedValue
    private ObjectId id;
    @TenantId
    private String tenancy;

    public ObjectId getId() { return id; }
    public void setId(ObjectId id) { this.id = id; }
    public String getTenancy() { return tenancy; }
    public void setTenancy(String tenancy) { this.tenancy = tenancy; }
}
"""
        )

        when:
        String query = TestUtils.getQuery(repository.getRequiredMethod("findAll"))

        then:
        query == $/{tenancy:{$$eq:'C:\\temp "main" O\'Brien'}}/$
        BsonDocument.parse(query).getDocument("tenancy").getString('$eq').value == 'C:\\temp "main" O\'Brien'
    }
}
