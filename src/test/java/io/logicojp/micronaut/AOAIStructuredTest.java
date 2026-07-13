package io.logicojp.micronaut;

import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormat;
import com.azure.ai.openai.models.ChatCompletionsJsonSchemaResponseFormatJsonSchema;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.openai.models.ChatResponseMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.logicojp.micronaut.factory.AOAIClientFactory;

@ExtendWith(MockitoExtension.class)
@ResourceLock("Application.JSONSchemaFile")
class AOAIStructuredTest {

    private static final String DEPLOYMENT_NAME = "test-deployment";
    private static final String SCHEMA_DESCRIPTION = "Fetch age and name from response.";

    @Mock
    private AOAIClientFactory factory;

    @Mock
    private OpenAIClient client;

    @Mock
    private ChatCompletions chatCompletions;

    @Mock
    private ChatChoice chatChoice;

    @Mock
    private ChatResponseMessage chatResponseMessage;

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String originalJsonSchemaFile;
    private AOAIStructured controller;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        originalJsonSchemaFile = Application.JSONSchemaFile;
        controller = new AOAIStructured(factory);
        setDeploymentName(controller, DEPLOYMENT_NAME);
    }

    @AfterEach
    void restoreStaticState() {
        Application.JSONSchemaFile = originalJsonSchemaFile;
    }

    @Test
    void querySample1_validCompletion_returnsPersonAndBuildsNonStrictSchema() throws Exception {
        when(factory.getAOAIClient()).thenReturn(client);
        stubCompletion("{\"name\":\"John\",\"age\":56}");

        Person result = controller.querySample1();

        assertEquals(new Person("John", 56), result);
        ArgumentCaptor<ChatCompletionsOptions> optionsCaptor =
                ArgumentCaptor.forClass(ChatCompletionsOptions.class);
        verify(factory, times(1)).getAOAIClient();
        verify(client, times(1)).getChatCompletions(eq(DEPLOYMENT_NAME), optionsCaptor.capture());

        ChatCompletionsOptions options = optionsCaptor.getValue();
        assertExpectedMessages(options);
        ChatCompletionsJsonSchemaResponseFormatJsonSchema responseSchema =
                assertResponseSchemaMetadata(options, false);
        JsonNode schema = objectMapper.readTree(responseSchema.getSchema().toBytes());
        assertEquals("object", schema.path("type").asText());
        assertEquals("urn:jsonschema:io:logicojp:micronaut:Person", schema.path("id").asText());
        assertEquals("string", schema.path("properties").path("name").path("type").asText());
        assertEquals("integer", schema.path("properties").path("age").path("type").asText());
    }

    @Test
    void querySample2_validCompletion_returnsPersonAndUsesStrictFileSchema() throws Exception {
        String schemaContent = """
                {
                  "$schema": "http://json-schema.org/draft-07/schema#",
                  "title": "SampleSchema",
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "age": { "type": "integer" }
                  },
                  "required": ["name", "age"],
                  "additionalProperties": false
                }
                """;
        Path schemaPath = tempDir.resolve("person-schema.json");
        Files.writeString(schemaPath, schemaContent, StandardCharsets.UTF_8);
        Application.JSONSchemaFile = schemaPath.toAbsolutePath().toString();
        when(factory.getAOAIClient()).thenReturn(client);
        stubCompletion("{\"name\":\"Ada\",\"age\":36}");

        Person result = controller.querySample2();

        assertEquals(new Person("Ada", 36), result);
        ArgumentCaptor<ChatCompletionsOptions> optionsCaptor =
                ArgumentCaptor.forClass(ChatCompletionsOptions.class);
        verify(factory, times(1)).getAOAIClient();
        verify(client, times(1)).getChatCompletions(eq(DEPLOYMENT_NAME), optionsCaptor.capture());

        ChatCompletionsOptions options = optionsCaptor.getValue();
        assertExpectedMessages(options);
        ChatCompletionsJsonSchemaResponseFormatJsonSchema responseSchema =
                assertResponseSchemaMetadata(options, true);
        JsonNode expectedSchema = objectMapper.readTree(Files.readAllBytes(schemaPath));
        JsonNode capturedSchema = objectMapper.readTree(responseSchema.getSchema().toBytes());
        assertEquals(expectedSchema, capturedSchema);
    }

    @Test
    void querySample1_malformedCompletion_throwsJsonProcessingException() {
        when(factory.getAOAIClient()).thenReturn(client);
        stubCompletion("{");

        assertThrows(JsonProcessingException.class, controller::querySample1);

        verify(factory, times(1)).getAOAIClient();
        verify(client, times(1)).getChatCompletions(
                eq(DEPLOYMENT_NAME), any(ChatCompletionsOptions.class));
    }

    @Test
    void querySample2_missingSchema_throwsUncheckedIOExceptionBeforeClientCall() {
        Application.JSONSchemaFile = tempDir.resolve("missing-schema.json").toAbsolutePath().toString();
        when(factory.getAOAIClient()).thenReturn(client);

        assertThrows(UncheckedIOException.class, controller::querySample2);

        verify(factory, times(1)).getAOAIClient();
        verifyNoInteractions(client);
    }

    private void stubCompletion(String content) {
        when(client.getChatCompletions(eq(DEPLOYMENT_NAME), any(ChatCompletionsOptions.class)))
                .thenReturn(chatCompletions);
        when(chatCompletions.getChoices()).thenReturn(List.of(chatChoice));
        when(chatChoice.getMessage()).thenReturn(chatResponseMessage);
        when(chatResponseMessage.getContent()).thenReturn(content);
    }

    private void assertExpectedMessages(ChatCompletionsOptions options) {
        assertEquals(2, options.getMessages().size());
        ChatRequestSystemMessage systemMessage =
                assertInstanceOf(ChatRequestSystemMessage.class, options.getMessages().get(0));
        ChatRequestUserMessage userMessage =
                assertInstanceOf(ChatRequestUserMessage.class, options.getMessages().get(1));
        assertEquals(controller.systemMessage, systemMessage.getStringContent());
        assertEquals(controller.userMessage, userMessage.getStringContent());
    }

    private static ChatCompletionsJsonSchemaResponseFormatJsonSchema assertResponseSchemaMetadata(
            ChatCompletionsOptions options, boolean strict) {
        ChatCompletionsJsonSchemaResponseFormat responseFormat = assertInstanceOf(
                ChatCompletionsJsonSchemaResponseFormat.class, options.getResponseFormat());
        ChatCompletionsJsonSchemaResponseFormatJsonSchema responseSchema = responseFormat.getJsonSchema();
        assertEquals("Person", responseSchema.getName());
        assertEquals(SCHEMA_DESCRIPTION, responseSchema.getDescription());
        assertEquals(strict, responseSchema.isStrict());
        return responseSchema;
    }

    private static void setDeploymentName(AOAIStructured controller, String deploymentName)
            throws ReflectiveOperationException {
        Field field = AOAIStructured.class.getDeclaredField("deploymentName");
        field.setAccessible(true);
        field.set(controller, deploymentName);
    }
}