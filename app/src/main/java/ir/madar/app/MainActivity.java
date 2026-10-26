package ir.madar.app;

import android.Manifest;
import android.app.Activity;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.app.AlarmManager;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONObject;
import org.json.JSONArray;
import org.vosk.Model;
import org.vosk.Recognizer;
import org.vosk.android.RecognitionListener;
import org.vosk.android.SpeechService;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Native UI: no WebView, no Google Speech API, no account, no telemetry. */
public final class MainActivity extends Activity implements RecognitionListener {
    private static final int IN_MIC=100, IN_NOTIFY=101, SAVE_JSON=201, OPEN_JSON=202;
    private static final int WHITE=0xFFF7F6FF, SOFT=0xFFBAC6E7, CYAN=0xFF86E9FF, VIOLET=0xFFCAA8FF, MUTED=0xFF8795BB;
    private static final String[] PERIODS={"DAILY","WEEKLY","MONTHLY","YEARLY"};
    private static final String[] WORK_PERIODS={"DAILY","MONTHLY","YEARLY","ULTIMATE"};
    private static final String[] PERIOD_LABELS={"روزانه","هفتگی","ماهانه","سالانه"};
    private static final String[] WORK_LABELS={"روزانه","ماهانه","سالانه","هدف نهایی"};
    private static final String[] REPEAT_CODES={"NONE","DAILY","WEEKLY","MONTHLY","YEARLY"};
    private static final String[] REPEAT_LABELS={"بدون تکرار","هر روز","هر هفته","هر ماه","هر سال"};
    private Store db;
    private ModelManager modelManager;
    private LinearLayout content, page, bottom;
    private String pageName="HOME", activeList="inbox", personalPeriod="DAILY", workPeriod="DAILY";
    private int personalOffset=0, workOffset=0;
    private final HashSet<String> selectedLists=new HashSet<>();
    private HashSet<String> exportSelection=null;
    private SpeechService speechService;
    private Recognizer activeRecognizer;
    private boolean listening=false;
    private final StringBuilder spoken=new StringBuilder();
    private String livePart="", draft="";
    private TextView voiceStatus, rawPreview, micLabel, micButton;
    private ObjectAnimator pulseX,pulseY;
    private int recordingGeneration=0;
    private boolean lowConfidence=false;
    private boolean refreshDailyOnResume=false;
    private EditText voiceInput;
    private ScrollView scroll;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(0xFF060B1D);getWindow().setNavigationBarColor(0xFF090E22);
        getWindow().getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        db=new Store(this);modelManager=new ModelManager(this);ReminderReceiver.scheduleAll(this,db);ReminderReceiver.scheduleDailyFollowups(this);
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},IN_NOTIFY);
        navigate("HOME");
    }
    private int d(float dp){return Math.round(dp*getResources().getDisplayMetrics().density);}
    private GradientDrawable glass(int dark,int border,float radius){GradientDrawable b=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{dark,0x70383062,0x6A142744});b.setCornerRadius(d(radius));b.setStroke(d(1),border);return b;}
    private GradientDrawable gradient(int a,int b,int r){GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{a,b});g.setCornerRadius(d(r));return g;}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);return l;}
    private LinearLayout horizontal(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private LinearLayout.LayoutParams lp(int width,int height,int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:d(width),height<0?height:d(height));p.topMargin=d(top);p.bottomMargin=d(bottom);return p;}
    private LinearLayout.LayoutParams flex(int top,int bottom){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,-2,1);p.setMargins(d(3),d(top),d(3),d(bottom));return p;}
    private TextView txt(String value,int sp,int color,boolean bold){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(sp);v.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);v.setTextDirection(View.TEXT_DIRECTION_RTL);v.setIncludeFontPadding(true);if(bold)v.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return v;}
    private TextView action(String title,int fg,boolean primary,Runnable cb){TextView v=txt(title,14,fg,true);v.setGravity(Gravity.CENTER);v.setPadding(d(12),d(11),d(12),d(11));v.setBackground(primary?gradient(0xFF6958CB,0xFF3B9FC1,15):glass(0x74192550,0x557D9BC7,15));v.setOnClickListener(w->cb.run());return v;}
    private LinearLayout card(){LinearLayout v=vertical();v.setPadding(d(17),d(17),d(17),d(17));v.setBackground(glass(0xB025284A,0x558F9BCF,22));return v;}
    private void addCard(LinearLayout v){page.addView(v,lp(-1,-2,7,7));}
    private void spacer(LinearLayout root,int pixels){View s=new View(this);root.addView(s,lp(1,pixels,0,0));}
    private void title(LinearLayout root,String heading,String subtitle){root.addView(txt(heading,28,WHITE,true),lp(-1,-2,17,3));if(subtitle!=null)root.addView(txt(subtitle,13,SOFT,false),lp(-1,-2,0,14));}
    private void miniTitle(LinearLayout root,String text){root.addView(txt(text,18,WHITE,true),lp(-1,-2,12,8));}
    private EditText input(String hint,String initial,boolean multi){EditText e=new EditText(this);e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setTextSize(15);e.setHint(hint);e.setText(initial);e.setTextDirection(View.TEXT_DIRECTION_RTL);e.setGravity(Gravity.RIGHT|Gravity.TOP);e.setPadding(d(15),d(13),d(15),d(13));e.setBackground(glass(0xB1182347,0x448EA6FF,14));if(multi){e.setMinLines(3);e.setMaxLines(8);e.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);}else{e.setSingleLine(true);}return e;}
    private void toast(String msg){Toast.makeText(this,msg,Toast.LENGTH_LONG).show();}
    private void navigate(String newPage){
        if(listening&& !"VOICE".equals(newPage))stopSpeech();
        pageName=newPage;show();
    }
    private void show(){
        FrameLayout frame=new FrameLayout(this);
        frame.addView(new SpaceView(),new FrameLayout.LayoutParams(-1,-1));
        LinearLayout root=vertical();root.setPadding(0,d(4),0,0);frame.addView(root,new FrameLayout.LayoutParams(-1,-1));
        scroll=new ScrollView(this);scroll.setClipToPadding(false);scroll.setFillViewport(false);
        page=vertical();page.setPadding(d(19),d(4),d(19),d(26));scroll.addView(page);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        bottom=horizontal();bottom.setPadding(d(9),d(7),d(9),d(7));bottom.setBackground(glass(0xEA101632,0x55828DC5,17));
        bottomButton("⌂","خانه","HOME");bottomButton("☷","لیست‌ها","LISTS");bottomButton("◎","اهداف","GOALS");bottomButton("▣","کار","WORK");bottomButton("⚙","بیشتر","SETTINGS");
        root.addView(bottom,lp(-1,-2,0,0));setContentView(frame);
        switch(pageName){case "VOICE":renderVoice();break;case "LISTS":renderLists();break;case "DETAIL":renderDetails();break;case "GOALS":renderGoals(false);break;case "WORK":renderGoals(true);break;case "SETTINGS":renderSettings();break;default:renderHome();break;}
    }
    private void bottomButton(String icon,String title,String dest){
        LinearLayout unit=vertical();unit.setGravity(Gravity.CENTER);TextView symbol=txt(icon,24,pageName.equals(dest)?CYAN:SOFT,true);symbol.setGravity(Gravity.CENTER);
        TextView label=txt(title,10,pageName.equals(dest)?WHITE:SOFT,false);label.setGravity(Gravity.CENTER);
        unit.addView(symbol,lp(-1,-2,0,0));unit.addView(label,lp(-1,-2,0,0));unit.setOnClickListener(v->navigate(dest));bottom.addView(unit,new LinearLayout.LayoutParams(0,-2,1));
    }
    private void renderHome(){
        title(page,"مدار ✦","هر روز، یک قدم نزدیک‌تر به آرزوهایت");
        LinearLayout hero=card();hero.setBackground(gradient(0xCC4E3A95,0xB614678B,26));
        hero.addView(txt("✦  دستیار صوتی فارسی",16,WHITE,true),lp(-1,-2,0,7));
        hero.addView(txt("کارت را بگو؛ مدار آن را به یک وظیفه قابل پیگیری تبدیل می‌کند.",14,WHITE,false),lp(-1,-2,0,18));
        TextView mic=txt("🎙",48,WHITE,true);mic.setGravity(Gravity.CENTER);mic.setBackground(glass(0x885B52C9,0xBBBAACFF,100));mic.setOnClickListener(v->navigate("VOICE"));
        LinearLayout line=horizontal();line.setGravity(Gravity.CENTER);line.addView(mic,lp(100,100,0,5));hero.addView(line,lp(-1,-2,0,3));
        TextView start=txt("برای شروع ضبط لمس کن",14,WHITE,true);start.setGravity(Gravity.CENTER);hero.addView(start,lp(-1,-2,4,0));
        hero.setOnClickListener(v->navigate("VOICE"));addCard(hero);
        int all=0,finished=0;for(Store.ListData l:db.lists()){all+=l.total;finished+=l.done;}
        LinearLayout summary=card();summary.addView(txt("پیشرفت کلی تو",17,WHITE,true));summary.addView(txt(finished+" از "+all+" وظیفه انجام شده",14,SOFT,false),lp(-1,-2,7,6));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(all,1));bar.setProgress(finished);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(CYAN));summary.addView(bar,lp(-1,6,0,7));
        summary.addView(txt(all==0?"اولین برنامه‌ات را بساز 🌱":(finished==all?"درخشان پیش رفتی! 🌟":"هر کار کوچک، یک پیشرفت واقعی است."),13,VIOLET,false));addCard(summary);
        miniTitle(page,"لیست‌های من");
        int shown=0;for(Store.ListData l:db.lists()){if(shown++>=4)break;listCard(l,false);}
        page.addView(action("مشاهده همه لیست‌ها  ←",WHITE,false,()->navigate("LISTS")),lp(-1,-2,9,4));
    }
    private void listCard(Store.ListData l,boolean selectable){
        LinearLayout shell=card();LinearLayout head=horizontal();
        TextView emoji=txt("◈",24,CYAN,true);head.addView(emoji,lp(35,-2,0,0));
        LinearLayout caption=vertical();caption.addView(txt(l.title,16,WHITE,true));caption.addView(txt(l.done+" / "+l.total+" انجام شده",12,SOFT,false),lp(-1,-2,2,0));head.addView(caption,new LinearLayout.LayoutParams(0,-2,1));
        if(selectable){TextView mark=txt(selectedLists.contains(l.id)?"☑":"□",26,selectedLists.contains(l.id)?CYAN:SOFT,true);head.addView(mark,lp(37,-2,0,0));}
        else head.addView(txt("‹",24,SOFT,true),lp(18,-2,0,0));
        shell.addView(head);ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(1,l.total));bar.setProgress(l.done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(0xFF89D5F9));shell.addView(bar,lp(-1,4,13,0));
        shell.setOnClickListener(v->{if(selectable&&!selectedLists.isEmpty()){toggleList(l.id);}else{activeList=l.id;navigate("DETAIL");}});
        if(selectable)shell.setOnLongClickListener(v->{toggleList(l.id);return true;});
        addCard(shell);
    }
    private void toggleList(String id){if(selectedLists.contains(id))selectedLists.remove(id);else selectedLists.add(id);show();}
    private void renderLists(){
        title(page,"لیست‌های من","کارهایت را مرتب کن، چند لیست را انتخاب کن و بکاپ بگیر.");
        LinearLayout controls=horizontal();controls.addView(action("＋ لیست جدید",WHITE,true,()->listDialog(null,null)),flex(0,0));
        controls.addView(action("🎙 افزودن با صدا",WHITE,false,()->navigate("VOICE")),flex(0,0));page.addView(controls,lp(-1,-2,0,10));
        if(!selectedLists.isEmpty()){
            LinearLayout select=card();select.addView(txt(selectedLists.size()+" لیست انتخاب شده",16,CYAN,true));
            LinearLayout ops=horizontal();ops.addView(action("بکاپ منتخب",WHITE,true,()->startExport(new HashSet<>(selectedLists))),flex(9,0));ops.addView(action("حذف منتخب",WHITE,false,this::confirmDeleteLists),flex(9,0));
            select.addView(ops);select.addView(action("لغو انتخاب",SOFT,false,()->{selectedLists.clear();show();}),lp(-1,-2,6,0));addCard(select);
        }
        LinearLayout searchBox=card();searchBox.addView(txt("⌕ جستجوی همه وظایف",15,WHITE,true),lp(-1,-2,0,8));
        EditText searchField=input("مثلاً: جلسه، خرید، تماس...","",false);searchBox.addView(searchField,lp(-1,-2,0,6));
        LinearLayout matches=vertical();searchBox.addView(matches);page.addView(searchBox,lp(-1,-2,0,8));
        searchField.addTextChangedListener(new android.text.TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int st,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int st,int before,int count){
                matches.removeAllViews();String q=s.toString().trim().toLowerCase(new Locale("fa","IR"));if(q.isEmpty())return;
                int found=0;for(Store.TaskData t:db.tasks(null)){
                    if(!t.title.toLowerCase(new Locale("fa","IR")).contains(q) && !t.description.toLowerCase(new Locale("fa","IR")).contains(q))continue;
                    TextView r=action((t.done?"✓ ":"○ ")+t.title,WHITE,false,()->{activeList=t.listId;navigate("DETAIL");});
                    matches.addView(r,lp(-1,-2,4,3));if(++found>=35)break;
                }
                if(found==0)matches.addView(txt("نتیجه‌ای پیدا نشد.",13,SOFT,false),lp(-1,-2,8,0));
            }
            @Override public void afterTextChanged(android.text.Editable s){}
        });
        for(Store.ListData l:db.lists())listCard(l,true);
        page.addView(action("بکاپ از تمام لیست‌ها و اهداف",WHITE,false,()->startExport(null)),lp(-1,-2,10,0));
        page.addView(action("بازیابی بکاپ JSON",WHITE,false,this::startImport),lp(-1,-2,9,0));
    }
    private void confirmDeleteLists(){
        int removable=0;for(String id:selectedLists)if(!id.equals("inbox"))removable++;
        final int count=removable;
        if(count==0){toast("صندوق ورودی قابل حذف نیست.");return;}
        new AlertDialog.Builder(this).setTitle("حذف "+count+" لیست؟")
          .setMessage("تمام وظایف و مراحل این لیست‌ها نیز پاک می‌شوند. ابتدا بکاپ بگیر.")
          .setNegativeButton("انصراف",null).setPositiveButton("حذف قطعی",(di,w)->{for(String lid:new HashSet<>(selectedLists))if(!"inbox".equals(lid))for(Store.TaskData t:db.tasks(lid))ReminderReceiver.cancelTaskAndSteps(this,db,t.id);db.deleteLists(selectedLists);selectedLists.clear();show();}).show();
    }
    private void listDialog(Store.ListData existing,Runnable afterAdd){
        EditText name=input("نام لیست؛ مثل سلامتی یا پروژه‌ها",existing==null?"":existing.title,false);
        LinearLayout box=vertical();box.setPadding(d(18),d(10),d(18),d(1));box.addView(name);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(existing==null?"ساخت لیست تازه":"تغییر نام لیست").setView(box)
            .setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create();
        dialog.setOnShowListener(unused->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String val=name.getText().toString().trim();if(val.isEmpty()){name.setError("نام را بنویس");return;}
            if(existing!=null)db.renameList(existing.id,val);else{activeList=db.addList(val);}
            dialog.dismiss();if(afterAdd!=null)afterAdd.run();else show();
        }));dialog.show();
    }
    private void renderDetails(){
        Store.ListData list=db.list(activeList);if(list==null){activeList="inbox";navigate("LISTS");return;}
        LinearLayout top=horizontal();top.addView(action("‹ برگشت",SOFT,false,()->navigate("LISTS")),lp(-2,-2,0,0));top.addView(action("✎ نام لیست",SOFT,false,()->listDialog(list,null)),flex(0,0));
        if(!"inbox".equals(list.id))top.addView(action("حذف",SOFT,false,()->new AlertDialog.Builder(this).setTitle("لیست حذف شود؟").setMessage("این کار برگشت‌پذیر نیست.").setNegativeButton("نه",null).setPositiveButton("حذف",(di,w)->{for(Store.TaskData t:db.tasks(list.id))ReminderReceiver.cancelTaskAndSteps(this,db,t.id);db.deleteLists(java.util.Collections.singleton(list.id));navigate("LISTS");}).show()),lp(-2,-2,0,0));
        page.addView(top,lp(-1,-2,14,1));title(page,list.title,"وظایف این فهرست را با حوصله به پایان برسان.");
        LinearLayout commands=horizontal();commands.addView(action("＋ کار جدید",WHITE,true,()->taskDialog(null)),flex(0,0));commands.addView(action("🎙 گفتن کار",WHITE,false,()->navigate("VOICE")),flex(0,0));page.addView(commands,lp(-1,-2,0,10));
        ArrayList<Store.TaskData> tasks=db.tasks(list.id);if(tasks.isEmpty()){
            LinearLayout empty=card();empty.addView(txt("✧ هنوز کاری ثبت نشده",18,WHITE,true));empty.addView(txt("با یک جمله یا چند کلمه اولین تسکت را بساز.",14,SOFT,false),lp(-1,-2,9,0));addCard(empty);
        }
        for(Store.TaskData t:tasks)taskCard(t);
        LinearLayout finish=card();finish.addView(txt("✦ جشن انجام تعهدات",17,WHITE,true));finish.addView(txt("بعد از انجام همه کارهای این لیست، دکمه را بزن.",13,SOFT,false),lp(-1,-2,4,13));
        finish.addView(action("🎉  من به تعهدم عمل کردم!",WHITE,true,()->celebrate(list)),lp(-1,55,0,2));addCard(finish);
    }
    private void taskCard(Store.TaskData task){
        LinearLayout shell=card();LinearLayout row=horizontal();
        TextView box=txt(task.done?"☑":"□",31,task.done?CYAN:SOFT,true);box.setGravity(Gravity.CENTER);
        box.setOnClickListener(v->{boolean next=!task.done;box.setText(next?"✦":"□");box.setTextColor(next?CYAN:SOFT);animateCompletion(box,()->{db.setDone(task,next);if(next)ReminderReceiver.dismissDeadlineNotification(this,"task",task.id);ReminderReceiver.scheduleAll(this,db);show();});});row.addView(box,lp(45,48,0,0));
        LinearLayout details=vertical();TextView label=txt(task.title,15,task.done?SOFT:WHITE,true);if(task.done)label.setPaintFlags(label.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);details.addView(label);
        String extra=(task.priority==3?"🔴 مهم   ":task.priority==2?"🟡 متوسط   ":"")+(task.due>0?"⏰ موعد: "+formatTime(task.due)+"   ":"بدون موعد مشخص   ")+(!"NONE".equals(task.repeat)?"↻ تکرارشونده":"");
        details.addView(txt(extra,11,SOFT,false),lp(-1,-2,6,0));
        if(task.description!=null&&!task.description.isEmpty())details.addView(txt(task.description,12,MUTED,false),lp(-1,-2,5,0));
        row.addView(details,new LinearLayout.LayoutParams(0,-2,1));TextView edit=txt("⋮",24,CYAN,true);edit.setGravity(Gravity.CENTER);edit.setOnClickListener(v->taskActions(task));row.addView(edit,lp(34,45,0,0));shell.addView(row);
        ArrayList<Store.StepData> steps=db.steps(task.id);
        if(!steps.isEmpty()){
            shell.addView(txt("مراحل انجام کار  ·  "+steps.stream().filter(x->x.done).count()+" از "+steps.size(),12,VIOLET,true),lp(-1,-2,12,4));
            for(Store.StepData step:steps)stageRow(shell,task,step);
        }
        shell.addView(action("＋ افزودن مرحله با موعد اختیاری",SOFT,false,()->stageDialog(task,null)),lp(-1,-2,10,0));
        shell.setOnClickListener(v->taskDialog(task));addCard(shell);
    }
    private void stageRow(LinearLayout parent,Store.TaskData task,Store.StepData step){
        LinearLayout row=horizontal();row.setPadding(d(5),d(3),d(5),d(3));
        TextView check=txt(step.done?"☑":"○",24,step.done?CYAN:SOFT,true);check.setGravity(Gravity.CENTER);
        check.setOnClickListener(v->{boolean next=!step.done;check.setText(next?"✦":"○");check.setTextColor(next?CYAN:SOFT);animateCompletion(check,()->{db.setStepDone(step,next);if(next)ReminderReceiver.dismissDeadlineNotification(this,"step",step.id);ReminderReceiver.scheduleAll(this,db);show();});});row.addView(check,lp(35,40,0,0));
        LinearLayout info=vertical();TextView title=txt(step.title,13,step.done?SOFT:WHITE,true);if(step.done)title.setPaintFlags(title.getPaintFlags()|android.graphics.Paint.STRIKE_THRU_TEXT_FLAG);info.addView(title);
        if(step.due>0)info.addView(txt("⏰ "+formatTime(step.due),10,SOFT,false),lp(-1,-2,1,0));
        row.addView(info,new LinearLayout.LayoutParams(0,-2,1));TextView menu=txt("⋮",20,CYAN,true);menu.setGravity(Gravity.CENTER);menu.setOnClickListener(v->stageActions(task,step));row.addView(menu,lp(30,38,0,0));
        parent.addView(row,lp(-1,-2,2,2));
    }
    private void animateCompletion(View target,Runnable after){
        target.setEnabled(false);target.setScaleX(.72f);target.setScaleY(.72f);target.setRotation(-10f);
        target.animate().scaleX(1.22f).scaleY(1.22f).rotation(5f).setDuration(170).withEndAction(()->target.animate().scaleX(1f).scaleY(1f).rotation(0f).setDuration(160).withEndAction(()->{target.setEnabled(true);after.run();}).start()).start();
    }
    private void taskActions(Store.TaskData task){new AlertDialog.Builder(this).setTitle(task.title).setItems(new String[]{"ویرایش","حذف"},(di,w)->{if(w==0)taskDialog(task);else new AlertDialog.Builder(this).setTitle("این وظیفه حذف شود؟").setNegativeButton("انصراف",null).setPositiveButton("حذف",(dia,which)->{ReminderReceiver.cancelTaskAndSteps(this,db,task.id);db.deleteTask(task.id);show();}).show();}).show();}
    private void stageDialog(Store.TaskData task,Store.StepData original){
        LinearLayout form=vertical();form.setPadding(d(18),d(6),d(18),d(2));
        EditText name=input("عنوان مرحله؛ مثلاً آماده‌کردن پیش‌نویس",original==null?"":original.title,false);form.addView(name,lp(-1,-2,0,10));
        long[] due={original==null?0:original.due};
        TextView dueButton=action(due[0]==0?"⏰ تعیین موعد مرحله (اختیاری)":"⏰ "+formatTime(due[0]),WHITE,false,()->{});
        dueButton.setOnClickListener(v->chooseTime(due,()->dueButton.setText("⏰ "+formatTime(due[0]))));form.addView(dueButton,lp(-1,-2,0,5));
        form.addView(action("پاک کردن موعد",SOFT,false,()->{due[0]=0;dueButton.setText("⏰ تعیین موعد مرحله (اختیاری)");}),lp(-1,-2,0,5));
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(original==null?"افزودن مرحله":"ویرایش مرحله").setView(form).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create();
        dlg.setOnShowListener(unused->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String val=name.getText().toString().trim();if(val.isEmpty()){name.setError("عنوان مرحله را بنویس");return;}try{if(original==null)db.addStep(task.id,val,due[0]);else{original.title=val;original.due=due[0];db.editStep(original);ReminderReceiver.dismissDeadlineNotification(this,"step",original.id);}ReminderReceiver.scheduleAll(this,db);dlg.dismiss();show();}catch(Exception e){toast("ذخیره مرحله انجام نشد: "+e.getMessage());}}));dlg.show();
    }
    private void stageActions(Store.TaskData task,Store.StepData step){new AlertDialog.Builder(this).setTitle(step.title).setItems(new String[]{"ویرایش مرحله","حذف مرحله"},(di,w)->{if(w==0)stageDialog(task,step);else new AlertDialog.Builder(this).setTitle("مرحله حذف شود؟").setNegativeButton("انصراف",null).setPositiveButton("حذف",(d1,w1)->{ReminderReceiver.cancelStep(this,step.id);db.deleteStep(step.id);show();}).show();}).show();}
    private String formatTime(long time){return new SimpleDateFormat("yyyy/MM/dd  HH:mm",new Locale("fa","IR")).format(new java.util.Date(time));}
    private void taskDialog(Store.TaskData original){
        LinearLayout form=vertical();form.setPadding(d(19),d(6),d(19),d(3));
        EditText name=input("متن وظیفه",original==null?"":original.title,false);form.addView(name,lp(-1,-2,0,12));
        EditText note=input("توضیحات (اختیاری)",original==null?"":original.description,true);form.addView(note,lp(-1,-2,0,9));
        TextView priorityLabel=txt("اولویت",13,SOFT,true);form.addView(priorityLabel,lp(-1,-2,0,3));
        Spinner priorities=new Spinner(this);priorities.setAdapter(spinnerAdapter(new String[]{"عادی","متوسط","مهم"}));priorities.setSelection(original==null?0:Math.max(0,Math.min(2,original.priority-1)));form.addView(priorities,lp(-1,47,0,7));
        form.addView(txt("تکرار",13,SOFT,true),lp(-1,-2,0,3));Spinner repeat=new Spinner(this);repeat.setAdapter(spinnerAdapter(REPEAT_LABELS));int repeatIndex=0;
        if(original!=null)for(int i=0;i<REPEAT_CODES.length;i++)if(REPEAT_CODES[i].equals(original.repeat))repeatIndex=i;
        repeat.setSelection(repeatIndex);form.addView(repeat,lp(-1,47,0,10));
        long[] due={original==null?0:original.due};TextView dateButton=action(due[0]==0?"⏰ تعیین موعد انجام (اختیاری)":"⏰ "+formatTime(due[0]),WHITE,false,()->{});
        dateButton.setOnClickListener(v->chooseTime(due,()->dateButton.setText("⏰ "+formatTime(due[0]))));form.addView(dateButton,lp(-1,-2,0,7));
        form.addView(action("پاک کردن موعد",SOFT,false,()->{due[0]=0;dateButton.setText("⏰ تعیین موعد انجام (اختیاری)");}),lp(-1,-2,0,7));
        form.addView(txt("مدار یک دقیقه پیش از موعد، می‌پرسد: «این کار انجام شد؟ اگر هنوز مانده، می‌توانی ۱۰ دقیقه دیگر یادآوری بگیری.»",11,SOFT,false),lp(-1,-2,0,5));
        ArrayList<Store.ListData> lists=db.lists();String[] names=new String[lists.size()];int idx=0;
        for(int i=0;i<lists.size();i++){names[i]=lists.get(i).title;if(original!=null&&original.listId.equals(lists.get(i).id))idx=i;else if(original==null&&activeList.equals(lists.get(i).id))idx=i;}
        form.addView(txt("لیست مقصد",13,SOFT,true),lp(-1,-2,0,3));Spinner listSelect=new Spinner(this);listSelect.setAdapter(spinnerAdapter(names));listSelect.setSelection(idx);form.addView(listSelect,lp(-1,45,0,8));
        ScrollView wrapper=new ScrollView(this);wrapper.addView(form);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(original==null?"وظیفه جدید":"ویرایش وظیفه").setView(wrapper).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create();
        dialog.setOnShowListener(unused->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            String text=name.getText().toString().trim();if(text.isEmpty()){name.setError("وظیفه خالی است");return;}
            String listId=lists.get(listSelect.getSelectedItemPosition()).id;String rec=REPEAT_CODES[repeat.getSelectedItemPosition()];int pri=priorities.getSelectedItemPosition()+1;
            try{
                if(original==null){db.addTask(listId,text,due[0],pri,rec);}
                else {original.title=text;original.description=note.getText().toString().trim();original.listId=listId;original.due=due[0];original.priority=pri;original.repeat=rec;db.editTask(original);ReminderReceiver.dismissDeadlineNotification(this,"task",original.id);}
                ReminderReceiver.scheduleAll(this,db);activeList=listId;dialog.dismiss();navigate("DETAIL");
            }catch(Exception e){toast("ذخیره انجام نشد: "+e.getMessage());}
        }));dialog.show();
    }
    private ArrayAdapter<String> spinnerAdapter(String[] items){ArrayAdapter<String> a=new ArrayAdapter<>(this,android.R.layout.simple_spinner_item,items);a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);return a;}
    private void chooseTime(long[] output,Runnable changed){Calendar initial=Calendar.getInstance();if(output[0]>0)initial.setTimeInMillis(output[0]);
        new DatePickerDialog(this,(picker,y,m,day)->new TimePickerDialog(this,(v,h,min)->{
            Calendar c=Calendar.getInstance();c.set(Calendar.YEAR,y);c.set(Calendar.MONTH,m);c.set(Calendar.DAY_OF_MONTH,day);c.set(Calendar.HOUR_OF_DAY,h);c.set(Calendar.MINUTE,min);c.set(Calendar.SECOND,0);c.set(Calendar.MILLISECOND,0);output[0]=c.getTimeInMillis();changed.run();
        },9,0,true).show(),initial.get(Calendar.YEAR),initial.get(Calendar.MONTH),initial.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void celebrate(Store.ListData list){Store.ListData fresh=db.list(list.id);if(fresh==null)return;
        if(fresh.total==0){toast("ابتدا کاری به این لیست اضافه کن.");return;}
        if(fresh.done<fresh.total){toast("هنوز "+(fresh.total-fresh.done)+" وظیفه باقی مانده؛ تو می‌توانی!");return;}
        if(Build.VERSION.SDK_INT>=26){Vibrator vib=(Vibrator)getSystemService(VIBRATOR_SERVICE);if(vib!=null&&vib.hasVibrator())vib.vibrate(VibrationEffect.createWaveform(new long[]{0,60,80,60,90,150},-1));}
        ReminderReceiver.achievement(this,fresh.title);
        new AlertDialog.Builder(this).setTitle("🎊  مأموریت انجام شد!").setMessage("✨ فوق‌العاده بود!\n\nهمه "+fresh.total+" کار «"+fresh.title+"» را انجام دادی.\n\nاین موفقیت نتیجه پایبندی تو به قول‌هایی است که به خودت داده‌ای. 🚀")
             .setPositiveButton("به افتخار من! 🌟",(di,w)->{}).show();
    }
    private void renderGoals(boolean work){
        String domain=work?"WORK":"PERSONAL";String[] codes=work?WORK_PERIODS:PERIODS;String[] captions=work?WORK_LABELS:PERIOD_LABELS;
        String period=work?workPeriod:personalPeriod;
        title(page,work?"مدار کار و حرفه 💼":"نقشه اهداف من ✨",work?"از برنامه روزانه محل کار تا هدف نهایی حرفه‌ای":"برنامه‌های روزانه، هفتگی، ماهانه و سالانه");
        LinearLayout selector=horizontal();for(int i=0;i<codes.length;i++){final String x=codes[i];TextView chip=action(captions[i],period.equals(x)?WHITE:SOFT,period.equals(x),()->{if(work){workPeriod=x;workOffset=0;}else{personalPeriod=x;personalOffset=0;}show();});chip.setTextSize(12);selector.addView(chip,flex(0,0));}page.addView(selector,lp(-1,-2,0,15));
        final String goalBucket=Store.bucket(period,work?workOffset:personalOffset);
        if(!"ULTIMATE".equals(period)){
            LinearLayout dates=horizontal();
            dates.addView(action("‹ قبلی",SOFT,false,()->{if(work)workOffset--;else personalOffset--;show();}),flex(0,0));
            TextView current=txt("📅 "+goalBucket,13,WHITE,true);current.setGravity(Gravity.CENTER);dates.addView(current,flex(0,0));
            dates.addView(action("بعدی ›",SOFT,false,()->{if(work)workOffset++;else personalOffset++;show();}),flex(0,0));
            page.addView(dates,lp(-1,-2,0,12));
        }
        ArrayList<Store.GoalData> items=db.goals(domain,period,goalBucket);int done=0;for(Store.GoalData g:items)if(g.done)done++;
        LinearLayout progress=card();progress.addView(txt("پیشرفت "+captions[java.util.Arrays.asList(codes).indexOf(period)],18,WHITE,true));progress.addView(txt(done+" از "+items.size()+" هدف محقق شده",13,SOFT,false),lp(-1,-2,5,7));
        ProgressBar bar=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(Math.max(items.size(),1));bar.setProgress(done);bar.setProgressTintList(android.content.res.ColorStateList.valueOf(VIOLET));progress.addView(bar,lp(-1,6,0,4));addCard(progress);
        page.addView(action("＋ افزودن "+(work?"هدف کاری":"هدف جدید"),WHITE,true,()->goalDialog(null,domain,period,goalBucket)),lp(-1,-2,8,10));
        if(items.isEmpty()){LinearLayout empty=card();empty.addView(txt("برای این بازه هنوز هدفی ثبت نشده.",14,SOFT,false));addCard(empty);}
        for(Store.GoalData g:items){LinearLayout row=card();LinearLayout body=horizontal();
            TextView checkbox=txt(g.done?"☑":"□",28,g.done?CYAN:SOFT,true);body.addView(checkbox,lp(39,40,0,0));checkbox.setOnClickListener(v->{db.setGoalDone(g,!g.done);show();});
            TextView title=txt(g.title,15,g.done?SOFT:WHITE,true);body.addView(title,new LinearLayout.LayoutParams(0,-2,1));
            TextView menu=txt("⋮",24,CYAN,true);menu.setGravity(Gravity.CENTER);body.addView(menu,lp(30,38,0,0));menu.setOnClickListener(v->goalMenu(g));
            row.addView(body);row.setOnClickListener(v->goalDialog(g,g.domain,g.period,g.bucket));addCard(row);
        }
    }
    private void goalDialog(Store.GoalData g,String domain,String period,String bucket){EditText edit=input("هدف را دقیق و قابل اندازه‌گیری بنویس",g==null?"":g.title,true);
        LinearLayout wrap=vertical();wrap.setPadding(d(18),d(10),d(18),0);wrap.addView(edit);
        AlertDialog dlg=new AlertDialog.Builder(this).setTitle(g==null?"افزودن هدف":"ویرایش هدف").setView(wrap).setNegativeButton("انصراف",null).setPositiveButton("ذخیره",null).create();
        dlg.setOnShowListener(u->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{String val=edit.getText().toString().trim();if(val.isEmpty()){edit.setError("هدف را بنویس");return;}if(g==null)db.addGoal(domain,period,bucket,val);else{g.title=val;db.editGoal(g);}dlg.dismiss();show();}));dlg.show();
    }
    private void goalMenu(Store.GoalData g){new AlertDialog.Builder(this).setTitle(g.title).setItems(new String[]{"ویرایش","حذف"},(di,i)->{if(i==0)goalDialog(g,g.domain,g.period,g.bucket);else new AlertDialog.Builder(this).setTitle("این هدف حذف شود؟").setNegativeButton("انصراف",null).setPositiveButton("حذف",(d,w)->{db.deleteGoal(g.id);show();}).show();}).show();}
    private void renderVoice(){
        title(page,"صدای تو، برنامه تو 🎙","بدون گوگل؛ پردازش گفتار پس از نصب مدل روی گوشی انجام می‌شود.");
        LinearLayout hero=card();hero.setBackground(gradient(0xD03E3F90,0xAB1B557A,26));
        hero.addView(txt("فقط بگو امروز چه کاری داری...",16,WHITE,true),lp(-1,-2,0,15));
        TextView big=txt(listening?"■":"🎙",47,WHITE,true);big.setGravity(Gravity.CENTER);big.setBackground(gradient(0xFF8864DC,0xFF3397C8,100));
        LinearLayout center=horizontal();center.setGravity(Gravity.CENTER);center.addView(big,lp(115,115,2,7));hero.addView(center);
        micButton=big;
        big.setOnClickListener(v->toggleSpeech());
        micLabel=txt(listening?"برای پایان ضبط، دکمه را لمس کن":"برای ضبط صدا لمس کن",15,WHITE,true);micLabel.setGravity(Gravity.CENTER);hero.addView(micLabel,lp(-1,-2,6,10));
        voiceStatus=txt(modelManager.installed()?"مدل فارسی روی گوشی: نصب شده ✓":"نیاز به دریافت اولیه مدل فارسی (حدود ۵۳ MB)",12,WHITE,false);voiceStatus.setGravity(Gravity.CENTER);hero.addView(voiceStatus,lp(-1,-2,5,2));addCard(hero);
        if(!modelManager.installed())page.addView(action("↓ دریافت مدل آفلاین فارسی",WHITE,false,()->installModel(false)),lp(-1,-2,7,5));
        LinearLayout transcript=card();transcript.addView(txt("متن خام تشخیص‌داده‌شده",16,WHITE,true),lp(-1,-2,0,9));
        rawPreview=txt(spoken.length()==0?(draft.isEmpty()?"پس از شروع ضبط، جمله‌ها اینجا نمایش داده می‌شوند.":"می‌توانی متن را مستقیماً در کادر پایین بنویسی."):spoken.toString()+" "+livePart,13,SOFT,false);
        rawPreview.setMinHeight(d(45));transcript.addView(rawPreview,lp(-1,-2,0,10));
        transcript.addView(txt("متن نهایی قابل ویرایش",16,WHITE,true),lp(-1,-2,6,7));
        voiceInput=input("متن وظیفه را اینجا می‌توانی اصلاح کنی...",draft,true);transcript.addView(voiceInput,lp(-1,-2,0,12));
        LinearLayout controls=horizontal();
        controls.addView(action("⌨ تایپ دستی",WHITE,false,()->{voiceInput.requestFocus();((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(voiceInput,InputMethodManager.SHOW_IMPLICIT);}),flex(0,0));
        controls.addView(action("✦ رسمی‌سازی متن",WHITE,false,()->{draft=PersianFormatter.clean(voiceInput.getText().toString());voiceInput.setText(draft);voiceInput.setSelection(draft.length());toast("رسمی‌سازی واژگانی انجام شد؛ حتماً متن را بازبینی کن.");}),flex(0,0));transcript.addView(controls);
        LinearLayout manage=horizontal();manage.addView(action("حذف متن",SOFT,false,()->{if(listening)stopSpeech();recordingGeneration++;draft="";spoken.setLength(0);livePart="";voiceInput.setText("");rawPreview.setText("متن پاک شد.");}),flex(7,0));
        manage.addView(action("افزودن به لیست ←",WHITE,true,this::chooseVoiceList),flex(7,0));transcript.addView(manage,lp(-1,-2,8,0));
        transcript.addView(action("＋ ایجاد لیست و افزودن",WHITE,false,()->{
            draft=voiceInput.getText().toString().trim();if(draft.isEmpty()){toast("اول متن را بگو یا تایپ کن.");return;}listDialog(null,()->saveVoiceToList(activeList));
        }),lp(-1,-2,8,0));addCard(transcript);
        LinearLayout info=card();info.addView(txt("نکته‌های ضبط دقیق",15,VIOLET,true));
        info.addView(txt("• نزدیک میکروفون و در محیط آرام صحبت کن.\n• جمله‌ها را واضح و با مکث کوتاه بگو.\n• برای ساخت چند کار، بین آن‌ها «؛» بگذار یا هر کار را در خط جدا بنویس.\n• نسخه فعلی رسمی‌سازی را با قواعد واژگانی انجام می‌دهد، نه یک مدل زبانی مولد.",13,SOFT,false),lp(-1,-2,9,0));addCard(info);
    }
    private void toggleSpeech(){if(listening)stopSpeech();else startSpeech();}
    private void startSpeech(){
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},IN_MIC);return;}
        if(!modelManager.installed()){
            new AlertDialog.Builder(this).setTitle("دریافت مدل گفتار فارسی").setMessage("برای تشخیص گفتار بدون سرویس گوگل، یک‌بار مدل حدوداً ۵۳ مگابایتی Vosk از سایت سازنده دریافت می‌شود. بعد از نصب، تشخیص صدا کاملاً آفلاین است.")
                .setNegativeButton("بعداً",null).setPositiveButton("دریافت و ضبط",(dialog,w)->installModel(true)).show();return;
        }
        voiceStatus.setText("موتور گفتار در حال آماده شدن است...");
        modelManager.prepare(false,modelCallback(true));
    }
    private void installModel(boolean autoRecord){
        if(modelManager.busy()){toast("مدل در حال دانلود یا بارگذاری است.");return;}
        modelManager.prepare(true,modelCallback(autoRecord));
    }
    private ModelManager.Callback modelCallback(boolean autoRecord){return new ModelManager.Callback(){
        @Override public void status(String status){if(voiceStatus!=null&&"VOICE".equals(pageName))voiceStatus.setText(status);}
        @Override public void ready(Model m){if(voiceStatus!=null&&"VOICE".equals(pageName))voiceStatus.setText("موتور آفلاین فارسی آماده است ✓");if(autoRecord&&"VOICE".equals(pageName))beginSpeech(m);}
        @Override public void error(String message){if(voiceStatus!=null&&"VOICE".equals(pageName))voiceStatus.setText(message);toast(message);}
    };}
    private void beginSpeech(Model model){
        if(listening)return;
        try{
            spoken.setLength(0);livePart="";draft="";lowConfidence=false;
            if(voiceInput!=null){voiceInput.setText("");voiceInput.setEnabled(false);}
            Recognizer recognizer=new Recognizer(model,16000.0f);
            activeRecognizer=recognizer;
            recognizer.setWords(true);
            speechService=new SpeechService(recognizer,16000.0f);
            final int session=++recordingGeneration;
            speechService.startListening(new RecognitionListener(){
                @Override public void onPartialResult(String value){if(session==recordingGeneration)MainActivity.this.onPartialResult(value);}
                @Override public void onResult(String value){if(session==recordingGeneration)MainActivity.this.onResult(value);}
                @Override public void onFinalResult(String value){if(session==recordingGeneration)MainActivity.this.onFinalResult(value);}
                @Override public void onError(Exception error){if(session==recordingGeneration)MainActivity.this.onError(error);}
                @Override public void onTimeout(){if(session==recordingGeneration)MainActivity.this.onTimeout();}
            });
            listening=true;startPulse();if(voiceStatus!=null)voiceStatus.setText("● در حال شنیدن و پردازش روی همین گوشی");if(micLabel!=null)micLabel.setText("■  پایان ضبط");if(rawPreview!=null)rawPreview.setText("دارم گوش می‌دهم...");
        }catch(Exception e){listening=false;if(voiceInput!=null)voiceInput.setEnabled(true);toast("میکروفون شروع نشد: "+e.getMessage());}
    }
    private void stopSpeech(){
        if(!listening&&speechService==null)return;
        listening=false;stopPulse();
        SpeechService old=speechService;speechService=null;
        if(old!=null){try{old.stop();}catch(Exception ignored){}try{old.shutdown();}catch(Exception ignored){}}
        if(activeRecognizer!=null){try{activeRecognizer.close();}catch(Exception ignored){}activeRecognizer=null;}
        if(voiceInput!=null){voiceInput.setEnabled(true);updateDraftFromSpeech(true);}
        if(micLabel!=null)micLabel.setText("🎙  ضبط دوباره");if(voiceStatus!=null)voiceStatus.setText(lowConfidence?"⚠ دقت برخی واژه‌ها پایین بود؛ متن را بازبینی کن.":"ضبط پایان یافت؛ متن را پیش از ثبت بازبینی کن.");
    }
    private void startPulse(){
        if(micButton==null)return;
        micButton.setText("■");
        pulseX=ObjectAnimator.ofFloat(micButton,"scaleX",1f,1.12f,1f);
        pulseY=ObjectAnimator.ofFloat(micButton,"scaleY",1f,1.12f,1f);
        pulseX.setRepeatCount(ObjectAnimator.INFINITE);pulseY.setRepeatCount(ObjectAnimator.INFINITE);
        pulseX.setDuration(1050);pulseY.setDuration(1050);pulseX.start();pulseY.start();
    }
    private void stopPulse(){
        if(pulseX!=null){pulseX.cancel();pulseX=null;}if(pulseY!=null){pulseY.cancel();pulseY=null;}
        if(micButton!=null){micButton.setScaleX(1f);micButton.setScaleY(1f);micButton.setText("🎙");}
    }
    private void checkConfidence(String json){
        try{JSONArray words=new JSONObject(json).optJSONArray("result");if(words==null)return;
            for(int i=0;i<words.length();i++)if(words.getJSONObject(i).optDouble("conf",1.0)<0.62){lowConfidence=true;break;}
        }catch(Exception ignored){}
    }
    private static String extract(String json,String key){try{return new JSONObject(json).optString(key,"").trim();}catch(Exception e){return "";}}
    private void appendSpeech(String hypothesis){if(hypothesis.isEmpty())return;if(spoken.length()>0&&!spoken.toString().endsWith(hypothesis))spoken.append(" ");
        if(!spoken.toString().endsWith(hypothesis))spoken.append(hypothesis);livePart="";updateDraftFromSpeech(false);
    }
    private void updateDraftFromSpeech(boolean includePartial){
        if(!"VOICE".equals(pageName))return;
        String full=spoken.toString().trim();if(includePartial&&!livePart.isEmpty()&&!full.endsWith(livePart))full+=(full.isEmpty()?"":" ")+livePart;
        if(rawPreview!=null)rawPreview.setText(full.isEmpty()?"هنوز جمله‌ای دریافت نشده.":full);
        if(voiceInput!=null){draft=PersianFormatter.clean(full);voiceInput.setText(draft);voiceInput.setSelection(draft.length());}
    }
    @Override public void onPartialResult(String s){String partial=extract(s,"partial");runOnUiThread(()->{if(!listening)return;livePart=partial;if(rawPreview!=null)rawPreview.setText(spoken.toString()+" "+livePart);});}
    @Override public void onResult(String s){String text=extract(s,"text");runOnUiThread(()->{if(!"VOICE".equals(pageName))return;checkConfidence(s);appendSpeech(text);});}
    @Override public void onFinalResult(String s){String text=extract(s,"text");runOnUiThread(()->{if(!"VOICE".equals(pageName))return;checkConfidence(s);appendSpeech(text);listening=false;stopPulse();if(voiceInput!=null){voiceInput.setEnabled(true);updateDraftFromSpeech(true);}if(micLabel!=null)micLabel.setText("🎙  ضبط دوباره");if(voiceStatus!=null)voiceStatus.setText(lowConfidence?"⚠ بعضی واژه‌ها با اطمینان کم تشخیص داده شدند؛ حتماً اصلاح کن.":"پردازش آفلاین انجام شد. متن را بررسی کن.");});}
    @Override public void onError(Exception e){runOnUiThread(()->{stopSpeech();toast("خطای تشخیص صدا: "+e.getMessage());});}
    @Override public void onTimeout(){runOnUiThread(this::stopSpeech);}
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);
        if(requestCode==IN_MIC){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&"VOICE".equals(pageName))startSpeech();else toast("برای ضبط باید اجازه میکروفون بدهی.");}
    }
    private void chooseVoiceList(){
        if(listening){toast("ابتدا ضبط صدا را متوقف کن.");return;}
        if(voiceInput==null)return;draft=voiceInput.getText().toString().trim();if(draft.isEmpty()){voiceInput.setError("متن را وارد کن");return;}
        ArrayList<Store.ListData> options=db.lists();String[] labels=new String[options.size()+1];
        for(int i=0;i<options.size();i++)labels[i]=options.get(i).title;labels[options.size()]="＋ ایجاد لیست جدید";
        new AlertDialog.Builder(this).setTitle("افزودن به کدام لیست؟").setItems(labels,(di,which)->{
            if(which==options.size())listDialog(null,()->saveVoiceToList(activeList));else saveVoiceToList(options.get(which).id);
        }).show();
    }
    private void saveVoiceToList(String listId){
        String[] items=PersianFormatter.splitTasks(draft);if(items.length==0){toast("چیزی برای ذخیره وجود ندارد.");return;}
        int total=0;for(String task:items){if(!task.isEmpty()){db.addTask(listId,task,0,1,"NONE");total++;}}
        draft="";spoken.setLength(0);livePart="";activeList=listId;toast(total+" وظیفه به لیست اضافه شد ✓");navigate("DETAIL");
    }
    private void renderSettings(){
        title(page,"بیشتر و تنظیمات ⚙","داده‌ها متعلق به خودت هستند؛ از آن‌ها مراقبت کن.");
        LinearLayout privacy=card();privacy.addView(txt("🔒  حریم خصوصی",19,WHITE,true));privacy.addView(txt("لیست‌ها و هدف‌ها در پایگاه داده SQLite گوشی ذخیره می‌شوند. متن و صوت برای تبدیل گفتار به هیچ سروری ارسال نمی‌شوند. تنها دانلود اولیه مدل نیازمند اینترنت است.",14,SOFT,false),lp(-1,-2,10,0));addCard(privacy);
        LinearLayout sound=card();sound.addView(txt("🎙  موتور تبدیل گفتار",19,WHITE,true));sound.addView(txt(modelManager.installed()?"مدل فارسی Vosk نصب است؛ ضبط بدون اینترنت کار می‌کند.":"مدل فارسی هنوز نصب نشده است.",13,SOFT,false),lp(-1,-2,7,12));
        sound.addView(action(modelManager.installed()?"آزمایش گفتار":"نصب مدل گفتار آفلاین",WHITE,true,()->navigate("VOICE")),lp(-1,-2,0,0));addCard(sound);
        boolean dailyOn=ReminderReceiver.dailyFollowupsEnabled(this);
        LinearLayout followups=card();followups.addView(txt("⏰  پیگیری هوشمند",19,WHITE,true));
        followups.addView(txt("برای هر وظیفه یا مرحله‌ای که موعد دارد، یک دقیقه پیش از موعد می‌پرسم: «انجامش دادی؟» می‌توانی انجام‌شدن را مستقیم از اعلان ثبت کنی یا ۱۰ دقیقه بعد دوباره یادآوری بگیری. پیگیری روزانه در ساعت‌های ۹، ۱۲، ۱۵، ۱۸ و ۲۱ انجام می‌شود.",13,SOFT,false),lp(-1,-2,8,10));
        followups.addView(txt(dailyOn?"وضعیت: پیگیری روزانه روشن است ✓":"وضعیت: پیگیری روزانه خاموش است",12,dailyOn?CYAN:VIOLET,true),lp(-1,-2,0,8));
        followups.addView(action(dailyOn?"خاموش کردن پیگیری روزانه":"فعال کردن پیگیری روزانه",WHITE,true,()->{ReminderReceiver.setDailyFollowupsEnabled(this,!ReminderReceiver.dailyFollowupsEnabled(this));show();}),lp(-1,-2,0,0));
        if(Build.VERSION.SDK_INT>=31){AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);if(am!=null&&!am.canScheduleExactAlarms())followups.addView(action("فعال‌سازی اعلان‌های دقیق در تنظیمات اندروید",WHITE,false,()->{try{refreshDailyOnResume=true;startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())));}catch(Exception e){refreshDailyOnResume=false;toast("از تنظیمات برنامه، دسترسی اعلان دقیق را فعال کن.");}}),lp(-1,-2,7,0));}
        addCard(followups);
        LinearLayout back=card();back.addView(txt("⬡  پشتیبان‌گیری و بازیابی",18,WHITE,true));back.addView(txt("از تمام لیست‌ها، مراحل، موعدها و اهداف نسخه JSON بگیر؛ یا از صفحه لیست‌ها فقط موارد انتخاب‌شده را ذخیره کن. بکاپ‌های نسخه قبلی نیز قابل بازیابی هستند.",13,SOFT,false),lp(-1,-2,5,10));
        back.addView(action("خروجی کامل JSON",WHITE,true,()->startExport(null)),lp(-1,-2,0,5));back.addView(action("واردکردن بکاپ JSON",WHITE,false,this::startImport),lp(-1,-2,6,0));addCard(back);
        LinearLayout about=card();about.addView(txt("مدار ۱٫۱ — طراحی آفلاین و مستقل",16,WHITE,true));about.addView(txt("تبدیل لحن محاوره‌ای با فرهنگ واژگان و قواعد محدود انجام می‌شود؛ برای متن‌های حساس قبل از ذخیره بازبینی کن. برای دقیق‌ترین زمان‌بندی، دسترسی اعلان دقیق را در تنظیمات اندروید فعال کن؛ محدودیت باتری بعضی گوشی‌ها نیز ممکن است اعلان‌ها را به تأخیر بیندازد.",13,SOFT,false),lp(-1,-2,6,0));addCard(about);
    }
    private void startExport(HashSet<String> selected){exportSelection=selected;
        Intent out=new Intent(Intent.ACTION_CREATE_DOCUMENT);out.addCategory(Intent.CATEGORY_OPENABLE);out.setType("application/json");
        String stamp=new SimpleDateFormat("yyyyMMdd-HHmm",Locale.US).format(new java.util.Date());out.putExtra(Intent.EXTRA_TITLE,"MADAR-"+(selected==null?"full":"selected")+"-"+stamp+".json");
        startActivityForResult(out,SAVE_JSON);
    }
    private void startImport(){Intent in=new Intent(Intent.ACTION_OPEN_DOCUMENT);in.addCategory(Intent.CATEGORY_OPENABLE);in.setType("*/*");in.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(in,OPEN_JSON);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);
        if(result!=RESULT_OK||data==null||data.getData()==null)return;
        try{
            if(request==SAVE_JSON){JSONObject json=db.backup(exportSelection);try(OutputStream stream=getContentResolver().openOutputStream(data.getData())){
                if(stream==null)throw new java.io.IOException("Cannot open destination");stream.write(json.toString(2).getBytes(StandardCharsets.UTF_8));stream.flush();
            }toast("بکاپ با موفقیت ذخیره شد ✓");exportSelection=null;}
            else if(request==OPEN_JSON){
                byte[] bytes;try(InputStream stream=getContentResolver().openInputStream(data.getData())){
                    if(stream==null)throw new java.io.IOException("Cannot read backup");ByteArrayOutputStream buffer=new ByteArrayOutputStream();byte[] chunk=new byte[16384];int n;
                    while((n=stream.read(chunk))!=-1){if(buffer.size()+n>50_000_000)throw new java.io.IOException("فایل بکاپ بزرگ‌تر از ۵۰ مگابایت است");buffer.write(chunk,0,n);}bytes=buffer.toByteArray();
                }
                JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                new AlertDialog.Builder(this).setTitle("بازیابی اطلاعات").setMessage("لیست‌ها و اهداف تازه اضافه می‌شوند؛ موارد با شناسه تکراری نادیده گرفته خواهند شد. ادامه می‌دهی؟")
                  .setNegativeButton("انصراف",null).setPositiveButton("بازیابی",(di,w)->{
                      try{toast(db.importBackup(json));ReminderReceiver.scheduleAll(this,db);selectedLists.clear();navigate("LISTS");}
                      catch(Exception e){new AlertDialog.Builder(this).setTitle("بکاپ بازیابی نشد").setMessage(e.getMessage()).setPositiveButton("باشه",null).show();}
                  }).show();
            }
        }catch(Exception ex){new AlertDialog.Builder(this).setTitle("خطا در فایل").setMessage(ex.getMessage()).setPositiveButton("متوجه شدم",null).show();}
    }
    @Override public void onBackPressed(){if(listening)stopSpeech();if("DETAIL".equals(pageName))navigate("LISTS");else if(!"HOME".equals(pageName))navigate("HOME");else super.onBackPressed();}
    @Override protected void onResume(){super.onResume();if(db!=null)ReminderReceiver.scheduleAll(this,db);if(refreshDailyOnResume){refreshDailyOnResume=false;ReminderReceiver.scheduleDailyFollowups(this);}}
    @Override protected void onStop(){if(listening)stopSpeech();super.onStop();}
    @Override protected void onDestroy(){if(speechService!=null)stopSpeech();modelManager.shutdown();db.close();super.onDestroy();}

    /** Deterministic procedurally painted space background; no remote asset and no image placeholder. */
    private final class SpaceView extends View {
        private final Paint p=new Paint(3);private final java.util.Random rng=new java.util.Random(71353);
        private final float[] x=new float[145],y=new float[145],sz=new float[145];
        SpaceView(){super(MainActivity.this);for(int i=0;i<x.length;i++){x[i]=rng.nextFloat();y[i]=rng.nextFloat();sz[i]=rng.nextFloat()*1.8f+.4f;}}
        @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();
            p.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF090D23,0xFF191439,0xFF081626},null,Shader.TileMode.CLAMP));c.drawRect(0,0,w,h,p);p.setShader(null);
            halo(c,w*.15f,h*.16f,d(205),0x325F55D3);halo(c,w*.87f,h*.48f,d(240),0x2543B9D9);halo(c,w*.25f,h*.80f,d(230),0x236B55B6);
            for(int i=0;i<x.length;i++){p.setColor(i%6==0?0x99A4F4FF:0x7795A8ED);p.setStyle(Paint.Style.FILL);c.drawCircle(x[i]*w,y[i]*h,d(sz[i]),p);}
            p.setShader(null);
        }
        private void halo(Canvas c,float cx,float cy,float r,int color){p.setShader(new RadialGradient(cx,cy,r,new int[]{color,color&0x00FFFFFF},null,Shader.TileMode.CLAMP));c.drawCircle(cx,cy,r,p);p.setShader(null);}
    }
}
