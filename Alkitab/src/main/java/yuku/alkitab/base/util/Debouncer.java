package yuku.alkitab.base.util;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Delays processing of a payload, and if another payload is submitted
 * afterwards, the earlier ones are not processed nor delivered any more.
 * @param <RequestType>
 * @param <ResultType>
 */
public abstract class Debouncer<RequestType, ResultType> {
	static final String TAG = Debouncer.class.getSimpleName();

	public static final int MSG_ON_RESULT = 1;

	private final DebounceHandler handler;
	private final long defaultDelay;
	final ScheduledExecutorService sched = Executors.newSingleThreadScheduledExecutor();

	final AtomicInteger serial = new AtomicInteger();

	static class DebounceHandler<RequestType, ResultType> extends Handler {
		private final WeakReference<Debouncer<RequestType, ResultType>> ref;

		public DebounceHandler(final Debouncer<RequestType, ResultType> debouncer) {
			this.ref = new WeakReference<>(debouncer);
		}

		@Override
		public void handleMessage(final Message msg) {
			final Debouncer<RequestType, ResultType> debouncer = this.ref.get();
			if (debouncer == null) return;

			// check again one more time
			if (isOutdated(3, msg.arg1, debouncer.serial.get())) return;

			//noinspection unchecked
			debouncer.onResult((ResultType) msg.obj);
		}
	}

	static abstract class Task implements Runnable {
		public int id;

		public Task(final int id) {
			this.id = id;
		}
	}

	/**
	 * Call this on the main thread.
	 * @param defaultDelay The default delay in ms before {@link #process(RequestType)} is performed.
	 */
	public Debouncer(final long defaultDelay) {
		this.defaultDelay = defaultDelay;
		this.handler = new DebounceHandler<>(this);
	}

	/**
	 * Schedule processing after the default delay.
	 * @param payload Payload to be sent to the {@link #process(RequestType)} method.
	 */
	public void submit(final RequestType payload) {
		submit(payload, defaultDelay);
	}

	/**
	 * Schedule processing after the specified delay.
	 * @param payload Payload to be sent to the {@link #process(RequestType)} method.
	 */
	public void submit(final RequestType payload, final long delay) {
		final Task t = new Task(serial.incrementAndGet()) {
			@Override
			public void run() {
				// check if this is still needed
				if (isOutdated(1, this.id, serial.get())) return;

				final ResultType result = process(payload);

				// check again if this is still needed
				if (isOutdated(2, this.id, serial.get())) return;

				// we are okay, deliver result in main thread
				final Message msg = Message.obtain();
				msg.what = MSG_ON_RESULT;
				msg.arg1 = this.id;
				msg.obj = result;

				handler.sendMessage(msg);
			}
		};

		sched.schedule(t, delay, TimeUnit.MILLISECONDS);
	}

	/**
	 * Called in non-UI thread.
	 * Override this to process the payload submitted in {@link #submit(RequestType)}.
	 */
	public abstract ResultType process(final RequestType payload);

	/**
	 * Called in the UI thread.
	 * Override this to receive the process result and e.g. update UI.
	 */
	public abstract void onResult(final ResultType result);

	static boolean isOutdated(int phase, int this_id, int current_id) {
		if (this_id == current_id) return false;

		switch (phase) {
			case 1:
				AppLog.d(TAG, "outdated task (" + this_id + " < " + current_id + ") found before process");
				break;
			case 2:
				AppLog.d(TAG, "outdated task (" + this_id + " < " + current_id + ") found after process");
				break;
			case 3:
				AppLog.d(TAG, "outdated task (" + this_id + " < " + current_id + ") found before onResult");
				break;
		}

		return true;
	}
}
