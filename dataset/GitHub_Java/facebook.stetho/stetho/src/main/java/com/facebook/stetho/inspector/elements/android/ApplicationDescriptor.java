/*
 * Copyright (c) 2014-present, Facebook, Inc.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree. An additional grant
 * of patent rights can be found in the PATENTS file in the same directory.
 */

package com.facebook.stetho.inspector.elements.android;

import android.app.Activity;
import android.app.Application;

import com.facebook.stetho.inspector.elements.ChainedDescriptor;
import com.facebook.stetho.inspector.elements.NodeType;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class ApplicationDescriptor extends ChainedDescriptor<Application> {
  private final Map<Application, ElementContext> mElementToContextMap =
      Collections.synchronizedMap(new IdentityHashMap<Application, ElementContext>());

  private final ActivityTracker mActivityTracker = ActivityTracker.get();

  private ElementContext getContext(Application element) {
    return mElementToContextMap.get(element);
  }

  @Override
  protected void onHook(Application element) {
    ElementContext context = new ElementContext(element);
    context.hook(element);
    mElementToContextMap.put(element, context);
  }

  @Override
  protected void onUnhook(Application element) {
    ElementContext context = mElementToContextMap.remove(element);
    context.unhook();
  }

  @Override
  protected NodeType onGetNodeType(Application element) {
    return NodeType.ELEMENT_NODE;
  }

  @Override
  protected int onGetChildCount(Application element) {
    ElementContext context = getContext(element);
    return context.getActivitiesList().size();
  }

  @Override
  protected Object onGetChildAt(Application element, int index) {
    ElementContext context = getContext(element);
    return context.getActivitiesList().get(index);
  }

  private class ElementContext {
    private final Application mElement;

    public ElementContext(Application element) {
      mElement = element;
    }

    public void hook(Application element) {
      mActivityTracker.registerListener(mListener);
    }

    public void unhook() {
      mActivityTracker.unregisterListener(mListener);
    }

    public List<Activity> getActivitiesList() {
      return mActivityTracker.getActivitiesView();
    }

    private final ActivityTracker.Listener mListener = new ActivityTracker.Listener() {
      @Override
      public void onActivityAdded(Activity activity) {
        getHost().onChildInserted(mElement, null, activity);
      }

      @Override
      public void onActivityRemoved(Activity activity) {
        getHost().onChildRemoved(mElement, activity);
      }
    };
  }
}
