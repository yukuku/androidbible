package yuku.alkitab.base.sync;

import android.util.Pair;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.util.Literals;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.base.widget.AttributeView;
import yuku.alkitab.model.ProgressMark;

/**
 * Pin is the new name for progress mark.
 */
public class Sync_Pins {
	private static final String GID_SPECIAL_PINS = "g2:pins";

	/**
	 * @return base revno, delta of shadow -> current.
	 */
	public static Pair<Sync.ClientState<Content>, List<Sync.Entity<Content>>> getClientStateAndCurrentEntities() {
		final SyncShadow ss = S.getDb().getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_PINS);

		final List<Sync.Entity<Content>> srcs = ss == null? Literals.List(): entitiesFromShadow(ss);
		final List<Sync.Entity<Content>> dsts = getEntitiesFromCurrent();

		final Sync.Delta<Content> delta = new Sync.Delta<>();

		// additions and modifications (should not happen at all for pins)
		for (final Sync.Entity<Content> dst : dsts) {
			final Sync.Entity<Content> existing = SyncUtils.findEntity(srcs, dst.gid, dst.kind);

			if (existing == null) {
				delta.operations.add(new Sync.Operation<>(Sync.Opkind.add, dst.kind, dst.gid, dst.content));
			} else {
				if (!SyncUtils.isSameContent(dst, existing)) { // only when it changes
					delta.operations.add(new Sync.Operation<>(Sync.Opkind.mod, dst.kind, dst.gid, dst.content));
				}
			}
		}

		// deletions
		for (final Sync.Entity<Content> src : srcs) {
			final Sync.Entity<Content> still_have = SyncUtils.findEntity(dsts, src.gid, src.kind);
			if (still_have == null) {
				delta.operations.add(new Sync.Operation<>(Sync.Opkind.del, src.kind, src.gid, null));
			}
		}

		return Pair.create(new Sync.ClientState<>(ss == null ? 0 : ss.revno, delta), dsts);
	}

	private static List<Sync.Entity<Content>> entitiesFromShadow(@NonNull final SyncShadow ss) {
		final BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(ss.data), Charset.forName("utf-8")));
		final Sync.SyncShadowDataJson<Content> data = App.getDefaultGson().fromJson(reader, new TypeToken<Sync.SyncShadowDataJson<Content>>() {}.getType());
		return data.entities;
	}

	@NonNull public static SyncShadow shadowFromEntities(@NonNull final List<Sync.Entity<Content>> entities, final int revno) {
		final Sync.SyncShadowDataJson<Content> data = new Sync.SyncShadowDataJson<>();
		data.entities = entities;
		final ByteArrayOutputStream baos = new ByteArrayOutputStream();
		final BufferedWriter w = new BufferedWriter(new OutputStreamWriter(baos, Charset.forName("utf-8")));
		App.getDefaultGson().toJson(data, new TypeToken<Sync.SyncShadowDataJson<Content>>() {}.getType(), w);
		SyncUtils.wontThrow(w::flush);
		final SyncShadow res = new SyncShadow();
		res.data = baos.toByteArray();
		res.syncSetName = SyncShadow.SYNC_SET_PINS;
		res.revno = revno;
		return res;
	}

	@NonNull public static List<Sync.Entity<Content>> getEntitiesFromCurrent() {
		final List<Sync.Entity<Content>> res = new ArrayList<>();

		final Content content = new Content();
		final List<Content.Pin> pins = content.pins = new ArrayList<>();

		for (int preset_id = 0; preset_id < AttributeView.PROGRESS_MARK_TOTAL_COUNT; preset_id++) {
			final ProgressMark pm = S.getDb().getProgressMarkByPresetId(preset_id);
			if (pm == null) continue;

			final Content.Pin pin = new Content.Pin();
			pin.preset_id = pm.preset_id;
			pin.ari = pm.ari;
			pin.caption = pm.caption;
			pin.modifyTime = Sqlitil.toInt(pm.modifyTime);
			pins.add(pin);
		}

		final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_PINS, GID_SPECIAL_PINS, content);
		res.add(entity);

		return res;
	}

	@Keep
	public static class Content {
		public List<Pin> pins;

		static Comparator<Pin> listSorter = (lhs, rhs) -> lhs.preset_id - rhs.preset_id;

		//region boilerplate equals and hashCode methods

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			final Content content = (Content) o;

			if (pins == null) {
				if (content.pins != null) return false;
			} else {
				if (content.pins == null) return false;

				final List<Pin> list1 = new ArrayList<>(pins);
				final List<Pin> list2 = new ArrayList<>(content.pins);

				Collections.sort(list1, listSorter);
				Collections.sort(list2, listSorter);

				if (!pins.equals(content.pins)) return false;
			}

			return true;
		}

		@Override
		public int hashCode() {
			return pins != null ? pins.hashCode() : 0;
		}

		//endregion

		@Override
		public String toString() {
			return "Content{" +
				"pins=" + pins +
				'}';
		}

		@Keep
		public static class Pin {
			public int preset_id;
			public String caption;
			public int ari;
			public int modifyTime;

			//region boilerplate equals and hashCode methods

			@Override
			public boolean equals(final Object o) {
				if (this == o) return true;
				if (o == null || getClass() != o.getClass()) return false;

				final Pin pin = (Pin) o;

				if (preset_id != pin.preset_id) return false;
				if (ari != pin.ari) return false;
				if (modifyTime != pin.modifyTime) return false;
				if (caption != null ? !caption.equals(pin.caption) : pin.caption != null) return false;

				return true;
			}

			@Override
			public int hashCode() {
				int result = preset_id;
				result = 31 * result + (caption != null ? caption.hashCode() : 0);
				result = 31 * result + ari;
				result = 31 * result + modifyTime;
				return result;
			}

			//endregion

			@Override
			public String toString() {
				return "Pin{" +
					"preset_id=" + preset_id +
					", caption='" + caption + '\'' +
					", ari=" + ari +
					", modifyTime=" + modifyTime +
					'}';
			}
		}
	}
}
