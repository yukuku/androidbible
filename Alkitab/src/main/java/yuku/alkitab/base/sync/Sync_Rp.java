package yuku.alkitab.base.sync;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import android.util.Pair;
import com.google.gson.reflect.TypeToken;
import gnu.trove.map.hash.TObjectLongHashMap;
import gnu.trove.set.TIntSet;
import yuku.alkitab.base.App;
import yuku.alkitab.base.S;
import yuku.alkitab.base.U;
import yuku.alkitab.base.model.ReadingPlan;
import yuku.alkitab.base.model.SyncShadow;
import yuku.alkitab.base.util.Literals;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reading plan sync
 */
public class Sync_Rp {
	/**
	 * @return base revno, delta of shadow -> current.
	 */
	public static Pair<Sync.ClientState<Content>, List<Sync.Entity<Content>>> getClientStateAndCurrentEntities() {
		final SyncShadow ss = S.getDb().getSyncShadowBySyncSetName(SyncShadow.SYNC_SET_RP);

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

		return Pair.create(new Sync.ClientState<>(ss == null ? 0 : ss.revno, delta), dsts);
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
		U.wontThrow(() -> w.flush());
		final SyncShadow res = new SyncShadow();
		res.data = baos.toByteArray();
		res.syncSetName = SyncShadow.SYNC_SET_RP;
		res.revno = revno;
		return res;
	}

	@NonNull public static List<Sync.Entity<Content>> getEntitiesFromCurrent() {
		final List<Sync.Entity<Content>> res = new ArrayList<>();

		// lookup map for startTime
		final List<ReadingPlan.ReadingPlanInfo> infos = S.getDb().listAllReadingPlanInfo();
		final TObjectLongHashMap<String /* gid */> /* long startTime */ startTimes = new TObjectLongHashMap<>(infos.size());
		for (final ReadingPlan.ReadingPlanInfo info : infos) {
			startTimes.put(ReadingPlan.gidFromName(info.name), info.startTime);
		}

		// The only source of data is from ReadingPlanProgress table,
		// but since reading plans with no done is not listed in ReadingPlanProgress,
		// we need to consult ReadingPlan table to know what they are.
		final Map<String /* gid */, TIntSet /* done reading codes */> map = S.getDb().getReadingPlanProgressSummaryForSync();
		for (final Map.Entry<String, TIntSet> e : map.entrySet()) {
			final String gid = e.getKey();

			final Content content = new Content();
			content.startTime = startTimes.containsKey(gid)? startTimes.get(gid): null;

			final TIntSet set = e.getValue();
			final Set<Integer> done = content.done = new LinkedHashSet<>(set.size());
			set.forEach(value -> {
				done.add(value);
				return true;
			});

			final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_RP_PROGRESS, gid, content);
			res.add(entity);
		}

		// add remaining reading plans without any done
		startTimes.forEachEntry((gid, startTime) -> {
			if (!map.containsKey(gid)) {
				final Content content = new Content();
				content.startTime = startTime;
				content.done = new LinkedHashSet<>();

				final Sync.Entity<Content> entity = new Sync.Entity<>(Sync.Entity.KIND_RP_PROGRESS, gid, content);
				res.add(entity);
			}
			return true;
		});

		return res;
	}

	@Keep
	public static class Content {
		public Long startTime; // time in millis when the reading plan has started. Can be null, if no such data is found. Server should always prioritize entities with non-null startTime.
		public Set<Integer> done; // reading codes that are checked

		//region boilerplate equals and hashCode methods

		@Override
		public boolean equals(final Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;

			final Content content = (Content) o;

			if (startTime != null ? !startTime.equals(content.startTime) : content.startTime != null) return false;
			if (done != null ? !done.equals(content.done) : content.done != null) return false;

			return true;
		}

		@Override
		public int hashCode() {
			int result = startTime != null ? startTime.hashCode() : 0;
			result = 31 * result + (done != null ? done.hashCode() : 0);
			return result;
		}

		//endregion

		@Override
		public String toString() {
			return "Content{" +
				"startTime=" + startTime +
				", done=" + done +
				'}';
		}
	}
}
