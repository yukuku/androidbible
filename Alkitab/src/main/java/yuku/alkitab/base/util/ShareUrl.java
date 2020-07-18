package yuku.alkitab.base.util;

import android.app.Activity;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.afollestad.materialdialogs.MaterialDialog;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import yuku.alkitab.base.App;
import yuku.alkitab.base.connection.Connections;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;
import yuku.alkitab.model.Version;
import yuku.alkitab.util.Ari;
import yuku.alkitab.util.IntArrayList;

public class ShareUrl {
    public interface Callback {
        void onSuccess(String shareUrl);
        void onUserCancel();
        void onError(Exception e);
        void onFinally();
    }

    public static void make(@NonNull final Activity activity, final boolean immediatelyCancel, @NonNull final String verseText, final int ari_bc, @NonNull final IntArrayList selectedVerses_1, @NonNull final String reference, @NonNull final Version version, @Nullable final String preset_name, @NonNull final Callback callback) {
        if (immediatelyCancel) { // user explicitly ask for not submitting url
            callback.onUserCancel();
            callback.onFinally();
            return;
        }

        final StringBuilder aris = new StringBuilder();

        for (int i = 0, len = selectedVerses_1.size(); i < len; i++) {
            final int verse_1 = selectedVerses_1.get(i);
            final int ari = Ari.encodeWithBc(ari_bc, verse_1);
            if (aris.length() != 0) {
                aris.append(',');
            }
            aris.append(ari);
        }
        final FormBody.Builder form = new FormBody.Builder()
            .add("verseText", verseText)
            .add("aris", aris.toString())
            .add("verseReferences", reference);

        if (preset_name != null) {
            form.add("preset_name", preset_name);
        }

        final String versionLongName = version.getLongName();
        if (versionLongName != null) {
            form.add("versionLongName", versionLongName);
        }

        final String versionShortName = version.getShortName();
        if (versionShortName != null) {
            form.add("versionShortName", versionShortName);
        }

        final Call call = Connections.getOkHttp().newCall(
            new Request.Builder()
                .url(BuildConfig.SERVER_HOST + "v/create")
                .post(form.build())
                .build()
        );

        // when set to true, do not call any callback
        final AtomicBoolean done = new AtomicBoolean();

        final MaterialDialog dialog = new MaterialDialog.Builder(activity)
            .content("Getting share URL…")
            .progress(true, 0)
            .negativeText(R.string.cancel)
            .onNegative((dialog1, which) -> {
                if (done.getAndSet(true)) return;
                done.set(true);

                callback.onUserCancel();
                try {
                    dialog1.dismiss();
                } catch (Exception ignored) {
                }

                callback.onFinally();
            })
            .dismissListener(dialog1 -> {
                if (done.getAndSet(true)) return;

                callback.onUserCancel();
                try {
                    dialog1.dismiss();
                } catch (Exception ignored) {
                }

                callback.onFinally();
            })
            .show();

        call.enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(@NonNull final Call call, @NonNull final IOException e) {
                if (!done.getAndSet(true)) {
                    onComplete(() -> callback.onError(e));
                }
            }

            @Override
            public void onResponse(@NonNull final Call call, @NonNull final Response response) {
                if (done.getAndSet(true)) return;

                final ResponseBody body = response.body();
                if (body == null) {
                    onComplete(() -> callback.onError(new IOException("empty response body")));
                } else {
                    try {
                        final ShareUrlResponseJson obj = App.getDefaultGson().fromJson(body.charStream(), ShareUrlResponseJson.class);
                        if (obj.success) {
                            onComplete(() -> callback.onSuccess(obj.share_url));
                        } else {
                            onComplete(() -> callback.onError(new Exception(obj.message)));
                        }
                    } catch (JsonParseException e) {
                        onComplete(() -> callback.onError(e));
                    }
                }
            }

            void onComplete(@NonNull final Runnable todo) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) return;

                    todo.run();

                    try {
                        dialog.dismiss();
                    } catch (Exception ignored) {
                    }

                    callback.onFinally();
                });
            }
        });
    }

    @Keep
    static class ShareUrlResponseJson {
        public boolean success;
        public String message;
        public String share_url;
    }
}
