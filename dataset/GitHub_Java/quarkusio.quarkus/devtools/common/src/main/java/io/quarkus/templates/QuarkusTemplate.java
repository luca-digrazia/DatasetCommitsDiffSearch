package io.quarkus.templates;

import java.io.IOException;
import java.util.Map;

import io.quarkus.cli.commands.writer.Writer;

public interface QuarkusTemplate {
    String PROJECT_GROUP_ID = "project_groupId";
    String PROJECT_ARTIFACT_ID = "project_artifactId";
    String PROJECT_VERSION = "project_version";
    String QUARKUS_VERSION = "quarkus_version";
    String PACKAGE_NAME = "package_name";
    String SOURCE_TYPE = "source_type";
    String CLASS_NAME = "class_name";
    String RESOURCE_PATH = "path";
    String ADDITIONAL_GITIGNORE_ENTRIES = "additional_gitignore_entries";

    String getName();

    void generate(final Writer writer, Map<String, Object> parameters) throws IOException;
}
