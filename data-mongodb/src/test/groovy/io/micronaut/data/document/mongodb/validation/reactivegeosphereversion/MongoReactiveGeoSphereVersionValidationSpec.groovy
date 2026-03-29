package io.micronaut.data.document.mongodb.validation.reactivegeosphereversion

import io.micronaut.data.document.mongodb.validation.geosphereversion.MongoGeoSphereVersionValidationSpec

class MongoReactiveGeoSphereVersionValidationSpec extends MongoGeoSphereVersionValidationSpec {

    @Override
    Map<String, String> getProperties() {
        super.getProperties() + [
                'micronaut.data.mongodb.driver-type': 'reactive'
        ]
    }

    @Override
    List<String> getPackageNames() {
        ['io.micronaut.data.document.mongodb.validation.geosphereversion']
    }
}
