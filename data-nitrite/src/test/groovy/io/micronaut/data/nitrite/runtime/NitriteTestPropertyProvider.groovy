package io.micronaut.data.nitrite.runtime

import io.micronaut.test.support.TestPropertyProvider

/**
 * Test property provider for Nitrite tests ported from MongoDB specs.
 * Uses in-memory storage without Docker.
 */
trait NitriteTestPropertyProvider implements TestPropertyProvider {

    @Override
    Map<String, String> getProperties() {
        return [
                'micronaut.data.nitrite.storage': 'memory',
                'micronaut.data.nitrite.create-indexes': 'true'
        ]
    }

}
