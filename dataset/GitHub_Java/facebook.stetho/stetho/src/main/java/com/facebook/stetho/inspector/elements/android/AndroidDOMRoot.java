// Copyright 2004-present Facebook. All Rights Reserved.

package com.facebook.stetho.inspector.elements.android;

import android.app.Application;

import com.facebook.stetho.common.Util;
import com.facebook.stetho.inspector.elements.ChainedDescriptor;
import com.facebook.stetho.inspector.elements.NodeType;

// For the root, we use 1 object for both element and descriptor.

final class AndroidDOMRoot extends ChainedDescriptor<AndroidDOMRoot> {
  private final Application mApplication;

  public AndroidDOMRoot(Application application) {
    mApplication = Util.throwIfNull(application);
  }

  @Override
  protected NodeType onGetNodeType(AndroidDOMRoot element) {
    return NodeType.DOCUMENT_NODE;
  }

  @Override
  protected String onGetNodeName(AndroidDOMRoot element) {
    return "root";
  }

  @Override
  protected int onGetChildCount(AndroidDOMRoot element) {
    return 1;
  }

  @Override
  protected Object onGetChildAt(AndroidDOMRoot element, int index) {
    if (index != 0) {
      throw new IndexOutOfBoundsException();
    }
    return mApplication;
  }
}
