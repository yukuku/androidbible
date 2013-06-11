package yuku.alkitab.readingplanconverter;

import yuku.alkitab.readingplan.model.Plan;

import java.io.File;

public class Main {
	public static final String TAG = Main.class.getSimpleName();

	public static void main(String[] args) throws Exception {
		final File planOutputDir = new File("materials/gen/plans");
		planOutputDir.mkdirs();

		for (File f : new File("materials/in/plans/").listFiles()) {
			final Plan plan = new InputTxt().readPlan(f);
			new OutputBintex().output(plan, new File(planOutputDir, plan.name + ".bt"));
		}
	}
}
