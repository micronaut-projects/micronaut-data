
package example

import jakarta.persistence.*

@Entity
class Manufacturer {

    @Id
    @GeneratedValue
    Long id
    String name
}
