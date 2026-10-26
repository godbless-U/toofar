# MADAR 1.1 — Reminder and task-stage acceptance tests

Run these on a real Android device (API 26+, ideally Android 13–16). Notifications and exact-alarm behavior cannot be fully validated by JVM unit tests.

## Deadline reminders

1. Grant notification permission and, on Android 12+, grant **Alarms & reminders / exact alarms** from MADAR Settings.
2. Create a task with a deadline at least three minutes in the future. Verify a reminder appears about one minute before the deadline and asks whether the task is done.
3. Tap **✓ انجام شد**. Verify the task becomes complete, its deadline notification disappears, and a success notification appears.
4. Repeat, but tap **هنوز نه؛ ۱۰ دقیقه بعد**. Verify the original notification is dismissed and a follow-up appears about ten minutes later if the task remains incomplete.
5. Edit a task's deadline, remove the deadline, mark it complete, and delete it. Verify stale deadline alarms/notifications do not remain.

## Task stages

1. Open a task and add two stages; give one stage a deadline and leave the other without one.
2. Mark each stage complete separately. Verify the checkbox pop/rotation animation, strike-through, and stage completion count update.
3. Edit and delete a stage. Verify the deadline reminder is rescheduled or cancelled as appropriate.
4. Set a deadline on a stage and verify the same one-minute-before notification/actions as a task.

## Daily follow-ups

1. Leave at least one incomplete task without a deadline. Verify local reminders are scheduled for 09:00, 12:00, 15:00, 18:00, and 21:00 local time and focus on undated tasks.
2. If there are no custom lists or tasks, verify the reminder invites the user to create a goal/list for a brighter future.
3. If a custom list exists but is empty, verify the reminder invites the user to add the first task.
4. If all tasks are complete, verify the reminder congratulates the user and suggests a new goal.
5. Turn daily follow-ups off and verify these five alarms stop; turn them back on and verify they are scheduled again.
6. Reboot the device and change the system time/time zone. Verify alarms are rescheduled.

## Backup and migration

1. Export a full JSON backup and a selected-list backup containing tasks, stages, and deadlines.
2. Import both files into a clean install and verify tasks, stage check states, due timestamps, and goals are restored.
3. Import an existing version-1 backup and verify it remains supported; older backups naturally have no stages.

## Known timing limitation

On Android 12+, the OS can defer alarms when exact-alarm access is not granted. MADAR falls back to an inexact alarm and exposes a Settings shortcut for exact-alarm access. Battery restrictions imposed by some device manufacturers can also delay notifications.
