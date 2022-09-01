/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.elements.android;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.Application;
import android.database.Observable;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import com.facebook.stetho.common.Util;

import javax.annotation.Nullable;
import javax.annotation.concurrent.GuardedBy;
import javax.annotation.concurrent.NotThreadSafe;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Tracks which {@link Activity} instances have been created and not yet destroyed in creation
 * order for use by Stetho features.  Note that automatic tracking is not available for
 * all versions of Android but it is possible to manually track activities using the {@link #add}
 * and {@link #remove} methods exposed below.  Be aware that this is an easy opportunity to
 * cause serious memory leaks in your application however.  Use with caution.
 * <p/>
 * Most callers can and should ignore this class, though it is necessary if you are implementing
 * Activity tracking pre-ICS.
 */
@NotThreadSafe
public class ActivityTracker {
  private static final ActivityTracker sInstance = new ActivityTracker();

  @GuardedBy("Looper.getMainLooper()")
  private final ArrayList<Activity> mActivities = new ArrayList<>();
  private final List<Activity> mActivitiesUnmodifiable = Collections.unmodifiableList(mActivities);

  @GuardedBy("ActivityTrackerObservable.mObservers")
  private final ActivityTrackerObservable mObservable = new ActivityTrackerObservable();

  @Nullable
  private AutomaticTracker mAutomaticTracker;

  public static ActivityTracker get() {
    return sInstance;
  }

  public void registerListener(Listener listener) {
    mObservable.registerObserver(listener);
  }

  public void unregisterListener(Listener listener) {
    mObservable.unregisterObserver(listener);
  }

  /**
   * Start automatic tracking if we are running on ICS+.
   *
   * @return Automatic tracking has been started.  No need to manually invoke {@link #add} or
   *     {@link #remove} methods.
   */
  public boolean beginTrackingIfPossible(Application application) {
    if (mAutomaticTracker == null) {
      AutomaticTracker automaticTracker =
          AutomaticTracker.newInstance(application, this /* tracker */);
      if (automaticTracker != null) {
        automaticTracker.register();
        mAutomaticTracker = automaticTracker;
        return true;
      }
    }
    return false;
  }

  public boolean endTracking() {
    if (mAutomaticTracker != null) {
      mAutomaticTracker.unregister();
      return true;
    }
    return false;
  }

  public void add(Activity activity) {
    Util.throwIfNull(activity);
    Util.throwIfNot(Looper.myLooper() == Looper.getMainLooper());
    mActivities.add(activity);
    mObservable.notifyActivityAdded(activity);
  }

  public void remove(Activity activity) {
    Util.throwIfNull(activity);
    Util.throwIfNot(Looper.myLooper() == Looper.getMainLooper());
    if (mActivities.remove(activity)) {
      mObservable.notifyActivityRemoved(activity);
    }
  }

  public List<Activity> getActivitiesView() {
    return mActivitiesUnmodifiable;
  }

  private static class ActivityTrackerObservable extends Observable<Listener> {
    public void notifyActivityAdded(Activity activity) {
      synchronized (mObservers) {
        int N = mObservers.size();
        for (int i = 0; i < N; i++) {
          Listener listener = mObservers.get(i);
          listener.onActivityAdded(activity);
        }
      }
    }

    public void notifyActivityRemoved(Activity activity) {
      synchronized (mObservers) {
        int N = mObservers.size();
        for (int i = 0; i < N; i++) {
          Listener listener = mObservers.get(i);
          listener.onActivityRemoved(activity);
        }
      }
    }
  }

  public interface Listener {
    public void onActivityAdded(Activity activity);
    public void onActivityRemoved(Activity activity);
  }

  private static abstract class AutomaticTracker {
    @Nullable
    public static AutomaticTracker newInstance(Application application, ActivityTracker tracker) {
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
        return new AutomaticTrackerICSAndBeyond(application, tracker);
      } else {
        return null;
      }
    }

    public abstract void register();
    public abstract void unregister();

    @TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
    private static class AutomaticTrackerICSAndBeyond extends AutomaticTracker {
      private final Application mApplication;
      private final ActivityTracker mTracker;

      public AutomaticTrackerICSAndBeyond(Application application, ActivityTracker tracker) {
        mApplication = application;
        mTracker = tracker;
      }

      public void register() {
        mApplication.registerActivityLifecycleCallbacks(mLifecycleCallbacks);
      }

      public void unregister() {
        mApplication.unregisterActivityLifecycleCallbacks(mLifecycleCallbacks);
      }

      private final Application.ActivityLifecycleCallbacks mLifecycleCallbacks =
          new Application.ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
          mTracker.add(activity);
        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {

        }

        @Override
        public void onActivityPaused(Activity activity) {

        }

        @Override
        public void onActivityStopped(Activity activity) {

        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

        }

        @Override
        public void onActivityDestroyed(Activity activity) {
          mTracker.remove(activity);
        }
      };
    }
  }
}
