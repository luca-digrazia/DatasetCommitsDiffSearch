package com.github.pedrovgs.effectiveandroidui.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import butterknife.InjectView;
import com.github.pedrovgs.DraggableView;
import com.github.pedrovgs.effectiveandroidui.R;
import com.github.pedrovgs.effectiveandroidui.domain.tvshow.ChapterCollection;
import com.github.pedrovgs.effectiveandroidui.domain.tvshow.TvShow;
import com.github.pedrovgs.effectiveandroidui.ui.presenter.TvShowPresenter;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterAdapteeCollection;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterRendererAdapter;
import com.github.pedrovgs.effectiveandroidui.ui.renderer.chapter.ChapterRendererAdapterFactory;
import com.github.pedrovgs.effectiveandroidui.util.ToastUtils;
import com.squareup.picasso.Picasso;
import javax.inject.Inject;

/**
 * Fragment created to show a TvShows using DraggablePanel library.
 *
 * This Fragment uses a Model View Presenter implementation to implement all the presentation
 * logic. Review TvShowPresenter to get more info about the implementation.
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public class TvShowDraggableFragment extends BaseFragment implements TvShowPresenter.View {

  @Inject TvShowPresenter tvShowPresenter;
  @Inject ChapterRendererAdapterFactory chapterRendererAdapterFactory;

  private ChapterRendererAdapter adapter;
  private ChapterAdapteeCollection chapterAdapteeCollection = new ChapterAdapteeCollection();

  @InjectView(R.id.draggable_view) DraggableView draggable_view;
  @InjectView(R.id.iv_fan_art) ImageView iv_fan_art;
  @InjectView(R.id.lv_chapters) ListView lv_chapters;
  @InjectView(R.id.pb_loading) ProgressBar pb_loading;
  @InjectView(R.id.v_empty_case) View v_empty_case;

  private TextView headr_tv_show_chapters;

  @Override public void onViewCreated(View view, Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    tvShowPresenter.setView(this);
    initializeDraggableView();
    initializeListView();
  }

  private void initializeListView() {
    headr_tv_show_chapters = (TextView) LayoutInflater.from(getActivity())
        .inflate(R.layout.header_tv_show_chapters, null);
    lv_chapters.addHeaderView(headr_tv_show_chapters);
    adapter = (ChapterRendererAdapter) chapterRendererAdapterFactory.getChapterRendererAdapter(
        chapterAdapteeCollection);
    lv_chapters.setAdapter(adapter);
  }

  public void showTvShow(final TvShow tvShow) {
    tvShowPresenter.loadTvShow(tvShow.getTitle());
  }

  @Override public void hideEmptyCase() {
    v_empty_case.setVisibility(View.GONE);
  }

  @Override public void showLoading() {
    pb_loading.setVisibility(View.VISIBLE);
  }

  @Override public void showFanArt(final String tvShowFanArtUrl) {
    iv_fan_art.setVisibility(View.VISIBLE);
    Picasso.with(getActivity()).load(tvShowFanArtUrl).into(iv_fan_art);
  }

  @Override public void showTvShowTitle(final String tvShowTitle) {
    String tvShowHeaderTitle = getString(R.string.tv_show_title, tvShowTitle);
    headr_tv_show_chapters.setText(tvShowHeaderTitle);
  }

  @Override public void showChapters(ChapterCollection chapters) {
    chapterAdapteeCollection.clear();
    chapterAdapteeCollection.addAll(chapters.getChapters());
    adapter.notifyDataSetChanged();
  }

  @Override public void hideLoading() {
    pb_loading.setVisibility(View.GONE);
  }

  @Override public void showEmptyCase() {
    v_empty_case.setVisibility(View.VISIBLE);
  }

  @Override public void showTvShowNotFoundMessage() {
    String tvShowNotFoundMessage = getString(R.string.tv_show_not_found);
    ToastUtils.showShortMessage(tvShowNotFoundMessage, getActivity());
  }

  @Override public void showConnectionErrorMessage() {
    String connectionErrorMessage = getString(R.string.connection_error_message);
    ToastUtils.showError(connectionErrorMessage, getActivity());
  }

  @Override public void showTvShow() {
    draggable_view.setVisibility(View.VISIBLE);
    draggable_view.maximize();
  }

  @Override protected int getFragmentLayout() {
    return R.layout.fragment_draggable_tv_show;
  }

  private void initializeDraggableView() {
    draggable_view.post(new Runnable() {
      @Override
      public void run() {
        draggable_view.closeToRight();
      }
    });
  }
}
