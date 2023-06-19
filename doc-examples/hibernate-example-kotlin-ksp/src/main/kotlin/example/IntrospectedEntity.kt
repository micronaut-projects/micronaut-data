
package example

import io.micronaut.core.annotation.Introspected
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Introspected
data class IntrospectedEntity(@Id
                @GeneratedValue
                var id: Long,
                              var title: String,
                              var pages: Int = 0)
