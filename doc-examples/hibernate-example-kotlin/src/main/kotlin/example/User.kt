package example

import io.micronaut.data.annotation.Where
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id

@Entity
@Where("@.enabled = true")
data class User(@Id
                @GeneratedValue
                var id: Long?,
                var name: String,
                var enabled: Boolean = true)
