package io.quarkus.annotation.processor.generate_doc;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.quarkus.annotation.processor.Constants;

final public class ConfigDocWriter {
    private final DocFormatter summaryTableDocFormatter = new SummaryTableDocFormatter();

    /**
     * Write extension configuration AsciiDoc format in `{root}/target/asciidoc/generated/config/`
     */
    public void writeExtensionConfigDocumentation(Map<String, List<ConfigDocItem>> extensionsConfigurations)
            throws IOException {
        for (Map.Entry<String, List<ConfigDocItem>> entry : extensionsConfigurations.entrySet()) {
            final List<ConfigDocItem> configDocItems = entry.getValue();
            final String extensionFileName = entry.getKey();

            sort(configDocItems);

            generateDocumentation(Constants.GENERATED_DOCS_PATH.resolve(extensionFileName), configDocItems);
        }
    }

    /**
     * Write all extension configuration AsciiDoc format in `{root}/target/asciidoc/generated/config/`
     */
    public void writeAllExtensionConfigDocumentation(Map<String, List<ConfigDocItem>> extensionsConfigurations)
            throws IOException {
        List<ConfigDocItem> allItems = new ArrayList<>();
        SortedMap<String, List<ConfigDocItem>> sortedExtensions = new TreeMap<>();
        sortedExtensions.putAll(extensionsConfigurations);
        for (Map.Entry<String, List<ConfigDocItem>> entry : sortedExtensions.entrySet()) {
            final List<ConfigDocItem> configDocItems = entry.getValue();

            sort(configDocItems);
            ConfigDocSection header = new ConfigDocSection();
            String title = entry.getKey();
            // sanitise
            if (title.startsWith("quarkus-"))
                title = title.substring(8);
            if (title.endsWith(".adoc"))
                title = title.substring(0, title.length() - 5);
            if (title.endsWith("-config"))
                title = title.substring(0, title.length() - 7);
            if (title.endsWith("-configuration"))
                title = title.substring(0, title.length() - 14);
            title = title.replace('-', ' ');
            title = capitalize(title);
            header.setSectionDetailsTitle(title);
            allItems.add(new ConfigDocItem(header, null));
            allItems.addAll(configDocItems);
        }
        generateDocumentation(Constants.GENERATED_DOCS_PATH.resolve("all-config.adoc"), allItems);
    }

    private String capitalize(String title) {
        char[] chars = title.toCharArray();
        boolean capitalize = true;
        for (int i = 0; i < chars.length; i++) {
            char c = chars[i];
            if (Character.isSpaceChar(c)) {
                capitalize = true;
                continue;
            }
            if (capitalize) {
                if (Character.isLetter(c))
                    chars[i] = Character.toUpperCase(c);
                capitalize = false;
            }
        }
        return new String(chars);
    }

    /**
     * Sort docs keys. The sorted list will contain the properties in the following order
     * - 1. Map config items as last elements of the generated docs.
     * - 2. Build time properties will come first.
     * - 3. Otherwise respect source code declaration order.
     * - 4. Elements within a configuration section will appear at the end of the generated doc while preserving described in
     * 1-4.
     */
    private static void sort(List<ConfigDocItem> configDocItems) {
        Collections.sort(configDocItems);
        for (ConfigDocItem configDocItem : configDocItems) {
            if (configDocItem.isConfigSection()) {
                sort(configDocItem.getConfigDocSection().getConfigDocItems());
            }
        }
    }

    /**
     * Generate documentation in a summary table and descriptive format
     *
     * @param targetPath
     * @param configDocItems
     * @throws IOException
     */
    private void generateDocumentation(Path targetPath, List<ConfigDocItem> configDocItems) throws IOException {
        try (Writer writer = Files.newBufferedWriter(targetPath)) {
            summaryTableDocFormatter.format(writer, configDocItems);

            boolean hasDuration = false, hasMemory = false;
            for (ConfigDocItem item : configDocItems) {
                if (item.hasDurationInformationNote())
                    hasDuration = true;
                if (item.hasMemoryInformationNote())
                    hasMemory = true;

            }
            if (hasDuration) {
                writer.append(Constants.DURATION_FORMAT_NOTE);
            }

            if (hasMemory) {
                writer.append(Constants.MEMORY_SIZE_FORMAT_NOTE);
            }
        }
    }
}
