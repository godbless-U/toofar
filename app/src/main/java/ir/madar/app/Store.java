package ir.madar.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** SQLite is authoritative: no network account, no server, no disposable in-memory task state. */
public final class Store extends SQLiteOpenHelper {
    private static final String NAME = "madar.db";
    private static final int SCHEMA_VERSION = 2;
    public Store(Context c) { super(c, NAME, null, SCHEMA_VERSION); setWriteAheadLoggingEnabled(true); }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE lists (id TEXT PRIMARY KEY, title TEXT NOT NULL, created INTEGER NOT NULL)");
        db.execSQL("CREATE TABLE tasks (id TEXT PRIMARY KEY, list_id TEXT NOT NULL REFERENCES lists(id) ON DELETE CASCADE, title TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', done INTEGER NOT NULL DEFAULT 0, priority INTEGER NOT NULL DEFAULT 1, due INTEGER NOT NULL DEFAULT 0, repeat_type TEXT NOT NULL DEFAULT 'NONE', created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_tasks_list ON tasks(list_id)");
        db.execSQL("CREATE INDEX idx_tasks_due ON tasks(due)");
        db.execSQL("CREATE TABLE task_steps (id TEXT PRIMARY KEY, task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX idx_steps_task ON task_steps(task_id, position)");
        db.execSQL("CREATE INDEX idx_steps_due ON task_steps(due)");
        db.execSQL("CREATE TABLE goals (id TEXT PRIMARY KEY, domain TEXT NOT NULL, period TEXT NOT NULL, bucket TEXT NOT NULL, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        db.execSQL("INSERT INTO lists(id,title,created) VALUES('inbox','صندوق ورودی',"+System.currentTimeMillis()+")");
    }
    @Override public void onConfigure(SQLiteDatabase db) { super.onConfigure(db); db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Additive migration only: existing user tasks and goals are preserved.
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS task_steps (id TEXT PRIMARY KEY, task_id TEXT NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, title TEXT NOT NULL, done INTEGER NOT NULL DEFAULT 0, due INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_steps_task ON task_steps(task_id, position)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_steps_due ON task_steps(due)");
        }
        if (newVersion > 2) throw new IllegalStateException("Missing schema migration " + oldVersion + " -> " + newVersion);
    }

    public static final class ListData {
        public String id, title; public long created; public int total, done;
    }
    public static final class TaskData {
        public String id, listId, title, description, repeat; public boolean done;
        public int priority; public long due, created;
    }
    public static final class StepData {
        public String id, taskId, title; public boolean done; public long due, created; public int position;
    }
    public static final class GoalData {
        public String id, domain, period, bucket, title; public boolean done; public long created;
    }
    private static String newId() { return UUID.randomUUID().toString(); }
    public static boolean isPeriod(String domain, String period) {
        if ("PERSONAL".equals(domain)) return "DAILY".equals(period)||"WEEKLY".equals(period)||"MONTHLY".equals(period)||"YEARLY".equals(period);
        if ("WORK".equals(domain)) return "DAILY".equals(period)||"MONTHLY".equals(period)||"YEARLY".equals(period)||"ULTIMATE".equals(period);
        return false;
    }
    public ArrayList<ListData> lists() {
        ArrayList<ListData> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT l.id,l.title,l.created,COUNT(t.id),COALESCE(SUM(t.done),0) FROM lists l LEFT JOIN tasks t ON t.list_id=l.id GROUP BY l.id ORDER BY l.created ASC", null)) {
            while(c.moveToNext()) { ListData x=new ListData(); x.id=c.getString(0);x.title=c.getString(1);x.created=c.getLong(2);x.total=c.getInt(3);x.done=c.getInt(4);result.add(x); }
        } return result;
    }
    public ListData list(String id) { for(ListData x:lists()) if(x.id.equals(id)) return x; return null; }
    public String addList(String title) {
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("نام لیست خالی است");
        String id = newId(); ContentValues v=new ContentValues(); v.put("id",id);v.put("title",title.trim());v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("lists",null,v);return id;
    }
    public void renameList(String id,String title) {
        if(title==null||title.trim().isEmpty())throw new IllegalArgumentException("نام لیست خالی است");
        ContentValues v=new ContentValues();v.put("title",title.trim());getWritableDatabase().update("lists",v,"id=?",new String[]{id});
    }
    public void deleteLists(Set<String> ids) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try { for(String id:ids) if(!"inbox".equals(id)) db.delete("lists","id=?",new String[]{id});db.setTransactionSuccessful(); }
        finally {db.endTransaction();}
    }
    public ArrayList<TaskData> tasks(String listId) {
        ArrayList<TaskData> result = new ArrayList<>();
        String filter = listId==null?"":" WHERE list_id=?";
        String[] args = listId==null?null:new String[]{listId};
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,list_id,title,description,done,priority,due,repeat_type,created FROM tasks"+filter+" ORDER BY done ASC, CASE WHEN due=0 THEN 1 ELSE 0 END, due ASC, priority DESC, created DESC",args)) {
            while(c.moveToNext()){ TaskData t=new TaskData(); t.id=c.getString(0);t.listId=c.getString(1);t.title=c.getString(2);t.description=c.getString(3);t.done=c.getInt(4)==1;t.priority=c.getInt(5);t.due=c.getLong(6);t.repeat=c.getString(7);t.created=c.getLong(8);result.add(t); }
        }return result;
    }
    public TaskData task(String id){try(Cursor c=getReadableDatabase().rawQuery("SELECT id,list_id,title,description,done,priority,due,repeat_type,created FROM tasks WHERE id=?",new String[]{id})){
        if(c.moveToFirst()){TaskData t=new TaskData();t.id=c.getString(0);t.listId=c.getString(1);t.title=c.getString(2);t.description=c.getString(3);t.done=c.getInt(4)==1;t.priority=c.getInt(5);t.due=c.getLong(6);t.repeat=c.getString(7);t.created=c.getLong(8);return t;}
    }return null;}
    public String addTask(String listId,String title,long due,int priority,String recurrence) {
        if(title==null||title.trim().isEmpty())throw new IllegalArgumentException("متن وظیفه خالی است");
        String id=newId();ContentValues v=new ContentValues();v.put("id",id);v.put("list_id",listId);v.put("title",title.trim());v.put("description","");v.put("priority",Math.max(1,Math.min(3,priority)));v.put("due",Math.max(0,due));v.put("repeat_type",recurrence);v.put("done",0);v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("tasks",null,v);return id;
    }
    public void editTask(TaskData t){ContentValues v=new ContentValues();v.put("title",t.title);v.put("description",t.description);v.put("priority",t.priority);v.put("due",t.due);v.put("repeat_type",t.repeat);v.put("list_id",t.listId);getWritableDatabase().update("tasks",v,"id=?",new String[]{t.id});}
    public void setDone(TaskData t,boolean done) {
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try {
            ContentValues v=new ContentValues();v.put("done",done?1:0);db.update("tasks",v,"id=?",new String[]{t.id});
            if(done&&!t.done&&!"NONE".equals(t.repeat)) {
                java.util.Calendar cal=java.util.Calendar.getInstance();cal.setTimeInMillis(t.due>0?t.due:System.currentTimeMillis());
                switch(t.repeat){case "DAILY":cal.add(java.util.Calendar.DAY_OF_YEAR,1);break;case "WEEKLY":cal.add(java.util.Calendar.WEEK_OF_YEAR,1);break;case "MONTHLY":cal.add(java.util.Calendar.MONTH,1);break;case "YEARLY":cal.add(java.util.Calendar.YEAR,1);break;default:break;}
                long next=cal.getTimeInMillis(); long now=System.currentTimeMillis();
                int limit=0;
                while(next<=now&&limit++<500){switch(t.repeat){case "DAILY":cal.add(java.util.Calendar.DAY_OF_YEAR,1);break;case "WEEKLY":cal.add(java.util.Calendar.WEEK_OF_YEAR,1);break;case "MONTHLY":cal.add(java.util.Calendar.MONTH,1);break;case "YEARLY":cal.add(java.util.Calendar.YEAR,1);break;default:break;}next=cal.getTimeInMillis();}
                ContentValues nextTask=new ContentValues();nextTask.put("id",newId());nextTask.put("list_id",t.listId);nextTask.put("title",t.title);nextTask.put("description",t.description);nextTask.put("priority",t.priority);nextTask.put("due",next);nextTask.put("repeat_type",t.repeat);nextTask.put("done",0);nextTask.put("created",System.currentTimeMillis());db.insertOrThrow("tasks",null,nextTask);
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
    }
    public void deleteTask(String id){getWritableDatabase().delete("tasks","id=?",new String[]{id});}

    public ArrayList<StepData> steps(String taskId) {
        ArrayList<StepData> result = new ArrayList<>();
        String filter = taskId == null ? "" : " WHERE task_id=?";
        String[] args = taskId == null ? null : new String[]{taskId};
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,task_id,title,done,due,position,created FROM task_steps" + filter + " ORDER BY task_id,position,created", args)) {
            while (c.moveToNext()) { StepData x = new StepData(); x.id=c.getString(0);x.taskId=c.getString(1);x.title=c.getString(2);x.done=c.getInt(3)==1;x.due=c.getLong(4);x.position=c.getInt(5);x.created=c.getLong(6);result.add(x); }
        }
        return result;
    }
    public StepData step(String id) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,task_id,title,done,due,position,created FROM task_steps WHERE id=?", new String[]{id})) {
            if (c.moveToFirst()) { StepData x=new StepData();x.id=c.getString(0);x.taskId=c.getString(1);x.title=c.getString(2);x.done=c.getInt(3)==1;x.due=c.getLong(4);x.position=c.getInt(5);x.created=c.getLong(6);return x; }
        }
        return null;
    }
    public String addStep(String taskId, String title, long due) {
        if (task(taskId) == null) throw new IllegalArgumentException("وظیفه پیدا نشد");
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("عنوان مرحله خالی است");
        int position=steps(taskId).size();String id=newId();ContentValues v=new ContentValues();v.put("id",id);v.put("task_id",taskId);v.put("title",title.trim());v.put("done",0);v.put("due",Math.max(0,due));v.put("position",position);v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("task_steps",null,v);return id;
    }
    public void editStep(StepData step) {
        if(step.title==null||step.title.trim().isEmpty())throw new IllegalArgumentException("عنوان مرحله خالی است");
        ContentValues v=new ContentValues();v.put("title",step.title.trim());v.put("due",Math.max(0,step.due));v.put("position",step.position);getWritableDatabase().update("task_steps",v,"id=?",new String[]{step.id});
    }
    public void setStepDone(StepData step, boolean done) { ContentValues v=new ContentValues();v.put("done",done?1:0);getWritableDatabase().update("task_steps",v,"id=?",new String[]{step.id}); }
    public void deleteStep(String id) { getWritableDatabase().delete("task_steps","id=?",new String[]{id}); }

    public static String bucket(String period,int offset){
        if("ULTIMATE".equals(period))return "ultimate";
        java.util.Calendar c=java.util.Calendar.getInstance();
        switch(period){case "DAILY":c.add(java.util.Calendar.DAY_OF_YEAR,offset);break;
            case "WEEKLY":c.add(java.util.Calendar.WEEK_OF_YEAR,offset);break;
            case "MONTHLY":c.add(java.util.Calendar.MONTH,offset);break;
            case "YEARLY":c.add(java.util.Calendar.YEAR,offset);break;default:break;}
        if("WEEKLY".equals(period)){int day=c.get(java.util.Calendar.DAY_OF_WEEK);int daysSinceMonday=(day+5)%7;c.add(java.util.Calendar.DAY_OF_YEAR,-daysSinceMonday);}
        java.text.SimpleDateFormat f=new java.text.SimpleDateFormat("yyyy-MM-dd",java.util.Locale.US);
        if("MONTHLY".equals(period))f=new java.text.SimpleDateFormat("yyyy-MM",java.util.Locale.US);
        if("YEARLY".equals(period))f=new java.text.SimpleDateFormat("yyyy",java.util.Locale.US);
        return f.format(c.getTime());
    }
    public ArrayList<GoalData> goals(String domain,String period){return goals(domain,period,null);}
    public ArrayList<GoalData> goals(String domain,String period,String bucket) {
        ArrayList<GoalData> result=new ArrayList<>();
        String where=" WHERE domain=?"+(period==null?"":" AND period=?")+(bucket==null?"":" AND bucket=?");
        java.util.ArrayList<String> args=new java.util.ArrayList<>();args.add(domain);if(period!=null)args.add(period);if(bucket!=null)args.add(bucket);
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,domain,period,bucket,title,done,created FROM goals"+where+" ORDER BY done ASC,created DESC",args.toArray(new String[0]))){
            while(c.moveToNext()){GoalData g=new GoalData();g.id=c.getString(0);g.domain=c.getString(1);g.period=c.getString(2);g.bucket=c.getString(3);g.title=c.getString(4);g.done=c.getInt(5)==1;g.created=c.getLong(6);result.add(g);}
        }return result;
    }
    public void addGoal(String domain,String period,String title){addGoal(domain,period,bucket(period,0),title);}
    public void addGoal(String domain,String period,String bucket,String title) {
        if(!isPeriod(domain,period)||title==null||title.trim().isEmpty()||bucket==null||bucket.length()>32)throw new IllegalArgumentException("هدف نامعتبر");
        ContentValues v=new ContentValues();v.put("id",newId());v.put("domain",domain);v.put("period",period);v.put("bucket",bucket);v.put("title",title.trim());v.put("done",0);v.put("created",System.currentTimeMillis());getWritableDatabase().insertOrThrow("goals",null,v);
    }
    public void editGoal(GoalData g){ContentValues v=new ContentValues();v.put("title",g.title);getWritableDatabase().update("goals",v,"id=?",new String[]{g.id});}
    public void setGoalDone(GoalData g,boolean done){ContentValues v=new ContentValues();v.put("done",done?1:0);getWritableDatabase().update("goals",v,"id=?",new String[]{g.id});}
    public void deleteGoal(String id){getWritableDatabase().delete("goals","id=?",new String[]{id});}

    /** JSON v2 includes task stages while remaining able to import v1 backups. */
    public JSONObject backup(Set<String> selected) throws JSONException {
        JSONObject root=new JSONObject();root.put("format","ir.madar.backup");root.put("version",2);root.put("created",System.currentTimeMillis());root.put("scope",selected==null?"all":"selected");
        JSONArray lists=new JSONArray();JSONArray tasks=new JSONArray();JSONArray steps=new JSONArray();JSONArray goals=new JSONArray();
        for(ListData l:lists()) {
            if(selected!=null&&!selected.contains(l.id))continue;
            JSONObject o=new JSONObject();o.put("id",l.id);o.put("title",l.title);o.put("created",l.created);lists.put(o);
            for(TaskData t:tasks(l.id)){
                JSONObject x=new JSONObject();x.put("id",t.id);x.put("list_id",t.listId);x.put("title",t.title);x.put("description",t.description);x.put("done",t.done);x.put("priority",t.priority);x.put("due",t.due);x.put("repeat_type",t.repeat);x.put("created",t.created);tasks.put(x);
                for(StepData step:steps(t.id)){JSONObject st=new JSONObject();st.put("id",step.id);st.put("task_id",step.taskId);st.put("title",step.title);st.put("done",step.done);st.put("due",step.due);st.put("position",step.position);st.put("created",step.created);steps.put(st);}
            }
        }
        if(selected==null)for(String d:new String[]{"PERSONAL","WORK"})for(GoalData g:goals(d,null)){
            JSONObject x=new JSONObject();x.put("id",g.id);x.put("domain",g.domain);x.put("period",g.period);x.put("bucket",g.bucket);x.put("title",g.title);x.put("done",g.done);x.put("created",g.created);goals.put(x);
        }
        root.put("lists",lists);root.put("tasks",tasks);root.put("steps",steps);root.put("goals",goals);return root;
    }

    /** Atomic import; existing matching IDs are skipped. Rejects foreign/corrupt JSON before writing. */
    public String importBackup(JSONObject root) throws JSONException {
        int backupVersion=root.optInt("version",-1);
        if(!"ir.madar.backup".equals(root.optString("format"))||(backupVersion!=1&&backupVersion!=2))throw new JSONException("قالب یا نسخه بکاپ پشتیبانی نمی‌شود");
        JSONArray ls=root.getJSONArray("lists"),ts=root.getJSONArray("tasks"),steps=root.optJSONArray("steps"),gs=root.optJSONArray("goals");
        if(steps==null)steps=new JSONArray();if(gs==null)gs=new JSONArray();
        if(ls.length()>10000||ts.length()>100000||steps.length()>200000||gs.length()>100000)throw new JSONException("فایل بیش از حد بزرگ است");
        HashSet<String> listIds=new HashSet<>();HashSet<String> taskIds=new HashSet<>();
        for(ListData l:lists())listIds.add(l.id);
        for(int i=0;i<ls.length();i++){
            JSONObject o=ls.getJSONObject(i);requireId(o,"id");if(o.getString("title").trim().isEmpty())throw new JSONException("نام لیست نامعتبر");listIds.add(o.getString("id"));
        }
        for(TaskData existing:tasks(null))taskIds.add(existing.id);
        for(int i=0;i<ts.length();i++){
            JSONObject t=ts.getJSONObject(i);requireId(t,"id");requireId(t,"list_id");if(!listIds.contains(t.getString("list_id")))throw new JSONException("لیست مرجع وظیفه پیدا نشد");if(t.getString("title").trim().isEmpty())throw new JSONException("وظیفه خالی است");taskIds.add(t.getString("id"));
        }
        for(int i=0;i<steps.length();i++){
            JSONObject st=steps.getJSONObject(i);requireId(st,"id");requireId(st,"task_id");if(!taskIds.contains(st.getString("task_id")))throw new JSONException("وظیفه مرجع مرحله پیدا نشد");if(st.optString("title").trim().isEmpty())throw new JSONException("عنوان مرحله نامعتبر است");
        }
        for(int i=0;i<gs.length();i++){
            JSONObject g=gs.getJSONObject(i);requireId(g,"id");if(!isPeriod(g.getString("domain"),g.getString("period")))throw new JSONException("نوع هدف نامعتبر");
        }
        SQLiteDatabase db=getWritableDatabase();db.beginTransaction();int added=0;
        try{
            for(int i=0;i<ls.length();i++){
                JSONObject o=ls.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",o.getString("id"));v.put("title",o.getString("title"));v.put("created",o.optLong("created",System.currentTimeMillis()));if(db.insertWithOnConflict("lists",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)added++;
            }
            for(int i=0;i<ts.length();i++){
                JSONObject o=ts.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",o.getString("id"));v.put("list_id",o.getString("list_id"));v.put("title",o.getString("title"));v.put("description",o.optString("description"));v.put("done",o.optBoolean("done")?1:0);v.put("priority",Math.max(1,Math.min(3,o.optInt("priority",1))));v.put("due",Math.max(0,o.optLong("due")));v.put("repeat_type",safeRepeat(o.optString("repeat_type","NONE")));v.put("created",o.optLong("created",System.currentTimeMillis()));if(db.insertWithOnConflict("tasks",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)added++;
            }
            for(int i=0;i<steps.length();i++){
                JSONObject o=steps.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",o.getString("id"));v.put("task_id",o.getString("task_id"));v.put("title",o.getString("title"));v.put("done",o.optBoolean("done")?1:0);v.put("due",Math.max(0,o.optLong("due")));v.put("position",Math.max(0,o.optInt("position")));v.put("created",o.optLong("created",System.currentTimeMillis()));if(db.insertWithOnConflict("task_steps",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)added++;
            }
            for(int i=0;i<gs.length();i++){
                JSONObject o=gs.getJSONObject(i);ContentValues v=new ContentValues();v.put("id",o.getString("id"));v.put("domain",o.getString("domain"));v.put("period",o.getString("period"));v.put("bucket",o.optString("bucket",bucket(o.getString("period"),0)));v.put("title",o.getString("title"));v.put("done",o.optBoolean("done")?1:0);v.put("created",o.optLong("created",System.currentTimeMillis()));if(db.insertWithOnConflict("goals",null,v,SQLiteDatabase.CONFLICT_IGNORE)!=-1)added++;
            }
            db.setTransactionSuccessful();
        } finally{db.endTransaction();}
        return added+" مورد جدید بازیابی شد. موارد تکراری نادیده گرفته شدند.";
    }
    private static void requireId(JSONObject o,String key)throws JSONException{String s=o.getString(key);if(s.isEmpty()||s.length()>128)throw new JSONException("شناسه نامعتبر");}
    private static String safeRepeat(String s){switch(s){case "DAILY":case "WEEKLY":case "MONTHLY":case "YEARLY":return s;default:return "NONE";}}
}
