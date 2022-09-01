package com.github.pedrovgs.effectiveandroidui.ui.renderer.chapterviewmodel;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import butterknife.ButterKnife;
import butterknife.InjectView;
import com.github.pedrovgs.effectiveandroidui.R;
import com.github.pedrovgs.effectiveandroidui.ui.viewmodel.ChapterViewModel;
import com.pedrogomez.renderers.Renderer;
import javax.inject.Inject;

/**
 * Renderer implementation for ChapterViewModel objects.
 *
 * If you want to lear more about how to use Renderers take a look to this project:
 * https://github.com/pedrovgs/Renderers
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public class ChapterViewModelRenderer extends Renderer<ChapterViewModel>
    implements ChapterViewModel.Listener {

  @InjectView(R.id.tv_chapter_number) TextView tv_chapter_number;
  @InjectView(R.id.tv_chapter_title) TextView tv_chapter_title;
  @InjectView(R.id.tv_chapter_publish_date) TextView tv_chapter_publish_date;

  private final Context context;

  @Inject
  public ChapterViewModelRenderer(Context context) {
    this.context = context;
  }

  private int position;

  public void setPosition(int position) {
    this.position = position;
  }

  @Override
  protected void setUpView(View view) {
    ButterKnife.inject(this, view);
  }

  @Override
  protected void hookListeners(View view) {
    //Empty because we are using ButterKnife to inject views.
  }

  @Override
  protected View inflate(LayoutInflater layoutInflater, ViewGroup viewGroup) {
    return layoutInflater.inflate(R.layout.row_chapter, viewGroup, false);
  }

  @Override
  public void render() {
    ChapterViewModel chapter = getContent();
    renderChapterNumber();
    renderChapterTitle(chapter);
    renderChapterPublishDate(chapter);
  }

  @Override public void onRecycle(ChapterViewModel content) {
    getContent().unregisterListener();
    super.onRecycle(content);
    getContent().registerListener(this);
  }

  @Override public void onCreate(ChapterViewModel content, LayoutInflater layoutInflater,
      ViewGroup parent) {
    super.onCreate(content, layoutInflater, parent);
    getContent().registerListener(this);
  }

  private void renderChapterNumber() {
    tv_chapter_number.setText(String.format("%02d", position + 1));
  }

  private void renderChapterTitle(ChapterViewModel chapter) {
    tv_chapter_title.setText(chapter.getTitle());
  }

  private void renderChapterPublishDate(ChapterViewModel chapter) {
    tv_chapter_publish_date.setText(chapter.getPublishDate());
  }

  @Override public void onRateChanged(int rate) {
    ChapterViewModel chapter = getContent();
    String rateMessage = context.getString(R.string.tv_show_rate, rate);
    tv_chapter_title.setText(chapter.getTitle() + " - " + rateMessage);
  }
}
