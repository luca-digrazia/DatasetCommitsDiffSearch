/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.build.gradle.tasks;

import static com.android.SdkConstants.ATTR_NAME;
import static com.android.SdkConstants.ATTR_TYPE;
import static com.android.SdkConstants.DOT_CLASS;
import static com.android.SdkConstants.DOT_JAR;
import static com.android.SdkConstants.DOT_XML;
import static com.android.SdkConstants.FD_RES_VALUES;
import static com.android.SdkConstants.TAG_ITEM;
import static com.android.SdkConstants.TAG_RESOURCES;
import static com.android.utils.SdkUtils.endsWithIgnoreCase;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.objectweb.asm.ClassReader.SKIP_DEBUG;
import static org.objectweb.asm.ClassReader.SKIP_FRAMES;

import com.android.SdkConstants;
import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.annotations.VisibleForTesting;
import com.android.ide.common.resources.ResourceUrl;
import com.android.ide.common.xml.XmlPrettyPrinter;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.checks.ResourceUsageModel;
import com.android.tools.lint.checks.ResourceUsageModel.Resource;
import com.android.tools.lint.checks.StringFormatDetector;
import com.android.utils.AsmUtils;
import com.android.utils.Pair;
import com.android.utils.XmlUtils;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathException;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Class responsible for searching through a Gradle built tree (after resource merging, compilation
 * and ProGuarding has been completed, but before final .apk assembly), which figures out which
 * resources if any are unused, and removes them.
 * <p>It does this by examining
 * <ul>
 *   <li>The merged manifest, to find root resource references (such as drawables used for activity
 *       icons)</li>
 *   <li>The R.txt file (to find the actual integer constants assigned to resources)</li>
 *   <li>The ProGuard log files (to find the mapping from original symbol names to short names)</li>
 *   <li>The merged resources (to find which resources reference other resources, e.g. drawable
 *       state lists including other drawables, or layouts including other layouts, or styles
 *       referencing other drawables, or menus items including action layouts, etc.)</li>
 *   <li>The ProGuard output classes (to find resource references in code that are actually
 *       reachable)</li>
 * </ul>
 * From all this, it builds up a reference graph, and based on the root references (e.g. from the
 * manifest and from the remaining code) it computes which resources are actually reachable in the
 * app, and anything that is not reachable is then marked for deletion.
 * <p>A resource is referenced in code if either the field R.type.name is referenced (which is the
 * case for non-final resource references, e.g. in libraries), or if the corresponding int value is
 * referenced (for final resource values). We check this by looking at the ProGuard output classes
 * with an ASM visitor. One complication is that code can also call
 * {@code Resources#getIdentifier(String,String,String)} where they can pass in the names of
 * resources to look up. To handle this scenario, we use the ClassVisitor to see if there are any
 * calls to the specific {@code Resources#getIdentifier} method. If not, great, the usage analysis
 * is completely accurate. If we <b>do</b> find one, we check <b>all</b> the string constants found
 * anywhere in the app, and look to see if any look relevant. For example, if we find the string
 * "string/foo" or "my.pkg:string/foo", we will then mark the string resource named foo (if any) as
 * potentially used. Similarly, if we find just "foo" or "/foo", we will mark <b>all</b> resources
 * named "foo" as potentially used. However, if the string is "bar/foo" or " foo " these strings are
 * ignored. This means we can potentially miss resources usages where the resource name is completed
 * computed (e.g. by concatenating individual characters or taking substrings of strings that do not
 * look like resource names), but that seems extremely unlikely to be a real-world scenario. <p> For
 * now, for reasons detailed in the code, this only applies to file-based resources like layouts,
 * menus and drawables, not value-based resources like strings and dimensions.
 */
public class ResourceUsageAnalyzer {
  private static final String ANDROID_RES = "android_res/";

  /** Special marker regexp which does not match a resource name */
  static final String NO_MATCH = "-nomatch-";

  private final Set<String> resourcePackages;
  private final Path rTxt;
  private final Path proguardMapping;
  private final Path classes;
  private final Path mergedManifest;
  private final Path mergedResourceDir;
  private final Logger logger;

  /**
   * The computed set of unused resources
   */
  private List<Resource> unused;
  /**
   * Map from resource class owners (VM format class) to corresponding resource entries. This lets
   * us map back from code references (obfuscated class and possibly obfuscated field reference)
   * back to the corresponding resource type and name.
   */
  private Map<String, Pair<ResourceType, Map<String, String>>> resourceObfuscation =
      Maps.newHashMapWithExpectedSize(30);
  /** Obfuscated name of android/support/v7/widget/SuggestionsAdapter.java */
  private String suggestionsAdapter;
  /** Obfuscated name of android/support/v7/internal/widget/ResourcesWrapper.java */
  private String resourcesWrapper;

  public ResourceUsageAnalyzer(
      Set<String> resourcePackages,
      @NonNull Path rTxt,
      @NonNull Path classes,
      @NonNull Path manifest,
      @Nullable Path mapping,
      @NonNull Path resources,
      @Nullable Path logFile) {
    this.resourcePackages = resourcePackages;
    this.rTxt = rTxt;
    this.proguardMapping = mapping;
    this.classes = classes;
    this.mergedManifest = manifest;
    this.mergedResourceDir = resources;

    this.logger = Logger.getLogger(getClass().getName());
    logger.setLevel(Level.FINE);
    if (logFile != null) {
      try {
        FileHandler fileHandler = new FileHandler(logFile.toString());
        fileHandler.setLevel(Level.FINE);
        fileHandler.setFormatter(new Formatter(){
          @Override public String format(LogRecord record) {
            return record.getMessage() + "\n";
          }
        });
        logger.addHandler(fileHandler);
      } catch (SecurityException | IOException e) {
        logger.warning(String.format("Unable to open '%s' to write log.", logFile));
      }
    }
  }

  public void shrink(Path destinationDir) throws IOException,
      ParserConfigurationException, SAXException {
    parseResourceTxtFile(rTxt, resourcePackages);
    recordMapping(proguardMapping);
    recordClassUsages(classes);
    recordManifestUsages(mergedManifest);
    recordResources(mergedResourceDir);
    keepPossiblyReferencedResources();
    dumpReferences();
    model.processToolsAttributes();
    unused = model.findUnused();
    removeUnused(destinationDir);
  }

  /**
   * Remove resources (already identified by {@link #shrink(Path)}).
   *
   * <p>This task will copy all remaining used resources over from the full resource directory to a
   * new reduced resource directory and removes unused values from all value xml files.
   *
   * @param destination directory to copy resources into; if null, delete resources in place
   * @throws IOException
   * @throws ParserConfigurationException
   * @throws SAXException
   */
  private void removeUnused(Path destination) throws IOException,
      ParserConfigurationException, SAXException {
    assert unused != null; // should always call analyze() first
    int resourceCount = unused.size() * 4; // *4: account for some resource folder repetition
    Set<File> skip = Sets.newHashSetWithExpectedSize(resourceCount);
    Set<File> rewrite = Sets.newHashSetWithExpectedSize(resourceCount);
    Set<Resource> deleted = Sets.newHashSetWithExpectedSize(resourceCount);
    for (Resource resource : unused) {
      deleted.add(resource);
      if (resource.declarations != null) {
        for (File file : resource.declarations) {
          String folder = file.getParentFile().getName();
          ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
          if (folderType != null && folderType != ResourceFolderType.VALUES) {
            logger.fine("Deleted unused resource " + file + " for resource " + resource);
            assert skip != null;
            skip.add(file);
          } else {
            // Can't delete values immediately; there can be many resources
            // in this file, so we have to process them all
            rewrite.add(file);
          }
        }
      }
    }
    // Special case the base values.xml folder
    File values = new File(mergedResourceDir.toFile(),
        FD_RES_VALUES + File.separatorChar + "values.xml");
    if (values.exists()) {
      rewrite.add(values);
    }

    Map<File, String> rewritten = Maps.newHashMapWithExpectedSize(rewrite.size());
    rewriteXml(rewrite, rewritten);
    // TODO(apell): The graph traversal does not mark IDs as reachable or not, so they cannot be
    // accurately removed from public.xml, but the declarations may be deleted if they occur in
    // other files. IDs should be added to values.xml so that there are no definitions in public.xml
    // without declarations.
    File publicXml = new File(mergedResourceDir.toFile(),
        FD_RES_VALUES + File.separatorChar + "public.xml");
    createStubIds(values, rewritten, publicXml);

    trimPublicResources(publicXml, deleted, rewritten);

    filteredCopy(mergedResourceDir.toFile(), destination, skip, rewritten);
  }

  /**
   * Deletes unused resources from value XML files.
   */
  private void rewriteXml(Set<File> rewrite, Map<File, String> rewritten)
      throws IOException, ParserConfigurationException, SAXException {
    // Delete value resources: Must rewrite the XML files
    for (File file : rewrite) {
      String xml = Files.toString(file, UTF_8);
      Document document = XmlUtils.parseDocument(xml, true);
      Element root = document.getDocumentElement();
      if (root != null && TAG_RESOURCES.equals(root.getTagName())) {
        List<String> removed = Lists.newArrayList();
        stripUnused(root, removed);
        logger.fine("Removed " + removed.size() + " unused resources from " + file + ":\n  "
            + Joiner.on(", ").join(removed));
        String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
        rewritten.put(file, formatted);
      }
    }
  }

  /**
   * Write stub values for IDs to values.xml to match those available in public.xml.
   */
  private void createStubIds(File values, Map<File, String> rewritten, File publicXml)
      throws IOException, ParserConfigurationException, SAXException {
    if (values.exists()) {
      String xml = rewritten.get(values);
      if (xml == null) {
        xml = Files.toString(values, UTF_8);
      }
      List<String> stubbed = Lists.newArrayList();
      Document document = XmlUtils.parseDocument(xml, true);
      Element root = document.getDocumentElement();
      for (Resource resource : model.getResources()) {
        boolean inPublicXml = false;
        if (resource.declarations != null) {
          for (File file : resource.declarations) {
            if (file.equals(publicXml)) {
              inPublicXml = true;
            }
          }
        }
        NodeList existing = null;
        try {
          XPathExpression expr = XPathFactory.newInstance().newXPath().compile(
              String.format("//item[@type=\"id\"][@name=\"%s\"]", resource.name));
          existing = (NodeList) expr.evaluate(document, XPathConstants.NODESET);
        } catch (XPathException e) {
          // Failed to retrieve any existing declarations for resource.
        }
        if (resource.type == ResourceType.ID && inPublicXml
            && (existing == null || existing.getLength() == 0)) {
          Element item = document.createElement(TAG_ITEM);
          item.setAttribute(ATTR_TYPE, resource.type.getName());
          item.setAttribute(ATTR_NAME, resource.name);
          root.appendChild(item);
          stubbed.add(resource.getUrl());
        }
      }
      logger.fine("Created " + stubbed.size() + " stub IDs for:\n  "
          + Joiner.on(", ").join(stubbed));
      String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
      rewritten.put(values, formatted);
    }
  }

  /**
   * Remove public definitions of unused resources.
   */
  private void trimPublicResources(File publicXml, Set<Resource> deleted,
      Map<File, String> rewritten) throws IOException, ParserConfigurationException, SAXException {
    if (publicXml.exists()) {
      String xml = rewritten.get(publicXml);
      if (xml == null) {
        xml = Files.toString(publicXml, UTF_8);
      }
      Document document = XmlUtils.parseDocument(xml, true);
      Element root = document.getDocumentElement();
      if (root != null && TAG_RESOURCES.equals(root.getTagName())) {
        NodeList children = root.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
          Node child = children.item(i);
          if (child.getNodeType() == Node.ELEMENT_NODE) {
            Element resourceElement = (Element) child;
            ResourceType type = ResourceType.getEnum(resourceElement.getAttribute(ATTR_TYPE));
            String name = resourceElement.getAttribute(ATTR_NAME);
            if (type != null && name != null) {
              Resource resource = model.getResource(type, name);
              if (resource != null && deleted.contains(resource)) {
                root.removeChild(child);
              }
            }
          }
        }
      }
      String formatted = XmlPrettyPrinter.prettyPrint(document, xml.endsWith("\n"));
      rewritten.put(publicXml, formatted);
    }
  }

  /**
   * Copies one resource directory tree into another; skipping some files, replacing the contents of
   * some, and passing everything else through unmodified
   */
  private static void filteredCopy(File source, Path destination, Set<File> skip,
      Map<File, String> replace) throws IOException {

    File destinationFile = destination.toFile();
    if (source.isDirectory()) {
      File[] children = source.listFiles();
      if (children != null) {
        if (!destinationFile.exists()) {
          boolean success = destinationFile.mkdirs();
          if (!success) {
            throw new IOException("Could not create " + destination);
          }
        }
        for (File child : children) {
          filteredCopy(child, destination.resolve(child.getName()), skip, replace);
        }
      }
    } else if (!skip.contains(source) && source.isFile()) {
      String contents = replace.get(source);
      if (contents != null) {
        Files.write(contents, destinationFile, UTF_8);
      } else {
        Files.copy(source, destinationFile);
      }
    }
  }

  private void stripUnused(Element element, List<String> removed) {
    ResourceType type = ResourceUsageModel.getResourceType(element);
    if (type == ResourceType.ATTR) {
      // Not yet properly handled
      return;
    }
    Resource resource = model.getResource(element);
    if (resource != null) {
      if (resource.type == ResourceType.DECLARE_STYLEABLE
          || resource.type == ResourceType.ATTR) {
        // Don't strip children of declare-styleable; we're not correctly
        // tracking field references of the R_styleable_attr fields yet
        return;
      }
      if (!resource.isReachable()
          && (resource.type == ResourceType.STYLE
              || resource.type == ResourceType.PLURALS
              || resource.type == ResourceType.ARRAY)) {
        NodeList children = element.getChildNodes();
        for (int i = children.getLength() - 1; i >= 0; i--) {
          Node child = children.item(i);
          element.removeChild(child);
        }
      }
    }
    NodeList children = element.getChildNodes();
    for (int i = children.getLength() - 1; i >= 0; i--) {
      Node child = children.item(i);
      if (child.getNodeType() == Node.ELEMENT_NODE) {
        stripUnused((Element) child, removed);
      }
    }
    if (resource != null && !resource.isReachable() && resource.type != ResourceType.ID) {
      removed.add(resource.getUrl());
      Node parent = element.getParentNode();
      parent.removeChild(element);
    }
  }

  private void dumpReferences() {
    logger.fine(model.dumpReferences());
  }

  private void keepPossiblyReferencedResources() {
    if ((!foundGetIdentifier && !foundWebContent) || strings == null) {
      // No calls to android.content.res.Resources#getIdentifier; no need
      // to worry about string references to resources
      return;
    }
    if (!model.isSafeMode()) {
      // User specifically asked for us not to guess resources to keep; they will
      // explicitly mark them as kept if necessary instead
      return;
    }
    List<String> sortedStrings = new ArrayList<String>(strings);
    Collections.sort(sortedStrings);
    logger.fine(
        "android.content.res.Resources#getIdentifier present: " + foundGetIdentifier);
    logger.fine("Web content present: " + foundWebContent);
    logger.fine("Referenced Strings:");
    for (String string : sortedStrings) {
      string = string.trim().replace("\n", "\\n");
      if (string.length() > 40) {
        string = string.substring(0, 37) + "...";
      } else if (string.isEmpty()) {
        continue;
      }
      logger.fine("  " + string);
    }
    int shortest = Integer.MAX_VALUE;
    Set<String> names = Sets.newHashSetWithExpectedSize(50);
    for (Resource resource : model.getResources()) {
      String name = resource.name;
      names.add(name);
      int length = name.length();
      if (length < shortest) {
        shortest = length;
      }
    }
    for (String string : strings) {
      if (string.length() < shortest) {
        continue;
      }
      // Check whether the string looks relevant
      // We consider four types of strings:
      //  (1) simple resource names, e.g. "foo" from @layout/foo
      //      These might be the parameter to a getIdentifier() call, or could
      //      be composed into a fully qualified resource name for the getIdentifier()
      //      method. We match these for *all* resource types.
      //  (2) Relative source names, e.g. layout/foo, from @layout/foo
      //      These might be composed into a fully qualified resource name for
      //      getIdentifier().
      //  (3) Fully qualified resource names of the form package:type/name.
      //  (4) If foundWebContent is true, look for android_res/ URL strings as well
      if (foundWebContent) {
        Resource resource = model.getResourceFromFilePath(string);
        if (resource != null) {
          ResourceUsageModel.markReachable(resource);
          continue;
        } else {
          int start = 0;
          int slash = string.lastIndexOf('/');
          if (slash != -1) {
            start = slash + 1;
          }
          int dot = string.indexOf('.', start);
          String name = string.substring(start, dot != -1 ? dot : string.length());
          if (names.contains(name)) {
            for (Map<String, Resource> map : model.getResourceMaps()) {
              resource = map.get(name);
              if (resource != null) {
                logger.fine(String.format(
                    "Marking %s used because it matches string pool constant %s",
                    resource, string));
              }
              ResourceUsageModel.markReachable(resource);
            }
          }
        }
      }
      // Look for normal getIdentifier resource URLs
      int n = string.length();
      boolean justName = true;
      boolean formatting = false;
      boolean haveSlash = false;
      for (int i = 0; i < n; i++) {
        char c = string.charAt(i);
        if (c == '/') {
          haveSlash = true;
          justName = false;
        } else if (c == '.' || c == ':' || c == '%') {
          justName = false;
          if (c == '%') {
            formatting = true;
          }
        } else if (!Character.isJavaIdentifierPart(c)) {
          // This shouldn't happen; we've filtered out these strings in
          // the {@link #referencedString} method
          assert false : string;
          break;
        }
      }
      String name;
      if (justName) {
        // Check name (below)
        name = string;
        // Check for a simple prefix match, e.g. as in
        // getResources().getIdentifier("ic_video_codec_" + codecName, "drawable", ...)
        for (Resource resource : model.getResources()) {
          if (resource.name.startsWith(name)) {
            logger.fine(String.format(
                "Marking %s used because its prefix matches string pool constant %s",
                resource, string));
            ResourceUsageModel.markReachable(resource);
          }
        }
      } else if (!haveSlash) {
        if (formatting) {
          // Possibly a formatting string, e.g.
          //   String name = String.format("my_prefix_%1d", index);
          //   int res = getContext().getResources().getIdentifier(name, "drawable", ...)
          try {
            Pattern pattern = Pattern.compile(convertFormatStringToRegexp(string));
            for (Resource resource : model.getResources()) {
              if (pattern.matcher(resource.name).matches()) {
                logger.fine(String.format(
                    "Marking %s used because it format-string matches string pool constant %s",
                    resource, string));
                ResourceUsageModel.markReachable(resource);
              }
            }
          } catch (PatternSyntaxException ignored) {
            // Might not have been a formatting string after all!
          }
        }
        // If we have more than just a symbol name, we expect to also see a slash
        //noinspection UnnecessaryContinue
        continue;
      } else {
        // Try to pick out the resource name pieces; if we can find the
        // resource type unambiguously; if not, just match on names
        int slash = string.indexOf('/');
        assert slash != -1; // checked with haveSlash above
        name = string.substring(slash + 1);
        if (name.isEmpty() || !names.contains(name)) {
          continue;
        }
        // See if have a known specific resource type
        if (slash > 0) {
          int colon = string.indexOf(':');
          String typeName = string.substring(colon != -1 ? colon + 1 : 0, slash);
          ResourceType type = ResourceType.getEnum(typeName);
          if (type == null) {
            continue;
          }
          Resource resource = model.getResource(type, name);
          if (resource != null) {
            logger.fine(String.format(
                "Marking %s used because it matches string pool constant %s",
                resource, string));
          }
          ResourceUsageModel.markReachable(resource);
          continue;
        }
        // fall through and check the name
      }
      if (names.contains(name)) {
        for (Map<String, Resource> map : model.getResourceMaps()) {
          Resource resource = map.get(name);
          if (resource != null) {
            logger.fine(String.format(
                "Marking %s used because it matches string pool constant %s",
                resource, string));
          }
          ResourceUsageModel.markReachable(resource);
        }
      } else if (Character.isDigit(name.charAt(0))) {
        // Just a number? There are cases where it calls getIdentifier by
        // a String number; see for example SuggestionsAdapter in the support
        // library which reports supporting a string like "2130837524" and
        // "android.resource://com.android.alarmclock/2130837524".
        try {
          int id = Integer.parseInt(name);
          if (id != 0) {
            ResourceUsageModel.markReachable(model.getResource(id));
          }
        } catch (NumberFormatException e) {
          // pass
        }
      }
    }
  }

  @VisibleForTesting
  static String convertFormatStringToRegexp(String formatString) {
    StringBuilder regexp = new StringBuilder();
    int from = 0;
    boolean hasEscapedLetters = false;
    Matcher matcher = StringFormatDetector.FORMAT.matcher(formatString);
    int length = formatString.length();
    while (matcher.find(from)) {
      int start = matcher.start();
      int end = matcher.end();
      if (start == 0 && end == length) {
        // Don't match if the entire string literal starts with % and ends with
        // the a formatting character, such as just "%d": this just matches absolutely
        // everything and is unlikely to be used in a resource lookup
        return NO_MATCH;
      }
      if (start > from) {
        hasEscapedLetters |= appendEscapedPattern(formatString, regexp, from, start);
      }
      // If the wildcard follows a previous wildcard, just skip it
      // (e.g. don't convert %s%s into .*.*; .* is enough.
      int regexLength = regexp.length();
      if (regexLength < 2
          || regexp.charAt(regexLength - 1) != '*'
          || regexp.charAt(regexLength - 2) != '.') {
        regexp.append(".*");
      }
      from = end;
    }
    if (from < length) {
      hasEscapedLetters |= appendEscapedPattern(formatString, regexp, from, length);
    }
    if (!hasEscapedLetters) {
      // If the regexp contains *only* formatting characters, e.g. "%.0f%d", or
      // if it contains only formatting characters and punctuation, e.g. "%s_%d",
      // don't treat this as a possible resource name pattern string: it is unlikely
      // to be intended for actual resource names, and has the side effect of matching
      // most names.
      return NO_MATCH;
    }
    return regexp.toString();
  }

  /**
   * Appends the characters in the range [from,to> from formatString as escaped regexp characters
   * into the given string builder. Returns true if there were any letters in the appended text.
   */
  private static boolean appendEscapedPattern(
      @NonNull String formatString, @NonNull StringBuilder regexp, int from, int to) {
    regexp.append(Pattern.quote(formatString.substring(from, to)));
    for (int i = from; i < to; i++) {
      if (Character.isLetter(formatString.charAt(i))) {
        return true;
      }
    }
    return false;
  }

  private void recordResources(Path resDir)
      throws IOException, SAXException, ParserConfigurationException {

    File[] resourceFolders = resDir.toFile().listFiles();
    if (resourceFolders != null) {
      for (File folder : resourceFolders) {
        ResourceFolderType folderType = ResourceFolderType.getFolderType(folder.getName());
        if (folderType != null) {
          recordResources(folderType, folder);
        }
      }
    }
  }

  private void recordResources(@NonNull ResourceFolderType folderType, File folder)
      throws ParserConfigurationException, SAXException, IOException {
    File[] files = folder.listFiles();
    if (files != null) {
      for (File file : files) {
        String path = file.getPath();
        model.file = file;
        try {
          boolean isXml = endsWithIgnoreCase(path, DOT_XML);
          if (isXml) {
            String xml = Files.toString(file, UTF_8);
            Document document = XmlUtils.parseDocument(xml, true);
            model.visitXmlDocument(file, folderType, document);
          } else {
            model.visitBinaryResource(folderType, file);
          }
        } finally {
          model.file = null;
        }
      }
    }
  }

  private void recordMapping(@Nullable Path mapping) throws IOException {
    if (mapping == null || !mapping.toFile().exists()) {
      return;
    }
    final String arrowIndicator = " -> ";
    final String resourceIndicator = ".R$";
    Map<String, String> nameMap = null;
    for (String line : Files.readLines(mapping.toFile(), UTF_8)) {
      if (line.startsWith(" ") || line.startsWith("\t")) {
        if (nameMap != null) {
          // We're processing the members of a resource class: record names into the map
          int n = line.length();
          int i = 0;
          for (; i < n; i++) {
            if (!Character.isWhitespace(line.charAt(i))) {
              break;
            }
          }
          if (i < n && line.startsWith("int", i)) { // int or int[]
            int start = line.indexOf(' ', i + 3) + 1;
            int arrow = line.indexOf(arrowIndicator);
            if (start > 0 && arrow != -1) {
              int end = line.indexOf(' ', start + 1);
              if (end != -1) {
                String oldName = line.substring(start, end);
                String newName = line.substring(arrow + arrowIndicator.length()).trim();
                if (!newName.equals(oldName)) {
                  nameMap.put(newName, oldName);
                }
              }
            }
          }
        }
        continue;
      } else {
        nameMap = null;
      }
      int index = line.indexOf(resourceIndicator);
      if (index == -1) {
        // Record obfuscated names of a few known appcompat usages of
        // Resources#getIdentifier that are unlikely to be used for general
        // resource name reflection
        if (line.startsWith("android.support.v7.widget.SuggestionsAdapter ")) {
          suggestionsAdapter =
              line.substring(
                          line.indexOf(arrowIndicator) + arrowIndicator.length(),
                          line.indexOf(':') != -1 ? line.indexOf(':') : line.length())
                      .trim()
                      .replace('.', '/')
                  + DOT_CLASS;
        } else if (line.startsWith("android.support.v7.internal.widget.ResourcesWrapper ")
            || line.startsWith("android.support.v7.widget.ResourcesWrapper ")
            || (resourcesWrapper == null // Recently wrapper moved
                && line.startsWith(
                    "android.support.v7.widget.TintContextWrapper$TintResources "))) {
          resourcesWrapper =
              line.substring(
                          line.indexOf(arrowIndicator) + arrowIndicator.length(),
                          line.indexOf(':') != -1 ? line.indexOf(':') : line.length())
                      .trim()
                      .replace('.', '/')
                  + DOT_CLASS;
        }
        continue;
      }
      int arrow = line.indexOf(arrowIndicator, index + 3);
      if (arrow == -1) {
        continue;
      }
      String typeName = line.substring(index + resourceIndicator.length(), arrow);
      ResourceType type = ResourceType.getEnum(typeName);
      if (type == null) {
        continue;
      }
      int end = line.indexOf(':', arrow + arrowIndicator.length());
      if (end == -1) {
        end = line.length();
      }
      String target = line.substring(arrow + arrowIndicator.length(), end).trim();
      String ownerName = AsmUtils.toInternalName(target);
      nameMap = Maps.newHashMap();
      Pair<ResourceType, Map<String, String>> pair = Pair.of(type, nameMap);
      resourceObfuscation.put(ownerName, pair);
      // For fast lookup in isResourceClass
      resourceObfuscation.put(ownerName + DOT_CLASS, pair);
    }
  }

  private void recordManifestUsages(Path manifest)
      throws IOException, ParserConfigurationException, SAXException {
    String xml = Files.toString(manifest.toFile(), UTF_8);
    Document document = XmlUtils.parseDocument(xml, true);
    model.visitXmlDocument(manifest.toFile(), null, document);
  }

  public static String getFieldName(@NonNull String styleName) {
    return styleName.replace('.', '_').replace('-', '_').replace(':', '_');
  }

  private Set<String> strings;
  private boolean foundGetIdentifier;
  private boolean foundWebContent;

  private void referencedString(@NonNull String string) {
    // See if the string is at all eligible; ignore strings that aren't
    // identifiers (has java identifier chars and nothing but .:/), or are empty or too long
    // We also allow "%", used for formatting strings.
    if (string.isEmpty() || string.length() > 80) {
      return;
    }
    boolean haveIdentifierChar = false;
    for (int i = 0, n = string.length(); i < n; i++) {
      char c = string.charAt(i);
      boolean identifierChar = Character.isJavaIdentifierPart(c);
      if (!identifierChar && c != '.' && c != ':' && c != '/' && c != '%') {
        // .:/ are for the fully qualified resource names, or for resource URLs or
        // relative file names
        return;
      } else if (identifierChar) {
        haveIdentifierChar = true;
      }
    }
    if (!haveIdentifierChar) {
      return;
    }
    if (strings == null) {
      strings = Sets.newHashSetWithExpectedSize(300);
    }
    strings.add(string);

    if (!foundWebContent && string.contains(ANDROID_RES)) {
      foundWebContent = true;
    }
  }

  private void recordClassUsages(Path file) throws IOException {
    if (file.toFile().isDirectory()) {
      File[] children = file.toFile().listFiles();
      if (children != null) {
        for (File child : children) {
          recordClassUsages(child.toPath());
        }
      }
    } else if (file.toFile().isFile()) {
      if (file.toFile().getPath().endsWith(DOT_CLASS)) {
        byte[] bytes = Files.toByteArray(file.toFile());
        recordClassUsages(file.toFile(), file.toFile().getName(), bytes);
      } else if (file.toFile().getPath().endsWith(DOT_JAR)) {
        ZipInputStream zis = null;
        try {
          FileInputStream fis = new FileInputStream(file.toFile());
          try {
            zis = new ZipInputStream(fis);
            ZipEntry entry = zis.getNextEntry();
            while (entry != null) {
              String name = entry.getName();
              if (name.endsWith(DOT_CLASS)
                  &&
                  // Skip resource type classes like R$drawable; they will
                  // reference the integer id's we're looking for, but these aren't
                  // actual usages we need to track; if somebody references the
                  // field elsewhere, we'll catch that
                  !isResourceClass(name)) {
                byte[] bytes = ByteStreams.toByteArray(zis);
                if (bytes != null) {
                  recordClassUsages(file.toFile(), name, bytes);
                }
              }
              entry = zis.getNextEntry();
            }
          } finally {
            Closeables.close(fis, true);
          }
        } finally {
          Closeables.close(zis, true);
        }
      }
    }
  }

  private void recordClassUsages(File file, String name, byte[] bytes) {
    ClassReader classReader = new ClassReader(bytes);
    classReader.accept(new UsageVisitor(file, name), SKIP_DEBUG | SKIP_FRAMES);
  }

  private void parseResourceTxtFile(Path rTxt, Set<String> resourcePackages) throws IOException {
    BufferedReader reader = java.nio.file.Files.newBufferedReader(rTxt, UTF_8);
    String line;
    while ((line = reader.readLine()) != null) {
      String[] tokens = line.split(" ");
      ResourceType type = ResourceType.getEnum(tokens[1]);
      for (String resourcePackage : resourcePackages) {
        String owner = resourcePackage.replace('.', '/') + "/R$" + type.getName();
        Pair<ResourceType, Map<String, String>> pair = resourceObfuscation.get(owner);
        if (pair == null) {
          Map<String, String> nameMap = Maps.newHashMap();
          pair = Pair.of(type, nameMap);
        }
        resourceObfuscation.put(owner, pair);
      }
      if (type == ResourceType.STYLEABLE) {
        if (tokens[0].equals("int[]")) {
          model.addResource(ResourceType.DECLARE_STYLEABLE, tokens[2], null);
        } else {
          // TODO(jongerrish): Implement stripping of styleables.
        }
      } else {
        model.addResource(type, tokens[2], tokens[3]);
      }
    }
  }

  /** Returns whether the given class file name points to an aapt-generated compiled R class */
  @VisibleForTesting
  boolean isResourceClass(@NonNull String name) {
    if (resourceObfuscation.containsKey(name)) {
      return true;
    }
    assert name.endsWith(DOT_CLASS) : name;
    int index = name.lastIndexOf('/');
    if (index != -1 && name.startsWith("R$", index + 1)) {
      String typeName = name.substring(index + 3, name.length() - DOT_CLASS.length());
      return ResourceType.getEnum(typeName) != null;
    }
    return false;
  }

  @VisibleForTesting
  @Nullable
  Resource getResourceFromCode(@NonNull String owner, @NonNull String name) {
    Pair<ResourceType, Map<String, String>> pair = resourceObfuscation.get(owner);
    if (pair != null) {
      ResourceType type = pair.getFirst();
      Map<String, String> nameMap = pair.getSecond();
      String renamedField = nameMap.get(name);
      if (renamedField != null) {
        name = renamedField;
      }
      return model.getResource(type, name);
    }
    return null;
  }

  public int getUnusedResourceCount() {
    return unused.size();
  }

  @VisibleForTesting
  ResourceUsageModel getModel() {
    return model;
  }

  /**
   * Class visitor responsible for looking for resource references in code. It looks for R.type.name
   * references (as well as inlined constants for these, in the case of non-library code), as well
   * as looking both for Resources#getIdentifier calls and recording string literals, used to handle
   * dynamic lookup of resources.
   */
  private class UsageVisitor extends ClassVisitor {
    private final File jarFile;
    private final String currentClass;

    public UsageVisitor(File jarFile, String name) {
      super(Opcodes.ASM5);
      this.jarFile = jarFile;
      currentClass = name;
    }

    @Override
    public MethodVisitor visitMethod(
        int access, final String name, String desc, String signature, String[] exceptions) {
      return new MethodVisitor(Opcodes.ASM5) {
        @Override
        public void visitLdcInsn(Object cst) {
          handleCodeConstant(cst, "ldc");
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String desc) {
          if (opcode == Opcodes.GETSTATIC) {
            Resource resource = getResourceFromCode(owner, name);
            if (resource != null) {
              ResourceUsageModel.markReachable(resource);
            }
          }
        }

        @Override
        public void visitMethodInsn(
            int opcode, String owner, String name, String desc, boolean itf) {
          super.visitMethodInsn(opcode, owner, name, desc, itf);
          if (owner.equals("android/content/res/Resources")
              && name.equals("getIdentifier")
              && desc.equals("(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)I")) {
            if (currentClass.equals(resourcesWrapper)
                || currentClass.equals(suggestionsAdapter)) {
              // "benign" usages: don't trigger reflection mode just because
              // the user has included appcompat
              return;
            }
            foundGetIdentifier = true;
            // TODO: Check previous instruction and see if we can find a literal
            // String; if so, we can more accurately dispatch the resource here
            // rather than having to check the whole string pool!
          }
          if (owner.equals("android/webkit/WebView") && name.startsWith("load")) {
            foundWebContent = true;
          }
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
          return new AnnotationUsageVisitor();
        }

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationUsageVisitor();
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(
            int parameter, String desc, boolean visible) {
          return new AnnotationUsageVisitor();
        }
      };
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      return new AnnotationUsageVisitor();
    }

    @Override
    public FieldVisitor visitField(
        int access, String name, String desc, String signature, Object value) {
      handleCodeConstant(value, "field");
      return new FieldVisitor(Opcodes.ASM5) {
        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
          return new AnnotationUsageVisitor();
        }
      };
    }

    private class AnnotationUsageVisitor extends AnnotationVisitor {
      public AnnotationUsageVisitor() {
        super(Opcodes.ASM5);
      }

      @Override
      public AnnotationVisitor visitAnnotation(String name, String desc) {
        return new AnnotationUsageVisitor();
      }

      @Override
      public AnnotationVisitor visitArray(String name) {
        return new AnnotationUsageVisitor();
      }

      @Override
      public void visit(String name, Object value) {
        handleCodeConstant(value, "annotation");
        super.visit(name, value);
      }
    }
    /** Invoked when an ASM visitor encounters a constant: record corresponding reference */
    private void handleCodeConstant(@Nullable Object cst, @NonNull String context) {
      if (cst instanceof Integer) {
        Integer value = (Integer) cst;
        Resource resource = model.getResource(value);
        if (ResourceUsageModel.markReachable(resource)) {
          logger.fine(String.format("Marking %s reachable: referenced from %s in %s:%s",
              resource, context, jarFile, currentClass));
        }
      } else if (cst instanceof int[]) {
        int[] values = (int[]) cst;
        for (int value : values) {
          Resource resource = model.getResource(value);
          if (ResourceUsageModel.markReachable(resource)) {
            logger.fine(String.format("Marking %s reachable: referenced from %s in %s:%s",
                resource, context, jarFile, currentClass));
          }
        }
      } else if (cst instanceof String) {
        String string = (String) cst;
        referencedString(string);
      }
    }
  }

  private final ResourceShrinkerUsageModel model = new ResourceShrinkerUsageModel();

  private class ResourceShrinkerUsageModel extends ResourceUsageModel {
    public File file;

    @NonNull
    @Override
    protected List<Resource> findRoots(@NonNull List<Resource> resources) {
      List<Resource> roots = super.findRoots(resources);
      logger.fine("The root reachable resources are:\n  " + Joiner.on(",\n  ").join(roots) + "\n");
      return roots;
    }

    @Override
    protected Resource declareResource(ResourceType type, String name, Node node) {
      Resource resource = super.declareResource(type, name, node);
      resource.addLocation(file);
      return resource;
    }

    @Override
    protected void referencedString(@NonNull String string) {
      ResourceUsageAnalyzer.this.referencedString(string);
      foundWebContent = true;
    }

    @Override
    public Resource getResource(Element element) {
      if (isPublic(element)) {
        ResourceType type = getTypeFromPublic(element);
        if (type != null) {
            String name = getFieldName(element);
            Resource resource = getResource(type, name);
            return resource;
        }
        return null;
      } else {
        return super.getResource(element);
      }
    }

    public boolean isPublic(Element element) {
      return element.getTagName().equals(ResourceType.PUBLIC.getName());
    }

    public ResourceType getTypeFromPublic(Element element) {
      String typeName = element.getAttribute(ATTR_TYPE);
      if (!typeName.isEmpty()) {
        return ResourceType.getEnum(typeName);
      }
      return null;
    }

    @Override
    public void recordResourceReferences(ResourceFolderType folderType, Node node, Resource from) {
      super.recordResourceReferences(folderType, node, from);
      // The parent class does not consider id declarations in xml files to also be uses, which is
      // wrong. Fix that behavior here by adding a reference to any id declarations.
      if (from != null && node.getNodeType() == Node.ELEMENT_NODE) {
        NamedNodeMap attributes = ((Element) node).getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
          Attr attr = (Attr) attributes.item(i);
          if (attr.getValue().startsWith(SdkConstants.PREFIX_RESOURCE_REF)
              && SdkConstants.ATTR_ID.equals(attr.getLocalName())
              && SdkConstants.ANDROID_URI.equals(attr.getNamespaceURI())) {
            ResourceUrl url = ResourceUrl.parse(attr.getValue());
            if (url != null) {
              Resource resource = getResource(url.type, url.name);
              if (resource != null) {
                from.addReference(resource);
              }
            }
          }
        }
      }
    }
  }
}
