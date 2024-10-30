package io.logicojp.micronaut.factory;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.OpenAIServiceVersion;
import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.http.policy.ExponentialBackoffOptions;
import com.azure.core.http.policy.RetryOptions;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import jakarta.inject.Singleton;

import java.time.Duration;

@Factory
public class AOAIClientFactory {
    @Property(name ="azure.openai.endpoint")
    private String endpoint;
    @Property(name ="azure.openai.apiKey")
    private String apiKey;

    @Singleton
    public OpenAIClient getAOAIClient() {
        ExponentialBackoffOptions exponentialBackoffOptions =
                new ExponentialBackoffOptions()
                        .setBaseDelay(Duration.ofSeconds(1))
                        .setMaxDelay(Duration.ofSeconds(10))
                        .setMaxRetries(3);
        RetryOptions retryOptions = new RetryOptions(exponentialBackoffOptions);
        return new OpenAIClientBuilder()
                .endpoint(endpoint)
                .credential(new AzureKeyCredential(apiKey))
                .serviceVersion(OpenAIServiceVersion.V2024_08_01_PREVIEW)
                .retryOptions(retryOptions)
                .buildClient();
    }
}
