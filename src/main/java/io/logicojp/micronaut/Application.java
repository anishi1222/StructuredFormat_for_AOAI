package io.logicojp.micronaut;

import io.micronaut.runtime.Micronaut;

public class Application {
    static String JSONSchemaFile;

    public static void main(String... args) {
        if(args.length == 0) {
            JSONSchemaFile = "SampleSchema.json";
        }
        else if(args.length == 1) {
            JSONSchemaFile = args[0];
        }
        else {
            System.out.println("Usage: java -jar <JAR file> <JSON Schema file>");
            System.exit(1);
        }
        Micronaut.run(Application.class, args);
    }
}