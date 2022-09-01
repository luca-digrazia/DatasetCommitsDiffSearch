package com.yammer.metrics.examples;

import java.io.File;
import java.util.List;
import java.util.concurrent.*;

import com.yammer.metrics.core.CounterMetric;
import com.yammer.metrics.core.Metrics;

public class ExampleRunner {
	private static final int WORKER_COUNT = 10;
	private static final BlockingQueue<File> JOBS = new LinkedBlockingQueue<File>();
	private static final ExecutorService POOL = Executors.newFixedThreadPool(WORKER_COUNT);
	private static final CounterMetric QUEUE_DEPTH = Metrics.newCounter(ExampleRunner.class, "queue-depth");

	public static class Job implements Runnable {
		@Override
		public void run() {
			try {
				while (true) {
					final File file = JOBS.poll(1, TimeUnit.MINUTES);
					QUEUE_DEPTH.dec();
					if (file.isDirectory()) {
						final List<File> contents = new DirectoryLister(file).list();
						QUEUE_DEPTH.inc(contents.size());
						JOBS.addAll(contents);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}


	public static void main(String[] args) throws Exception {
		Metrics.enableConsoleReporting(10, TimeUnit.SECONDS);

		System.err.println("Scanning all files on your hard drive...");

		JOBS.add(new File("/"));
		QUEUE_DEPTH.inc();
		for (int i = 0; i < WORKER_COUNT; i++) {
			POOL.submit(new Job());
		}

		POOL.awaitTermination(10, TimeUnit.DAYS);
	}
}
