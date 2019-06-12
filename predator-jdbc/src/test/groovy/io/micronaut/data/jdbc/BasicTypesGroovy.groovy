package io.micronaut.data.jdbc

import io.micronaut.data.annotation.GeneratedValue
import io.micronaut.data.annotation.Id

import javax.persistence.Entity
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime

@Entity
class BasicTypesGroovy {

    @Id
    @GeneratedValue
    Long myId

    int primitiveInteger = 1
    long primitiveLong = 2
    boolean primitiveBoolean = true
    char primitiveChar = 'c'
    short primitiveShort = 3
    double primitiveDouble = 1.1
    float primitiveFloat = 1.2
    byte primitiveByte = 4
    String string = "test"
    CharSequence charSequence = "test2"
    Integer wrapperInteger = 1
    Long wrapperLong = 2
    Boolean wrapperBoolean = true
    Character wrapperChar = 'c'
    Short wrapperShort = 3
    Double wrapperDouble = 1.1
    Float wrapperFloat = 1.2
    Byte wrapperByte = 4
    URL url = new URL("https://test.com")
    URI uri = new URI("https://test.com")
    byte[] byteArray = [1,2,3] as byte[]
    Date date = new Date()
    LocalDateTime localDateTime = LocalDateTime.now()
    Instant instant = Instant.now()
    UUID uuid = UUID.randomUUID()
    BigDecimal bigDecimal = new BigDecimal("100")
    TimeZone timeZone = TimeZone.getTimeZone("GMT")
    Charset charset = StandardCharsets.UTF_8
}
