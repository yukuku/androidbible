package yuku.alkitab.readingplanconverter;

import yuku.alkitab.readingplan.model.ByDayPlanData;
import yuku.alkitab.readingplan.model.Plan;
import yuku.bintex.BintexWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

public class OutputBintex {
	public static final String TAG = OutputBintex.class.getSimpleName();

	public void output(Plan plan, File output) throws Exception {
		BintexWriter bw = new BintexWriter(new FileOutputStream(output));

		bw.writeInt(0x98587526); // magic
		bw.writeUint8(1); // version

		bw.writeValueString(plan.name);
		bw.writeValueString(plan.title);
		bw.writeValueString(plan.description);

		bw.writeValueString(plan.kind.name());

		if (plan.data instanceof ByDayPlanData) {
			final List<ByDayPlanData.DayReading> dayReadings = ((ByDayPlanData) plan.data).dayReadings;
			bw.writeUint16(dayReadings.size());

			for (ByDayPlanData.DayReading dayReading : dayReadings) {
				bw.writeUint8(dayReading.aris.length);
				for (int ari : dayReading.aris) {
					bw.writeVarUint(ari);
				}
			}
		}

		bw.close();
	}
}
