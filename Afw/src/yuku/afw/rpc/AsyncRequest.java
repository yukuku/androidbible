package yuku.afw.rpc;

import android.content.Context;
import android.os.AsyncTask;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import yuku.afw.App;
import yuku.afw.D;
import yuku.afw.rpc.Response.Validity;

/**
 * Listener tree:
 * - OnResponse
 *   - OnSuccess
 *   - OnFailed
 *     - OnApiError
 * 
 * When a listener returns false, the parent listener will be called. Otherwise no more listeners will be called. 
 */
public class AsyncRequest<Z extends BaseData> {
	public static final String TAG = AsyncRequest.class.getSimpleName();
	
	protected static List<OnLoadingStatusChangedListener> onLoadingStatusChangedListeners = new ArrayList<AsyncRequest.OnLoadingStatusChangedListener>(16);
	protected static AtomicInteger activeCount = new AtomicInteger(0);
	protected static AtomicInteger serialNumber = new AtomicInteger(0);
	
	private static Object onLoadingStatusChangedListeners_lock = new Object();
	
	public enum LoadingStatus {
		Start,
		Stop,
	}
	
	public interface OnLoadingStatusChangedListener {
		void onLoadingStatusChanged(LoadingStatus status, int activeCount);
	}
	
	protected void onSuccess(Response response, Z data) {
	}
	
	protected void onFailed(Response response, Z data) {
	}
	
	protected void onResponse(Response response, Z data) {
	}
	
	protected void onApiError(Response response, Z data) {
	}

	private final Request request;
	private Z data;
	private Task<Z> task;
	private SparseArray<Object> tags;
	private boolean finished = false;
	private int id;
	private boolean consumed;
	
	public AsyncRequest(Request request, Z data) {
		this.request = request;
		this.data = data;
		this.task = new Task<Z>();
		this.id = serialNumber.incrementAndGet();
	}
	
	public void cancel() {
		task.cancel(false);
		finished = true;
	}
	
	public synchronized void setTag(Object tag) {
		setTag(0, tag);
	}
	
	public synchronized void setTag(int id, Object tag) {
		if (tags == null) {
			tags = new SparseArray<Object>(2);
		}
		tags.put(id, tag);
	}
	
	public synchronized <T> T getTag() {
		return this.<T>getTag(0);
	}
	
	@SuppressWarnings("unchecked")
	public synchronized <T> T getTag(int id) {
		return (T) tags.get(id);
	}

	public class Task<Y> extends AsyncTask<Request, Integer, Void> {
		final Z return_data; // should be never null
		Response return_response;
		Request request;
		
		public Task() {
			return_data = AsyncRequest.this.data;
		}
		
		@Override protected Void doInBackground(Request... params) {
			request = params[0];

			HttpPerformer httpPerformer = new HttpPerformer(this, request);
			if (D.EBUG) Log.d(TAG, "async start [" + id + "] (" + getActiveCount() + " active, total " + Thread.activeCount() + " threads) " + request.toString()); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
			
			return_response = httpPerformer.perform();
			
			if (return_data.isSuccessResponse(return_response)) {
				ResponseProcessor rp = return_data.getResponseProcessor(return_response);
				try {
					rp.process(return_response.data);
				} catch (Exception e) {
					return_response.validity = Validity.ProcessError;
					Log.w(TAG, "Error during ResponseProcessor#process", e);
				}
			}
			
			return null;
		}
		
		@Override protected void onCancelled() {
			return_response = new Response(request, Validity.Cancelled, "cancelled from asynctask#onCancelled");
			onPostExecute(null); //$NON-NLS-1$
		}
		
		@Override protected void onPostExecute(Void result) {
			if (D.EBUG) Log.d(TAG, "async stop [" + id + "] response: " + return_response.toString());  //$NON-NLS-1$//$NON-NLS-2$
			
			try {
				onReceiveResponse(return_response, return_data);
			} finally {
				trackStop();
			}
		}
	}
	
	public static void trackStop() {
		int activeCount = AsyncRequest.activeCount.decrementAndGet();
		synchronized (onLoadingStatusChangedListeners) {
			for (int i = 0, len = onLoadingStatusChangedListeners.size(); i < len; i++) {
				onLoadingStatusChangedListeners.get(i).onLoadingStatusChanged(LoadingStatus.Stop, activeCount);
			}
		}
	}

	protected void onReceiveResponse(Response response, Z data) {
		if (data.isSuccessResponse(response)) {
			onSuccess(response, data);
		} else {
			if (response.validity == Validity.IoError) {
				showErrorToastIfNoRecentErrorToast(App.context, "Network error");
			} else if (response.validity == Validity.JsonError) {
				showErrorToastIfNoRecentErrorToast(App.context, "Response from network error");
			}
			
			if (response.validity == Validity.Ok) {
				onApiError(response, data);
			}
			
			if (! consumed) {
				onFailed(response, data);
			}
		}
		
		if (! consumed) {
			onResponse(response, data);
		}
		
		this.finished = true;
	}

	private static long lastErrorToastTime = -1;

	private static void showErrorToastIfNoRecentErrorToast(Context context, String msg) {
		long now = SystemClock.uptimeMillis();
		
		if (lastErrorToastTime == -1 || now - lastErrorToastTime > 2000L) {
			Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
			lastErrorToastTime = now;
		}
	}

	public static void setLastErrorToastTime(long lastErrorToastTime) {
		AsyncRequest.lastErrorToastTime = lastErrorToastTime;
	}
	
	public AsyncRequest<Z> start() {
		trackStart();
		task.execute(request);
		return this;
	}

	public static void trackStart() {
		int activeCount = AsyncRequest.activeCount.incrementAndGet();
		synchronized (onLoadingStatusChangedListeners_lock) {
			for (int i = 0, len = onLoadingStatusChangedListeners.size(); i < len; i++) {
				onLoadingStatusChangedListeners.get(i).onLoadingStatusChanged(LoadingStatus.Start, activeCount);
			}
		}
	}
	
	public Request getRequest() {
		return request;
	}
	
	public static void addOnLoadingStatusChangedListener(OnLoadingStatusChangedListener onLoadingStatusChangedListener) {
		synchronized (onLoadingStatusChangedListeners_lock) {
			onLoadingStatusChangedListeners.add(onLoadingStatusChangedListener);
		}
	}
	
	public static void removeOnLoadingStatusChangedListener(OnLoadingStatusChangedListener onLoadingStatusChangedListener) {
		synchronized (onLoadingStatusChangedListeners_lock) {
			onLoadingStatusChangedListeners.remove(onLoadingStatusChangedListener);
		}
	}
	
	public static int getActiveCount() {
		return activeCount.get();
	}
	
	/**
	 * Used to determine if this request has no more effect and hence safe to do additional requests whose response
	 * may conflict with the currently executing request's response.
	 * @return true if this task has been returned with a response OR {@link #cancel()} has been called.
	 */
	public boolean isFinished() {
		return finished;
	}
	
	public boolean isCancelled() {
		return task.isCancelled();
	}
	
	protected void consume() {
		consumed = true;
	}
}
