package org.androidannotations.helper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.annotation.processing.Filer;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.JavaFileObject;

public class FileHelper {

	public static Option<File> findRootProject(ProcessingEnvironment processingEnv) {
		Option<FileHolder> rootProjectHolder = findRootProjectHolder(processingEnv);
		if (rootProjectHolder.isAbsent()) {
			return Option.absent();
		}

		return Option.of(rootProjectHolder.get().projectRoot);
	}

	/**
	 * We use a dirty trick to find the AndroidManifest.xml file, since it's not
	 * available in the classpath. The idea is quite simple : create a fake
	 * class file, retrieve its URI, and start going up in parent folders to
	 * find the AndroidManifest.xml file. Any better solution will be
	 * appreciated.
	 */
	public static Option<FileHolder> findRootProjectHolder(ProcessingEnvironment processingEnv) {
		Filer filer = processingEnv.getFiler();

		JavaFileObject dummySourceFile;
		try {
			dummySourceFile = filer.createSourceFile("dummy" + System.currentTimeMillis());
		} catch (IOException ignored) {
			return Option.absent();
		}
		String dummySourceFilePath = dummySourceFile.toUri().toString();

		if (dummySourceFilePath.startsWith("file:")) {
			if (!dummySourceFilePath.startsWith("file://")) {
				dummySourceFilePath = "file://" + dummySourceFilePath.substring("file:".length());
			}
		} else {
			dummySourceFilePath = "file://" + dummySourceFilePath;
		}

		URI cleanURI;
		try {
			cleanURI = new URI(dummySourceFilePath);
		} catch (URISyntaxException e) {
			return Option.absent();
		}

		File dummyFile = new File(cleanURI);
		File sourcesGenerationFolder = dummyFile.getParentFile();
		File projectRoot = sourcesGenerationFolder.getParentFile();

		return Option.of(new FileHolder(dummySourceFilePath, sourcesGenerationFolder, projectRoot));
	}

	public static File resolveOutputDirectory(ProcessingEnvironment processingEnv) {
		Option<File> rootProjectOption = FileHelper.findRootProject(processingEnv);
		if (rootProjectOption.isAbsent()) {
			// Execution root folder
			return null;
		}

		File rootProject = rootProjectOption.get();

		// Target folder - Maven
		File targetFolder = new File(rootProject, "target");
		if (targetFolder.isDirectory() && targetFolder.canWrite()) {
			return targetFolder;
		}

		// Build folder - Gradle
		File buildFolder = new File(rootProject, "build");
		if (buildFolder.isDirectory() && buildFolder.canWrite()) {
			return buildFolder;
		}

		// Bin folder - Eclipse
		File binFolder = new File(rootProject, "bin");
		if (binFolder.isDirectory() && binFolder.canWrite()) {
			return binFolder;
		}

		// Fallback to projet root folder
		return rootProject;
	}

	static class FileHolder {
		String dummySourceFilePath;
		File sourcesGenerationFolder;
		File projectRoot;

		public FileHolder(String dummySourceFilePath, File sourcesGenerationFolder, File projectRoot) {
			this.dummySourceFilePath = dummySourceFilePath;
			this.sourcesGenerationFolder = sourcesGenerationFolder;
			this.projectRoot = projectRoot;
		}
	}

}
