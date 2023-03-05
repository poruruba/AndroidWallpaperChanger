package jp.or.myhome.sample.wallpaper_test;

import androidx.appcompat.app.AppCompatActivity;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import androidx.work.PeriodicWorkRequest;
import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowMetrics;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import com.google.common.util.concurrent.ListenableFuture;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "LogTag";
    WorkManager manager;
    static int Sw;
    static int Sh;
    static String media_url = "https://【立ち上げたNode.jsサーバのホスト名】/wallpaper-get";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = WorkManager.getInstance(this);

        WindowMetrics windowMetrics = this.getWindowManager().getCurrentWindowMetrics();
        Sw = windowMetrics.getBounds().width();
        Sh = windowMetrics.getBounds().height();
        Log.d( TAG, "ScreenWidth=" + Sw + " ScreenHeight=" + Sh);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_item,
                new String[]{ "一日ごと", "一時間ごと", "15分ごと" }
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner spin;
        spin = (Spinner)findViewById(R.id.spin_interval);
        spin.setAdapter(adapter);

        Button btn;
        btn = (Button)findViewById(R.id.btn_update_wallpaper);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_stop_wallpaper);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_update_status);
        btn.setOnClickListener(this);

        updateStatus();
    }

    private void updateStatus(){
        TextView text;
        text = (TextView)findViewById(R.id.txt_status);
        try {
            ListenableFuture<List<WorkInfo>> listenable = manager.getWorkInfosForUniqueWork(WallpaperChangeWorker.WORKER_TAG);
            List<WorkInfo> list = listenable.get();
            int num = list.size();
            if( num == 0 ){
                text.setText("Not Running");
            }else
            if(num > 1 ) {
                throw new Exception("error list.size() != 1");
            }else{
                text.setText(list.get(0).getState().toString());
            }
        }catch(Exception ex){
            Toast.makeText(this, ex.getMessage(), Toast.LENGTH_LONG).show();
            text.setText("Error");
        }
    }

    private static void updateWallpaper(Context context, String url_str) throws Exception{
        WallpaperManager wpm = WallpaperManager.getInstance(context);
        int Dw = wpm.getDesiredMinimumWidth();
        int Dh = wpm.getDesiredMinimumHeight();
        Log.d( TAG, "desiredMinimumWidth=" + Dw + " desiredMinimumHeight=" + Dh);

        URL url = new URL(url_str);
        HttpURLConnection con = (HttpURLConnection)url.openConnection();
        InputStream input = con.getInputStream();

        Bitmap image = BitmapFactory.decodeStream(input);
        int Iw = image.getWidth();
        int Ih = image.getHeight();
        Log.d( TAG, "imageWidth=" + Iw + " imageHeight=" + Ih);

        int Ch = (int)((float)Iw / (float)Dw * Dh);
        if( Ih >= Ch ){
            wpm.setBitmap(image, null, true, WallpaperManager.FLAG_SYSTEM);
        }else{
            int Cw = (int)((float)Dw / (float)Dh * Ih);
            int startX = (Iw - Cw) / 2;
            Bitmap bmp = Bitmap.createBitmap(Cw, Ih, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bmp);
            Rect rectSrc = new Rect(startX, 0, startX + Cw, Ih);
            Rect rectDest = new Rect(0, 0, Cw, Ih);
            canvas.drawBitmap(image, rectSrc, rectDest, null);
            wpm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM);
        }

        Log.d(TAG, "Wallpaper Updated");
    }

    @Override
    public void onClick(View view) {
        switch(view.getId()){
            case R.id.btn_update_wallpaper:{
                Spinner spin;
                spin = (Spinner) findViewById(R.id.spin_interval);
                int interval = spin.getSelectedItemPosition();

                PeriodicWorkRequest request;
                switch(interval){
                    case 1: {
                        // 一時間ごと
                        request = new PeriodicWorkRequest.Builder( WallpaperChangeWorker.class,1, TimeUnit.HOURS)
                                .addTag(WallpaperChangeWorker.WORKER_TAG)
                                .build();
                        break;
                    }
                    case 2: {
                        // 15分ごと
                        request = new PeriodicWorkRequest.Builder( WallpaperChangeWorker.class, 15, TimeUnit.MINUTES)
                            .addTag(WallpaperChangeWorker.WORKER_TAG)
                            .build();
                        break;
                    }
                    default: {
                        // 一日ごと
                        request = new PeriodicWorkRequest.Builder( WallpaperChangeWorker.class,1, TimeUnit.DAYS)
                                .addTag(WallpaperChangeWorker.WORKER_TAG)
                                .build();
                        break;
                    }
                }

                manager.enqueueUniquePeriodicWork(WallpaperChangeWorker.WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, request);

                Toast.makeText(this, "壁紙の更新を要求しました。", Toast.LENGTH_LONG).show();
                updateStatus();
                break;
            }
            case R.id.btn_stop_wallpaper:{
                manager.cancelUniqueWork(WallpaperChangeWorker.WORKER_TAG);
                updateStatus();
                break;
            }
            case R.id.btn_update_status:{
                updateStatus();
                break;
            }
        }
    }

    public static class WallpaperChangeWorker extends Worker {
        Context context;
        final public static String WORKER_TAG = "WallpaperChangeWorkerTAG";

        public WallpaperChangeWorker(Context context, WorkerParameters workerParams) {
            super(context, workerParams);

            this.context = context;
        }

        public Result doWork() {
            try{
                updateWallpaper(context, media_url);
            }catch(Exception ex){
                Log.d(TAG, ex.getMessage());
                return Result.retry();
            }

            return Result.success();
        }
    }
}