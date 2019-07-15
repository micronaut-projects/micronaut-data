package example.domain

import javax.persistence.*

@Entity
data class Owner(@Id
                 @GeneratedValue
                 var id: Long?,
                 val name: String,
                 val age: Int)