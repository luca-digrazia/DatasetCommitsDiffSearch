package io.quarkus.dynamodb.runtime;

import io.quarkus.runtime.annotations.ConfigItem;
import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;

@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public class DynamodbConfig {

    /**
     * Enable DynamoDB service endpoint discovery.
     */
    @ConfigItem
    public boolean enableEndpointDiscovery;

    /**
     * AWS service configurations
     */
    @ConfigItem
    public AwsConfig aws;

    /**
     * SDK client configurations
     */
    @ConfigItem(name = ConfigItem.PARENT)
    public SdkConfig sdk;

    /**
     * Apache HTTP client transport configuration
     */
    @ConfigItem
    public ApacheHttpClientConfig syncClient;

    /**
     * Netty HTTP client transport configuration
     */
    @ConfigItem
    public NettyHttpClientConfig asyncClient;
}
