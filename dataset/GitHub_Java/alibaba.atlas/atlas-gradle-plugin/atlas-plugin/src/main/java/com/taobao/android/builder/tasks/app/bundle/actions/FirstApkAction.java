package com.taobao.android.builder.tasks.app.bundle.actions;

import com.android.SdkConstants;
import com.android.build.gradle.internal.api.AppVariantOutputContext;
import com.android.build.gradle.internal.scope.TaskOutputHolder;
import com.android.build.gradle.tasks.PackageApplication;
import com.taobao.android.builder.AtlasBuildContext;
import com.taobao.android.builder.tools.zip.BetterZip;
import org.apache.commons.io.FileUtils;
import org.gradle.api.Action;
import org.gradle.api.Task;

import java.io.File;
import java.io.IOException;

import static com.android.SdkConstants.FN_RES_BASE;
import static com.android.SdkConstants.RES_QUALIFIER_SEP;

/**
 * FirstApkAction
 *
 * @author zhayu.ll
 * @date 18/1/12
 * @time 下午7:23
 * @description  
 */
public class FirstApkAction implements Action<Task> {

    private AppVariantOutputContext appVariantOutputContext;

    public FirstApkAction(AppVariantOutputContext appVariantOutputContext) {
        this.appVariantOutputContext = appVariantOutputContext;
    }
    @Override
    public void execute(Task task) {
        assert task instanceof PackageApplication;
        PackageApplication packageApplication = (PackageApplication) task;
        File bundleInfoFile = new File(appVariantOutputContext.getScope().getGlobalScope().getOutputsDir(),
                "bundleInfo-" +
                        appVariantOutputContext.getVariantContext().getVariantConfiguration()
                                .getVersionName() +
                        ".json");
        File resOutBaseNameFile =
                new File(
                        packageApplication.getResourceFiles().getSingleFile(),
                        FN_RES_BASE
                                + RES_QUALIFIER_SEP
                                + appVariantOutputContext.getVariantContext().getVariantName()
                                + SdkConstants.DOT_RES);

        File[]dexs = packageApplication.getDexFolders().getSingleFile().listFiles(pathname -> pathname.getName().equals("classes.dex"));
        if (dexs!= null) {
            File file = AtlasBuildContext.atlasApkProcessor.securitySignApk(dexs[0], new File(appVariantOutputContext.getScope().getOutput(TaskOutputHolder.TaskOutputType.MERGED_MANIFESTS).getSingleFile(),"AndroidManifest.xml"),appVariantOutputContext.getVariantContext().getBuildType(),false);
            if (file!= null && file.exists()){
                try {
                    BetterZip.addFile(resOutBaseNameFile, "res/drawable/".concat(file.getName()), file);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        if (bundleInfoFile.exists()){
            try {
                BetterZip.addFile(resOutBaseNameFile, "assets/".concat(bundleInfoFile.getName()), bundleInfoFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
