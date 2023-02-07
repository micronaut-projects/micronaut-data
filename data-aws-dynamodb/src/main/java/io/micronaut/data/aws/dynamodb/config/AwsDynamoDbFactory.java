/*
 * Copyright 2017-2023 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.data.aws.dynamodb.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Internal;
import io.micronaut.data.aws.dynamodb.config.AwsDynamoDbConfiguration;
import jakarta.inject.Singleton;

/**
 * The AWS DynamoDB Client factory.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@Factory
@Internal
@Requires(property = "aws.dynamodb.endpoint")
public class AwsDynamoDbFactory {

    /**
     * Creates sync AWS DynamoDB client.
     *
     * @param configuration the AWS DynamoDB configuration
     * @return an instance of {@link AmazonDynamoDB}
     */
    @Bean(preDestroy = "shutdown")
    @Singleton
    @Requires(beans = AwsDynamoDbConfiguration.class)
    AmazonDynamoDB buildCosmosClient(AwsDynamoDbConfiguration configuration) {
        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();
        builder.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(configuration.getAwsAccessKeyId(), configuration.getAwsSecretAccessKey())));
        builder.withEndpointConfiguration(
            new AwsClientBuilder.EndpointConfiguration(configuration.getEndpoint(), configuration.getRegion()));
        return builder.build();
    }
}
