/*
 * Copyright 2018 JessYan
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
package me.jessyan.autosize;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import me.jessyan.autosize.utils.Preconditions;
import me.jessyan.autosize.utils.ScreenUtils;

/**
 * ================================================
 * Created by JessYan on 2018/8/8 09:58
 * <a href="mailto:jess.yan.effort@gmail.com">Contact me</a>
 * <a href="https://github.com/JessYanCoding">Follow me</a>
 * ================================================
 */
public class AutoSizeConfig {
    private static volatile AutoSizeConfig sInstance;
    private static final String KEY_DESIGN_WIDTH_IN_DP = "design_width_in_dp";
    private static final String KEY_DESIGN_HEIGHT_IN_DP = "design_height_in_dp";
    private Application mApplication;
    /**
     * 最初的 {@link DisplayMetrics#density}
     */
    private float mInitDensity = -1;
    /**
     * 最初的 {@link DisplayMetrics#scaledDensity}
     */
    private float mInitScaledDensity;

    /**
     * 设计图上的总宽度, 单位 dp
     */
    private int mDesignWidthInDp;
    /**
     * 设计图上的总高度, 单位 dp
     */
    private int mDesignHeightInDp;
    /**
     * 设备的屏幕总宽度, 单位 px
     */
    private int mScreenWidth;
    /**
     * 设备的屏幕总高度, 单位 px, 如果 {@link #isUseDeviceSize} 为 {@code false}, 屏幕总高度会减去状态栏的高度
     */
    private int mScreenHeight;
    /**
     * 为了保证在不同高宽比的屏幕上显示效果也能完全一致, 所以本方案适配时是以设计图宽度与设备实际宽度的比例应用到
     * 每个 View 上, 从而使每个 View 的高和宽用同样的比例缩放, 避免在与设计图高宽比不一致的设备上出现适配的 View 高或宽变形的问题
     * {@link #isBaseOnWidth} 为 {@code true} 时代表以宽度等比例缩放, {@code false} 代表以高度等比例缩放
     * {@link #isBaseOnWidth} 为全局配置, 默认为 {@code true}, 每个 {@link Activity} 也可以单独选择使用高或者宽做等比例缩放
     */
    private boolean isBaseOnWidth = true;
    /**
     * 此字段表示是否使用设备的实际尺寸做适配
     * {@link #isUseDeviceSize} 为 {@code true} 表示屏幕高度 {@link #mScreenHeight} 包含状态栏的高度
     * {@link #isUseDeviceSize} 为 {@code false} 表示 {@link #mScreenHeight} 不包含状态栏的高度, 默认为 {@code true}
     */
    private boolean isUseDeviceSize = true;

    public static AutoSizeConfig getInstance() {
        if (sInstance == null) {
            synchronized (AutoSizeConfig.class) {
                if (sInstance == null) {
                    sInstance = new AutoSizeConfig();
                }
            }
        }
        return sInstance;
    }

    private AutoSizeConfig() {
    }

    /**
     * 建议在 Application 启动时完成初始化, 只能调用一次初始化方法, 否则报错
     * 此方法默认使用以宽度进行等比例适配, 如想使用以高度进行等比例适配, 请调用 {@link #init(Application, boolean)}
     *
     * @param application {@link Application}
     */
    public AutoSizeConfig init(Application application) {
        return init(application, true);
    }

    /**
     * 建议在 Application 启动时完成初始化, 只能调用一次初始化方法, 否则报错
     *
     * @param application   {@link Application}
     * @param isBaseOnWidth 详情请查看 {@link #isBaseOnWidth} 的注释
     */
    public AutoSizeConfig init(final Application application, boolean isBaseOnWidth) {
        Preconditions.checkArgument(mInitDensity == -1, "AutoSizeConfig#init() can only be called once");
        Preconditions.checkNotNull(application, "application == null");
        mApplication = application;
        final DisplayMetrics displayMetrics = application.getResources().getDisplayMetrics();

        getMetaData(application);
        int[] screenSize = ScreenUtils.getScreenSize(application);
        mScreenWidth = screenSize[0];
        mScreenHeight = screenSize[1];

        mInitDensity = displayMetrics.density;
        mInitScaledDensity = displayMetrics.scaledDensity;
        application.registerComponentCallbacks(new ComponentCallbacks() {
            @Override
            public void onConfigurationChanged(Configuration newConfig) {
                if (newConfig != null && newConfig.fontScale > 0) {
                    mInitScaledDensity =
                            application.getResources().getDisplayMetrics().scaledDensity;
                }
            }

            @Override
            public void onLowMemory() {

            }
        });
        application.registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacksImpl());
        return this;
    }

    /**
     * 是否全局按照宽度进行等比例适配
     *
     * @param baseOnWidth {@code true} 为按照宽度, {@code false} 为按照高度
     * @see #isBaseOnWidth 详情请查看这个字段的注释
     */
    public AutoSizeConfig setBaseOnWidth(boolean baseOnWidth) {
        isBaseOnWidth = baseOnWidth;
        return this;
    }

    /**
     * 是否使用设备的实际尺寸做适配
     *
     * @param useDeviceSize {@code true} 为使用设备的实际尺寸 (包含状态栏), {@code false} 为不使用 (不包含状态栏)
     * @see #isUseDeviceSize 详情请查看这个字段的注释
     */
    public AutoSizeConfig setUseDeviceSize(boolean useDeviceSize) {
        isUseDeviceSize = useDeviceSize;
        return this;
    }

    /**
     * 返回 {@link #isBaseOnWidth}
     *
     * @return {@link #isBaseOnWidth}
     */
    public boolean isBaseOnWidth() {
        return isBaseOnWidth;
    }

    /**
     * 返回 {@link #isUseDeviceSize}
     *
     * @return {@link #isUseDeviceSize}
     */
    public boolean isUseDeviceSize() {
        return isUseDeviceSize;
    }

    /**
     * 返回 {@link #mScreenWidth}
     *
     * @return {@link #mScreenWidth}
     */
    public int getScreenWidth() {
        return mScreenWidth;
    }

    /**
     * 返回 {@link #mScreenHeight}
     *
     * @return {@link #mScreenHeight}
     */
    public int getScreenHeight() {
        return isUseDeviceSize() ? mScreenHeight : mScreenHeight - ScreenUtils.getStatusBarHeight(mApplication);
    }

    /**
     * 获取 {@link #mDesignWidthInDp}
     *
     * @return {@link #mDesignWidthInDp}
     */
    public int getDesignWidthInDp() {
        Preconditions.checkArgument(mDesignWidthInDp > 0, "you must set " + KEY_DESIGN_WIDTH_IN_DP + "  in your AndroidManifest file");
        return mDesignWidthInDp;
    }

    /**
     * 获取 {@link #mDesignHeightInDp}
     *
     * @return {@link #mDesignHeightInDp}
     */
    public int getDesignHeightInDp() {
        Preconditions.checkArgument(mDesignHeightInDp > 0, "you must set " + KEY_DESIGN_HEIGHT_IN_DP + "  in your AndroidManifest file");
        return mDesignHeightInDp;
    }

    /**
     * 获取使用者在 AndroidManifest 中填写的 Meta 信息
     * <p>
     * Example usage:
     * <pre>
     * <meta-data android:name="design_width_in_dp"
     *            android:value="360"/>
     * <meta-data android:name="design_height_in_dp"
     *            android:value="640"/>
     * </pre>
     *
     * @param context {@link Context}
     */
    private void getMetaData(Context context) {
        PackageManager packageManager = context.getPackageManager();
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = packageManager.getApplicationInfo(context
                    .getPackageName(), PackageManager.GET_META_DATA);
            if (applicationInfo != null && applicationInfo.metaData != null) {
                if (applicationInfo.metaData.containsKey(KEY_DESIGN_WIDTH_IN_DP)) {
                    mDesignWidthInDp = (int) applicationInfo.metaData.get(KEY_DESIGN_WIDTH_IN_DP);
                }
                if (applicationInfo.metaData.containsKey(KEY_DESIGN_HEIGHT_IN_DP)) {
                    mDesignHeightInDp = (int) applicationInfo.metaData.get(KEY_DESIGN_HEIGHT_IN_DP);
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
