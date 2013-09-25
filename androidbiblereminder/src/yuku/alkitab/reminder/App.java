package yuku.alkitab.reminder;

import yuku.alkitab.reminder.br.DevotionReminderReceiver;

public class App extends yuku.afw.App {
	@Override
	public void onCreate() {
		super.onCreate();
		DevotionReminderReceiver.scheduleAlarm(context);
	}
}