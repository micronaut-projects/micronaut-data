package io.micronaut.data.aws.dynamodb

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.core.annotation.AnnotationMetadataProvider
import io.micronaut.core.naming.NameUtils
import io.micronaut.data.annotation.Query
import io.micronaut.data.aws.dynamodb.entities.DeviceId
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.writer.BeanDefinitionVisitor

class BuildDynamoDbQuerySpec extends AbstractTypeElementSpec {

    void "test DynamoDB query"() {
        given:
        def repository = buildRepository('test.DynamoDbDeviceRepository', """
import io.micronaut.core.annotation.NonNull;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.aws.dynamodb.annotation.DynamoDbRepository;
import io.micronaut.data.aws.dynamodb.annotation.UseIndex;
import io.micronaut.data.aws.dynamodb.entities.Device;
import io.micronaut.data.aws.dynamodb.entities.DeviceId;
import java.util.Optional;

@DynamoDbRepository
interface DynamoDbDeviceRepository extends GenericRepository<Device, DeviceId> {

    Optional<Device> findById(@NonNull @Id DeviceId id);

    @UseIndex("CountryRegionIndex")
    List<Device> findByCountryAndRegion(String country, String region);

    List<Device> findByVendorId(Long vendorId);
}
"""
        )

        when:
        def findByIdQuery = getQuery(repository.getRequiredMethod("findById", DeviceId))
        def findByCountryAndRegionQuery = getQuery(repository.getRequiredMethod("findByCountryAndRegion", String, String))
        def findByVendorIdQuery = getQuery(repository.getRequiredMethod("findByVendorId", Long))
        then:
        findByIdQuery == 'SELECT "vendorId","product","description","country","region" FROM "Device" WHERE ("vendorId" = ? AND "product" = ?)'
        findByCountryAndRegionQuery == 'SELECT "vendorId","product","description","country","region" FROM "Device"."CountryRegionIndex" WHERE ("country" = ? AND "region" = ?)'
        findByVendorIdQuery == 'SELECT "vendorId","product","description","country","region" FROM "Device" WHERE ("vendorId" = ?)'
    }

    BeanDefinition<?> buildRepository(String name, String source) {
        def pkg = NameUtils.getPackageName(name)
        return buildBeanDefinition(name + BeanDefinitionVisitor.PROXY_SUFFIX, """
package $pkg;
import io.micronaut.data.model.*;
import io.micronaut.data.repository.*;
import io.micronaut.data.annotation.*;
import java.util.*;
$source
""")

    }

    static String getQuery(AnnotationMetadataProvider metadata) {
        return metadata.getAnnotation(Query).stringValue().get()
    }
}
