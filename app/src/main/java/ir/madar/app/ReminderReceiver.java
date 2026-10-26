package ir.madar.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import java.util.ArrayList;
import java.util.Calendar;

/** Local deadline and daily follow-up notifications. No server or analytics. */
public final class ReminderReceiver extends BroadcastReceiver {
    public static final String CHANNEL = "madar_reminders";
    private static final String PREFS = "madar_reminder_settings";
    private static final String PREF_DAILY = "daily_followups_enabled";
    private static final String ACTION_TASK = "ir.madar.app.REMIND_TASK";
    private static final String ACTION_STEP = "ir.madar.app.REMIND_STEP";
    private static final String ACTION_DAILY = "ir.madar.app.DAILY_FOLLOWUP";
    private static final String ACTION_DONE = "ir.madar.app.ACTION_MARK_DONE";
    private static final String ACTION_SNOOZE_BUTTON = "ir.madar.app.ACTION_SNOOZE_BUTTON";
    private static final String ACTION_SNOOZE_FIRE = "ir.madar.app.ACTION_SNOOZE_FIRE";
    private static final int DAILY_BASE = 17000;
    private static final int[] HOURS = {9, 12, 15, 18, 21};

    private static void channel(Context c) {
        NotificationManager mgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "یادآورها و موفقیت‌ها", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("یادآوری موعد وظایف، مراحل و پیگیری روزانه مدار");
            mgr.createNotificationChannel(ch);
        }
    }

    public static boolean dailyFollowupsEnabled(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(PREF_DAILY, true);
    }

    public static void setDailyFollowupsEnabled(Context c, boolean enabled) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(PREF_DAILY, enabled).apply();
        if (enabled) scheduleDailyFollowups(c); else cancelDailyFollowups(c);
    }

    public static void scheduleAll(Context c, Store db) {
        for (Store.TaskData task : db.tasks(null)) schedule(c, task);
        for (Store.StepData step : db.steps(null)) schedule(c, step);
    }

    /** Task deadline notification is scheduled one minute before the chosen due time. */
    public static void schedule(Context c, Store.TaskData task) {
        if (task == null) return;
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_TASK).putExtra("entity_id", task.id).putExtra("entity_type", "task");
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode("task", task.id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        alarm.cancel(pi);
        if (task.due > System.currentTimeMillis() && !task.done) setAlarm(c, alarm, Math.max(System.currentTimeMillis() + 1000L, task.due - 60_000L), pi);
    }

    public static void schedule(Context c, Store.StepData step) {
        if (step == null) return;
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_STEP).putExtra("entity_id", step.id).putExtra("entity_type", "step");
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode("step", step.id), i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        alarm.cancel(pi);
        if (step.due > System.currentTimeMillis() && !step.done) setAlarm(c, alarm, Math.max(System.currentTimeMillis() + 1000L, step.due - 60_000L), pi);
    }

    private static void setAlarm(Context c, AlarmManager alarm, long at, PendingIntent pi) {
        // Exact alarms are used only if Android has granted the special access; otherwise fall back safely.
        if (Build.VERSION.SDK_INT >= 31) {
            if (alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
        }
    }

    public static void scheduleDailyFollowups(Context c) {
        if (!dailyFollowupsEnabled(c)) { cancelDailyFollowups(c); return; }
        for (int i = 0; i < HOURS.length; i++) scheduleDailySlot(c, i);
    }

    private static void scheduleDailySlot(Context c, int slot) {
        if (slot < 0 || slot >= HOURS.length || !dailyFollowupsEnabled(c)) return;
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_DAILY).putExtra("slot", slot);
        PendingIntent pi = PendingIntent.getBroadcast(c, DAILY_BASE + slot, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Calendar next = Calendar.getInstance();
        next.set(Calendar.HOUR_OF_DAY, HOURS[slot]); next.set(Calendar.MINUTE, 0); next.set(Calendar.SECOND, 0); next.set(Calendar.MILLISECOND, 0);
        if (next.getTimeInMillis() <= System.currentTimeMillis()) next.add(Calendar.DAY_OF_YEAR, 1);
        alarm.cancel(pi);
        setAlarm(c, alarm, next.getTimeInMillis(), pi);
    }

    private static void cancelDailyFollowups(Context c) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        for (int slot = 0; slot < HOURS.length; slot++) {
            Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION_DAILY).putExtra("slot", slot);
            PendingIntent pi = PendingIntent.getBroadcast(c, DAILY_BASE + slot, i, PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) { alarm.cancel(pi); pi.cancel(); }
        }
    }

    public static void cancel(Context c, String taskId) { cancelEntity(c, "task", taskId); dismissDeadlineNotification(c, "task", taskId); }
    public static void cancelStep(Context c, String stepId) { cancelEntity(c, "step", stepId); dismissDeadlineNotification(c, "step", stepId); }
    public static void dismissDeadlineNotification(Context c, String type, String id) {
        NotificationManager mgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr != null) mgr.cancel(positiveId(type + ":deadline:" + id));
    }
    private static void cancelEntity(Context c, String type, String id) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        String action = "step".equals(type) ? ACTION_STEP : ACTION_TASK;
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode(type, id), new Intent(c, ReminderReceiver.class).setAction(action).putExtra("entity_id", id).putExtra("entity_type", type), PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null) { alarm.cancel(pi); pi.cancel(); }
    }
    public static void cancelTaskAndSteps(Context c, Store db, String taskId) {
        cancel(c, taskId);
        for (Store.StepData step : db.steps(taskId)) cancelStep(c, step.id);
    }

    public static void achievement(Context c, String title) {
        sendSimple(c, positiveId(title + "-achievement"), "✨ آفرین! به قولت عمل کردی", "تمام کارهای «" + title + "» را انجام دادی. این یک پیروزی واقعی است! 🎉");
    }

    private static int requestCode(String type, String id) { return positiveId(type + ":" + id); }
    private static int positiveId(String s) { return (s == null ? 1 : s.hashCode()) & 0x7fffffff; }
    private static boolean notificationsAllowed(Context c) {
        return Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
    private static PendingIntent openApp(Context c, int request) {
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, request, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void sendSimple(Context c, int id, String title, String message) {
        if (!notificationsAllowed(c)) return;
        channel(c);
        NotificationManager mgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        Notification n = new Notification.Builder(c, CHANNEL).setContentTitle(title).setContentText(message)
                .setSmallIcon(android.R.drawable.star_big_on).setAutoCancel(true).setContentIntent(openApp(c, id))
                .setStyle(new Notification.BigTextStyle().bigText(message)).build();
        mgr.notify(id, n);
    }

    private static void sendDeadline(Context c, String type, String id, String title, long due, boolean snoozed) {
        if (!notificationsAllowed(c)) return;
        channel(c);
        NotificationManager mgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        boolean beforeDue = due > System.currentTimeMillis();
        String head = snoozed ? "⏳ پیگیری دوباره مدار" : (beforeDue ? "⏰ یک دقیقه تا موعد" : "⌛ موعد این کار رسیده");
        String message = ("step".equals(type) ? "مرحله «" : "وظیفه «") + title + "»؛ " +
                (beforeDue ? "موعدش نزدیک است. انجامش دادی؟" : "از موعدش گذشته است. انجامش دادی؟");
        int notifId = positiveId(type + ":deadline:" + id);
        Intent done = new Intent(c, ReminderReceiver.class).setAction(ACTION_DONE).putExtra("entity_type", type).putExtra("entity_id", id);
        PendingIntent donePi = PendingIntent.getBroadcast(c, requestCode("done", id), done, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Intent snooze = new Intent(c, ReminderReceiver.class).setAction(ACTION_SNOOZE_BUTTON).putExtra("entity_type", type).putExtra("entity_id", id).putExtra("entity_title", title);
        PendingIntent snoozePi = PendingIntent.getBroadcast(c, requestCode("snooze", id), snooze, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL).setContentTitle(head).setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_info).setAutoCancel(true).setContentIntent(openApp(c, notifId))
                .setStyle(new Notification.BigTextStyle().bigText(message))
                .addAction(android.R.drawable.checkbox_on_background, "✓ انجام شد", donePi)
                .addAction(android.R.drawable.ic_lock_idle_alarm, "هنوز نه؛ ۱۰ دقیقه بعد", snoozePi);
        mgr.notify(notifId, b.build());
    }

    private static void scheduleSnooze(Context c, String type, String id, String title) {
        AlarmManager alarm = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (alarm == null) return;
        Intent fire = new Intent(c, ReminderReceiver.class).setAction(ACTION_SNOOZE_FIRE)
                .putExtra("entity_type", type).putExtra("entity_id", id).putExtra("entity_title", title);
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode("snooze-fire", id), fire, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        setAlarm(c, alarm, System.currentTimeMillis() + 10 * 60_000L, pi);
        sendSimple(c, positiveId(id + "-snoozed"), "باشه، کمی بعد دوباره می‌پرسم", "مدار ۱۰ دقیقه دیگر برای پیگیری «" + title + "» برمی‌گردد. 🌱");
    }

    private static void sendDailyFollowup(Context c, int slot) {
        clearDailyFollowupNotifications(c);
        Store db = new Store(c);
        try {
            ArrayList<Store.TaskData> all = db.tasks(null);
            ArrayList<Store.TaskData> pending = new ArrayList<>();
            ArrayList<Store.TaskData> withoutDeadline = new ArrayList<>();
            for (Store.TaskData task : all) if (!task.done) { pending.add(task); if (task.due <= 0) withoutDeadline.add(task); }
            ArrayList<Store.ListData> lists = db.lists();
            boolean hasCustomList = false;
            for (Store.ListData list : lists) if (!"inbox".equals(list.id)) { hasCustomList = true; break; }
            if (pending.isEmpty() && !hasCustomList) {
                sendSimple(c, dailyNotificationId(slot, 1), "🌱 یک قدم برای آینده روشن‌تر", all.isEmpty()
                        ? "وقتشه اولین هدف یا لیستت رو بسازی؛ حتی یک قدم کوچک امروز، آینده‌ات رو می‌سازه. مدار کنارته! ✨"
                        : "آفرین، کارهای ثبت‌شده‌ات را به پایان رساندی! برای آینده روشن‌تر، یک هدف یا لیست تازه بساز. ✨");
                return;
            }
            if (pending.isEmpty() && all.isEmpty()) {
                sendSimple(c, dailyNotificationId(slot, 2), "🪐 لیستت آماده است؛ اولین قدم را ثبت کن", "یک وظیفه کوچک یا هدف روزانه اضافه کن تا مدار بتواند در مسیر پیشرفت همراهت باشد.");
                return;
            }
            if (pending.isEmpty()) {
                sendSimple(c, dailyNotificationId(slot, 2), "🌟 امروزت رو با یک هدف تازه ادامه بده", "کارهای بازت انجام شده‌اند؛ به خودت افتخار کن و اگر آماده‌ای، قدم بعدی را برای آینده‌ات انتخاب کن.");
                return;
            }
            boolean hasUndated = !withoutDeadline.isEmpty();
            ArrayList<Store.TaskData> focus = hasUndated ? withoutDeadline : pending;
            StringBuilder message = new StringBuilder(hasUndated
                    ? "برای این کارها هنوز موعد تعیین نکردی؛ یک قدم را همین حالا جلو ببر:\n"
                    : "این کارها موعد مشخص دارند؛ مدار یک دقیقه پیش از موعد پیگیری می‌کند:\n");
            int shown = Math.min(3, focus.size());
            for (int i = 0; i < shown; i++) {
                Store.TaskData t = focus.get(i);
                Store.ListData list = db.list(t.listId);
                message.append("• ").append(t.title);
                if (list != null) message.append("  —  ").append(list.title);
                message.append('\n');
            }
            if (focus.size() > shown) message.append("و ").append(focus.size() - shown).append(" کار دیگر…\n");
            message.append("کدام قدم را همین حالا جلو می‌بری؟ 🚀");
            sendSimple(c, dailyNotificationId(slot, 3), "⏳ وقت یک پیگیری کوتاه است", message.toString());
        } finally { db.close(); }
    }

    private static int dailyNotificationId(int slot, int kind) { return 19100 + Math.max(0, slot) * 10 + kind; }
    private static void clearDailyFollowupNotifications(Context c) {
        NotificationManager mgr = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (mgr == null) return;
        for (int slot = 0; slot < HOURS.length; slot++) for (int kind = 1; kind <= 3; kind++) mgr.cancel(dailyNotificationId(slot, kind));
    }

    @Override public void onReceive(Context c, Intent i) {
        if (i == null || i.getAction() == null) return;
        String action = i.getAction();
        if (ACTION_DAILY.equals(action)) {
            int slot = i.getIntExtra("slot", -1);
            if (dailyFollowupsEnabled(c)) { scheduleDailySlot(c, slot); sendDailyFollowup(c, slot); }
            return;
        }
        if (ACTION_SNOOZE_BUTTON.equals(action)) {
            String type = i.getStringExtra("entity_type");
            String id = i.getStringExtra("entity_id");
            dismissDeadlineNotification(c, type, id);
            scheduleSnooze(c, type, id, i.getStringExtra("entity_title"));
            return;
        }
        String type = i.getStringExtra("entity_type");
        String id = i.getStringExtra("entity_id");
        if (id == null || type == null) return;
        Store db = new Store(c);
        try {
            if (ACTION_DONE.equals(action)) {
                dismissDeadlineNotification(c, type, id);
                if ("step".equals(type)) {
                    Store.StepData step = db.step(id);
                    if (step != null && !step.done) { db.setStepDone(step, true); cancelStep(c, id); sendSimple(c, positiveId(id + "-done"), "✨ مرحله ثبت شد", "آفرین! یک مرحله دیگر را با موفقیت پشت سر گذاشتی."); }
                } else {
                    Store.TaskData task = db.task(id);
                    if (task != null && !task.done) { db.setDone(task, true); cancel(c, id); sendSimple(c, positiveId(id + "-done"), "🎉 وظیفه انجام‌شده ثبت شد", "به قولت عمل کردی؛ همین قدم‌های کوچک تو را جلو می‌برند."); }
                }
                scheduleAll(c, db);
                return;
            }
            if (ACTION_SNOOZE_FIRE.equals(action)) {
                String title = i.getStringExtra("entity_title");
                if ("step".equals(type)) {
                    Store.StepData step = db.step(id);
                    if (step != null && !step.done) sendDeadline(c, type, id, title == null ? step.title : title, step.due, true);
                } else {
                    Store.TaskData task = db.task(id);
                    if (task != null && !task.done) sendDeadline(c, type, id, title == null ? task.title : title, task.due, true);
                }
                return;
            }
            if (ACTION_TASK.equals(action)) {
                Store.TaskData task = db.task(id);
                if (task != null && !task.done && task.due > 0 && task.due <= System.currentTimeMillis() + 60_000L)
                    sendDeadline(c, "task", task.id, task.title, task.due, false);
            } else if (ACTION_STEP.equals(action)) {
                Store.StepData step = db.step(id);
                if (step != null && !step.done && step.due > 0 && step.due <= System.currentTimeMillis() + 60_000L)
                    sendDeadline(c, "step", step.id, step.title, step.due, false);
            }
        } finally { db.close(); }
    }
}
