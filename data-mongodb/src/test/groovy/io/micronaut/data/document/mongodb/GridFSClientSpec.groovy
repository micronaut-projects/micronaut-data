package io.micronaut.data.document.mongodb

import io.micronaut.context.ApplicationContext
import io.micronaut.data.mongodb.client.GridFSClient
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import org.bson.UuidRepresentation
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.nio.file.Files

@MicronautTest
class GridFSClientSpec extends Specification implements MongoTestPropertyProvider {

    @AutoCleanup
    @Shared
    ApplicationContext applicationContext = ApplicationContext.run(getTestProperties())


    void 'upload file to default bucket'() {
        given:
        GridFSClient gridFSClient = applicationContext.getBean(GridFSClient)
        def tempFile = File.createTempFile("temp", ".tmp")
        tempFile.write("Hello world!")
        tempFile.deleteOnExit()

        when:
        def objectId = gridFSClient.uploadFile(tempFile.toPath(), "test.txt", Map.of("hello", "world"))
        def downloadedFile = gridFSClient.downloadFile(objectId)
        def fileContent = ""
        if(downloadedFile.isPresent()){
            fileContent = Files.readString(downloadedFile.get(), StandardCharsets.UTF_8)
        }
        then:
        objectId != null
        downloadedFile.isPresent()
        fileContent == "Hello world!"

    }

    Map<String, Object> getTestProperties() {
        return [
                "micronaut.data.mongodb.driver-type"       : "sync",
                'micronaut.data.mongodb.create-collections': 'true',
                'micronaut.data.mongodb.gridfs.database'   : 'test',
                'mongodb.uuid-representation'              : UuidRepresentation.STANDARD.name(),
                'mongodb.package-names'                    : getPackageNames()
        ]
    }
}
