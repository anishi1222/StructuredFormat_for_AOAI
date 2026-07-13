package io.logicojp.micronaut;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.mockito.MockedStatic;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import io.micronaut.runtime.Micronaut;

@ResourceLock("Application.JSONSchemaFile")
class ApplicationTest {

    private String originalJsonSchemaFile;

    @BeforeEach
    void saveStaticState() {
        originalJsonSchemaFile = Application.JSONSchemaFile;
    }

    @AfterEach
    void restoreStaticState() {
        Application.JSONSchemaFile = originalJsonSchemaFile;
    }

    @Test
    void main_noArguments_setsDefaultSchemaAndStartsMicronaut() {
        String[] args = {};
        Application.JSONSchemaFile = "sentinel-schema.json";

        try (MockedStatic<Micronaut> micronaut = mockStatic(Micronaut.class)) {
            Application.main(args);

            assertEquals("SampleSchema.json", Application.JSONSchemaFile);
            micronaut.verify(() -> Micronaut.run(Application.class, args), times(1));
            micronaut.verifyNoMoreInteractions();
        }
    }

    @Test
    void main_oneArgument_setsProvidedSchemaAndStartsMicronaut() {
        String[] args = {"custom-schema.json"};

        try (MockedStatic<Micronaut> micronaut = mockStatic(Micronaut.class)) {
            Application.main(args);

            assertEquals("custom-schema.json", Application.JSONSchemaFile);
            micronaut.verify(() -> Micronaut.run(Application.class, args), times(1));
            micronaut.verifyNoMoreInteractions();
        }
    }
}