package com.shuyu.gsyvideoplayer;


import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.danikula.videocache.HttpProxyCacheServer;
import com.danikula.videocache.file.Md5FileNameGenerator;
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener;
import com.shuyu.gsyvideoplayer.player.IJKPlayerManager;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.FileUtils;
import com.shuyu.gsyvideoplayer.utils.StorageUtils;

import java.io.File;
import tv.danmaku.ijk.media.player.IjkLibLoader;

import static com.shuyu.gsyvideoplayer.utils.CommonUtil.hideNavKey;
import static com.shuyu.gsyvideoplayer.video.base.GSYBaseVideoPlayer.FULLSCREEN_ID;

/**
 * 视频管理，单例
 * 目前使用的是IJK封装的谷歌EXOPlayer
 * Created by shuyu on 2016/11/11.
 */

public class GSYVideoManager extends GSYVideoBaseManager {

    public static String TAG = "GSYVideoManager";

    @SuppressLint("StaticFieldLeak")
    private static GSYVideoManager videoManager;

    //单例模式实在不好给instance()加参数，还是直接设为静态变量吧
    //自定义so包加载类
    private static IjkLibLoader ijkLibLoader;

    /***
     * @param libLoader 是否使用外部动态加载so
     * */
    private GSYVideoManager(IjkLibLoader libLoader) {
        ijkLibLoader = libLoader;
        init(libLoader);
    }


    /**
     * 单例管理器
     */
    public static synchronized GSYVideoManager instance() {
        if (videoManager == null) {
            videoManager = new GSYVideoManager(ijkLibLoader);
        }
        return videoManager;
    }

    /**
     * 同步创建一个临时管理器
     */
    public static synchronized GSYVideoManager tmpInstance(GSYMediaPlayerListener listener) {
        GSYVideoManager gsyVideoManager = new GSYVideoManager(ijkLibLoader);
        gsyVideoManager.buffterPoint = videoManager.buffterPoint;
        gsyVideoManager.optionModelList = videoManager.optionModelList;
        gsyVideoManager.cacheFile = videoManager.cacheFile;
        gsyVideoManager.playTag = videoManager.playTag;
        gsyVideoManager.mMapHeadData = videoManager.mMapHeadData;
        gsyVideoManager.currentVideoWidth = videoManager.currentVideoWidth;
        gsyVideoManager.currentVideoHeight = videoManager.currentVideoHeight;
        gsyVideoManager.context = videoManager.context;
        gsyVideoManager.lastState = videoManager.lastState;
        gsyVideoManager.playPosition = videoManager.playPosition;
        gsyVideoManager.timeOut = videoManager.timeOut;
        gsyVideoManager.videoType = videoManager.videoType;
        gsyVideoManager.needMute = videoManager.needMute;
        gsyVideoManager.needTimeOutOther = videoManager.needTimeOutOther;
        gsyVideoManager.setListener(listener);
        return gsyVideoManager;
    }

    /**
     * 替换管理器
     */
    public static synchronized void changeManager(GSYVideoManager gsyVideoManager) {
        videoManager = gsyVideoManager;
    }


    /**
     * 设置自定义so包加载类
     * 需要在instance之前设置
     */
    public static void setIjkLibLoader(IjkLibLoader libLoader) {
        IJKPlayerManager.setIjkLibLoader(libLoader);
        ijkLibLoader = libLoader;
    }


    public static IjkLibLoader getIjkLibLoader() {
        return ijkLibLoader;
    }


    /**
     * 删除默认所有缓存文件
     */
    public static void clearAllDefaultCache(Context context) {
        String path = StorageUtils.getIndividualCacheDirectory
                (context.getApplicationContext()).getAbsolutePath();
        FileUtils.deleteFiles(new File(path));
    }

    /**
     * 删除url对应默认缓存文件
     */
    public static void clearDefaultCache(Context context, String url) {
        Md5FileNameGenerator md5FileNameGenerator = new Md5FileNameGenerator();
        String name = md5FileNameGenerator.generate(url);
        String pathTmp = StorageUtils.getIndividualCacheDirectory
                (context.getApplicationContext()).getAbsolutePath()
                + File.separator + name + ".download";
        String path = StorageUtils.getIndividualCacheDirectory
                (context.getApplicationContext()).getAbsolutePath()
                + File.separator + name;
        CommonUtil.deleteFile(pathTmp);
        CommonUtil.deleteFile(path);

    }


    /**
     * 获取缓存代理服务,带文件目录的
     */
    public static HttpProxyCacheServer getProxy(Context context, File file) {

        //如果为空，返回默认的
        if (file == null) {
            return getProxy(context);
        }

        //如果已经有缓存文件路径，那么判断缓存文件路径是否一致
        if (GSYVideoManager.instance().cacheFile != null
                && !GSYVideoManager.instance().cacheFile.getAbsolutePath().equals(file.getAbsolutePath())) {
            //不一致先关了旧的
            HttpProxyCacheServer proxy = GSYVideoManager.instance().proxy;

            if (proxy != null) {
                proxy.shutdown();
            }
            //开启新的
            return (GSYVideoManager.instance().proxy =
                    GSYVideoManager.instance().newProxy(context, file));
        } else {
            //还没有缓存文件的或者一致的，返回原来
            HttpProxyCacheServer proxy = GSYVideoManager.instance().proxy;

            return proxy == null ? (GSYVideoManager.instance().proxy =
                    GSYVideoManager.instance().newProxy(context, file)) : proxy;
        }
    }

    /**
     * 退出全屏，主要用于返回键
     *
     * @return 返回是否全屏
     */
    @SuppressWarnings("ResourceType")
    public static boolean backFromWindowFull(Context context) {
        boolean backFrom = false;
        ViewGroup vp = (ViewGroup) (CommonUtil.scanForActivity(context)).findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(FULLSCREEN_ID);
        if (oldF != null) {
            backFrom = true;
            hideNavKey(context);
            if (GSYVideoManager.instance().lastListener() != null) {
                GSYVideoManager.instance().lastListener().onBackFullscreen();
            }
        }
        return backFrom;
    }

    /**
     * 页面销毁了记得调用是否所有的video
     */
    public static void releaseAllVideos() {
        if (GSYVideoManager.instance().listener() != null) {
            GSYVideoManager.instance().listener().onCompletion();
        }
        GSYVideoManager.instance().releaseMediaPlayer();
    }

    /**
     * 暂停播放
     */
    public static void onPause() {
        if (GSYVideoManager.instance().listener() != null) {
            GSYVideoManager.instance().listener().onVideoPause();
        }
    }

    /**
     * 恢复播放
     */
    public static void onResume() {
        if (GSYVideoManager.instance().listener() != null) {
            GSYVideoManager.instance().listener().onVideoResume();
        }
    }

    /**
     * 获取缓存代理服务
     */
    private static HttpProxyCacheServer getProxy(Context context) {
        HttpProxyCacheServer proxy = GSYVideoManager.instance().proxy;
        return proxy == null ? (GSYVideoManager.instance().proxy =
                GSYVideoManager.instance().newProxy(context)) : proxy;
    }
}