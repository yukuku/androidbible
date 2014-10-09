package yuku.alkitab.base.sync;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.gson.Gson;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.util.Sqlitil;
import yuku.alkitab.model.Label;
import yuku.alkitab.model.Marker;
import yuku.alkitab.model.Marker_Label;

import java.util.ArrayList;
import java.util.List;

import static yuku.alkitab.base.util.Literals.List;

public class Sync {
	public enum Opkind {
		add, mod, del, // do not change the enum value names here. This will be un/serialized by gson.
	}

	public static class Operation<C> {
		public Opkind opkind;
		public String kind;
		public String gid;
		public C content;

		public Operation(final Opkind opkind, final String kind, final String gid, final C content) {
			this.opkind = opkind;
			this.kind = kind;
			this.gid = gid;
			this.content = content;
		}

		@Override
		public String toString() {
			return "{" + opkind +
				" " + kind +
				" " + gid.substring(0, 10) +
				" " + content +
				'}';
		}
	}

	public static class Delta<C> {
		@NonNull public List<Operation<C>> operations;

		public Delta() {
			operations = new ArrayList<>();
		}

		@Override
		public String toString() {
			return "Delta{" +
				"operations=" + operations +
				'}';
		}
	}

	public static class Entity<C> {
		public static final String KIND_MARKER = "Marker";
		public static final String KIND_LABEL = "Label";
		public static final String KIND_MARKER_LABEL = "Marker_Label";

		/** Kind of this entity. Currently can be {@link #KIND_MARKER}, {@link #KIND_LABEL}, {@link #KIND_MARKER_LABEL}. */
		public String kind;
		public String gid;
		public C content;
	}

	/**
	 * Entity content for {@link yuku.alkitab.model.Marker} and {@link yuku.alkitab.model.Label}.
	 */
	public static class MabelContent {
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

			final MabelContent that = (MabelContent) o;

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
			if (marker_gid != null) sb.append(marker_gid.substring(0, 10)).append(' ');
			if (label_gid != null) sb.append(label_gid.substring(0, 10)).append(' ');

			sb.setLength(sb.length() - 1);
			sb.append('}');
			return sb.toString();
		}

		@NonNull static String q(@NonNull String s) {
			final String c;
			if (s.length() > 20) {
				c = s.substring(0, 19).replace("\n", "\\n") + "…";
			} else {
				c = s.replace("\n", "\\n");
			}
			return "'" + c + "'";
		}
	}

	public static class SyncShadowMabelDataJson {
		public List<Entity<MabelContent>> entities;
	}

	public static class MabelClientState {
		public int base_revno;
		@NonNull public Delta<MabelContent> delta;

		public MabelClientState(final int base_revno, final @NonNull Delta<MabelContent> delta) {
			this.base_revno = base_revno;
			this.delta = delta;
		}
	}

	/**
	 * @return base revno, delta of shadow -> current.
	 */
	public static MabelClientState getMabelClientState() {
		final SyncShadow ss = S.getDb().getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_MABEL);

		final List<Entity<MabelContent>> srcs = ss == null? List(): mabelEntitiesFromShadow(ss);
		final List<Entity<MabelContent>> dsts = getMabelEntitiesFromCurrent();

		final Delta<MabelContent> delta = new Delta<>();

		// additions and modifications
		for (final Entity<MabelContent> dst : dsts) {
			final Entity<MabelContent> existing = findMabelEntity(srcs, dst.gid, dst.kind);

			if (existing == null) {
				delta.operations.add(new Operation<>(Opkind.add, dst.kind, dst.gid, dst.content));
			} else {
				if (!isSameMabelContent(dst, existing)) { // only when it changes
					delta.operations.add(new Operation<>(Opkind.mod, dst.kind, dst.gid, dst.content));
				}
			}
		}

		// deletions
		for (final Entity<MabelContent> src : srcs) {
			final Entity<MabelContent> still_have = findMabelEntity(dsts, src.gid, src.kind);
			if (still_have == null) {
				delta.operations.add(new Operation<>(Opkind.del, src.kind, src.gid, null));
			}
		}

		return new MabelClientState(ss == null ? 0 : ss.revno, delta);
	}

	private static boolean isSameMabelContent(final Entity<MabelContent> a, final Entity<MabelContent> b) {
		if (!U.equals(a.gid, b.gid)) return false;
		if (!U.equals(a.kind, b.kind)) return false;

		return U.equals(a.content, b.content);
	}

	private static Entity<MabelContent> findMabelEntity(final List<Entity<MabelContent>> list, final String gid, final String kind) {
		for (final Entity<MabelContent> entity : list) {
			if (U.equals(gid, entity.gid) && U.equals(kind, entity.kind)) {
				return entity;
			}
		}
		return null;
	}

	private static List<Entity<MabelContent>> mabelEntitiesFromShadow(@NonNull final SyncShadow ss) {
		final SyncShadowMabelDataJson data = new Gson().fromJson(U.utf8BytesToString(ss.data), SyncShadowMabelDataJson.class);
		return data.entities;
	}

	@NonNull public static SyncShadow shadowFromMabelEntities(@NonNull final List<Entity<MabelContent>> entities, final int revno) {
		final SyncShadowMabelDataJson data = new SyncShadowMabelDataJson();
		data.entities = entities;
		final String s = new Gson().toJson(data);
		final SyncShadow res = new SyncShadow();
		res.data = U.stringToUtf8Bytes(s);
		res.syncSetName = SyncShadow.SYNC_SET_MABEL;
		res.revno = revno;
		return res;
	}

	public static List<Entity<MabelContent>> getMabelEntitiesFromCurrent() {
		final List<Entity<MabelContent>> res = new ArrayList<>();

		{ // markers
			for (final Marker marker : S.getDb().listAllMarkers()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_MARKER;
				entity.gid = marker.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.ari = marker.ari;
				content.caption = marker.caption;
				content.kind = marker.kind.code;
				content.verseCount = marker.verseCount;
				content.createTime = Sqlitil.toInt(marker.createTime);
				content.modifyTime = Sqlitil.toInt(marker.modifyTime);
				res.add(entity);
			}
		}

		{ // labels
			for (final Label label : S.getDb().listAllLabels()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_LABEL;
				entity.gid = label.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.title = label.title;
				content.backgroundColor = label.backgroundColor;
				content.ordering = label.ordering;
				res.add(entity);
			}
		}

		{ // marker_labels
			for (final Marker_Label marker_label : S.getDb().listAllMarker_Labels()) {
				final Entity<MabelContent> entity = new Entity<>();
				entity.kind = Entity.KIND_MARKER_LABEL;
				entity.gid = marker_label.gid;
				final MabelContent content = entity.content = new MabelContent();
				content.marker_gid = marker_label.marker_gid;
				content.label_gid = marker_label.label_gid;
				res.add(entity);
			}
		}

		return res;
	}

	/**
	 * Modify or create a marker from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param marker an existing marker (content will be modified), or null to create a new marker
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Marker updateMarkerWithEntityContent(@Nullable final Marker marker, @NonNull final String gid, @NonNull final MabelContent content) {
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
	 * Modify or create a label from an entity content. This is called when the server append delta
	 * asks for an add or a mod operation.
	 * This will not merge content, will only overwrite.
	 * @param label an existing label (content will be modified), or null to create a new label
	 * @param content entity content, containing the new data.
	 */
	@NonNull public static Label updateLabelWithEntityContent(@Nullable final Label label, @NonNull final String gid, @NonNull final MabelContent content) {
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
	@NonNull public static Marker_Label updateMarker_LabelWithEntityContent(@Nullable final Marker_Label marker_label, @NonNull final String gid, @NonNull final MabelContent content) {
		final Marker_Label res = marker_label != null ? marker_label : Marker_Label.createEmptyMarker_Label();

		res.gid = gid;
		res.marker_gid = content.marker_gid;
		res.label_gid = content.label_gid;

		return res;
	}

}
