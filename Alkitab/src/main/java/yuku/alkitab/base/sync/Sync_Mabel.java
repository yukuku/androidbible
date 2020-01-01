package yuku.alkitab.base.sync;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.gson.reflect.TypeToken;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.util.Literals;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

public class Sync_Mabel {
	public static Sync.GetClientStateResult<Content> getClientStateAndCurrentEntities() {
		final SyncShadow ss = S.getDb().getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL);

		final List<Sync.Entity<Content>> srcs = ss == null? Literals.List(): entitiesFromShadow(ss);
		final List<Sync.Entity<Content>> dsts = getEntitiesFromCurrent();

		final Sync.Delta<Content> delta = new Sync.Delta<>();

		// additions and modifications
		for (final Sync.Entity<Content> dst : dsts) {
			final Sync.Entity<Content> existing = findEntity(srcs, dst.gid, dst.kind);

			if (existing == null) {
				delta.operations.add(new Sync.Operation<>(Sync.Opkind.add, dst.kind, dst.gid, dst.content));
			} else {
				if (!isSameContent(dst, existing)) { // only when it changes
					delta.operations.add(new Sync.Operation<>(Sync.Opkind.mod, dst.kind, dst.gid, dst.content));
				}
			}
		}

		// deletions
		for (final Sync.Entity<Content> src : srcs) {
			final Sync.Entity<Content> still_have = findEntity(dsts, src.gid, src.kind);
			if (still_have == null) {
				delta.operations.add(new Sync.Operation<>(Sync.Opkind.del, src.kind, src.gid, null));
			}
		}

		return new Sync.GetClientStateResult<>(new Sync.ClientState<>(ss == null ? 0 : ss.revno, delta), srcs, dsts);
	}

	private static boolean isSameContent(final Sync.Entity<Content> a, final Sync.Entity<Content> b) {
		if (!U.equals(a.gid, b.gid)) return false;
		if (!U.equals(a.kind, b.kind)) return false;

		return U.equals(a.content, b.content);
	}

	private static Sync.Entity<Content> findEntity(final List<Sync.Entity<Content>> list, final String gid, final String kind) {
		for (final Sync.Entity<Content> entity : list) {
			if (U.equals(gid, entity.gid) && U.equals(kind, entity.kind)) {
				return entity;
			}
		}
		return null;
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
		res.syncSetName = SyncShadow.SYNC_SET_MABEL;
		res.revno = revno;
		return res;
	}

	@NonNull public static List<Sync.Entity<Content>> getEntitiesFromCurrent() {
		final List<Sync.Entity<Content>> res = new ArrayList<>();

		{ // markers
			for (final Marker marker : S.getDb().listAllMarkers()) {
				final Content content = new Content();
				content.ari = marker.ari;
				content.caption = marker.caption;
				content.kind = marker.kind.code;
				content.verseCount = marker.verseCount;
				content.createTime = Sqlitil.toInt(marker.createTime);
				content.modifyTime = Sqlitil.toInt(marker.modifyTime);

				final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_MARKER, marker.gid, content);
				res.add(entity);
			}
		}

		{ // labels
			for (final Label label : S.getDb().listAllLabels()) {
				final Content content = new Content();
				content.title = label.title;
				content.backgroundColor = label.backgroundColor;
				content.ordering = label.ordering;

				final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_LABEL, label.gid, content);
				res.add(entity);
			}
		}

		{ // marker_labels
			for (final Marker_Label marker_label : S.getDb().listAllMarker_Labels()) {
				final Content content = new Content();
				content.marker_gid = marker_label.marker_gid;
				content.label_gid = marker_label.label_gid;

				final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_MARKER_LABEL, marker_label.gid, content);
				res.add(entity);
			}
		}

		return res;
	}

	/**
	 * Modify or create a label from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param label an existing label (content will be modified), or null to create a new label
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Label updateLabelWithEntityContent(@Nullable final Label label, @NonNull final String gid, @NonNull final Content content) {
		final Label res = label != null ? label : Label.createEmptyLabel();

		res.gid = gid;
		res.title = content.title;
		res.ordering = content.ordering;
		res.backgroundColor = content.backgroundColor;

		return res;
	}

	/**
	 * Modify or create a marker-label association from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param marker_label an existing marker-label association (content will be modified), or null to create a new marker-label association
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Marker_Label updateMarker_LabelWithEntityContent(@Nullable final Marker_Label marker_label, @NonNull final String gid, @NonNull final Content content) {
		final Marker_Label res = marker_label != null ? marker_label : Marker_Label.createEmptyMarker_Label();

		res.gid = gid;
		res.marker_gid = content.marker_gid;
		res.label_gid = content.label_gid;

		return res;
	}

	/**
	 * Modify or create a marker from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param marker an existing marker (content will be modified), or null to create a new marker
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Marker updateMarkerWithEntityContent(@Nullable final Marker marker, @NonNull final String gid, @NonNull final Content content) {
		final Marker res = marker != null ? marker : Marker.createEmptyMarker();

		res.gid = gid;
		res.ari = content.ari;
		res.kind = Marker.Kind.fromCode(content.kind);
		res.caption = content.caption;
		res.verseCount = content.verseCount;
		res.createTime = Sqlitil.toDate(content.createTime);
		res.modifyTime = Sqlitil.toDate(content.modifyTime);

		return res;
	}

	/**
	 * Entity content for {@link yuku.alkitab.model.Marker} and {@link yuku.alkitab.model.Label}.
	 */
	@Keep
	public static class Content {
		public Integer ari; // marker
		public Integer kind; // marker
		public String caption; // marker
		public Integer verseCount; // marker
		public Integer createTime; // marker
		public Integer modifyTime; // marker
		public String title; // label
		public Integer ordering; // label
		public String backgroundColor; // label
		public String marker_gid; // marker_label
		public String label_gid; // marker_label

		//region boilerplate equals and hashCode methods
		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			final Content that = (Content) o;

			if (ari != null ? !ari.equals(that.ari) : that.ari != null) return false;
			if (backgroundColor != null ? !backgroundColor.equals(that.backgroundColor) : that.backgroundColor != null) return false;
			if (caption != null ? !caption.equals(that.caption) : that.caption != null) return false;
			if (createTime != null ? !createTime.equals(that.createTime) : that.createTime != null) return false;
			if (kind != null ? !kind.equals(that.kind) : that.kind != null) return false;
			if (label_gid != null ? !label_gid.equals(that.label_gid) : that.label_gid != null) return false;
			if (marker_gid != null ? !marker_gid.equals(that.marker_gid) : that.marker_gid != null) return false;
			if (modifyTime != null ? !modifyTime.equals(that.modifyTime) : that.modifyTime != null) return false;
			if (ordering != null ? !ordering.equals(that.ordering) : that.ordering != null) return false;
			if (title != null ? !title.equals(that.title) : that.title != null) return false;
			if (verseCount != null ? !verseCount.equals(that.verseCount) : that.verseCount != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = ari != null ? ari.hashCode() : 0;
			result = 31 * result + (kind != null ? kind.hashCode() : 0);
			result = 31 * result + (caption != null ? caption.hashCode() : 0);
			result = 31 * result + (verseCount != null ? verseCount.hashCode() : 0);
			result = 31 * result + (createTime != null ? createTime.hashCode() : 0);
			result = 31 * result + (modifyTime != null ? modifyTime.hashCode() : 0);
			result = 31 * result + (title != null ? title.hashCode() : 0);
			result = 31 * result + (ordering != null ? ordering.hashCode() : 0);
			result = 31 * result + (backgroundColor != null ? backgroundColor.hashCode() : 0);
			result = 31 * result + (marker_gid != null ? marker_gid.hashCode() : 0);
			result = 31 * result + (label_gid != null ? label_gid.hashCode() : 0);
			return result;
		}
		//endregion

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder("{");
			if (ari != null) sb.append(ari).append(' ');
			if (kind != null) sb.append(kind).append(' ');
			if (caption != null) sb.append(q(caption)).append(' ');
			if (verseCount != null) sb.append(verseCount).append(' ');
			if (createTime != null) sb.append(createTime).append(' ');
			if (modifyTime != null) sb.append(modifyTime).append(' ');
			if (title != null) sb.append(q(title)).append(' ');
			if (ordering != null) sb.append(ordering).append(' ');
			if (backgroundColor != null) sb.append(backgroundColor).append(' ');
			if (marker_gid != null) sb.append(marker_gid.length() <= 10? marker_gid: marker_gid.substring(0, 10)).append(' ');
			if (label_gid != null) sb.append(label_gid.length() <= 10? label_gid: label_gid.substring(0, 10)).append(' ');

			sb.setLength(sb.length() - 1);
			sb.append('}');
			return sb.toString();
		}

		@NonNull
		static String q(@NonNull String s) {
			final String c;
			if (s.length() > 20) {
				c = s.substring(0, 19).replace("\n", "\\n") + "…";
			} else {
				c = s.replace("\n", "\\n");
			}
			return "'" + c + "'";
		}
	}
}
