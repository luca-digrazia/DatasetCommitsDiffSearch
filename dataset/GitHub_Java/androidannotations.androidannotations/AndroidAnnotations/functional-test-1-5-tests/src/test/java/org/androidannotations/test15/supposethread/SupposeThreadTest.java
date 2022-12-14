package org.androidannotations.test15.supposethread;

import android.os.Looper;
import com.xtremelabs.robolectric.Robolectric;
import com.xtremelabs.robolectric.util.Scheduler;
import org.androidannotations.api.BackgroundExecutor;
import org.androidannotations.test15.AndroidAnnotationsTestRunner;
import org.androidannotations.test15.EmptyActivityWithoutLayout;
import org.androidannotations.test15.ebean.ThreadControlledBean;
import org.androidannotations.test15.ebean.ThreadControlledBean_;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidAnnotationsTestRunner.class)
public class SupposeThreadTest {

    private Runnable placeholder = new Runnable() {
        @Override
        public void run() {
        }
    };
    private ThreadControlledBean bean;

    @Before
    public void setUp() throws Exception {
        EmptyActivityWithoutLayout context = new EmptyActivityWithoutLayout();
        bean = ThreadControlledBean_.getInstance_(context);
        BackgroundExecutor.setExecutor(new Executor() {
            @Override
            public void execute(Runnable command) {
                command.run();
            }
        });
    }

    @Test
    public void testSupposeUiSuccess() throws Exception {
        bean.uiSupposed();
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeUiFail() throws Exception {
        invokeInSeparateThread(new Runnable() {
            @Override
            public void run() {
                bean.uiSupposed();
            }
        });
    }

    @Test
    public void testSupposeBackground() throws Exception {
        invokeInSeparateThread(new Runnable() {
            @Override
            public void run() {
                bean.backgroundSupposed();
            }
        });
    }

    @Test
    public void testSupposeUiAndUi() throws Exception {
        Scheduler scheduler = Robolectric.shadowOf(Looper.getMainLooper()).getScheduler();

        final AtomicBoolean run = new AtomicBoolean(false);

        scheduler.pause();
        bean.uiSupposedAndUi(new Runnable() {
            @Override
            public void run() {
                run.set(true);
            }
        });

        if (run.get()) {
            throw new IllegalStateException("Runnable wasn't post through handler, but was invoked");
        }

        scheduler.unPause();
        scheduler.advanceToLastPostedRunnable();

        if (!run.get()) {
            throw new IllegalStateException("Runnable wasn't invoked");
        }
    }

    @Test
    public void testSupposeBackgroundAndBackground() {
        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                bean.backgroundSupposeAndBackground(new Runnable() {
                    @Override
                    public void run() {
                        BackgroundExecutor.checkBgThread(ThreadControlledBean.SERIAL2);
                    }
                });
            }
        }, "", ThreadControlledBean.SERIAL1);
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeUiAndUiFail() throws Exception {
        invokeInSeparateThread(new Runnable() {
            @Override
            public void run() {
                bean.uiSupposedAndUi(placeholder);
            }
        });
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeBackgroundAndBackgroundFail() {
        bean.backgroundSupposeAndBackground(placeholder);
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeFailBackground() throws Exception {
        bean.backgroundSupposed();
    }

    @Test
    public void testSupposeSerial() throws Exception {
        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                bean.serialBackgroundSupposed();
            }
        }, "", ThreadControlledBean.SERIAL1);

        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                bean.serialBackgroundSupposed();
            }
        }, "", ThreadControlledBean.SERIAL2);
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeFailSerialUi() throws Exception {
        bean.serialBackgroundSupposed();
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeFailSerialWrong() throws Exception {
        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                bean.serialBackgroundSupposed();
            }
        }, "", "wrong_serial");
    }

    @Test(expected = IllegalStateException.class)
    public void testSupposeFailSerialEmpty() throws Exception {
        BackgroundExecutor.execute(new Runnable() {
            @Override
            public void run() {
                bean.serialBackgroundSupposed();
            }
        });
    }

    private void invokeInSeparateThread(final Runnable runnable) throws Exception {
        final CountDownLatch runIndicator = new CountDownLatch(1);
        final AtomicReference<Exception> exceptionThrown = new AtomicReference<Exception>(null);

        new Thread() {
            @Override
            public void run() {
                try {
                    runnable.run();
                } catch (Exception e) {
                    exceptionThrown.set(e);
                }
                runIndicator.countDown();
            }
        }.start();

        boolean ran = runIndicator.await(2, TimeUnit.SECONDS);
        Assert.assertTrue("Method wasn't invoke in 2 seconds", ran);

        Exception e = exceptionThrown.get();
        if (e != null) {
            throw e;
        }
    }
}
