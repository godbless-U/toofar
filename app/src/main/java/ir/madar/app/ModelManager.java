package ir.madar.app;

import android.app.Activity;
import org.vosk.Model;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Downloads only the public Vosk acoustic model. Transcriptions never leave the phone. */
public final class ModelManager {
    public static final String MODEL_NAME="vosk-model-small-fa-0.42";
    public static final String SOURCE="https://alphacephei.com/vosk/models/"+MODEL_NAME+".zip";
    public interface Callback { void status(String status);void ready(Model model);void error(String error); }
    private final Activity activity;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private volatile Model model;
    private volatile boolean busy;
    public ModelManager(Activity activity){this.activity=activity;}
    public boolean installed(){File dir=location();return new File(dir,"am").isDirectory()&&new File(dir,"conf").isDirectory();}
    private File location(){return new File(activity.getFilesDir(),"models/"+MODEL_NAME);}
    public boolean busy(){return busy;}
    private void status(Callback cb,String s){activity.runOnUiThread(()->cb.status(s));}
    public void prepare(boolean mayDownload,Callback cb){
        if(model!=null){cb.ready(model);return;}
        if(busy){cb.status("مدل گفتار در حال آماده‌سازی است...");return;}
        if(!mayDownload&&!installed()){cb.error("ابتدا مدل آفلاین فارسی را دانلود کنید.");return;}
        busy=true;
        executor.execute(()->{
            try{
                if(!installed()){status(cb,"در حال دریافت مدل فارسی؛ حدود ۵۳ مگابایت...");download(cb);}
                status(cb,"در حال بارگذاری موتور تشخیص صدا...");
                Model loaded=new Model(location().getAbsolutePath());model=loaded;
                activity.runOnUiThread(()->cb.ready(loaded));
            }catch(Exception e){activity.runOnUiThread(()->cb.error("مشکل در آماده‌سازی گفتار: "+e.getMessage()));}
            finally{busy=false;}
        });
    }
    private void download(Callback cb)throws Exception{
        File parent=location().getParentFile();if(parent==null||(!parent.exists()&&!parent.mkdirs()))throw new java.io.IOException("Cannot create model directory");
        File archive=new File(parent,"voice-download.part");File staging=new File(parent,"unpacking");
        remove(staging);if(!staging.mkdirs())throw new java.io.IOException("Cannot create extraction folder");
        try{
            HttpURLConnection c=(HttpURLConnection)new URL(SOURCE).openConnection();
            c.setConnectTimeout(25000);c.setReadTimeout(60000);c.setInstanceFollowRedirects(true);c.setRequestProperty("User-Agent","MADAR-Android/1.0");
            try{
                if(c.getResponseCode()!=200)throw new java.io.IOException("Download HTTP "+c.getResponseCode());
                int size=c.getContentLength();long bytes=0,previous=0;
                try(InputStream stream=c.getInputStream();FileOutputStream out=new FileOutputStream(archive)){
                    byte[] buffer=new byte[32768];int n;
                    while((n=stream.read(buffer))!=-1){bytes+=n;if(bytes>200_000_000L)throw new java.io.IOException("Archive unexpectedly large");out.write(buffer,0,n);
                        if(bytes-previous>2_000_000L){previous=bytes;int pct=size>0?(int)(100*bytes/size):0;status(cb,size>0?"دانلود مدل: "+pct+"٪":"دریافت مدل: "+bytes/1_000_000L+" مگابایت");}
                    }
                }
            }finally{c.disconnect();}
            status(cb,"در حال نصب مدل روی گوشی...");
            long total=0;int entries=0;String base=staging.getCanonicalPath()+File.separator;
            try(ZipInputStream zin=new ZipInputStream(new FileInputStream(archive))){ZipEntry entry;
                while((entry=zin.getNextEntry())!=null){
                    if(++entries>20000)throw new java.io.IOException("Too many archive files");
                    String name=entry.getName();
                    if(!name.startsWith(MODEL_NAME+"/"))throw new java.io.IOException("Invalid archive structure");
                    String path=name.substring(MODEL_NAME.length()+1);
                    if(path.isEmpty()){zin.closeEntry();continue;}
                    File target=new File(staging,path);String canonical=target.getCanonicalPath();
                    if(!canonical.startsWith(base))throw new java.io.IOException("Unsafe archive entry");
                    if(entry.isDirectory()){if(!target.isDirectory()&&!target.mkdirs())throw new java.io.IOException("Cannot create model folder");}
                    else {
                        File p=target.getParentFile();if(p!=null&&!p.exists()&&!p.mkdirs())throw new java.io.IOException("Cannot create model subfolder");
                        try(FileOutputStream out=new FileOutputStream(target)){byte[] buf=new byte[32768];int n;while((n=zin.read(buf))!=-1){total+=n;if(total>800_000_000L)throw new java.io.IOException("Model expanded size too large");out.write(buf,0,n);}}
                    }
                    zin.closeEntry();
                }
            }
            if(!new File(staging,"am").isDirectory()||!new File(staging,"conf").isDirectory())throw new java.io.IOException("Model files not found");
            remove(location());if(!staging.renameTo(location()))throw new java.io.IOException("Could not finalize model installation");
        }finally{if(archive.exists())archive.delete();remove(staging);}
    }
    private static void remove(File file){if(!file.exists())return;if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File f:children)remove(f);}file.delete();}
    public void shutdown(){executor.shutdown();}
}
