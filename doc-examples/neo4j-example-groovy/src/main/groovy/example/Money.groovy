package example

import groovy.transform.Immutable

@Immutable
class Money {
    BigDecimal amount
    String currency
}
