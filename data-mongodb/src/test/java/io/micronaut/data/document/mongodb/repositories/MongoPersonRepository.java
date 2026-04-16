package io.micronaut.data.document.mongodb.repositories;

import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import io.micronaut.data.document.tck.entities.Person;
import io.micronaut.data.document.tck.repositories.PersonRepository;
import io.micronaut.data.model.Page;
import io.micronaut.data.model.Pageable;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.data.mongodb.annotation.MongoAggregateOptions;
import io.micronaut.data.mongodb.annotation.MongoAggregateQuery;
import io.micronaut.data.mongodb.annotation.MongoDeleteOptions;
import io.micronaut.data.mongodb.annotation.MongoDeleteQuery;
import io.micronaut.data.mongodb.annotation.MongoFindOptions;
import io.micronaut.data.mongodb.annotation.MongoFindQuery;
import io.micronaut.data.mongodb.annotation.MongoRepository;
import io.micronaut.data.mongodb.annotation.MongoUpdateOptions;
import io.micronaut.data.mongodb.annotation.MongoUpdateQuery;
import io.micronaut.data.mongodb.annotation.MongoUpdateReturningQuery;
import org.bson.BsonDocument;

import java.util.List;

@MongoAggregateOptions(bypassDocumentValidation = true, allowDiskUse = true)
@MongoFindOptions(batchSize = 3, allowDiskUse = true)
@MongoDeleteOptions()
@MongoUpdateOptions(bypassDocumentValidation = true)
@MongoRepository
public interface MongoPersonRepository extends PersonRepository {

    List<BsonDocument> queryAll();

    List<Person> findAllByNameBetween(String from, String to);

    List<Person> findAllByNameNotBetween(String from, String to);

    @MongoFindQuery(filter = "{name:{$regex: :t}}", sort = "{ name : 1 }", project = "{ name: 1}")
    List<Person> customFind(String t);

    @MongoFindQuery(filter = "{name:{$regex: :t}}", sort = "{ name : 1 }", project = "{ name: 1}")
    Page<Person> customFindPage(String t, Pageable pageable);

    @MongoAggregateQuery("[{$match: {name:{$regex: :t}}}, {$sort: {name: 1}}, {$project: {name: 1}}]")
    List<Person> customAgg(String t);

    @MongoAggregateQuery("[{$match: {name:{$regex: :t}}}, {$sort: {name: 1}}, {$project: {name: 1}}]")
    Page<Person> customAggrPage(String t, Pageable pageable);

    @MongoUpdateQuery(update = "{$set:{name: :newName}}", filter = "{name:{$eq: :oldName}}")
    long updateNamesCustom(String newName, String oldName);

    @MongoUpdateQuery(update = "{$set:{name: :name}}", filter = "{_id:{$eq: :id}}")
    long updateCustomOnlyNames(List<Person> people);

    @MongoUpdateQuery(update = "{$set:{name: 'tom'}}", filter = "{name:{$eq: :name}}")
    int updateCustomSingle(Person person);

    @MongoDeleteQuery("{name:{$eq: :name}}")
    int deleteCustom(List<Person> people);

    @MongoDeleteQuery("{name:{$eq: :name}}")
    int deleteCustomSingle(Person person);

    @MongoDeleteQuery("{name:{$eq: :xyz}}")
    int deleteCustomSingleNoEntity(String xyz);

    @MongoUpdateQuery(filter = "{addresses: null}", update = "{$set:{addresses: []}}")
    long updateMissingAddressesToAnEmptyArray();

    @MongoUpdateQuery(update = "{$set:{'addresses.$[address].zipCode': :zipCode}}", arrayFilters = "{'address.zipCode': null}}}")
    long updateMissingZipcodeInAddress(String zipCode);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Person updateCustomReturning(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Object updateCustomReturningAsObject(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.BEFORE)
    Person updateCustomReturningBefore(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    Person updateCustomReturningAfterWithOptions(String id, String newName, FindOneAndUpdateOptions options);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", returnDocument = ReturnDocument.AFTER)
    PersonNameDto updateCustomReturningDto(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}")
    Person updateCustomReturningDefault(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{_id:{$eq: :id}}", project = "{name: 1}", returnDocument = ReturnDocument.AFTER)
    String updateCustomReturningNameProjection(String id, String newName);

    @MongoUpdateReturningQuery(update = "{$set:{name: :newName}}", filter = "{name:{$eq: :name}}", sort = "{age: 1}", returnDocument = ReturnDocument.BEFORE)
    Person updateCustomReturningSortedBefore(String name, String newName);

    @MongoFindQuery(filter = "{'name': {'$in': :names}}")
    List<Person> findByNameInList(String[] names);

    List<Person> findByNameNotLike(String name);

    @Introspected
    record PersonNameDto(String name) {
    }
}
