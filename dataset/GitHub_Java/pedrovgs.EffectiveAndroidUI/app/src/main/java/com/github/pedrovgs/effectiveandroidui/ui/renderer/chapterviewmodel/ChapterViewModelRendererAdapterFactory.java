/*
 * Copyright (C) 2014 Pedro Vicente Gómez Sánchez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.pedrovgs.effectiveandroidui.ui.renderer.chapterviewmodel;

import android.view.LayoutInflater;
import com.github.pedrovgs.effectiveandroidui.ui.viewmodel.ChapterViewModel;
import com.pedrogomez.renderers.RendererAdapter;
import javax.inject.Inject;

/**
 * Factory created to provide RendererAdapter<ChapterViewModel> implementations.
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public class ChapterViewModelRendererAdapterFactory {

  private final ChapterViewModelRendererBuilder rendererBuilder;
  private final LayoutInflater layoutInflater;

  @Inject
  public ChapterViewModelRendererAdapterFactory(ChapterViewModelRendererBuilder rendererBuilder,
      LayoutInflater layoutInflater) {
    this.rendererBuilder = rendererBuilder;
    this.layoutInflater = layoutInflater;
  }

  public RendererAdapter<ChapterViewModel> getChapterRendererAdapter(
      final ChapterViewModelCollection chapterCollection) {
    return new ChapterViewModelRendererAdapter(layoutInflater, rendererBuilder, chapterCollection);
  }
}
