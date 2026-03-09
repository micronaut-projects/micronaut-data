package io.micronaut.data.jakarta.tck;

import org.junit.platform.suite.api.IncludeClassNamePatterns;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;

public class MongoDBJakartaDataTCKSuite {

    @Suite
    @SelectPackages("ee.jakarta.tck.data")
    @IncludeClassNamePatterns("ee.jakarta.tck.data.standalone.entity.*")
    public static class EntityTests {
    }

    @Suite
    @SelectPackages("ee.jakarta.tck.data")
    @IncludeClassNamePatterns("ee.jakarta.tck.data.standalone.persistence.*")
    public static class PersistenceTests {
    }

    @Suite
    @SelectPackages("ee.jakarta.tck.data")
    @IncludeClassNamePatterns("ee.jakarta.tck.data.web.validation.*")
    public static class ValidationTests {
    }

}
