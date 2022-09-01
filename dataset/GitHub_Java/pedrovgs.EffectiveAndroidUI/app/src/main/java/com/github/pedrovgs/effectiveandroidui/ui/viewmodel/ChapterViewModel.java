package com.github.pedrovgs.effectiveandroidui.ui.viewmodel;

import com.github.pedrovgs.effectiveandroidui.domain.tvshow.Chapter;
import com.github.pedrovgs.effectiveandroidui.util.RandomUtils;
import com.github.pedrovgs.effectiveandroidui.util.TimeMachine;

/**
 * ViewModel crated to represent a Chapter from the presentation point of view.
 *
 * This class could be a interface implementation if the ChapterViewModel has more than one
 * implementation.
 *
 * This view model contains code to show how to implement a system update inside a ListView without
 * use a notifyDataSetChanged.
 *
 * @author Pedro Vicente Gómez Sánchez
 */
public class ChapterViewModel {

  private static final int MAX_RATE_VALUE = 10;
  private static final int TWO_SECONDS = 2000;

  private final Chapter chapter;

  private Listener listener = new NullListener();

  /**
   * Runnable implementation used to simulate a system update related with the tv show rate.
   */
  private Runnable updateRateRunnable = new Runnable() {
    @Override public void run() {
      int randomRate = RandomUtils.getRandomValueBelow(MAX_RATE_VALUE);
      listener.onRateChanged(randomRate);
    }
  };

  public ChapterViewModel(Chapter chapter) {
    this.chapter = chapter;
  }

  public String getTitle() {
    return chapter.getTitle();
  }

  public String getPublishDate() {
    return chapter.getPublishDate();
  }

  /**
   * In addition to register the listener this method is going to trigger a view model update two
   * seconds in the future. This has been implemented to show how to use view model binding
   * manually
   * implemented to update each row without the full list view.
   */
  public void registerListener(final Listener listener) {
    this.listener = listener;
    TimeMachine.sendMessageToTheFuture(updateRateRunnable, TWO_SECONDS);
  }

  /**
   * Set NullListener (NullObject pattern implementation) and cancel the update sent to the future
   * when the listener was registered.
   */
  public void unregisterListener() {
    this.listener = new NullListener();
    TimeMachine.cancelMessage(updateRateRunnable);
  }

  public interface Listener {

    public void onRateChanged(final int rate);
  }

  /**
   * NullObject pattern implementation to avoid listener field null checks inside this view model.
   */
  private class NullListener implements Listener {
    @Override public void onRateChanged(int rate) {
      //Empty
    }
  }
}
