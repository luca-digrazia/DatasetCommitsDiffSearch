package com.github.pedrovgs.effectiveandroidui.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import butterknife.InjectView;
import com.github.pedrovgs.effectiveandroidui.R;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterAdapteeCollection;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterRendererAdapter;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterRendererAdapterFactory;
import com.github.pedrovgs.effectiveandroidui.ui.viewmodel.ChapterViewModel;
import com.github.pedrovgs.effectiveandroidui.ui.viewmodel.TvShowViewModel;
import com.github.pedrovgs.effectiveandroidui.util.ToastUtils;
import com.squareup.picasso.Picasso;
import java.util.List;
import javax.inject.Inject;

/**
 * Fragment created to show a TvShows. This fragment is going to be used in the tablet version.
 *
 * This Fragment uses a Model View View Model implementation to implement all the presentation
 * logic. Review TvShowViewModel to get more info about the implementation.
 *
 * This fragment contains duplicate code that you can see in TvShowDraggableFragment because I want
 * to show two different approaches to work over the Android UI layer with different patterns (MVVM
 * and MVP) and I don't want to mix both fragments in a hierarchy to clarify different
 * implementations.
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public class TvShowFragment extends BaseFragment implements TvShowViewModel.Listener {

  @Inject TvShowViewModel tvShowViewModel;
  @Inject ChapterRendererAdapterFactory chapterRendererAdapterFactory;

  private ChapterRendererAdapter adapter;
  private ChapterAdapteeCollection chapterAdapteeCollection = new ChapterAdapteeCollection();

  @InjectView(R.id.iv_fan_art) ImageView iv_fan_art;
  @InjectView(R.id.lv_chapters) ListView lv_chapters;
  @InjectView(R.id.pb_loading) ProgressBar pb_loading;
  @InjectView(R.id.v_empty_case) View v_empty_case;

  private TextView headr_tv_show_chapters;

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    initializeListView();
    bindViewModel();
  }

  public void showTvShow(final String tvShowId) {
    tvShowViewModel.loadTvShow(tvShowId);
  }

  @Override protected int getFragmentLayout() {
    return R.layout.fragment_tv_show;
  }

  @Override public void onFanArtLoaded(String fanArt) {
    Picasso.with(getActivity()).load(fanArt).into(iv_fan_art);
  }

  @Override public void onChaptersLoaded(List<ChapterViewModel> chapters) {
    //Empty for now
  }

  @Override public void onVisibilityChanged(final boolean visible) {
    int visibility = getVisibility(visible);
    iv_fan_art.setVisibility(visibility);
    lv_chapters.setVisibility(visibility);
  }

  @Override public void onLoadVisibilityChanged(final boolean visible) {
    pb_loading.setVisibility(getVisibility(visible));
  }

  @Override public void onEmptyCaseVisibilityChanged(final boolean visible) {
    v_empty_case.setVisibility(getVisibility(visible));
  }

  @Override public void onTvShowMessageNotFound() {
    ToastUtils.showError(getString(R.string.tv_show_not_found), getActivity());
  }

  @Override public void onConnectionErrorMessageNotFound() {
    ToastUtils.showError(getString(R.string.connection_error_message), getActivity());
  }

  private void initializeListView() {
    headr_tv_show_chapters = (TextView) LayoutInflater.from(getActivity())
        .inflate(R.layout.header_tv_show_chapters, null);
    lv_chapters.addHeaderView(headr_tv_show_chapters);
    adapter = (ChapterRendererAdapter) chapterRendererAdapterFactory.getChapterRendererAdapter(
        chapterAdapteeCollection);
    lv_chapters.setAdapter(adapter);
  }

  private void bindViewModel() {
    tvShowViewModel.setListener(this);
  }

  private int getVisibility(final boolean visible) {
    return visible ? View.VISIBLE : View.GONE;
  }
}
