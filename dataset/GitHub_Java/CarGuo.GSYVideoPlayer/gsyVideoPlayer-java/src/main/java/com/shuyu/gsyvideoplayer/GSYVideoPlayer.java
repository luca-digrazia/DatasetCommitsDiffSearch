package com.shuyu.gsyvideoplayer;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Handler;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.InflateException;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.danikula.videocache.HttpProxyCacheServer;
import com.danikula.videocache.file.Md5FileNameGenerator;
import com.google.android.exoplayer.C;
import com.shuyu.gsyvideoplayer.utils.CommonUtil;
import com.shuyu.gsyvideoplayer.utils.Debuger;
import com.shuyu.gsyvideoplayer.utils.GSYVideoType;
import com.shuyu.gsyvideoplayer.utils.NetInfoModule;
import com.shuyu.gsyvideoplayer.utils.StorageUtils;
import com.shuyu.gsyvideoplayer.video.GSYBaseVideoPlayer;

import java.io.File;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkLibLoader;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

import static com.shuyu.gsyvideoplayer.utils.CommonUtil.getTextSpeed;
import static com.shuyu.gsyvideoplayer.utils.CommonUtil.hideNavKey;


/**
 * Created by shuyu on 2016/11/11.
 */

public abstract class GSYVideoPlayer extends GSYBaseVideoPlayer implements View.OnClickListener, View.OnTouchListener, SeekBar.OnSeekBarChangeListener, TextureView.SurfaceTextureListener {

    public static final String TAG = "GSYVideoPlayer";


    public static final int CURRENT_STATE_NORMAL = 0; //??????
    public static final int CURRENT_STATE_PREPAREING = 1; //?????????
    public static final int CURRENT_STATE_PLAYING = 2; //?????????
    public static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3; //????????????
    public static final int CURRENT_STATE_PAUSE = 5; //??????
    public static final int CURRENT_STATE_AUTO_COMPLETE = 6; //??????????????????
    public static final int CURRENT_STATE_ERROR = 7; //????????????

    public static final int FULL_SCREEN_NORMAL_DELAY = 2000;

    protected static int mBackUpPlayingBufferState = -1;

    protected static boolean IF_FULLSCREEN_FROM_NORMAL = false;

    public static boolean IF_RELEASE_WHEN_ON_PAUSE = true;

    protected Timer updateProcessTimer;

    protected Surface mSurface;

    protected ProgressTimerTask mProgressTimerTask;

    protected AudioManager mAudioManager; //?????????????????????

    protected Handler mHandler = new Handler();

    protected String mPlayTag = ""; //?????????tag?????????????????????????????????url???????????????

    protected NetInfoModule mNetInfoModule;

    protected int mPlayPosition = -22; //?????????tag?????????????????????????????????url???????????????

    protected float mDownX;//?????????X

    protected float mDownY; //?????????Y

    protected float mMoveY;

    protected float mBrightnessData = -1; //??????

    protected int mDownPosition; //?????????????????????

    protected int mGestureDownVolume; //???????????????????????????

    protected int mScreenWidth; //????????????

    protected int mScreenHeight; //????????????

    protected int mThreshold = 80; //???????????????

    protected int mSeekToInAdvance = -1; //// TODO: 2016/11/13 ????????????

    protected int mBuffterPoint;//????????????

    protected int mSeekTimePosition; //???????????????????????????

    protected int mSeekEndOffset; //?????????????????????????????????

    protected long mSeekOnStart = -1; //?????????????????????

    protected long mPauseTime; //????????????????????????

    protected long mCurrentPosition; //?????????????????????

    protected boolean mTouchingProgressBar = false;

    protected boolean mChangeVolume = false;//??????????????????

    protected boolean mChangePosition = false;//????????????????????????

    protected boolean mShowVKey = false; //????????????????????????

    protected boolean mBrightness = false;//??????????????????

    protected boolean mFirstTouch = false;//??????????????????


    /**
     * ??????UI
     */
    public abstract int getLayoutId();

    /**
     * ????????????
     */
    public abstract void startPlayLogic();

    /**
     * 1.5.0??????????????????????????????????????????????????????????????????
     */
    public GSYVideoPlayer(Context context, Boolean fullFlag) {
        super(context, fullFlag);
        init(context);
    }

    /**
     * ??????IjkMediaPlayer?????????????????????????????????IjkLibLoader?????????
     */
    public GSYVideoPlayer(Context context, IjkLibLoader ijkLibLoader) {
        super(context);
        GSYVideoManager.setIjkLibLoader(ijkLibLoader);
        init(context);
    }

    public GSYVideoPlayer(Context context) {
        super(context);
        init(context);
    }

    public GSYVideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    protected void init(Context context) {

        if (getActivityContext() != null) {
            this.mContext = getActivityContext();
        } else {
            this.mContext = context;
        }

        initInflate(mContext);

        mStartButton = findViewById(R.id.start);
        mSmallClose = findViewById(R.id.small_close);
        mBackButton = (ImageView) findViewById(R.id.back);
        mFullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        mProgressBar = (SeekBar) findViewById(R.id.progress);
        mCurrentTimeTextView = (TextView) findViewById(R.id.current);
        mTotalTimeTextView = (TextView) findViewById(R.id.total);
        mBottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        mTextureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
        mTopContainer = (ViewGroup) findViewById(R.id.layout_top);
        if (isInEditMode())
            return;
        mStartButton.setOnClickListener(this);
        mFullscreenButton.setOnClickListener(this);
        mProgressBar.setOnSeekBarChangeListener(this);
        mBottomContainer.setOnClickListener(this);
        mTextureViewContainer.setOnClickListener(this);
        mProgressBar.setOnTouchListener(this);

        mTextureViewContainer.setOnTouchListener(this);
        mFullscreenButton.setOnTouchListener(this);
        mScreenWidth = getActivityContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getActivityContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getActivityContext().getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        mSeekEndOffset = CommonUtil.dip2px(getActivityContext(), 50);
    }

    private void initInflate(Context context) {
        try {
            View.inflate(context, getLayoutId(), this);
        } catch (InflateException e) {
            if (e.toString().contains("GSYImageCover")) {
                Debuger.printfError("********************\n" +
                        "*****   ??????   *****" +
                        "********************\n" +
                        "*???????????????????????????????????????GSYImageCover\n" +
                        "****  Attention  ***\n" +
                        "*Please remove GSYImageCover from Layout in this Version\n" +
                        "********************\n");
                e.printStackTrace();
                throw new InflateException("???????????????????????????????????????GSYImageCover???please remove GSYImageCover from your layout");
            } else {
                e.printStackTrace();
            }
        }
    }

    /**
     * ???????????????so????????????????????????setUp????????????
     * ??????setUp????????????????????????GSYVideoManager
     */
    public void setIjkLibLoader(IjkLibLoader libLoader) {
        GSYVideoManager.setIjkLibLoader(libLoader);
    }

    /**
     * ????????????URL
     *
     * @param url           ??????url
     * @param cacheWithPlay ?????????????????????
     * @param title         title
     * @return
     */
    public boolean setUp(String url, boolean cacheWithPlay, String title) {
        return setUp(url, cacheWithPlay, ((File) null), title);
    }


    /**
     * ????????????URL
     *
     * @param url           ??????url
     * @param cacheWithPlay ?????????????????????
     * @param cachePath     ????????????????????????M3U8??????HLS???????????????false
     * @param mapHeadData   ????????????
     * @param title         title
     * @return
     */
    @Override
    public boolean setUp(String url, boolean cacheWithPlay, File cachePath, Map<String, String> mapHeadData, String title) {
        if (setUp(url, cacheWithPlay, cachePath, title)) {
            this.mMapHeadData.clear();
            if (mapHeadData != null)
                this.mMapHeadData.putAll(mapHeadData);
            return true;
        }
        return false;
    }

    /**
     * ????????????URL
     *
     * @param url           ??????url
     * @param cacheWithPlay ?????????????????????
     * @param cachePath     ????????????????????????M3U8??????HLS???????????????false
     * @param title         title
     * @return
     */
    @Override
    public boolean setUp(String url, boolean cacheWithPlay, File cachePath, String title) {
        mCache = cacheWithPlay;
        mCachePath = cachePath;
        mOriginUrl = url;
        if (isCurrentMediaListener() &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) < FULL_SCREEN_NORMAL_DELAY)
            return false;
        mCurrentState = CURRENT_STATE_NORMAL;
        if (cacheWithPlay && url.startsWith("http") && !url.contains("127.0.0.1") && !url.contains(".m3u8")) {
            HttpProxyCacheServer proxy = GSYVideoManager.getProxy(getActivityContext().getApplicationContext(), cachePath);
            //???????????????url?????????????????????mUrl???
            url = proxy.getProxyUrl(url);
            mCacheFile = (!url.startsWith("http"));
            //?????????????????????
            if (!mCacheFile && GSYVideoManager.instance() != null) {
                proxy.registerCacheListener(GSYVideoManager.instance(), mOriginUrl);
            }
        } else if (!cacheWithPlay && (!url.startsWith("http") && !url.startsWith("rtmp")
                && !url.startsWith("rtsp") && !url.contains(".m3u8"))) {
            mCacheFile = true;
        }
        this.mUrl = url;
        this.mTitle = title;
        setStateAndUi(CURRENT_STATE_NORMAL);
        return true;
    }

    /**
     * ????????????????????????
     *
     * @param state
     */
    protected void setStateAndUi(int state) {
        mCurrentState = state;
        switch (mCurrentState) {
            case CURRENT_STATE_NORMAL:
                if (isCurrentMediaListener()) {
                    cancelProgressTimer();
                    GSYVideoManager.instance().releaseMediaPlayer();
                    releasePauseCover();
                    mBuffterPoint = 0;
                }
                if (mAudioManager != null) {
                    mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
                }
                releaseNetWorkState();
                break;
            case CURRENT_STATE_PREPAREING:
                resetProgressAndTime();
                break;
            case CURRENT_STATE_PLAYING:
                startProgressTimer();
                break;
            case CURRENT_STATE_PAUSE:
                startProgressTimer();
                break;
            case CURRENT_STATE_ERROR:
                if (isCurrentMediaListener()) {
                    GSYVideoManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                mProgressBar.setProgress(100);
                mCurrentTimeTextView.setText(mTotalTimeTextView.getText());
                break;
        }
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (mHideKey && mIfCurrentIsFullscreen) {
            hideNavKey(mContext);
        }
        if (i == R.id.start) {
            if (TextUtils.isEmpty(mUrl)) {
                Toast.makeText(getActivityContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (mCurrentState == CURRENT_STATE_NORMAL || mCurrentState == CURRENT_STATE_ERROR) {
                if (!mUrl.startsWith("file") && !CommonUtil.isWifiConnected(getContext())
                        && mNeedShowWifiTip) {
                    showWifiDialog();
                    return;
                }
                startButtonLogic();
            } else if (mCurrentState == CURRENT_STATE_PLAYING) {
                GSYVideoManager.instance().getMediaPlayer().pause();
                setStateAndUi(CURRENT_STATE_PAUSE);
                if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                    if (mIfCurrentIsFullscreen) {
                        Debuger.printfLog("onClickStopFullscreen");
                        mVideoAllCallBack.onClickStopFullscreen(mOriginUrl, mTitle, GSYVideoPlayer.this);
                    } else {
                        Debuger.printfLog("onClickStop");
                        mVideoAllCallBack.onClickStop(mOriginUrl, mTitle, GSYVideoPlayer.this);
                    }
                }
            } else if (mCurrentState == CURRENT_STATE_PAUSE) {
                if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                    if (mIfCurrentIsFullscreen) {
                        Debuger.printfLog("onClickResumeFullscreen");
                        mVideoAllCallBack.onClickResumeFullscreen(mOriginUrl, mTitle, GSYVideoPlayer.this);
                    } else {
                        Debuger.printfLog("onClickResume");
                        mVideoAllCallBack.onClickResume(mOriginUrl, mTitle, GSYVideoPlayer.this);
                    }
                }
                GSYVideoManager.instance().getMediaPlayer().start();
                setStateAndUi(CURRENT_STATE_PLAYING);
            } else if (mCurrentState == CURRENT_STATE_AUTO_COMPLETE) {
                startButtonLogic();
            }
        } else if (i == R.id.surface_container && mCurrentState == CURRENT_STATE_ERROR) {
            if (mVideoAllCallBack != null) {
                Debuger.printfLog("onClickStartError");
                mVideoAllCallBack.onClickStartError(mOriginUrl, mTitle, GSYVideoPlayer.this);
            }
            prepareVideo();
        }
    }

    protected void showWifiDialog() {
    }

    /**
     * ?????????????????????
     */
    private void startButtonLogic() {
        if (mVideoAllCallBack != null && mCurrentState == CURRENT_STATE_NORMAL) {
            Debuger.printfLog("onClickStartIcon");
            mVideoAllCallBack.onClickStartIcon(mOriginUrl, mTitle, GSYVideoPlayer.this);
        } else if (mVideoAllCallBack != null) {
            Debuger.printfLog("onClickStartError");
            mVideoAllCallBack.onClickStartError(mOriginUrl, mTitle, GSYVideoPlayer.this);
        }
        prepareVideo();
    }

    /**
     * ????????????????????????
     */
    protected void prepareVideo() {
        if (GSYVideoManager.instance().listener() != null) {
            GSYVideoManager.instance().listener().onCompletion();
        }
        GSYVideoManager.instance().setListener(this);
        GSYVideoManager.instance().setPlayTag(mPlayTag);
        GSYVideoManager.instance().setPlayPosition(mPlayPosition);
        addTextureView();
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        ((Activity) getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        mBackUpPlayingBufferState = -1;
        GSYVideoManager.instance().prepare(mUrl, mMapHeadData, mLooping, mSpeed);
        setStateAndUi(CURRENT_STATE_PREPAREING);
    }

    /**
     * ????????????????????????????????????????????????
     */
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            releaseAllVideos();
                        }
                    });
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (GSYVideoManager.instance().getMediaPlayer().isPlaying()) {
                        GSYVideoManager.instance().getMediaPlayer().pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };


    /**
     * ??????
     */
    public void onVideoReset() {
        setStateAndUi(CURRENT_STATE_NORMAL);
    }

    /**
     * ????????????
     */
    @Override
    public void onVideoPause() {
        if (GSYVideoManager.instance().getMediaPlayer().isPlaying()) {
            setStateAndUi(CURRENT_STATE_PAUSE);
            mPauseTime = System.currentTimeMillis();
            mCurrentPosition = GSYVideoManager.instance().getMediaPlayer().getCurrentPosition();
            if (GSYVideoManager.instance().getMediaPlayer() != null)
                GSYVideoManager.instance().getMediaPlayer().pause();
        }
    }

    /**
     * ??????????????????
     */
    @Override
    public void onVideoResume() {
        mPauseTime = 0;
        if (mCurrentState == CURRENT_STATE_PAUSE) {
            if (mCurrentPosition > 0 && GSYVideoManager.instance().getMediaPlayer() != null) {
                setStateAndUi(CURRENT_STATE_PLAYING);
                GSYVideoManager.instance().getMediaPlayer().seekTo(mCurrentPosition);
                GSYVideoManager.instance().getMediaPlayer().start();
            }
        }
    }

    /**
     * ???????????????view
     */
    protected void addTextureView() {
        if (mTextureViewContainer.getChildCount() > 0) {
            mTextureViewContainer.removeAllViews();
        }
        mTextureView = null;
        mTextureView = new GSYTextureView(getContext());
        mTextureView.setSurfaceTextureListener(this);
        mTextureView.setRotation(mRotate);

        int params = getTextureParams();

        if (mTextureViewContainer instanceof RelativeLayout) {
            RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(params, params);
            layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            mTextureViewContainer.addView(mTextureView, layoutParams);
        } else if (mTextureViewContainer instanceof FrameLayout) {
            LayoutParams layoutParams = new LayoutParams(params, params);
            layoutParams.gravity = Gravity.CENTER;
            mTextureViewContainer.addView(mTextureView, layoutParams);
        }
    }

    protected int getTextureParams() {
        boolean typeChanged = (GSYVideoType.getShowType() != GSYVideoType.SCREEN_TYPE_DEFAULT);
        return (typeChanged) ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
    }

    /**
     * ??????TextureView?????????????????????
     */
    protected void changeTextureViewShowType() {
        int params = getTextureParams();
        ViewGroup.LayoutParams layoutParams = mTextureView.getLayoutParams();
        layoutParams.width = params;
        layoutParams.height = params;
        mTextureView.setLayoutParams(layoutParams);
    }

    /**
     * ?????????
     **/
    @Override
    protected void setSmallVideoTextureView(OnTouchListener onTouchListener) {
        mTextureViewContainer.setOnTouchListener(onTouchListener);
        mProgressBar.setOnTouchListener(null);
        mFullscreenButton.setOnTouchListener(null);
        mFullscreenButton.setVisibility(INVISIBLE);
        mProgressBar.setVisibility(INVISIBLE);
        mCurrentTimeTextView.setVisibility(INVISIBLE);
        mTotalTimeTextView.setVisibility(INVISIBLE);
        mTextureViewContainer.setOnClickListener(null);
        mSmallClose.setVisibility(VISIBLE);
        mSmallClose.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                hideSmallVideo();
                releaseAllVideos();
            }
        });
    }

    /**
     * ??????????????????
     */
    public void setRotationView(int rotate) {
        this.mRotate = rotate;
        mTextureView.setRotation(rotate);
    }

    public void refreshVideo() {
        if (mTextureView != null) {
            mTextureView.requestLayout();
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {

        mSurface = new Surface(surface);


        //?????????????????????????????????
        showPauseCover();

        GSYVideoManager.instance().setDisplay(mSurface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        GSYVideoManager.instance().setDisplay(null);
        surface.release();
        cancelProgressTimer();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        //?????????????????????????????????
        releasePauseCover();
    }

    /**
     * ????????????????????????
     */
    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.fullscreen) {
            return false;
        }
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mTouchingProgressBar = true;
                    mDownX = x;
                    mDownY = y;
                    mMoveY = 0;
                    mChangeVolume = false;
                    mChangePosition = false;
                    mShowVKey = false;
                    mBrightness = false;
                    mFirstTouch = true;

                    break;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);

                    if ((mIfCurrentIsFullscreen && mIsTouchWigetFull)
                            || (mIsTouchWiget && !mIfCurrentIsFullscreen)) {
                        if (!mChangePosition && !mChangeVolume && !mBrightness) {
                            if (absDeltaX > mThreshold || absDeltaY > mThreshold) {
                                cancelProgressTimer();
                                if (absDeltaX >= mThreshold) {
                                    //????????????????????????
                                    int screenWidth = CommonUtil.getScreenWidth(getContext());
                                    if (Math.abs(screenWidth - mDownX) > mSeekEndOffset) {
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    } else {
                                        mShowVKey = true;
                                    }
                                } else {
                                    int screenHeight = CommonUtil.getScreenHeight(getContext());
                                    boolean noEnd = Math.abs(screenHeight - mDownY) > mSeekEndOffset;
                                    if (mFirstTouch) {
                                        mBrightness = (mDownX < mScreenWidth * 0.5f) && noEnd;
                                        mFirstTouch = false;
                                    }
                                    if (!mBrightness) {
                                        mChangeVolume = noEnd;
                                        mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                    }
                                    mShowVKey = !noEnd;
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + (deltaX * totalTimeDuration / mScreenWidth) / mSeekRatio);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = CommonUtil.stringForTime(mSeekTimePosition);
                        String totalTime = CommonUtil.stringForTime(totalTimeDuration);
                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    } else if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(-deltaY, volumePercent);
                    } else if (!mChangePosition && mBrightness) {
                        if (Math.abs(deltaY) > mThreshold) {
                            float percent = (-deltaY / mScreenHeight);
                            onBrightnessSlide(percent);
                            mDownY = y;
                        }
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    dismissBrightnessDialog();
                    if (mChangePosition && GSYVideoManager.instance().getMediaPlayer() != null && (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE)) {
                        GSYVideoManager.instance().getMediaPlayer().seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        mProgressBar.setProgress(progress);
                        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                            Debuger.printfLog("onTouchScreenSeekPosition");
                            mVideoAllCallBack.onTouchScreenSeekPosition(mOriginUrl, mTitle, GSYVideoPlayer.this);
                        }
                    } else if (mBrightness) {
                        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                            Debuger.printfLog("onTouchScreenSeekLight");
                            mVideoAllCallBack.onTouchScreenSeekLight(mOriginUrl, mTitle, GSYVideoPlayer.this);
                        }
                    } else if (mChangeVolume) {
                        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
                            Debuger.printfLog("onTouchScreenSeekVolume");
                            mVideoAllCallBack.onTouchScreenSeekVolume(mOriginUrl, mTitle, GSYVideoPlayer.this);
                        }
                    }
                    startProgressTimer();
                    //?????????????????????????????????????????????????????????
                    if (mHideKey && mShowVKey) {
                        return true;
                    }
                    break;
            }
        } else if (id == R.id.progress) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    cancelProgressTimer();
                    ViewParent vpdown = getParent();
                    while (vpdown != null) {
                        vpdown.requestDisallowInterceptTouchEvent(true);
                        vpdown = vpdown.getParent();
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    startProgressTimer();
                    ViewParent vpup = getParent();
                    while (vpup != null) {
                        vpup.requestDisallowInterceptTouchEvent(false);
                        vpup = vpup.getParent();
                    }
                    mBrightnessData = -1f;
                    break;
            }
        }

        return false;
    }

    /**
     * ???????????????????????????bitmap
     */
    protected void showPauseCover() {
        if (mCurrentState == CURRENT_STATE_PAUSE && mFullPauseBitmap != null
                && !mFullPauseBitmap.isRecycled() && mShowPauseCover
                && mSurface != null && mSurface.isValid()) {
            RectF rectF = new RectF(0, 0, mTextureView.getWidth(), mTextureView.getHeight());
            Canvas canvas = mSurface.lockCanvas(new Rect(0, 0, mTextureView.getWidth(), mTextureView.getHeight()));
            if (canvas != null) {
                canvas.drawBitmap(mFullPauseBitmap, null, rectF, null);
                mSurface.unlockCanvasAndPost(canvas);
            }
        }

    }

    /**
     * ???????????????????????????bitmap
     */
    protected void releasePauseCover() {
        try {
            if (mCurrentState != CURRENT_STATE_PAUSE && mFullPauseBitmap != null
                    && !mFullPauseBitmap.isRecycled() && mShowPauseCover) {
                mFullPauseBitmap.recycle();
                mFullPauseBitmap = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    protected void showProgressDialog(float deltaX,
                                      String seekTime, int seekTimePosition,
                                      String totalTime, int totalTimeDuration) {
    }

    protected void dismissProgressDialog() {

    }

    protected void showVolumeDialog(float deltaY, int volumePercent) {

    }

    protected void dismissVolumeDialog() {

    }

    protected void showBrightnessDialog(float percent) {

    }

    protected void dismissBrightnessDialog() {

    }

    protected void onClickUiToggle() {

    }


    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    /***
     * ???????????????
     */
    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
            if (isIfCurrentIsFullscreen()) {
                Debuger.printfLog("onClickSeekbarFullscreen");
                mVideoAllCallBack.onClickSeekbarFullscreen(mOriginUrl, mTitle, GSYVideoPlayer.this);
            } else {
                Debuger.printfLog("onClickSeekbar");
                mVideoAllCallBack.onClickSeekbar(mOriginUrl, mTitle, GSYVideoPlayer.this);
            }
        }
        if (GSYVideoManager.instance().getMediaPlayer() != null && mHadPlay) {
            try {
                int time = seekBar.getProgress() * getDuration() / 100;
                GSYVideoManager.instance().getMediaPlayer().seekTo(time);
            } catch (Exception e) {
                Debuger.printfWarning(e.toString());
            }
        }
    }

    @Override
    public void onPrepared() {
        if (mCurrentState != CURRENT_STATE_PREPAREING) return;

        if (GSYVideoManager.instance().getMediaPlayer() != null) {
            GSYVideoManager.instance().getMediaPlayer().start();
        }

        if (GSYVideoManager.instance().getMediaPlayer() != null && mSeekToInAdvance != -1) {
            GSYVideoManager.instance().getMediaPlayer().seekTo(mSeekToInAdvance);
            mSeekToInAdvance = -1;
        }

        startProgressTimer();

        setStateAndUi(CURRENT_STATE_PLAYING);

        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
            Debuger.printfLog("onPrepared");
            mVideoAllCallBack.onPrepared(mOriginUrl, mTitle, GSYVideoPlayer.this);
        }

        if (GSYVideoManager.instance().getMediaPlayer() != null && mSeekOnStart > 0) {
            GSYVideoManager.instance().getMediaPlayer().seekTo(mSeekOnStart);
            mSeekOnStart = 0;
        }
        createNetWorkState();
        listenerNetWorkState();
        mHadPlay = true;
    }

    @Override
    public void onAutoCompletion() {
        if (mVideoAllCallBack != null && isCurrentMediaListener()) {
            Debuger.printfLog("onAutoComplete");
            mVideoAllCallBack.onAutoComplete(mOriginUrl, mTitle, GSYVideoPlayer.this);
        }
        setStateAndUi(CURRENT_STATE_AUTO_COMPLETE);
        if (mTextureViewContainer.getChildCount() > 0) {
            mTextureViewContainer.removeAllViews();
        }

        if (IF_FULLSCREEN_FROM_NORMAL) {
            IF_FULLSCREEN_FROM_NORMAL = false;
            if (GSYVideoManager.instance().lastListener() != null) {
                GSYVideoManager.instance().lastListener().onAutoCompletion();
            }
        }
        if (!mIfCurrentIsFullscreen)
            GSYVideoManager.instance().setLastListener(null);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        releaseNetWorkState();

    }

    @Override
    public void onCompletion() {
        //make me normal first
        setStateAndUi(CURRENT_STATE_NORMAL);
        if (mTextureViewContainer.getChildCount() > 0) {
            mTextureViewContainer.removeAllViews();
        }

        if (IF_FULLSCREEN_FROM_NORMAL) {//?????????????????????????????????????????????????????????????????????
            IF_FULLSCREEN_FROM_NORMAL = false;
            if (GSYVideoManager.instance().lastListener() != null) {
                GSYVideoManager.instance().lastListener().onCompletion();//???????????????onAutoCompletion
            }
        }
        if (!mIfCurrentIsFullscreen) {
            GSYVideoManager.instance().setListener(null);
            GSYVideoManager.instance().setLastListener(null);
        }
        GSYVideoManager.instance().setCurrentVideoHeight(0);
        GSYVideoManager.instance().setCurrentVideoWidth(0);

        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        ((Activity) getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        releaseNetWorkState();

    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (mCurrentState != CURRENT_STATE_NORMAL && mCurrentState != CURRENT_STATE_PREPAREING) {
            if (percent != 0) {
                setTextAndProgress(percent);
                mBuffterPoint = percent;
                Debuger.printfLog("Net speed: " + getNetSpeedText() + " percent " + percent);
            }
            //??????????????????
            if (mLooping && mHadPlay && percent == 0 && mProgressBar.getProgress() >= (mProgressBar.getMax() - 1)) {
                loopSetProgressAndTime();
            }
        }
    }

    @Override
    public void onSeekComplete() {

    }

    @Override
    public void onError(int what, int extra) {

        if (mNetChanged) {
            mNetChanged = false;
            netWorkErrorLogic();
            if (mVideoAllCallBack != null) {
                mVideoAllCallBack.onPlayError(mOriginUrl, mTitle, GSYVideoPlayer.this);
            }
            return;
        }

        if (what != 38 && what != -38) {
            setStateAndUi(CURRENT_STATE_ERROR);
            deleteCacheFileWhenError();
            if (mVideoAllCallBack != null) {
                mVideoAllCallBack.onPlayError(mOriginUrl, mTitle, GSYVideoPlayer.this);
            }
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        if (what == MediaPlayer.MEDIA_INFO_BUFFERING_START) {
            mBackUpPlayingBufferState = mCurrentState;
            //?????????onPrepared??????????????????buffering???????????????loading
            if (mHadPlay && mCurrentState != CURRENT_STATE_PREPAREING && mCurrentState > 0)
                setStateAndUi(CURRENT_STATE_PLAYING_BUFFERING_START);

        } else if (what == MediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (mBackUpPlayingBufferState != -1) {

                if (mHadPlay && mCurrentState != CURRENT_STATE_PREPAREING && mCurrentState > 0)
                    setStateAndUi(mBackUpPlayingBufferState);

                mBackUpPlayingBufferState = -1;
            }
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            mRotate = extra;
            if (mTextureView != null)
                mTextureView.setRotation(mRotate);
        }
    }

    @Override
    public void onVideoSizeChanged() {
        int mVideoWidth = GSYVideoManager.instance().getCurrentVideoWidth();
        int mVideoHeight = GSYVideoManager.instance().getCurrentVideoHeight();
        if (mVideoWidth != 0 && mVideoHeight != 0) {
            mTextureView.requestLayout();
        }
    }

    @Override
    public void onBackFullscreen() {

    }

    /**
     * ?????????????????????????????????
     *
     * @param context
     * @param actionBar ?????????actionBar????????????????????????
     * @param statusBar ???????????????bar????????????????????????
     * @return
     */
    @Override
    public GSYBaseVideoPlayer startWindowFullscreen(Context context, boolean actionBar, boolean statusBar) {
        GSYBaseVideoPlayer gsyBaseVideoPlayer = super.startWindowFullscreen(context, actionBar, statusBar);
        GSYVideoPlayer gsyVideoPlayer = (GSYVideoPlayer) gsyBaseVideoPlayer;
        gsyVideoPlayer.createNetWorkState();
        gsyVideoPlayer.listenerNetWorkState();
        return gsyBaseVideoPlayer;
    }

    /**
     * ??????????????????????????????
     *
     * @param oldF
     * @param vp
     * @param gsyVideoPlayer
     */
    @Override
    protected void resolveNormalVideoShow(View oldF, ViewGroup vp, GSYVideoPlayer gsyVideoPlayer) {
        if (gsyVideoPlayer != null)
            gsyVideoPlayer.releaseNetWorkState();
        super.resolveNormalVideoShow(oldF, vp, gsyVideoPlayer);
    }

    /**
     * ???????????????????????????????????????
     */
    protected void netWorkErrorLogic() {
        final long currentPosition = getCurrentPositionWhenPlaying();
        Debuger.printfError("******* Net State Changed. renew player to connect *******" + currentPosition);
        GSYVideoManager.instance().releaseMediaPlayer();
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                setSeekOnStart(currentPosition);
                startPlayLogic();
            }
        }, 500);
    }

    /**
     * ??????????????????
     */
    public void clearCurrentCache() {
        //????????????true????????????????????????
        if (mCacheFile && mCache) {
            //?????????????????????
            Debuger.printfError(" mCacheFile Local Error " + mUrl);
            //???????????????????????????????????????
            CommonUtil.deleteFile(mUrl.replace("file://", ""));
            mUrl = mOriginUrl;
        } else if (mUrl.contains("127.0.0.1")) {
            //????????????????????????????????????
            Md5FileNameGenerator md5FileNameGenerator = new Md5FileNameGenerator();
            String name = md5FileNameGenerator.generate(mOriginUrl);
            if (mCachePath != null) {
                String path = mCachePath.getAbsolutePath() + File.separator + name + ".download";
                CommonUtil.deleteFile(path);
            } else {
                String path = StorageUtils.getIndividualCacheDirectory
                        (getActivityContext().getApplicationContext()).getAbsolutePath()
                        + File.separator + name + ".download";
                CommonUtil.deleteFile(path);
            }
        }

    }


    /**
     * ??????????????????????????????????????????
     */
    private void deleteCacheFileWhenError() {
        clearCurrentCache();
        Debuger.printfError("Link Or mCache Error, Please Try Again" + mUrl);
        mUrl = mOriginUrl;
    }

    protected void startProgressTimer() {
        cancelProgressTimer();
        updateProcessTimer = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        updateProcessTimer.schedule(mProgressTimerTask, 0, 300);
    }

    protected void cancelProgressTimer() {
        if (updateProcessTimer != null) {
            updateProcessTimer.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }

    }

    protected class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTextAndProgress(0);
                    }
                });
            }
        }
    }

    /**
     * ????????????????????????
     */
    public int getCurrentPositionWhenPlaying() {
        int position = 0;
        if (mCurrentState == CURRENT_STATE_PLAYING || mCurrentState == CURRENT_STATE_PAUSE) {
            try {
                position = (int) GSYVideoManager.instance().getMediaPlayer().getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    /**
     * ?????????????????????
     */
    public int getDuration() {
        int duration = 0;
        try {
            duration = (int) GSYVideoManager.instance().getMediaPlayer().getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    protected void setTextAndProgress(int secProgress) {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, secProgress, position, duration);
    }

    protected void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) mProgressBar.setProgress(progress);
        }
        if (secProgress > 94) secProgress = 100;
        if (secProgress != 0 && !mCacheFile) {
            mProgressBar.setSecondaryProgress(secProgress);
        }
        mTotalTimeTextView.setText(CommonUtil.stringForTime(totalTime));
        if (currentTime > 0)
            mCurrentTimeTextView.setText(CommonUtil.stringForTime(currentTime));
    }


    protected void resetProgressAndTime() {
        mProgressBar.setProgress(0);
        mProgressBar.setSecondaryProgress(0);
        mCurrentTimeTextView.setText(CommonUtil.stringForTime(0));
        mTotalTimeTextView.setText(CommonUtil.stringForTime(0));
    }


    protected void loopSetProgressAndTime() {
        mProgressBar.setProgress(0);
        mProgressBar.setSecondaryProgress(0);
        mCurrentTimeTextView.setText(CommonUtil.stringForTime(0));
    }

    /**
     * ??????????????????????????????????????????video
     */
    public static void releaseAllVideos() {
        CLICK_QUIT_FULLSCREEN_TIME = 0;
        if (IF_RELEASE_WHEN_ON_PAUSE) {
            if (GSYVideoManager.instance().listener() != null) {
                GSYVideoManager.instance().listener().onCompletion();
            }
            GSYVideoManager.instance().releaseMediaPlayer();
        } else {
            IF_RELEASE_WHEN_ON_PAUSE = true;
        }
    }

    /**
     * if I am playing release me
     */
    public void release() {
        CLICK_QUIT_FULLSCREEN_TIME = 0;
        if (isCurrentMediaListener() &&
                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
            releaseAllVideos();
        }
        mHadPlay = false;
    }


    protected boolean isCurrentMediaListener() {
        return GSYVideoManager.instance().listener() != null
                && GSYVideoManager.instance().listener() == this;
    }


    /**
     * ??????????????????
     *
     * @param percent
     */
    private void onBrightnessSlide(float percent) {
        mBrightnessData = ((Activity) (mContext)).getWindow().getAttributes().screenBrightness;
        if (mBrightnessData <= 0.00f) {
            mBrightnessData = 0.50f;
        } else if (mBrightnessData < 0.01f) {
            mBrightnessData = 0.01f;
        }
        WindowManager.LayoutParams lpa = ((Activity) (mContext)).getWindow().getAttributes();
        lpa.screenBrightness = mBrightnessData + percent;
        if (lpa.screenBrightness > 1.0f) {
            lpa.screenBrightness = 1.0f;
        } else if (lpa.screenBrightness < 0.01f) {
            lpa.screenBrightness = 0.01f;
        }
        showBrightnessDialog(lpa.screenBrightness);
        ((Activity) (mContext)).getWindow().setAttributes(lpa);
    }

    /**
     * ??????????????????
     */
    protected void createNetWorkState() {
        if (mNetInfoModule == null) {
            mNetInfoModule = new NetInfoModule(getActivityContext().getApplicationContext(), new NetInfoModule.NetChangeListener() {
                @Override
                public void changed(String state) {
                    if (!mNetSate.equals(state)) {
                        Debuger.printfError("******* change network state ******* " + state);
                        mNetChanged = true;
                    }
                    mNetSate = state;
                }
            });
            mNetSate = mNetInfoModule.getCurrentConnectionType();
        }
    }

    /**
     * ??????????????????
     */
    protected void listenerNetWorkState() {
        if (mNetInfoModule != null) {
            mNetInfoModule.onHostResume();
        }
    }

    /**
     * ??????????????????
     */
    protected void unListenerNetWorkState() {
        if (mNetInfoModule != null) {
            mNetInfoModule.onHostPause();
        }
    }

    /**
     * ??????????????????
     */
    protected void releaseNetWorkState() {
        if (mNetInfoModule != null) {
            mNetInfoModule.onHostPause();
            mNetInfoModule = null;
        }
    }


    /**
     * ??????????????????
     */
    public View getStartButton() {
        return mStartButton;
    }

    /**
     * ??????????????????
     */
    public ImageView getFullscreenButton() {
        return mFullscreenButton;
    }

    /**
     * ??????????????????
     */
    public ImageView getBackButton() {
        return mBackButton;
    }

    /**
     * ????????????????????????
     */
    public int getCurrentState() {
        return mCurrentState;
    }

    /**
     * ??????tag??????????????????????????????url???????????????
     */
    public String getPlayTag() {
        return mPlayTag;
    }

    /**
     * ??????tag??????????????????????????????url???????????????
     *
     * @param playTag ?????????????????????
     */
    public void setPlayTag(String playTag) {
        this.mPlayTag = playTag;
    }


    public int getPlayPosition() {
        return mPlayPosition;
    }

    /**
     * ??????????????????????????????
     */
    public void setPlayPosition(int playPosition) {
        this.mPlayPosition = playPosition;
    }

    /**
     * ??????????????????????????????
     */
    public void setSmallCloseShow() {
        mSmallClose.setVisibility(VISIBLE);
    }

    /**
     * ??????????????????????????????
     */
    public void setSmallCloseHide() {
        mSmallClose.setVisibility(GONE);
    }

    /**
     * ????????????????????????????????????
     *
     * @return ??????????????????
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
     * ????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????0.??????????????????????????????
     */
    public long getNetSpeed() {
        if (GSYVideoManager.instance().getMediaPlayer() != null
                && (GSYVideoManager.instance().getMediaPlayer() instanceof IjkMediaPlayer)) {
            return ((IjkMediaPlayer) GSYVideoManager.instance().getMediaPlayer()).getTcpSpeed();
        } else {
            return -1;
        }

    }

    /**
     * ????????????
     * ?????????????????????????????????????????????????????????????????????????????????????????????????????????
     * ????????????????????????????????????????????????????????????0.??????????????????????????????
     */
    public String getNetSpeedText() {
        long speed = getNetSpeed();
        return getTextSpeed(speed);
    }

    public long getSeekOnStart() {
        return mSeekOnStart;
    }

    /**
     * ?????????????????????
     * ????????????????????????????????????????????????
     * ?????????startPlayLogic??????????????????????????????
     */
    public void setSeekOnStart(long seekOnStart) {
        this.mSeekOnStart = seekOnStart;
    }


    /**
     * ????????????/????????????
     */
    public int getBuffterPoint() {
        return mBuffterPoint;
    }


    /**
     * ???????????????????????????
     *
     * @return GSYVideoPlayer ???????????????????????????
     */
    @SuppressWarnings("ResourceType")
    public GSYVideoPlayer getFullWindowPlayer() {
        ViewGroup vp = (ViewGroup) (CommonUtil.scanForActivity(getContext())).findViewById(Window.ID_ANDROID_CONTENT);
        final View full = vp.findViewById(FULLSCREEN_ID);
        GSYVideoPlayer gsyVideoPlayer = null;
        if (full != null) {
            gsyVideoPlayer = (GSYVideoPlayer) full;
        }
        return gsyVideoPlayer;
    }

}