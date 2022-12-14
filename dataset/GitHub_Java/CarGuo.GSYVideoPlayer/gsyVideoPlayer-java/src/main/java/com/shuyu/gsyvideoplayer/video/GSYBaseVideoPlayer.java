package com.shuyu.gsyvideoplayer.video;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Point;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.shuyu.gsyvideoplayer.GSYTextureView;
import com.shuyu.gsyvideoplayer.GSYVideoManager;
import com.shuyu.gsyvideoplayer.GSYVideoPlayer;
import com.shuyu.gsyvideoplayer.R;
import com.shuyu.gsyvideoplayer.SmallVideoTouch;
import com.shuyu.gsyvideoplayer.listener.GSYMediaPlayerListener;
import com.shuyu.gsyvideoplayer.listener.VideoAllCallBack;
import com.shuyu.gsyvideoplayer.model.GSYModel;
import com.shuyu.gsyvideoplayer.model.VideoOptionModel;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.utils.OrientationUtils;
import com.transitionseverywhere.TransitionManager;

import java.io.File;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static com.shuyu.gsyvideoplayer.utils.CommonUtil.getActionBarHeight;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.getStatusBarHeight;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.hideNavKey;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.hideSupportActionBar;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.showNavKey;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.showSupportActionBar;

/**
 * Created by shuyu on 2016/11/17.
 */

public abstract class GSYBaseVideoPlayer extends FrameLayout implements GSYMediaPlayerListener {

    public static final int SMALL_ID = 84778;

    protected static final int FULLSCREEN_ID = 85597;

    protected static long CLICK_QUIT_FULLSCREEN_TIME = 0;

    protected boolean mActionBar = false;//?????????????????????window??????????????????????????????actionbar

    protected boolean mStatusBar = false;//?????????????????????window??????????????????????????????statusbar

    protected boolean mHideKey = true;//????????????????????????

    protected boolean mCache = false;//?????????????????????

    private boolean mShowFullAnimation = true;//??????????????????????????????

    protected boolean mNeedShowWifiTip = true; //??????????????????????????????

    protected int[] mListItemRect;//??????item??????????????????

    protected int[] mListItemSize;//??????item?????????

    protected int mCurrentState = -1; //?????????????????????

    protected int mRotate = 0; //???????????????????????????????????????????????????

    protected int mShrinkImageRes = -1; //?????????????????????????????????

    protected int mEnlargeImageRes = -1; //???????????????????????????

    protected float mSeekRatio = 1; //?????????????????????????????????

    protected float mSpeed = 1;//????????????

    protected boolean mRotateViewAuto = true; //??????????????????

    protected boolean mIfCurrentIsFullscreen = false;//??????????????????

    protected boolean mLockLand = false;//??????????????????????????????

    protected boolean mLooping = false;//??????

    protected boolean mHadPlay = false;//???????????????

    protected boolean mCacheFile = false; //????????????????????????

    protected boolean mIsTouchWiget = true; //???????????????????????????????????????

    protected boolean mIsTouchWigetFull = true; //????????????????????????????????????

    protected boolean mShowPauseCover = true;//????????????????????????

    protected boolean mRotateWithSystem = true; //???????????????????????????????????????

    protected boolean mNetChanged = false; //???????????????????????????

    protected String mNetSate = "NORMAL";

    protected Context mContext;

    protected String mOriginUrl; //?????????url

    protected String mUrl; //????????????URL

    protected String mTitle;

    protected File mCachePath;

    protected ViewGroup mTextureViewContainer; //??????????????????

    protected View mSmallClose; //?????????????????????

    protected VideoAllCallBack mVideoAllCallBack;

    protected Map<String, String> mMapHeadData = new HashMap<>();

    protected GSYTextureView mTextureView;

    protected ImageView mCoverImageView; //??????????????????????????????~

    protected View mStartButton;

    protected SeekBar mProgressBar;

    protected ImageView mFullscreenButton;

    protected TextView mCurrentTimeTextView, mTotalTimeTextView;

    protected ViewGroup mTopContainer, mBottomContainer;

    protected ImageView mBackButton;

    protected Bitmap mFullPauseBitmap = null;//???????????????????????????

    protected OrientationUtils mOrientationUtils; //???????????????

    private Handler mHandler = new Handler();

    private int mSystemUiVisibility;

    /**
     * 1.5.0??????????????????????????????????????????????????????????????????
     */
    public GSYBaseVideoPlayer(Context context, Boolean fullFlag) {
        super(context);
        mIfCurrentIsFullscreen = fullFlag;
    }

    public GSYBaseVideoPlayer(Context context) {
        super(context);
    }

    public GSYBaseVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public GSYBaseVideoPlayer(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public Context getActivityContext() {
        return CommonUtil.getActivityContext(getContext());
    }

    private ViewGroup getViewGroup() {
        return (ViewGroup) (CommonUtil.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
    }

    /**
     * ???????????????
     */
    private void removeVideo(ViewGroup vp, int id) {
        View old = vp.findViewById(id);
        if (old != null) {
            if (old.getParent() != null) {
                ViewGroup viewGroup = (ViewGroup) old.getParent();
                vp.removeView(viewGroup);
            }
        }
    }

    /**
     * ?????????????????????
     */
    private void saveLocationStatus(Context context, boolean statusBar, boolean actionBar) {
        getLocationOnScreen(mListItemRect);
        int statusBarH = getStatusBarHeight(context);
        int actionBerH = getActionBarHeight((Activity) context);
        if (statusBar) {
            mListItemRect[1] = mListItemRect[1] - statusBarH;
        }
        if (actionBar) {
            mListItemRect[1] = mListItemRect[1] - actionBerH;
        }
        mListItemSize[0] = getWidth();
        mListItemSize[1] = getHeight();
    }

    /**
     * ??????
     */
    private void resolveFullVideoShow(Context context, final GSYBaseVideoPlayer gsyVideoPlayer, final FrameLayout frameLayout) {
        LayoutParams lp = (LayoutParams) gsyVideoPlayer.getLayoutParams();
        lp.setMargins(0, 0, 0, 0);
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.gravity = Gravity.CENTER;
        gsyVideoPlayer.setLayoutParams(lp);
        gsyVideoPlayer.setIfCurrentIsFullscreen(true);
        mOrientationUtils = new OrientationUtils((Activity) context, gsyVideoPlayer);
        mOrientationUtils.setEnable(mRotateViewAuto);
        mOrientationUtils.setRotateWithSystem(mRotateWithSystem);
        gsyVideoPlayer.mOrientationUtils = mOrientationUtils;

        if (isShowFullAnimation()) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mLockLand && mOrientationUtils.getIsLand() != 1) {
                        mOrientationUtils.resolveByClick();
                    }
                    gsyVideoPlayer.setVisibility(VISIBLE);
                    frameLayout.setVisibility(VISIBLE);
                }
            }, 300);
        } else {
            if (mLockLand) {
                mOrientationUtils.resolveByClick();
            }
            gsyVideoPlayer.setVisibility(VISIBLE);
            frameLayout.setVisibility(VISIBLE);
        }


        if (mVideoAllCallBack != null) {
            Debuger.printfError("onEnterFullscreen");
            mVideoAllCallBack.onEnterFullscreen(mOriginUrl, mTitle, gsyVideoPlayer);
        }
        mIfCurrentIsFullscreen = true;
    }

    /**
     * ??????
     */
    protected void resolveNormalVideoShow(View oldF, ViewGroup vp, GSYVideoPlayer gsyVideoPlayer) {

        if (oldF != null && oldF.getParent() != null) {
            ViewGroup viewGroup = (ViewGroup) oldF.getParent();
            vp.removeView(viewGroup);
        }
        mCurrentState = GSYVideoManager.instance().getLastState();
        if (gsyVideoPlayer != null) {
            mCurrentState = gsyVideoPlayer.getCurrentState();
            mNetChanged = gsyVideoPlayer.mNetChanged;
            mNetSate = gsyVideoPlayer.mNetSate;
        }
        GSYVideoManager.instance().setListener(GSYVideoManager.instance().lastListener());
        GSYVideoManager.instance().setLastListener(null);
        setStateAndUi(mCurrentState);
        addTextureView();
        CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
        if (mVideoAllCallBack != null) {
            Debuger.printfError("onQuitFullscreen");
            mVideoAllCallBack.onQuitFullscreen(mOriginUrl, mTitle, this);
        }
        mIfCurrentIsFullscreen = false;
        if (mHideKey) {
            showNavKey(mContext, mSystemUiVisibility);
        }
        showSupportActionBar(mContext, mActionBar, mStatusBar);
        getFullscreenButton().setImageResource(getEnlargeImageRes());
    }

    /**
     * ??????window?????????????????????
     *
     * @param context
     * @param actionBar ?????????actionBar????????????????????????
     * @param statusBar ???????????????bar????????????????????????
     */
    @SuppressWarnings("ResourceType")
    public GSYBaseVideoPlayer startWindowFullscreen(final Context context, final boolean actionBar, final boolean statusBar) {

        mSystemUiVisibility = ((Activity) context).getWindow().getDecorView().getSystemUiVisibility();

        hideSupportActionBar(context, actionBar, statusBar);

        if (mHideKey) {
            hideNavKey(context);
        }

        this.mActionBar = actionBar;

        this.mStatusBar = statusBar;

        mListItemRect = new int[2];

        mListItemSize = new int[2];

        final ViewGroup vp = getViewGroup();

        removeVideo(vp, FULLSCREEN_ID);

        //?????????????????????
        pauseFullCoverLogic();

        if (mTextureViewContainer.getChildCount() > 0) {
            mTextureViewContainer.removeAllViews();
        }


        saveLocationStatus(context, statusBar, actionBar);

        boolean hadNewConstructor = true;

        try {
            GSYBaseVideoPlayer.this.getClass().getConstructor(Context.class, Boolean.class);
        } catch (Exception e) {
            hadNewConstructor = false;
        }

        try {
            //??????????????????????????????????????????
            Constructor<GSYBaseVideoPlayer> constructor;
            final GSYBaseVideoPlayer gsyVideoPlayer;
            if (!hadNewConstructor) {
                constructor = (Constructor<GSYBaseVideoPlayer>) GSYBaseVideoPlayer.this.getClass().getConstructor(Context.class);
                gsyVideoPlayer = constructor.newInstance(getActivityContext());
            } else {
                constructor = (Constructor<GSYBaseVideoPlayer>) GSYBaseVideoPlayer.this.getClass().getConstructor(Context.class, Boolean.class);
                gsyVideoPlayer = constructor.newInstance(getActivityContext(), true);
            }

            gsyVideoPlayer.setId(FULLSCREEN_ID);
            gsyVideoPlayer.setIfCurrentIsFullscreen(true);
            gsyVideoPlayer.setVideoAllCallBack(mVideoAllCallBack);
            gsyVideoPlayer.setLooping(isLooping());
            gsyVideoPlayer.setSpeed(getSpeed());
            gsyVideoPlayer.setIsTouchWigetFull(mIsTouchWigetFull);
            final LayoutParams lpParent = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            final FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.setBackgroundColor(Color.BLACK);

            if (mShowFullAnimation) {
                LayoutParams lp = new LayoutParams(getWidth(), getHeight());
                lp.setMargins(mListItemRect[0], mListItemRect[1], 0, 0);
                frameLayout.addView(gsyVideoPlayer, lp);
                vp.addView(frameLayout, lpParent);
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        TransitionManager.beginDelayedTransition(vp);
                        resolveFullVideoShow(context, gsyVideoPlayer, frameLayout);
                    }
                }, 300);
            } else {
                LayoutParams lp = new LayoutParams(getWidth(), getHeight());
                frameLayout.addView(gsyVideoPlayer, lp);
                vp.addView(frameLayout, lpParent);
                gsyVideoPlayer.setVisibility(INVISIBLE);
                frameLayout.setVisibility(INVISIBLE);
                resolveFullVideoShow(context, gsyVideoPlayer, frameLayout);
            }
            gsyVideoPlayer.mHadPlay = mHadPlay;
            gsyVideoPlayer.mCacheFile = mCacheFile;
            gsyVideoPlayer.mFullPauseBitmap = mFullPauseBitmap;
            gsyVideoPlayer.mNeedShowWifiTip = mNeedShowWifiTip;
            gsyVideoPlayer.mShrinkImageRes = mShrinkImageRes;
            gsyVideoPlayer.mEnlargeImageRes = mEnlargeImageRes;
            gsyVideoPlayer.mRotate = mRotate;
            gsyVideoPlayer.mShowPauseCover = mShowPauseCover;
            gsyVideoPlayer.mSeekRatio = mSeekRatio;
            gsyVideoPlayer.mNetChanged = mNetChanged;
            gsyVideoPlayer.mNetSate = mNetSate;
            gsyVideoPlayer.mRotateWithSystem = mRotateWithSystem;
            gsyVideoPlayer.setUp(mOriginUrl, mCache, mCachePath, mMapHeadData, mTitle);
            gsyVideoPlayer.setStateAndUi(mCurrentState);
            gsyVideoPlayer.addTextureView();

            gsyVideoPlayer.getFullscreenButton().setImageResource(getShrinkImageRes());
            gsyVideoPlayer.getFullscreenButton().setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearFullscreenLayout();
                }
            });

            gsyVideoPlayer.getBackButton().setVisibility(VISIBLE);
            gsyVideoPlayer.getBackButton().setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    clearFullscreenLayout();
                }
            });

            GSYVideoManager.instance().setLastListener(this);
            GSYVideoManager.instance().setListener(gsyVideoPlayer);
            return gsyVideoPlayer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }


    /**
     * ??????window?????????????????????
     */
    @SuppressWarnings("ResourceType")
    public void clearFullscreenLayout() {
        mIfCurrentIsFullscreen = false;
        int delay = 0;
        if (mOrientationUtils != null) {
            delay = mOrientationUtils.backToProtVideo();
            mOrientationUtils.setEnable(false);
            if (mOrientationUtils != null) {
                mOrientationUtils.releaseListener();
                mOrientationUtils = null;
            }
        }


        final ViewGroup vp = getViewGroup();
        final View oldF = vp.findViewById(FULLSCREEN_ID);
        if (oldF != null) {
            //??????fix bug#265?????????????????????????????????????????????
            GSYVideoPlayer gsyVideoPlayer = (GSYVideoPlayer) oldF;
            gsyVideoPlayer.mIfCurrentIsFullscreen = false;
        }

        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                backToNormal();
            }
        }, delay);

    }

    /**
     * ??????????????????
     */
    @SuppressWarnings("ResourceType")
    private void backToNormal() {

        final ViewGroup vp = getViewGroup();

        final View oldF = vp.findViewById(FULLSCREEN_ID);
        final GSYVideoPlayer gsyVideoPlayer;
        if (oldF != null) {
            gsyVideoPlayer = (GSYVideoPlayer) oldF;
            //???????????????
            pauseFullBackCoverLogic(gsyVideoPlayer);
            if (mShowFullAnimation) {
                TransitionManager.beginDelayedTransition(vp);

                LayoutParams lp = (LayoutParams) gsyVideoPlayer.getLayoutParams();
                lp.setMargins(mListItemRect[0], mListItemRect[1], 0, 0);
                lp.width = mListItemSize[0];
                lp.height = mListItemSize[1];
                //????????????????????????????????????????????????
                lp.gravity = Gravity.NO_GRAVITY;
                gsyVideoPlayer.setLayoutParams(lp);

                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        resolveNormalVideoShow(oldF, vp, gsyVideoPlayer);
                    }
                }, 400);
            } else {
                resolveNormalVideoShow(oldF, vp, gsyVideoPlayer);
            }

        } else {
            resolveNormalVideoShow(null, vp, null);
        }
    }

    /**
     * ?????????????????????????????????????????????
     */
    private void pauseFullCoverLogic() {
        if (mCurrentState == GSYVideoPlayer.CURRENT_STATE_PAUSE && mTextureView != null
                && (mFullPauseBitmap == null || mFullPauseBitmap.isRecycled()) && mShowPauseCover) {
            try {
                mFullPauseBitmap = mTextureView.getBitmap(mTextureView.getSizeW(), mTextureView.getSizeH());
            } catch (Exception e) {
                e.printStackTrace();
                mFullPauseBitmap = null;
            }
        }
    }

    /**
     * ???????????????????????????????????????????????????
     */
    private void pauseFullBackCoverLogic(GSYBaseVideoPlayer gsyVideoPlayer) {
        //?????????????????????
        if (gsyVideoPlayer.mCurrentState == GSYVideoPlayer.CURRENT_STATE_PAUSE
                && gsyVideoPlayer.mTextureView != null && mShowPauseCover) {
            //????????????????????????????????????????????????????????????
            if (gsyVideoPlayer.mFullPauseBitmap != null
                    && !gsyVideoPlayer.mFullPauseBitmap.isRecycled() && mShowPauseCover) {
                mFullPauseBitmap = gsyVideoPlayer.mFullPauseBitmap;
            } else if (mShowPauseCover) {
                //???????????????????????????????????????????????????????????????????????????
                try {
                    mFullPauseBitmap = mTextureView.getBitmap(mTextureView.getSizeW(), mTextureView.getSizeH());
                } catch (Exception e) {
                    e.printStackTrace();
                    mFullPauseBitmap = null;
                }
            }
        }
    }


    /**
     * ???????????????
     */
    @SuppressWarnings("ResourceType")
    public GSYBaseVideoPlayer showSmallVideo(Point size, final boolean actionBar, final boolean statusBar) {

        final ViewGroup vp = getViewGroup();

        removeVideo(vp, SMALL_ID);

        if (mTextureViewContainer.getChildCount() > 0) {
            mTextureViewContainer.removeAllViews();
        }

        try {
            Constructor<GSYBaseVideoPlayer> constructor = (Constructor<GSYBaseVideoPlayer>) GSYBaseVideoPlayer.this.getClass().getConstructor(Context.class);
            GSYBaseVideoPlayer gsyVideoPlayer = constructor.newInstance(getActivityContext());
            gsyVideoPlayer.setId(SMALL_ID);

            LayoutParams lpParent = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            FrameLayout frameLayout = new FrameLayout(mContext);

            LayoutParams lp = new LayoutParams(size.x, size.y);
            int marginLeft = CommonUtil.getScreenWidth(mContext) - size.x;
            int marginTop = CommonUtil.getScreenHeight(mContext) - size.y;

            if (actionBar) {
                marginTop = marginTop - getActionBarHeight((Activity) mContext);
            }

            if (statusBar) {
                marginTop = marginTop - getStatusBarHeight(mContext);
            }

            lp.setMargins(marginLeft, marginTop, 0, 0);
            frameLayout.addView(gsyVideoPlayer, lp);

            vp.addView(frameLayout, lpParent);
            gsyVideoPlayer.mHadPlay = mHadPlay;
            gsyVideoPlayer.mNetChanged = mNetChanged;
            gsyVideoPlayer.mNetSate = mNetSate;
            gsyVideoPlayer.setUp(mOriginUrl, mCache, mCachePath, mMapHeadData, mTitle);
            gsyVideoPlayer.setStateAndUi(mCurrentState);
            gsyVideoPlayer.addTextureView();
            //?????????????????????????????????
            gsyVideoPlayer.onClickUiToggle();
            gsyVideoPlayer.setVideoAllCallBack(mVideoAllCallBack);
            gsyVideoPlayer.setLooping(isLooping());
            gsyVideoPlayer.setSpeed(getSpeed());
            gsyVideoPlayer.setSmallVideoTextureView(new SmallVideoTouch(gsyVideoPlayer, marginLeft, marginTop));

            GSYVideoManager.instance().setLastListener(this);
            GSYVideoManager.instance().setListener(gsyVideoPlayer);

            if (mVideoAllCallBack != null) {
                Debuger.printfError("onEnterSmallWidget");
                mVideoAllCallBack.onEnterSmallWidget(mOriginUrl, mTitle, gsyVideoPlayer);
            }

            return gsyVideoPlayer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * ???????????????
     */
    @SuppressWarnings("ResourceType")
    public void hideSmallVideo() {
        final ViewGroup vp = getViewGroup();
        GSYVideoPlayer gsyVideoPlayer = (GSYVideoPlayer) vp.findViewById(SMALL_ID);
        removeVideo(vp, SMALL_ID);
        mCurrentState = GSYVideoManager.instance().getLastState();
        if (gsyVideoPlayer != null) {
            mCurrentState = gsyVideoPlayer.getCurrentState();
            mNetChanged = gsyVideoPlayer.mNetChanged;
            mNetSate = gsyVideoPlayer.mNetSate;
        }
        GSYVideoManager.instance().setListener(GSYVideoManager.instance().lastListener());
        GSYVideoManager.instance().setLastListener(null);
        setStateAndUi(mCurrentState);
        addTextureView();
        CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
        if (mVideoAllCallBack != null) {
            Debuger.printfLog("onQuitSmallWidget");
            mVideoAllCallBack.onQuitSmallWidget(mOriginUrl, mTitle, this);
        }
    }


    /**
     * ????????????URL
     *
     * @param url
     * @param cacheWithPlay ?????????????????????
     * @param title
     * @return
     */
    public abstract boolean setUp(String url, boolean cacheWithPlay, File cachePath, String title);

    /**
     * ????????????URL
     *
     * @param url
     * @param cacheWithPlay ?????????????????????
     * @param mapHeadData
     * @param title
     * @return
     */

    public abstract boolean setUp(String url, boolean cacheWithPlay, File cachePath, Map<String, String> mapHeadData, String title);

    /**
     * ????????????????????????
     *
     * @param state
     */
    protected abstract void setStateAndUi(int state);

    /**
     * ???????????????view
     */
    protected abstract void addTextureView();

    /**
     * ?????????
     **/
    protected abstract void setSmallVideoTextureView(OnTouchListener onTouchListener);


    protected abstract void onClickUiToggle();

    /**
     * ??????????????????
     */
    public abstract ImageView getFullscreenButton();

    /**
     * ??????????????????
     */
    public abstract ImageView getBackButton();


    public boolean isIfCurrentIsFullscreen() {
        return mIfCurrentIsFullscreen;
    }

    public void setIfCurrentIsFullscreen(boolean ifCurrentIsFullscreen) {
        this.mIfCurrentIsFullscreen = ifCurrentIsFullscreen;
    }


    public boolean isShowFullAnimation() {
        return mShowFullAnimation;
    }

    /**
     * ????????????
     *
     * @param showFullAnimation ??????????????????????????????
     */
    public void setShowFullAnimation(boolean showFullAnimation) {
        this.mShowFullAnimation = showFullAnimation;
    }


    public boolean isLooping() {
        return mLooping;
    }

    /**
     * ????????????
     */
    public void setLooping(boolean looping) {
        this.mLooping = looping;
    }


    /**
     * ??????????????????????????????
     *
     * @param mVideoAllCallBack
     */
    public void setVideoAllCallBack(VideoAllCallBack mVideoAllCallBack) {
        this.mVideoAllCallBack = mVideoAllCallBack;
    }


    public boolean isRotateViewAuto() {
        return mRotateViewAuto;
    }

    /**
     * ????????????????????????
     */
    public void setRotateViewAuto(boolean rotateViewAuto) {
        this.mRotateViewAuto = rotateViewAuto;
        if (mOrientationUtils != null) {
            mOrientationUtils.setEnable(rotateViewAuto);
        }
    }

    public boolean isLockLand() {
        return mLockLand;
    }

    /**
     * ?????????????????????????????????false??????????????????setRotateViewAuto??????
     */
    public void setLockLand(boolean lockLand) {
        this.mLockLand = lockLand;
    }


    public float getSpeed() {
        return mSpeed;
    }

    /**
     * ????????????
     */
    public void setSpeed(float speed) {
        setSpeed(speed, false);
    }

    /**
     * ????????????
     *
     * @param speed      ??????
     * @param soundTouch ?????????6.0????????????????????????
     */
    public void setSpeed(float speed, boolean soundTouch) {
        this.mSpeed = speed;
        if (GSYVideoManager.instance().getMediaPlayer() != null
                && GSYVideoManager.instance().getMediaPlayer() instanceof IjkMediaPlayer) {
            if (speed > 0) {
                ((IjkMediaPlayer) GSYVideoManager.instance().getMediaPlayer()).setSpeed(speed);

                if (soundTouch) {
                    VideoOptionModel videoOptionModel =
                            new VideoOptionModel(IjkMediaPlayer.OPT_CATEGORY_PLAYER, "soundtouch", 1);
                    List<VideoOptionModel> list = GSYVideoManager.instance().getOptionModelList();
                    if (list != null) {
                        list.add(videoOptionModel);
                    } else {
                        list = new ArrayList<>();
                        list.add(videoOptionModel);
                    }
                    GSYVideoManager.instance().setOptionModelList(list);
                }
            }
        }
    }

    public boolean isHideKey() {
        return mHideKey;
    }

    /**
     * ???????????????????????????????????????
     */
    public void setHideKey(boolean hideKey) {
        this.mHideKey = hideKey;
    }

    public boolean isNeedShowWifiTip() {
        return mNeedShowWifiTip;
    }


    public boolean isTouchWiget() {
        return mIsTouchWiget;
    }

    /**
     * ????????????????????????????????????????????????
     * ??????true
     */
    public void setIsTouchWiget(boolean isTouchWiget) {
        this.mIsTouchWiget = isTouchWiget;
    }

    public boolean isTouchWigetFull() {
        return mIsTouchWigetFull;
    }

    /**
     * ??????????????????????????????????????????????????????
     * ?????? true
     */
    public void setIsTouchWigetFull(boolean isTouchWigetFull) {
        this.mIsTouchWigetFull = isTouchWigetFull;
    }


    /**
     * ??????????????????????????????,??????true
     */
    public void setNeedShowWifiTip(boolean needShowWifiTip) {
        this.mNeedShowWifiTip = needShowWifiTip;
    }

    public int getEnlargeImageRes() {
        if (mShrinkImageRes == -1) {
            return R.drawable.video_enlarge;
        }
        return mEnlargeImageRes;
    }

    /**
     * ??????????????? ????????????????????? ???????????????
     * ?????????setUp????????????
     * ?????????????????????
     */
    public void setEnlargeImageRes(int mEnlargeImageRes) {
        this.mEnlargeImageRes = mEnlargeImageRes;
    }

    public int getShrinkImageRes() {
        if (mShrinkImageRes == -1) {
            return R.drawable.video_shrink;
        }
        return mShrinkImageRes;
    }

    /**
     * ??????????????? ?????????????????? ???????????????
     * ?????????setUp????????????
     * ?????????????????????
     */
    public void setShrinkImageRes(int mShrinkImageRes) {
        this.mShrinkImageRes = mShrinkImageRes;
    }


    public boolean isShowPauseCover() {
        return mShowPauseCover;
    }

    /**
     * ?????????????????????????????????cover??????
     * ??????????????????????????????????????????????????????????????????????????????????????????????????????????????????OOM
     * ??????????????????????????????????????????????????????????????????
     *
     * @param showPauseCover ??????true
     */
    public void setShowPauseCover(boolean showPauseCover) {
        this.mShowPauseCover = showPauseCover;
    }

    /**
     * ?????????????????????????????????
     *
     * @param seekRatio ??????????????????????????????1????????????????????????????????????seek??????
     */
    public void setSeekRatio(float seekRatio) {
        if (seekRatio < 0) {
            return;
        }
        this.mSeekRatio = seekRatio;
    }

    public float getSeekRatio() {
        return mSeekRatio;
    }


    public boolean isRotateWithSystem() {
        return mRotateWithSystem;
    }

    /**
     * ???????????????????????????false?????????????????????????????????????????????
     *
     * @param rotateWithSystem ??????true
     */
    public void setRotateWithSystem(boolean rotateWithSystem) {
        this.mRotateWithSystem = rotateWithSystem;
    }

}
