/*
Copyright 2014 David Morrissey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.davemorrissey.labs.subscaleview.sample;

import android.graphics.PointF;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.Toast;
import com.davemorrissey.labs.subscaleview.ImageViewState;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.davemorrissey.labs.subscaleview.sample.R.id;

import java.util.Random;

public class PageFragment extends Fragment implements OnClickListener, OnLongClickListener {

    private static final String BUNDLE_STATE = "state";
    private static final String BUNDLE_ASSET = "asset";

    private int orientation = 0;

    private String asset;

    public PageFragment() {
    }

    public PageFragment(String asset) {
        this.asset = asset;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.page, container, false);

        ImageViewState imageViewState = null;
        if (savedInstanceState != null) {
            if (asset == null && savedInstanceState.containsKey(BUNDLE_ASSET)) {
                asset = savedInstanceState.getString(BUNDLE_ASSET);
            }
            if (savedInstanceState.containsKey(BUNDLE_STATE)) {
                imageViewState = (ImageViewState)savedInstanceState.getSerializable(BUNDLE_STATE);
                orientation = imageViewState.getOrientation();
            }
        }

        rootView.findViewById(id.rotate).setOnClickListener(this);
        rootView.findViewById(id.scale).setOnClickListener(this);
        rootView.findViewById(id.center).setOnClickListener(this);
        if (asset != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            imageView.setImageAsset(asset, imageViewState);
            imageView.setDoubleTapZoomStyle(SubsamplingScaleImageView.ZOOM_FOCUS_CENTER);
            imageView.setPanLimit(SubsamplingScaleImageView.PAN_LIMIT_INSIDE);
            imageView.setOnClickListener(this);
            imageView.setOnLongClickListener(this);
        }

        return rootView;
    }

    @Override
    public void onClick(View view) {
        View rootView = getView();
        if (view.getId() == id.rotate && rootView != null) {
            orientation = (orientation + 90) % 360;
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            imageView.setOrientation(orientation);
        } else if (view.getId() == id.scale && rootView != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            Random random = new Random();
            if (imageView.isImageReady()) {
                int sx = random.nextInt(imageView.getSWidth());
                int sy = random.nextInt(imageView.getSHeight());
                imageView.animateScaleAndCenter(random.nextFloat() * 2, new PointF(sx, sy)).withDuration(1500).start();
            }
        } else if (view.getId() == id.center && rootView != null) {
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            Random random = new Random();
            if (imageView.isImageReady()) {
                int sx = random.nextInt(imageView.getSWidth());
                int sy = random.nextInt(imageView.getSHeight());
                imageView.animateCenter(new PointF(sx, sy)).withDuration(1500).start();
            }
        } else if (view.getId() == id.imageView) {
            Toast.makeText(getActivity(), "Clicked", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onLongClick(View view) {
        if (view.getId() == id.imageView) {
            Toast.makeText(getActivity(), "Long clicked", Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        View rootView = getView();
        if (rootView != null) {
            outState.putString(BUNDLE_ASSET, asset);
            SubsamplingScaleImageView imageView = (SubsamplingScaleImageView)rootView.findViewById(id.imageView);
            ImageViewState state = imageView.getState();
            if (state != null) {
                outState.putSerializable(BUNDLE_STATE, imageView.getState());
            }
        }
    }

}
