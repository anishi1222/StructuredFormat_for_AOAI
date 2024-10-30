package io.logicojp.micronaut;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.*;
import com.azure.core.util.BinaryData;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.JsonSchema;
import com.fasterxml.jackson.module.jsonSchema.factories.SchemaFactoryWrapper;
import io.logicojp.micronaut.factory.AOAIClientFactory;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.scheduling.annotation.ExecuteOn;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;

@Controller("/aoai")
public class AOAIStructured {

    final String userMessage = """
            My name is John and I was born in 1970.
            """;
    final String systemMessage = """
            ユーザーの発言内容から名前と年齢を抽出して JSON 形式に整形してください。
            """;

    @Property(name ="azure.openai.deploymentName")
    private String deploymentName;

    @Inject
    private final AOAIClientFactory aoaiClientFactory;

    public AOAIStructured(AOAIClientFactory aoaiClientFactory) {
        this.aoaiClientFactory = aoaiClientFactory;
    }

@Get("/query1")
@Produces(MediaType.APPLICATION_JSON)
@ExecuteOn(TaskExecutors.IO)
public Person querySample(@QueryValue(value = "u", defaultValue = userMessage) String _userMessage) throws IOException {

    Logger logger = LoggerFactory.getLogger(AOAIStructured.class);
    // Initialize ObjectMapper and SchemaFactoryWrapper
    ObjectMapper objectMapper = new ObjectMapper();
    SchemaFactoryWrapper visitor = new SchemaFactoryWrapper();

    // Generate JSON schema
    try {
        objectMapper.acceptJsonFormatVisitor(objectMapper.constructType(Person.class), visitor);
    } catch (JsonMappingException e) {
        throw new RuntimeException(e);
    }
    JsonSchema jsonSchema = visitor.finalSchema();

    OpenAIClient client = aoaiClientFactory.getAOAIClient();
    ChatCompletions chatCompletions
        = client.getChatCompletions(deploymentName,
            new ChatCompletionsOptions(
                Arrays.asList(
                    new ChatRequestSystemMessage(systemMessage),
                    new ChatRequestUserMessage(_userMessage)
                )
            )
            .setResponseFormat(
                new ChatCompletionsJsonSchemaResponseFormat(
                    new ChatCompletionsJsonSchemaResponseFormatJsonSchema("Person")
                        .setStrict(false)
                        .setDescription("Fetch age and name from response.")
                            .setSchema(BinaryData.fromObject(jsonSchema))
                )
            )
        );

    return objectMapper.readValue(chatCompletions.getChoices().getFirst().getMessage().getContent(), Person.class);
}
}
