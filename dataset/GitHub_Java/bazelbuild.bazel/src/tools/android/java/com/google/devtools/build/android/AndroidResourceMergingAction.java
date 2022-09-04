// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.android;

import com.android.builder.core.VariantType;
import com.android.ide.common.internal.PngCruncher;
import com.android.ide.common.internal.PngException;
import com.android.utils.StdLogger;
import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.devtools.build.android.AndroidDataMerger.MergeConflictException;
import com.google.devtools.build.android.AndroidResourceMerger.MergingException;
import com.google.devtools.build.android.AndroidResourceProcessor.AaptConfigOptions;
import com.google.devtools.build.android.Converters.ExistingPathConverter;
import com.google.devtools.build.android.Converters.PathConverter;
import com.google.devtools.build.android.Converters.SerializedAndroidDataConverter;
import com.google.devtools.build.android.Converters.SerializedAndroidDataListConverter;
import com.google.devtools.common.options.Option;
import com.google.devtools.common.options.OptionDocumentationCategory;
import com.google.devtools.common.options.OptionEffectTag;
import com.google.devtools.common.options.OptionsBase;
import com.google.devtools.common.options.OptionsParser;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides an entry point for the resource merging action. After merging, this action generates the
 * R.class files required to compile the rest of the java sources.
 *
 * <p>This action only generates the class jar. The R source jar is generated by AAPT at a later
 * time and off of the critical path, by {@link AndroidResourceValidatorAction}. That way, the
 * source will contain javadocs derived from comments in the .xml files. Ideally users wouldn't use
 * the javadoc, but instead generate documentation directly from the source .xml files.
 */
public class AndroidResourceMergingAction {

  private static final StdLogger stdLogger = new StdLogger(StdLogger.Level.WARNING);

  private static final Logger logger =
      Logger.getLogger(AndroidResourceMergingAction.class.getName());

  /** Flag specifications for this action. */
  public static final class Options extends OptionsBase {

    @Option(
      name = "primaryData",
      defaultValue = "null",
      converter = SerializedAndroidDataConverter.class,
      category = "input",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help =
          "The directory containing the primary resource directory. The contents will override"
              + " the contents of any other resource directories during merging."
              + " The expected format is "
              + SerializedAndroidData.EXPECTED_FORMAT
    )
    public SerializedAndroidData primaryData;

    @Option(
      name = "primaryManifest",
      defaultValue = "null",
      converter = ExistingPathConverter.class,
      category = "input",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path to primary resource's manifest file."
    )
    public Path primaryManifest;

    @Option(
      name = "data",
      defaultValue = "",
      converter = SerializedAndroidDataListConverter.class,
      category = "input",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help =
          "Transitive Data dependencies. These values will be used if not defined in the "
              + "primary resources. The expected format is "
              + SerializedAndroidData.EXPECTED_FORMAT
              + "[&...]"
    )
    public List<SerializedAndroidData> transitiveData;

    @Option(
      name = "directData",
      defaultValue = "",
      converter = SerializedAndroidDataListConverter.class,
      category = "input",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help =
          "Direct Data dependencies. These values will be used if not defined in the "
              + "primary resources. The expected format is "
              + SerializedAndroidData.EXPECTED_FORMAT
              + "[&...]"
    )
    public List<SerializedAndroidData> directData;

    @Option(
      name = "resourcesOutput",
      defaultValue = "null",
      converter = PathConverter.class,
      category = "output",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path to the write merged resources archive."
    )
    public Path resourcesOutput;

    @Option(
      name = "classJarOutput",
      defaultValue = "null",
      converter = PathConverter.class,
      category = "output",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path for the generated java class jar."
    )
    public Path classJarOutput;

    @Option(
      name = "manifestOutput",
      defaultValue = "null",
      converter = PathConverter.class,
      category = "output",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path for the output processed AndroidManifest.xml."
    )
    public Path manifestOutput;

    @Option(
      name = "packageForR",
      defaultValue = "null",
      category = "config",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Custom java package to generate the R symbols files."
    )
    public String packageForR;

    @Option(
      name = "symbolsBinOut",
      defaultValue = "null",
      converter = PathConverter.class,
      category = "config",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path to write the merged symbols binary."
    )
    public Path symbolsBinOut;

    @Option(
      name = "dataBindingInfoOut",
      defaultValue = "null",
      converter = PathConverter.class,
      category = "output",
      documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
      effectTags = {OptionEffectTag.UNKNOWN},
      help = "Path to where data binding's layout info output should be written."
    )
    public Path dataBindingInfoOut;

    @Option(name = "throwOnResourceConflict",
        defaultValue = "false",
        category = "config",
        documentationCategory = OptionDocumentationCategory.UNCATEGORIZED,
        effectTags = {OptionEffectTag.UNKNOWN},
        help = "If passed, resource merge conflicts will be treated as errors instead of warnings")
    public boolean throwOnResourceConflict;
  }

  public static void main(String[] args) throws Exception {
    final Stopwatch timer = Stopwatch.createStarted();
    OptionsParser optionsParser =
        OptionsParser.newOptionsParser(Options.class, AaptConfigOptions.class);
    optionsParser.enableParamsFileSupport(FileSystems.getDefault());
    optionsParser.parseAndExitUponError(args);
    AaptConfigOptions aaptConfigOptions = optionsParser.getOptions(AaptConfigOptions.class);
    Options options = optionsParser.getOptions(Options.class);

    Preconditions.checkNotNull(options.primaryData);
    Preconditions.checkNotNull(options.primaryManifest);

    try (ScopedTemporaryDirectory scopedTmp =
        new ScopedTemporaryDirectory("android_resource_merge_tmp")) {
      Path tmp = scopedTmp.getPath();
      Path mergedAssets = tmp.resolve("merged_assets");
      Path mergedResources = tmp.resolve("merged_resources");
      Path generatedSources = tmp.resolve("generated_resources");
      Path processedManifest = tmp.resolve("manifest-processed/AndroidManifest.xml");

      logger.fine(String.format("Setup finished at %sms", timer.elapsed(TimeUnit.MILLISECONDS)));

      VariantType packageType = VariantType.LIBRARY;
      AndroidResourceClassWriter resourceClassWriter =
          AndroidResourceClassWriter.createWith(aaptConfigOptions.androidJar,
              generatedSources,
              Strings.nullToEmpty(options.packageForR));
      resourceClassWriter.setIncludeClassFile(true);
      resourceClassWriter.setIncludeJavaFile(false);

      final MergedAndroidData mergedData =
          AndroidResourceMerger.mergeData(
              options.primaryData,
              options.primaryManifest,
              options.directData,
              options.transitiveData,
              mergedResources,
              mergedAssets,
              new StubPngCruncher(),
              packageType,
              options.symbolsBinOut,
              resourceClassWriter,
              options.throwOnResourceConflict);

      logger.fine(String.format("Merging finished at %sms", timer.elapsed(TimeUnit.MILLISECONDS)));

      // Until enough users with manifest placeholders migrate to the new manifest merger,
      // we need to replace ${applicationId} and ${packageName} with options.packageForR to make
      // the manifests compatible with the old manifest merger.
      if (options.manifestOutput != null) {
        MergedAndroidData processedData =
            AndroidManifestProcessor.with(stdLogger)
                .processManifest(
                    packageType,
                    options.packageForR,
                    null, /* applicationId */
                    -1, /* versionCode */
                    null, /* versionName */
                    mergedData,
                    processedManifest);
        AndroidResourceOutputs.copyManifestToOutput(processedData, options.manifestOutput);
      }

      if (options.classJarOutput != null) {
        AndroidResourceOutputs.createClassJar(generatedSources, options.classJarOutput);
        logger.fine(
            String.format(
                "Create classJar finished at %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
      }

      if (options.resourcesOutput != null) {
        Path resourcesDir =
            AndroidResourceProcessor.processDataBindings(
                tmp.resolve("res_no_binding"),
                mergedData.getResourceDir(),
                options.dataBindingInfoOut,
                packageType,
                options.packageForR,
                options.primaryManifest,
                true);

        // For now, try compressing the library resources that we pass to the validator. This takes
        // extra CPU resources to pack and unpack (~2x), but can reduce the zip size (~4x).
        AndroidResourceOutputs.createResourcesZip(
            resourcesDir, mergedData.getAssetDir(), options.resourcesOutput, true /* compress */);
        logger.fine(
            String.format(
                "Create resources.zip finished at %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
      }
    } catch (MergeConflictException e) {
      logger.log(Level.SEVERE, e.getMessage());
      System.exit(1);
    } catch (MergingException e) {
      logger.log(Level.SEVERE, "Error during merging resources", e);
      throw e;
    } catch (AndroidManifestProcessor.ManifestProcessingException e) {
      System.exit(1);
    } catch (Exception e) {
      logger.log(Level.SEVERE, "Unexpected", e);
      throw e;
    }
    logger.fine(String.format("Resources merged in %sms", timer.elapsed(TimeUnit.MILLISECONDS)));
  }

  /**
   * The merged {@link Options#resourcesOutput} is only used for validation and not for running
   * (unlike the final APK), so the image files do not need to be the true image files. We only need
   * the filenames to be the same.
   *
   * <p>Thus, we only create empty files for PNGs (convenient with a custom PngCruncher object).
   * This does miss out on other image files like .webp.
   */
  private static final class StubPngCruncher implements PngCruncher {

    @Override
    public void crunchPng(int key, File from, File to) throws PngException {
      try {
        Files.touch(to);
      } catch (IOException e) {
        throw new PngException(e);
      }
    }

    @Override
    public int start() {
      return 0;
    }

    @Override
    public void end(int key) {
    }

  }
}
