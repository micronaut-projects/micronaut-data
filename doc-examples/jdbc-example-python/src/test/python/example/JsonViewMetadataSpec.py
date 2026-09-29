from com.fasterxml.jackson.annotation import JsonProperty
from micronaut.core.beans import BeanIntrospection
from micronaut.test.extensions.junit5.annotation import MicronautTest
from org.junit.jupiter.api import Test

from example.ContactMetadataView import ContactMetadataView
from example.Metadata import Metadata


@MicronautTest(startApplication=False)
class JsonViewMetadataSpec:

    @Test
    def metadataPropertyIsMappedToUnderscoreMetadata(self):
        introspection = BeanIntrospection.getIntrospection(ContactMetadataView)
        metadata = introspection.getRequiredProperty("metadata", Metadata)
        assert metadata.stringValue(JsonProperty).orElseThrow() == "_metadata"

    @Test
    def metadataIsIntrospected(self):
        introspection = BeanIntrospection.getIntrospection(Metadata)
        assert sorted(introspection.getPropertyNames()) == ["asof", "etag"]
