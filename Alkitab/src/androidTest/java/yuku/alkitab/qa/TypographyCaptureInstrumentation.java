package yuku.alkitab.qa;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.JsonWriter;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import androidx.appcompat.widget.SearchView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.core.view.GravityCompat;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import yuku.alkitab.base.App;
import yuku.alkitab.base.ac.NoteActivity;
import yuku.alkitab.base.ac.HelpActivity;
import yuku.alkitab.base.ac.GotoActivity;
import yuku.alkitab.base.ac.SearchActivity;
import yuku.alkitab.base.dialog.TypeHighlightDialog;
import yuku.alkitab.base.dialog.TypeBookmarkDialog;
import yuku.alkitab.base.dialog.ProgressMarkListDialog;
import yuku.alkitab.model.Marker;
import androidx.fragment.app.FragmentActivity;
import java.util.Date;
import yuku.alkitab.base.feedback.PrereleaseFeedback;
import yuku.alkitab.debug.BuildConfig;
import yuku.alkitab.debug.R;

public class TypographyCaptureInstrumentation extends Instrumentation {
    private Bundle arguments;
    private Activity activity;
    private File output;
    private final List<String> failures = new ArrayList<>();

    @Override public void onCreate(Bundle arguments) {
        this.arguments = arguments;
        start();
    }

    @Override public void onStart() {
        try {
            String phase = arguments.getString("phase");
            if (!"before".equals(phase) && !"after".equals(phase)) throw new IllegalArgumentException("phase must be before or after");
            String folder = arguments.getString("folder", phase);
            if (!folder.matches("[a-z0-9-]+")) throw new IllegalArgumentException("Invalid output folder");
            output = new File(getTargetContext().getExternalFilesDir(null), "typography/" + folder);
            if (!output.mkdirs() && !output.isDirectory()) throw new IllegalStateException("Cannot create output directory");
            getTargetContext().getSharedPreferences("yuku.alkitab.debug_preferences", 0).edit()
                .putString("prerelease_feedback_hidden_commit", BuildConfig.LAST_COMMIT_HASH)
                .putString("lastVersionId", "preset/in-bimk")
                .putBoolean("useLegacyVerseItem", false).putBoolean("useLegacyToolbar", false).apply();
            launch("yuku.alkitab.base.IsiActivity", new Bundle());
            seedFixtures();
            reader();
            capture("reader");
            String requested = arguments.getString("cases");
            List<String> cases = requested == null ? Arrays.asList(
                "verse-selection", "drawer", "appearance", "appearance-font-menu", "versions", "version-picker", "goto-dialer", "goto-direct", "goto-grid", "goto-book-menu",
                "search", "search-results", "search-filter", "settings", "settings-display", "settings-usage", "settings-copy", "settings-transfer", "settings-experimental",
                "sign-in", "create-account", "reset-password", "sync-log", "about", "feedback", "devotion", "devotion-selector", "song", "song-selector", "song-book-menu", "song-search", "song-media", "song-drawer",
                "plan", "plan-drawer", "plan-date", "plan-list", "plan-catalog", "song-catalog", "markers", "bookmarks", "notes", "highlights", "note-editor",
                "bookmark-editor", "label-chooser", "progress", "correction", "correction-form", "verse-dialog", "xref", "report-verse", "pdb-options", "debug-sync", "alert",
                "legacy-reader", "legacy-goto-dialer", "legacy-goto-grid", "legacy-sign-in", "legacy-song",
                "highlight", "color-grid", "color-spectrum", "color-sliders", "colors-day", "colors-night", "fonts", "widget", "transfer-export", "audio", "audio-selector", "audio-speed", "audio-source", "audio-log"
            ) : Arrays.asList(requested.split(","));
            for (String name : cases) {
                try {
                    show(name);
                    settle();
                    capture(name);
                    Bundle status = new Bundle();
                    status.putString("stream", "Captured " + name + "\n");
                    sendStatus(0, status);
                } catch (Throwable error) {
                    failures.add(name + ": " + error);
                } finally {
                    if (activity != null) runOnMainSync(() -> activity.finish());
                    SystemClock.sleep(250);
                }
            }
            try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(new File(output, "result.json")), "UTF-8"))) {
                writer.setIndent("  ");
                writer.beginObject().name("phase").value(phase).name("failures").beginArray();
                for (String failure : failures) writer.value(failure);
                writer.endArray().endObject();
            }
            Bundle result = new Bundle();
            result.putString("stream", failures.isEmpty() ? "All typography captures completed\n" : failures.toString());
            finish(failures.isEmpty() ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        } catch (Throwable error) {
            Bundle result = new Bundle();
            result.putString("stream", error.toString());
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private void captureNativeFonts(String name) {
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            runOnMainSync(() -> {
                try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(new File(output, name + "-native-fonts.json")), "UTF-8"))) {
                    writer.setIndent("  ");
                    writer.beginArray();
                    ArrayDeque<View> views = new ArrayDeque<>(android.view.inspector.WindowInspector.getGlobalWindowViews());
                    while (!views.isEmpty()) {
                        View view = views.remove();
                        Rect bounds = new Rect();
                        if (view instanceof TextView && view.isShown() && view.getGlobalVisibleRect(bounds)) {
                            TextView text = (TextView) view;
                            if (text.length() > 0) {
                                String id = view.getId() < 0x01000000 ? "" : view.getResources().getResourceName(view.getId());
                                String sample = text.getText().toString();
                                writer.beginObject().name("id").value(id).name("text").value(sample.substring(0, Math.min(100, sample.length())))
                                    .name("sizeSp").value(text.getTextSize() / text.getResources().getDisplayMetrics().scaledDensity)
                                    .name("weight").value(text.getTypeface().getWeight()).name("italic").value(text.getTypeface().isItalic()).endObject();
                            }
                        }
                        if (view instanceof android.view.ViewGroup) {
                            android.view.ViewGroup group = (android.view.ViewGroup) view;
                            for (int i = 0; i < group.getChildCount(); i++) views.add(group.getChildAt(i));
                        }
                    }
                    writer.endArray();
                } catch (Exception error) {
                    throw new IllegalStateException("Cannot record native typography", error);
                }
            });
        }
    }

    private void launch(String className, Bundle extras) throws Exception {
        Intent intent = new Intent();
        intent.setClassName(getTargetContext(), className);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.putExtras(extras);
        ActivityMonitor monitor = addMonitor(className, null, false);
        runOnMainSync(() -> getTargetContext().startActivity(intent));
        activity = monitor.waitForActivityWithTimeout(60000);
        removeMonitor(monitor);
        if (activity == null) throw new IllegalStateException("Activity did not start: " + className);
        settle();
    }

    private void reader() throws Exception {
        launch("yuku.alkitab.base.IsiActivity", new Bundle());
    }

    private void settings(String fragment) throws Exception {
        Bundle extras = new Bundle();
        if (fragment != null) extras.putString("subClassName", "yuku.alkitab.base.settings." + fragment);
        launch("yuku.alkitab.base.settings.SettingsActivity", extras);
    }

    private void show(String name) throws Exception {
        getTargetContext().getSharedPreferences("yuku.alkitab.debug_preferences", 0).edit()
            .putInt("goto_last_tab", 1)
            .putBoolean("useLegacyVerseItem", name.equals("legacy-reader"))
            .putBoolean("useLegacyToolbar", name.equals("legacy-reader"))
            .putBoolean("useLegacyGoto", name.startsWith("legacy-goto"))
            .putBoolean("useLegacySong", name.equals("legacy-song"))
            .putBoolean("useLegacySyncLogin", name.equals("legacy-sign-in")).apply();
        switch (name) {
            case "verse-selection":
                reader();
                yuku.alkitab.util.IntArrayList selected = new yuku.alkitab.util.IntArrayList(); selected.add(1);
                runOnMainSync(() -> ((yuku.alkitab.base.IsiActivity) activity).getLsSplit0().checkVerses(selected, true)); break;
            case "audio-source": case "audio-log":
                runOnMainSync(() -> App.services.versions.setActiveVersion(App.services.versions.getVersionFromVersionId("preset/in-bimk")));
                reader(); runOnMainSync(() -> TypographyComponentFixtures.showAudio(activity, name.equals("audio-source"))); break;
            case "drawer": reader(); drawer(); break;
            case "appearance": case "appearance-font-menu":
                reader(); clickView(R.id.bDisplay); settle();
                if (name.endsWith("menu")) click("Roboto");
                break;
            case "versions": case "versions-downloaded": case "version-details":
                launch("yuku.alkitab.versionmanager.VersionsActivity", new Bundle());
                if (name.equals("versions-downloaded")) click("Terunduh");
                if (name.equals("version-details")) click("BIMK");
                break;
            case "version-picker": reader(); click("BIMK"); break;
            case "goto-dialer": case "goto-direct": case "goto-grid": case "goto-book-menu":
            case "goto-typed": case "goto-verse-typed": case "goto-grid-chapter": case "goto-grid-verse": case "goto-direct-candidates":
                Bundle go = new Bundle(); go.putInt("bookId", 0); go.putInt("chapter", 1); go.putInt("verse", 1);
                launch("yuku.alkitab.base.ac.GotoActivity", go);
                click(name.startsWith("goto-direct") ? "Ketik" : name.startsWith("goto-grid") ? "Pilih" : "Telusuri");
                if (name.equals("goto-book-menu")) { settle(); click("Kejadian"); }
                if (name.startsWith("goto-grid-")) click("Kej");
                if (name.equals("goto-grid-verse")) click("1");
                if (name.equals("goto-typed") || name.equals("goto-verse-typed")) { clickKey("3"); clickKey("4"); }
                if (name.equals("goto-verse-typed")) { clickValue(1); clickKey("2"); }
                if (name.equals("goto-direct-candidates")) {
                    ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
                    nodes.add(getUiAutomation().getRootInActiveWindow());
                    while (!nodes.isEmpty()) {
                        AccessibilityNodeInfo node = nodes.remove();
                        if (node.isEditable()) {
                            Bundle text = new Bundle();
                            text.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "Ke");
                            if (!node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, text)) throw new IllegalStateException("Cannot enter book query");
                            break;
                        }
                        for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
                    }
                    hideKeyboard();
                }
                break;
            case "search": case "search-results": case "search-filter":
                Bundle search = new Bundle(); search.putInt("openedBookId", 0);
                launch("yuku.alkitab.base.ac.SearchActivity", search);
                hideKeyboard();
                if (name.equals("search-results")) { runOnMainSync(() -> ((SearchView) activity.findViewById(R.id.searchView)).setQuery("terang", true)); }
                if (name.equals("search-filter")) clickView(R.id.bEditFilter);
                break;
            case "settings": settings(null); break;
            case "settings-display": settings("DisplayFragment"); break;
            case "settings-usage": settings("UsageFragment"); break;
            case "settings-copy": settings("CopyShareFragment"); break;
            case "settings-transfer": settings("DataTransferFragment"); break;
            case "settings-experimental": settings("ExperimentalFragment"); break;
            case "sign-in": case "create-account": case "reset-password":
                launch("yuku.alkitab.base.sync.SyncLoginActivity", new Bundle());
                if (name.equals("create-account")) click("Buat akun");
                if (name.equals("reset-password")) click("Lupa password?");
                break;
            case "sync-log": launch("yuku.alkitab.base.sync.SyncLogActivity", new Bundle()); break;
            case "about": launch("yuku.alkitab.base.ac.AboutActivity", new Bundle()); break;
            case "feedback": reader(); runOnMainSync(() -> PrereleaseFeedback.INSTANCE.show(activity, null)); break;
            case "devotion": case "devotion-selector":
                launch("yuku.alkitab.base.ac.DevotionActivity", new Bundle());
                if (name.equals("devotion-selector")) click("Santapan Harian ▾");
                break;
            case "song": case "song-selector": case "song-book-menu": case "song-search": case "song-media": case "song-drawer":
            case "song-input": case "song-appended": case "song-four-digits": case "song-drawer-input": case "song-drawer-book-menu":
                launch("yuku.alkitab.songs.SongViewActivity", new Bundle());
                if (name.equals("song-selector") || name.equals("song-book-menu") || name.equals("song-input") || name.equals("song-appended") || name.equals("song-four-digits")) clickView(R.id.toolbar);
                if (name.equals("song-book-menu")) click("KJ");
                if (name.equals("song-search")) click("Cari");
                if (name.equals("song-media")) click(activity.getString(R.string.sn_media_title));
                if (name.startsWith("song-drawer")) drawer();
                if (name.equals("song-drawer-book-menu")) clickView(R.id.bChangeBook);
                if (name.equals("song-input") || name.equals("song-appended") || name.equals("song-drawer-input")) clickKey("2");
                if (name.equals("song-appended")) clickKey("1");
                if (name.equals("song-four-digits")) { clickKey("1"); clickKey("2"); clickKey("3"); clickKey("4"); }
                hideKeyboard();
                break;
            case "plan": case "plan-drawer": case "plan-date": case "plan-list":
                launch("yuku.alkitab.base.ac.ReadingPlanActivity", new Bundle());
                if (name.equals("plan-drawer")) drawer();
                if (name.equals("plan-date")) clickView(R.id.bToday);
                if (name.equals("plan-list")) clickView(R.id.tDetail);
                break;
            case "plan-catalog": case "song-catalog":
                String uri = BuildConfig.SERVER_HOST + (name.equals("plan-catalog") ? "/rp/downloads?" : "/songs/downloads?dataFormatVersion=5&") + App.getAppIdentifierParamsEncoded();
                Bundle help = new Bundle(); help.putString("page", uri); help.putString("overrideTitle", name.equals("plan-catalog") ? "Unduh jadwal baca" : "Unduh buku lagu");
                launch("yuku.alkitab.base.ac.HelpActivity", help);
                String tabLabel = name.equals("plan-catalog") ? "Pilihan" : "Utama";
                long deadline = SystemClock.uptimeMillis() + 45000;
                while (!hasLabel(tabLabel) && SystemClock.uptimeMillis() < deadline) SystemClock.sleep(250);
                if (!hasLabel(tabLabel)) throw new IllegalStateException("Catalog did not finish loading");
                break;
            case "markers": launch("yuku.alkitab.base.ac.MarkersActivity", new Bundle()); break;
            case "bookmarks": case "notes": case "highlights":
                Bundle marker = new Bundle(); marker.putInt("filter_kind", name.equals("bookmarks") ? 1 : name.equals("notes") ? 2 : 3);
                launch("yuku.alkitab.base.ac.MarkerListActivity", marker); break;
            case "note-editor":
                Bundle note = new Bundle(); note.putString("reference", "Kejadian 1:1"); note.putInt("ariForNewNote", 257); note.putInt("verseCountForNewNote", 1);
                launch("yuku.alkitab.base.ac.NoteActivity", note); hideKeyboard(); break;
            case "highlight": case "color-grid": case "color-spectrum": case "color-sliders":
                reader();
                runOnMainSync(() -> new TypeHighlightDialog(activity, 257, color -> {}, -1, null, "Kejadian 1:1", "Pada mulanya, waktu Allah mulai menciptakan alam semesta."));
                settle();
                if (name.startsWith("color-")) click("Warna lainnya");
                if (name.equals("color-spectrum")) click("Spektrum");
                if (name.equals("color-sliders")) click("Penggeser");
                break;
            case "colors-day": case "colors-night":
                Bundle colors = new Bundle(); colors.putBoolean("nightMode", name.equals("colors-night"));
                launch("yuku.alkitab.base.ac.ColorSettingsActivity", colors); break;
            case "transfer-export":
                Bundle transfer = new Bundle(); transfer.putInt("mode", 0);
                launch("yuku.alkitab.datatransfer.ui.DataTransferActivity", transfer); break;
            case "legacy-reader": reader(); break;
            case "legacy-goto-dialer": case "legacy-goto-grid": case "legacy-goto-typed": case "legacy-goto-book-menu":
                Bundle legacyGo = new Bundle(); legacyGo.putInt("bookId", 0); legacyGo.putInt("chapter", 1); legacyGo.putInt("verse", 1);
                launch("yuku.alkitab.base.ac.GotoActivity", legacyGo); click(name.endsWith("grid") ? "PILIH" : "TELUSURI");
                if (name.equals("legacy-goto-typed")) { clickKey("3"); clickKey("4"); }
                if (name.equals("legacy-goto-book-menu")) clickView(R.id.cbBook);
                break;
            case "legacy-sign-in": launch("yuku.alkitab.base.sync.SyncLoginActivity", new Bundle()); break;
            case "legacy-song": launch("yuku.alkitab.songs.SongViewActivity", new Bundle()); break;
            case "song-case-fixture":
                reader(); runOnMainSync(() -> TypographyComponentFixtures.showSongCasing(activity)); break;
            case "bookmark-editor": case "label-chooser":
                reader(); runOnMainSync(() -> TypeBookmarkDialog.NewBookmark(activity, 257, 1).show());
                if (name.equals("label-chooser")) click(activity.getString(R.string.tambah_titiktiga));
                hideKeyboard(); break;
            case "progress":
                reader(); runOnMainSync(() -> new ProgressMarkListDialog().show(((FragmentActivity) activity).getSupportFragmentManager(), "typography")); break;
            case "correction": case "correction-form":
                Bundle patch = new Bundle(); patch.putString("baseBody", "Pada mulanya, waktu Allah mulai menciptakan alam semesta."); patch.putString("extraInfo", "Typography capture fixture");
                launch("yuku.alkitab.base.ac.PatchTextActivity", patch);
                if (name.equals("correction-form")) click("OK");
                hideKeyboard(); break;
            case "verse-dialog":
                Bundle verses = new Bundle(); verses.putString("target", "o:Gen.1.1");
                launch("yuku.alkitab.base.ac.VersesDialogActivity", verses); break;
            case "xref":
                runOnMainSync(() -> App.services.versions.setActiveVersion(App.services.versions.getMVersionInternal()));
                reader();
                yuku.alkitab.base.dialog.XrefDialog xref = yuku.alkitab.base.dialog.XrefDialog.Companion.newInstance((769 << 8) | 1);
                xref.init(App.services.versions.activeVersion(), App.services.versions.activeVersionId(), (source, target) -> kotlin.Unit.INSTANCE);
                runOnMainSync(() -> xref.show(((FragmentActivity) activity).getSupportFragmentManager(), "typography")); break;
            case "report-verse":
                Bundle report = new Bundle(); report.putInt("ari", 257); report.putString("reference", "Kejadian 1:1");
                report.putString("verseText", "Pada mulanya, waktu Allah mulai menciptakan alam semesta."); report.putString("versionDescription", "BIMK");
                launch("yuku.alkitab.ribka.RibkaReportActivity", report); hideKeyboard(); break;
            case "pdb-options":
                reader(); runOnMainSync(() -> {
                    View content = activity.getLayoutInflater().inflate(R.layout.dialog_pdbconvert_options, null);
                    ((TextView) content.findViewById(R.id.lSample)).setText("In the beginning God created heaven and earth");
                    ((android.widget.Spinner) content.findViewById(R.id.cbEncoding)).setAdapter(new android.widget.ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, new String[]{"UTF-8", "ISO-8859-1"}));
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity).setTitle(R.string.pdb_file_options).setView(content).setPositiveButton(R.string.ok, null).setNegativeButton(R.string.cancel, null).show();
                }); break;
            case "debug-sync": launch("yuku.alkitab.base.ac.SecretSyncDebugActivity", new Bundle()); hideKeyboard(); break;
            case "alert":
                Bundle alert = new Bundle(); alert.putString("title", "Perhatian"); alert.putString("message", "Contoh pesan untuk memeriksa tipografi dialog.");
                launch("yuku.alkitab.base.ac.AlertDialogActivity", alert); break;
            case "fonts": launch("yuku.alkitab.base.ac.FontManagerActivity", new Bundle()); SystemClock.sleep(3000); break;
            case "widget":
                Bundle widget = new Bundle(); widget.putInt("appWidgetId", 424242);
                launch("yuku.alkitab.base.appwidget.DailyVerseAppWidgetConfigurationActivity", widget); break;
            case "audio": case "audio-selector": case "audio-speed":
                runOnMainSync(() -> App.services.versions.setActiveVersion(App.services.versions.getMVersionInternal()));
                reader();
                settle();
                if (!hasLabel(activity.getString(R.string.audio_bar_select_audio))) click(activity.getString(R.string.menu_audio));
                settle();
                clickIfPresent("Jeda");
                if (name.equals("audio-selector")) click(activity.getString(R.string.audio_bar_select_audio));
                if (name.equals("audio-speed")) click("1×");
                break;
            default: throw new IllegalArgumentException(name);
        }
    }

    private void drawer() {
        runOnMainSync(() -> ((DrawerLayout) activity.findViewById(R.id.drawerLayout)).openDrawer(GravityCompat.START));
        settle();
    }

    private void clickView(int id) {
        boolean[] clicked = {false};
        runOnMainSync(() -> {
            View view = activity.findViewById(id);
            clicked[0] = view != null && view.performClick();
        });
        if (!clicked[0]) throw new IllegalStateException("Missing clickable view " + id);
        settle();
    }

    private void seedFixtures() {
        Date date = new Date(1793491200000L);
        if (App.services.storage.getDb().listMarkersForAriKind(257, Marker.Kind.bookmark).isEmpty())
            App.services.storage.getDb().insertMarker(257, Marker.Kind.bookmark, "Audit tipografi", 1, date, date);
        if (App.services.storage.getDb().listMarkersForAriKind(258, Marker.Kind.note).isEmpty())
            App.services.storage.getDb().insertMarker(258, Marker.Kind.note, "Catatan uji tipografi untuk membandingkan judul, tanggal, dan isi bacaan.", 1, date, date);
        if (App.services.storage.getDb().listMarkersForAriKind(287, Marker.Kind.highlight).isEmpty())
            App.services.storage.getDb().insertMarker(287, Marker.Kind.highlight, yuku.alkitab.base.util.Highlights.encode(0xffff00), 1, date, date);
        if (App.services.storage.getDb().listAllProgressMarks().isEmpty()) {
            yuku.alkitab.model.ProgressMark progress = new yuku.alkitab.model.ProgressMark();
            progress.preset_id = 0; progress.ari = 257; progress.caption = "Audit tipografi"; progress.modifyTime = date;
            App.services.storage.getDb().insertOrUpdateProgressMark(progress);
        }
        if (App.services.storage.getDb().listAllLabels().isEmpty()) App.services.storage.getDb().insertLabel("Audit tipografi", null);
    }

    private boolean hasLabel(String label) {
        AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
        if (root == null) return false;
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(root);
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.remove();
            if (label.equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString()) || label.equalsIgnoreCase(node.getContentDescription() == null ? "" : node.getContentDescription().toString())) return true;
            for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
        }
        return false;
    }

    private void click(String label) {
        if (!clickIfPresent(label)) throw new IllegalStateException("Missing UI label " + label);
        settle();
    }

    private boolean clickIfPresent(String label) {
        long deadline = SystemClock.uptimeMillis() + 5000;
        do {
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) {
                ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
                nodes.add(root);
                while (!nodes.isEmpty()) {
                    AccessibilityNodeInfo node = nodes.remove();
                    if (label.equalsIgnoreCase(node.getText() == null ? "" : node.getText().toString()) || label.equalsIgnoreCase(node.getContentDescription() == null ? "" : node.getContentDescription().toString())) {
                        Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                        if (!bounds.isEmpty()) { tap(bounds.centerX(), bounds.centerY()); return true; }
                    }
                    for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
                }
            }
            SystemClock.sleep(250);
        } while (SystemClock.uptimeMillis() < deadline);
        return false;
    }

    private void tap(int x, int y) {
        long time = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(time, time, MotionEvent.ACTION_DOWN, x, y, 0);
        MotionEvent up = MotionEvent.obtain(time, time + 60, MotionEvent.ACTION_UP, x, y, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        getUiAutomation().injectInputEvent(down, true); getUiAutomation().injectInputEvent(up, true);
        down.recycle(); up.recycle();
    }

    private void clickValue(int index) {
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(getUiAutomation().getRootInActiveWindow());
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.remove();
            if (node.getStateDescription() != null && index-- == 0) {
                Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                tap(bounds.centerX(), bounds.centerY());
                settle();
                return;
            }
            for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
        }
        throw new IllegalStateException("Missing number value");
    }

    private void clickKey(String label) {
        ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
        nodes.add(getUiAutomation().getRootInActiveWindow());
        while (!nodes.isEmpty()) {
            AccessibilityNodeInfo node = nodes.remove();
            if (label.contentEquals(node.getText() == null ? "" : node.getText())) {
                boolean isValue = false;
                AccessibilityNodeInfo ancestor = node;
                while (ancestor != null) {
                    if (ancestor.getStateDescription() != null) { isValue = true; break; }
                    ancestor = ancestor.getParent();
                }
                if (!isValue) {
                    Rect bounds = new Rect(); node.getBoundsInScreen(bounds);
                    tap(bounds.centerX(), bounds.centerY());
                    settle();
                    return;
                }
            }
            for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
        }
        throw new IllegalStateException("Missing keypad key " + label);
    }

    private void hideKeyboard() {
        runOnMainSync(() -> ((InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0));
        settle();
    }

    private void settle() {
        waitForIdleSync();
        SystemClock.sleep(1200);
    }

    private void capture(String name) throws Exception {
        Bitmap bitmap = getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("Screenshot unavailable");
        try (FileOutputStream stream = new FileOutputStream(new File(output, name + ".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream); }
        bitmap.recycle();
        if (!arguments.containsKey("folder")) return;
        captureNativeFonts(name);
        try (JsonWriter writer = new JsonWriter(new OutputStreamWriter(new FileOutputStream(new File(output, name + "-input-state.json")), "UTF-8"))) {
            writer.setIndent("  ");
            writer.beginArray();
            ArrayDeque<AccessibilityNodeInfo> nodes = new ArrayDeque<>();
            AccessibilityNodeInfo root = getUiAutomation().getRootInActiveWindow();
            if (root != null) nodes.add(root);
            while (!nodes.isEmpty()) {
                AccessibilityNodeInfo node = nodes.remove();
                if (node.getStateDescription() != null) {
                    writer.beginObject().name("text").value(node.getText() == null ? null : node.getText().toString())
                        .name("stateDescription").value(node.getStateDescription().toString()).endObject();
                }
                for (int i = 0; i < node.getChildCount(); i++) { AccessibilityNodeInfo child = node.getChild(i); if (child != null) nodes.add(child); }
            }
            writer.endArray();
        }
    }
}
