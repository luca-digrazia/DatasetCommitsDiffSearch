package io.quarkus.cli.commands.file;

import io.quarkus.cli.commands.project.BuildTool;
import io.quarkus.cli.commands.writer.ProjectWriter;
import io.quarkus.dependencies.Extension;
import io.quarkus.maven.utilities.MojoUtils;
import io.quarkus.platform.descriptor.QuarkusPlatformDescriptor;
import io.quarkus.platform.tools.ToolsUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Scanner;
import java.util.function.Consumer;
import org.apache.maven.model.Dependency;

public class GradleBuildFile extends BuildFile {

    private static final String BUILD_GRADLE_PATH = "build.gradle";
    private static final String SETTINGS_GRADLE_PATH = "settings.gradle";
    private static final String GRADLE_PROPERTIES_PATH = "gradle.properties";

    private final ProjectWriter rootWriter;

    private Model model;

    public GradleBuildFile(ProjectWriter writer) {
        super(writer, BuildTool.GRADLE);
        rootWriter = writer;
    }

    public GradleBuildFile(ProjectWriter writer, ProjectWriter rootWriter) {
        super(writer, BuildTool.GRADLE);
        this.rootWriter = rootWriter;
    }

    protected void rootWrite(String fileName, String content) throws IOException {
        rootWriter.write(fileName, content);
    }

    protected ProjectWriter getRootWriter() {
        return rootWriter;
    }

    @Override
    public void close() throws IOException {
        if (getWriter() == getRootWriter()) {
            write(SETTINGS_GRADLE_PATH, getModel().getSettingsContent());
        } else {
            rootWrite(SETTINGS_GRADLE_PATH, getModel().getSettingsContent());
        }
        write(BUILD_GRADLE_PATH, getModel().getBuildContent());
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            getModel().getPropertiesContent().store(out, "Gradle properties");
            if (getModel().isRootProperties()) {
                rootWrite(GRADLE_PROPERTIES_PATH, out.toString(StandardCharsets.UTF_8.toString()));
            } else {
                write(GRADLE_PROPERTIES_PATH, out.toString(StandardCharsets.UTF_8.toString()));
            }
        }
    }

    @Override
    public void completeFile(String groupId, String artifactId, String version, QuarkusPlatformDescriptor platform,
            Properties props) throws IOException {
        completeSettingsContent(artifactId);
        completeBuildContent(groupId, version, platform, props);
        completeProperties(platform);
    }

    private void completeBuildContent(String groupId, String version, QuarkusPlatformDescriptor platform, Properties props)
            throws IOException {
        final String buildContent = getModel().getBuildContent();
        StringBuilder res = new StringBuilder(buildContent);
        if (!buildContent.contains("id 'io.quarkus'")) {
            res.append("plugins {");
            res.append(System.lineSeparator()).append("    id 'java'").append(System.lineSeparator());
            res.append(System.lineSeparator()).append("    id 'io.quarkus'").append(System.lineSeparator());
            res.append("}");
        }
        if (!containsBOM(platform.getBomGroupId(), platform.getBomArtifactId())) {
            res.append(System.lineSeparator());
            res.append("dependencies {").append(System.lineSeparator());
            res.append(
                    "    implementation enforcedPlatform(\"${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:${quarkusPlatformVersion}\")")
                    .append(System.lineSeparator());
            res.append("    implementation 'io.quarkus:quarkus-resteasy'").append(System.lineSeparator());
            res.append("    testImplementation 'io.quarkus:quarkus-junit5'").append(System.lineSeparator());
            res.append("    testImplementation 'io.rest-assured:rest-assured'").append(System.lineSeparator());
            res.append("}").append(System.lineSeparator());

        }
        String groupLine = "group '" + groupId + "'";
        if (!buildContent.contains(groupLine)) {
            res.append(System.lineSeparator()).append(groupLine)
                    .append(System.lineSeparator());
        }
        String versionLine = "version '" + version + "'";
        if (!buildContent.contains(versionLine)) {
            res.append(System.lineSeparator()).append(versionLine)
                    .append(System.lineSeparator());
        }

        res.append(System.lineSeparator())
                .append("test {").append(System.lineSeparator())
                .append("    systemProperty \"java.util.logging.manager\", \"org.jboss.logmanager.LogManager\"")
                .append(System.lineSeparator())
                .append("}");

        getModel().setBuildContent(res.toString());
    }

    private void completeSettingsContent(String artifactId) throws IOException {
        final String settingsContent = getModel().getSettingsContent();
        final StringBuilder res = new StringBuilder();
        if (!settingsContent.contains("id 'io.quarkus'")) {
            res.append(System.lineSeparator());
            res.append("pluginManagement {").append(System.lineSeparator());
            res.append("    repositories {").append(System.lineSeparator());
            res.append("        mavenLocal()").append(System.lineSeparator());
            res.append("        mavenCentral()").append(System.lineSeparator());
            res.append("        gradlePluginPortal()").append(System.lineSeparator());
            res.append("    }").append(System.lineSeparator());
            res.append("    plugins {").append(System.lineSeparator());
            res.append("        id 'io.quarkus' version \"${quarkusPluginVersion}\"").append(System.lineSeparator());
            res.append("    }").append(System.lineSeparator());
            res.append("}").append(System.lineSeparator());
        }
        if (!settingsContent.contains("rootProject.name")) {
            res.append(System.lineSeparator()).append("rootProject.name='").append(artifactId).append("'")
                    .append(System.lineSeparator());
        }
        res.append(settingsContent);
        getModel().setSettingsContent(res.toString());
    }

    private void completeProperties(QuarkusPlatformDescriptor platform) throws IOException {
        Properties props = getModel().getPropertiesContent();
        if (props.getProperty("quarkusPluginVersion") == null) {
            props.setProperty("quarkusPluginVersion", ToolsUtils.getPluginVersion(ToolsUtils.readQuarkusProperties(platform)));
        }
        if (props.getProperty("quarkusPlatformGroupId") == null) {
            props.setProperty("quarkusPlatformGroupId", platform.getBomGroupId());
        }
        if (props.getProperty("quarkusPlatformArtifactId") == null) {
            props.setProperty("quarkusPlatformArtifactId", platform.getBomArtifactId());
        }
        if (props.getProperty("quarkusPlatformVersion") == null) {
            props.setProperty("quarkusPlatformVersion", platform.getBomVersion());
        }
    }

    @Override
    protected void addDependencyInBuildFile(Dependency dependency) throws IOException {
        StringBuilder newBuildContent = new StringBuilder();
        readLineByLine(getModel().getBuildContent(), new AppendDependency(newBuildContent, dependency));
        getModel().setBuildContent(newBuildContent.toString());
    }

    @Override
    public boolean removeDependency(QuarkusPlatformDescriptor platform, Extension extension) throws IOException {
        PRINTER.ok(" Removing extension " + extension.managementKey());
        Dependency dep;
        if (containsBOM(platform.getBomGroupId(), platform.getBomArtifactId())
                && isDefinedInBom(platform.getManagedDependencies(), extension)) {
            dep = extension.toDependency(true);
        } else {
            dep = extension.toDependency(false);
            if (getProperty(MojoUtils.TEMPLATE_PROPERTY_QUARKUS_VERSION_NAME) != null) {
                dep.setVersion(MojoUtils.TEMPLATE_PROPERTY_QUARKUS_VERSION_VALUE);
            }
        }
        removeDependencyFromBuildFile(dep);
        return true;
    }

    @Override
    protected void removeDependencyFromBuildFile(Dependency dependency) throws IOException {
        String depString = new StringBuilder("'").append(dependency.getGroupId()).append(":")
                .append(dependency.getArtifactId()).toString();
        StringBuilder newBuildContent = new StringBuilder();
        Scanner scanner = new Scanner(getModel().getBuildContent());
        while (scanner.hasNextLine()) {
            String line = scanner.nextLine();
            if (!line.contains(depString)) {
                newBuildContent.append(line).append(System.lineSeparator());
            }
        }
        scanner.close();
        getModel().setBuildContent(newBuildContent.toString());
    }

    private void readLineByLine(String content, Consumer<String> lineConsumer) {
        try (Scanner scanner = new Scanner(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8.name())) {
            while (scanner.hasNextLine()) {
                String currentLine = scanner.nextLine();
                lineConsumer.accept(currentLine);
            }
        }
    }

    private static class AppendDependency implements Consumer<String> {

        private StringBuilder newContent;
        private Dependency dependency;

        public AppendDependency(StringBuilder newContent, Dependency dependency) {
            this.newContent = newContent;
            this.dependency = dependency;
        }

        @Override
        public void accept(String currentLine) {
            newContent.append(currentLine).append(System.lineSeparator());
            if (currentLine.startsWith("dependencies {")) {
                newContent.append("    implementation '")
                        .append(dependency.getGroupId())
                        .append(":")
                        .append(dependency.getArtifactId());
                if (dependency.getVersion() != null && !dependency.getVersion().isEmpty()) {
                    newContent.append(":")
                            .append(dependency.getVersion());
                }
                newContent.append("'")
                        .append(System.lineSeparator());
            }
        }

    }

    @Override
    protected boolean hasDependency(Extension extension) throws IOException {
        return getDependencies().stream()
                .anyMatch(d -> extension.getGroupId().equals(d.getGroupId())
                        && extension.getArtifactId().equals(d.getArtifactId()));
    }

    @Override
    protected boolean containsBOM(String groupId, String artifactId) throws IOException {
        String buildContent = getModel().getBuildContent();
        return buildContent.contains("enforcedPlatform(\"${quarkusPlatformGroupId}:${quarkusPlatformArtifactId}:")
                || buildContent.contains("enforcedPlatform(\"" + groupId + ":" + artifactId + ":");
    }

    @Override
    public List<Dependency> getDependencies() throws IOException {
        return Collections.emptyList();
    }

    @Override
    public String getProperty(String propertyName) throws IOException {
        return getModel().getPropertiesContent().getProperty(propertyName);
    }

    @Override
    protected List<Dependency> getManagedDependencies() {
        // Gradle tooling API only provide resolved dependencies.
        return Collections.emptyList();
    }

    private Model getModel() throws IOException {
        if (model == null) {
            initModel();
        }
        return model;
    }

    protected void initModel() throws IOException {
        String settingsContent = "";
        String buildContent = "";
        Properties propertiesContent = new Properties();
        boolean isRootProperties = false;
        if (getWriter() == getRootWriter()) {
            if (getWriter().exists(SETTINGS_GRADLE_PATH)) {
                final byte[] settings = getWriter().getContent(SETTINGS_GRADLE_PATH);
                settingsContent = new String(settings, StandardCharsets.UTF_8);
            }
        } else {
            if (getRootWriter().exists(SETTINGS_GRADLE_PATH)) {
                final byte[] settings = getRootWriter().getContent(SETTINGS_GRADLE_PATH);
                settingsContent = new String(settings, StandardCharsets.UTF_8);
            }
        }
        if (getWriter().exists(BUILD_GRADLE_PATH)) {
            final byte[] build = getWriter().getContent(BUILD_GRADLE_PATH);
            buildContent = new String(build, StandardCharsets.UTF_8);
        }
        if (getWriter().exists(GRADLE_PROPERTIES_PATH)) {
            final byte[] properties = getWriter().getContent(GRADLE_PROPERTIES_PATH);
            propertiesContent.load(new ByteArrayInputStream(properties));
        }
        if (!propertiesContent.containsKey("quarkusPluginVersion") &&
                !propertiesContent.containsKey("quarkusPlatformGroupId") &&
                !propertiesContent.containsKey("quarkusPlatformArtifactId") &&
                !propertiesContent.containsKey("quarkusPlatformVersion")) {
            if (getRootWriter().exists(GRADLE_PROPERTIES_PATH)) {
                final byte[] properties = getRootWriter().getContent(GRADLE_PROPERTIES_PATH);
                propertiesContent.load(new ByteArrayInputStream(properties));
            }
            isRootProperties = true;
        }
        this.model = new Model(settingsContent, buildContent, propertiesContent, isRootProperties);
    }

    protected String getBuildContent() throws IOException {
        return getModel().getBuildContent();
    }

    private static class Model {
        private String settingsContent;
        private String buildContent;
        private Properties propertiesContent;
        private boolean rootProperties;

        public Model(String settingsContent, String buildContent, Properties propertiesContent, boolean rootProperties) {
            this.settingsContent = settingsContent;
            this.buildContent = buildContent;
            this.propertiesContent = propertiesContent;
            this.rootProperties = rootProperties;
        }

        public String getSettingsContent() {
            return settingsContent;
        }

        public String getBuildContent() {
            return buildContent;
        }

        public Properties getPropertiesContent() {
            return propertiesContent;
        }

        public void setSettingsContent(String settingsContent) {
            this.settingsContent = settingsContent;
        }

        public void setBuildContent(String buildContent) {
            this.buildContent = buildContent;
        }

        public boolean isRootProperties() {
            return rootProperties;
        }
    }
}
