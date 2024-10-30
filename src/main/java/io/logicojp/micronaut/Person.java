package io.logicojp.micronaut;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record Person (
        String name,
        int age
) {}