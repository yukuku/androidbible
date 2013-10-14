package yuku.alkitab.reminder;

import yuku.alkitab.reminder.util.DevotionReminder;

public class App extends yuku.afw.App {
	@Override
	public void onCreate() {
		super.onCreate();
		DevotionReminder.scheduleAlarm(context);
	}
}