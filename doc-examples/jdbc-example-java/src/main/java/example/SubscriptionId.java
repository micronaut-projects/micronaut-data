package example;

import io.micronaut.data.annotation.Embeddable;

@Embeddable
public record SubscriptionId(String subscriberId, Long subscriptionNo) {}
