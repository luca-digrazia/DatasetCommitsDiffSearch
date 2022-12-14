package com.googlecode.androidannotations.helper;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.tools.Diagnostic.Kind;
import javax.tools.JavaFileObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class AndroidManifestFinder {

    private static final int MAX_PARENTS_FROM_SOURCE_FOLDER = 10;

    private ProcessingEnvironment processingEnv;

    public AndroidManifestFinder(ProcessingEnvironment processingEnv) {
        this.processingEnv = processingEnv;
    }

    public AndroidManifest extractAndroidManifest() {
        try {
            return extractAndroidManifestThrowing();
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }
            throw new RuntimeException(e);
        }
    }

    private AndroidManifest extractAndroidManifestThrowing() throws Exception {
        File androidManifestFile = findManifestFileThrowing();
        return parseThrowing(androidManifestFile);
    }

    /**
     * We use a dirty trick to find the AndroidManifest.xml file, since it's not
     * available in the classpath. The idea is quite simple : create a fake
     * class file, retrieve its URI, and start going up in parent folders to
     * find the AndroidManifest.xml file. Any better solution will be
     * appreciated.
     */
    private File findManifestFileThrowing() throws Exception {
        Filer filer = processingEnv.getFiler();

        JavaFileObject dummySourceFile = filer.createSourceFile("dummy" + System.currentTimeMillis());
        String dummySourceFilePath = dummySourceFile.toUri().toString();

        if (dummySourceFilePath.startsWith("file:")) {
            if (!dummySourceFilePath.startsWith("file://")) {
                dummySourceFilePath = "file://" + dummySourceFilePath.substring("file:".length());
            }
        } else {
            dummySourceFilePath = "file://" + dummySourceFilePath;
        }

        Messager messager = processingEnv.getMessager();
        messager.printMessage(Kind.NOTE, "Dummy source file: " + dummySourceFilePath);

        URI cleanURI = new URI(dummySourceFilePath);

        File dummyFile = new File(cleanURI);

        File sourcesGenerationFolder = dummyFile.getParentFile();

        File projectRoot = sourcesGenerationFolder.getParentFile();

        File androidManifestFile = new File(projectRoot, "AndroidManifest.xml");
        for (int i = 0; i < MAX_PARENTS_FROM_SOURCE_FOLDER; i++) {
            if (androidManifestFile.exists()) {
                break;
            } else {
                if (projectRoot.getParentFile() != null) {
                    projectRoot = projectRoot.getParentFile();
                    androidManifestFile = new File(projectRoot, "AndroidManifest.xml");
                } else {
                    break;
                }
            }
        }

        if (!androidManifestFile.exists()) {
            throw new IllegalStateException("Could not find the AndroidManifest.xml file, going up from path [" + sourcesGenerationFolder.getAbsolutePath() + "] found using dummy file [" + dummySourceFilePath + "] (max atempts: " + MAX_PARENTS_FROM_SOURCE_FOLDER + ")");
        } else {
            messager.printMessage(Kind.NOTE, "AndroidManifest.xml file found: " + androidManifestFile.toString());
        }

        return androidManifestFile;
    }

    private AndroidManifest parseThrowing(File androidManifestFile) throws Exception {

        DocumentBuilderFactory docBuilderFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docBuilderFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(androidManifestFile);

        Element documentElement = doc.getDocumentElement();
        documentElement.normalize();

        String applicationPackage = documentElement.getAttribute("package");

        NodeList applicationNodes = documentElement.getElementsByTagName("application");

        List<String> applicationValidQualifiedNames = new ArrayList<String>();
        
        for (int i = 0; i < applicationNodes.getLength(); i++) {
            Node applicationNode = applicationNodes.item(i);
            Node nameAttribute = applicationNode.getAttributes().getNamedItem("android:name");
            applicationValidQualifiedNames.addAll(manifestNameToValidQualifiedNames(applicationPackage, nameAttribute));
        }
        
        NodeList activityNodes = documentElement.getElementsByTagName("activity");

        List<String> activityQualifiedNames = new ArrayList<String>();

        for (int i = 0; i < activityNodes.getLength(); i++) {
            Node activityNode = activityNodes.item(i);
            Node nameAttribute = activityNode.getAttributes().getNamedItem("android:name");
            activityQualifiedNames.addAll(manifestNameToValidQualifiedNames(applicationPackage, nameAttribute));
            
        }

        return new AndroidManifest(applicationPackage, applicationValidQualifiedNames, activityQualifiedNames);
    }

    private List<String> manifestNameToValidQualifiedNames(String applicationPackage, Node nameAttribute) {
        if (nameAttribute != null) {
            List<String> activityValidQualifiedNames = new ArrayList<String>();
            String activityName = nameAttribute.getNodeValue();
            String activityQualifiedName;
            if (activityName.startsWith(applicationPackage)) {
                activityQualifiedName = activityName;
            } else {
                if (activityName.startsWith(".")) {
                    activityQualifiedName = applicationPackage + activityName;
                } else {
                    // Sometimes it's a full name, sometimes a relative
                    // name. So we must add both, just in case.
                    // relative name
                    activityQualifiedName = applicationPackage + "." + activityName;
                    // full name
                    activityValidQualifiedNames.add(activityName);
                }
            }
            activityValidQualifiedNames.add(activityQualifiedName);
            return activityValidQualifiedNames;
        } else {
            return Collections.emptyList();
        }
    }

}
