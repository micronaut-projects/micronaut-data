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

import io.micronaut.context.annotation.ConfigurationProperties;

import static io.micronaut.data.aws.dynamodb.config.AwsDynamoDbConfiguration.PREFIX;

/**
 * The AWS DynamoDB configuration.
 *
 * @author radovanradic
 * @since 4.0.0
 */
@ConfigurationProperties(PREFIX)
public class AwsDynamoDbConfiguration {

    public static final String PREFIX = "aws.dynamodb";

    private String endpoint;

    private String region;

    private String awsAccessKeyId;

    private String awsSecretAccessKey;

    /**
     * @return the endpoint
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * Sets the endpoint.
     * @param endpoint the endpoint
     */
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    /**
     * @return the AWS region
     */
    public String getRegion() {
        return region;
    }

    /**
     * Sets the AWS region.
     * @param region the region
     */
    public void setRegion(String region) {
        this.region = region;
    }

    /**
     * @return the AWS access key id
     */
    public String getAwsAccessKeyId() {
        return awsAccessKeyId;
    }

    /**
     * Sets the AWS access key id.
     * @param awsAccessKeyId the AWS access key id
     */
    public void setAwsAccessKeyId(String awsAccessKeyId) {
        this.awsAccessKeyId = awsAccessKeyId;
    }

    /**
     * @return the AWS secret access key
     */
    public String getAwsSecretAccessKey() {
        return awsSecretAccessKey;
    }

    /**
     * Sets the AWS secret access key.
     * @param awsSecretAccessKey the AWS secret access key
     */
    public void setAwsSecretAccessKey(String awsSecretAccessKey) {
        this.awsSecretAccessKey = awsSecretAccessKey;
    }
}
