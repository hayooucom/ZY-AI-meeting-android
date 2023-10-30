package com.zyinfo.asr;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ForegroundServiceStartNotAllowedException;
import static androidx.core.app.NotificationCompat.PRIORITY_MIN;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.util.Log;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

public class RecordService extends Service {
    private AudioRecord audio_rec = null;
    private AudioRecord app_audio = null;
    private AudioRecord pure_mic_audio = null;

    public Intent  projection_data = null;
    private static final String TAG = "zyasr";
    private boolean useGPU = false;
    private ZYinfoASR model;
    private int sample_rate = 16000;
    private int idx = 0;
    public String reognized_text = "";
    public volatile boolean is_recording = false;
    private int is_init_model = 0;
    public static String userid = "";

    private static final int MAX_QUEUE_SIZE = 2500;
    private MainActivity mainActivity;
    private Context context=null;
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "zyasr_notice";

    private boolean has_reg_bt_reciver = false;
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        Intent notificationIntent = new Intent(this, MainActivity.class);
//        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_MUTABLE);
//
//        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
//                .setContentTitle("展映语音转录")
//                .setContentText("展映语音转录服务正在运行中")
//                .setSmallIcon(R.drawable.ic_launcher)
//                .setTicker("")
//                .setContentIntent(pendingIntent)
//                .build();
//
//        startForeground(NOTIFICATION_ID, notification);

        mstartForeground();

        //configRecord();

        return START_STICKY;
    }

    private void mstartForeground() {

        // Before starting the service as foreground check that the app has the
        // appropriate runtime permissions. In this case, verify that the user
        // has granted the CAMERA permission.
        int micp = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (micp == PackageManager.PERMISSION_DENIED) {
            // Without camera permissions the service cannot run in the
            // foreground. Consider informing user or updating your app UI if
            // visible.
            stopSelf();
            return;
        }
        String channelId = "";
        // 8.0 以上需要特殊处理
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            channelId = createNotificationChannel(CHANNEL_ID, "ForegroundService");
        } else {
            channelId = "";
        }
//        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId);
//        Notification notification = builder.setOngoing(true)
//                .setSmallIcon(R.mipmap.ic_launcher)
//                .setPriority(PRIORITY_MIN)
//                .setCategory(Notification.CATEGORY_SERVICE)
//                .build();
//        startForeground(1, notification);

        try {
            Notification notification = new NotificationCompat.Builder(this, channelId).setOngoing(true)
                                .setSmallIcon(R.mipmap.ic_launcher)
                                .setPriority(PRIORITY_MIN)
                                .setCategory(Notification.CATEGORY_SERVICE)
                                .build();
            int type = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE |ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                if(mainActivity!=null){
                    if(mainActivity.is_init_app_audio){
                        type  |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION;
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        /* id = */ 100, // Cannot be 0
                        /* notification = */ notification,
                        /* foregroundServiceType = */ type
                );
            }else{
                startForeground(100,notification);
            }
        } catch (Exception e) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    e instanceof ForegroundServiceStartNotAllowedException
            ) {
                //startService();
                // App not in a valid state to start foreground service
                // (e.g started from bg)
            }
            // ...
        }
    }
    @RequiresApi(Build.VERSION_CODES.O)
    private String createNotificationChannel0(String channelId, String channelName){
        NotificationChannel chan = new NotificationChannel(channelId,
                channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        NotificationManager service = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        service.createNotificationChannel(chan);
        return channelId;
    }


    public void configRecord() {
        int numBytes = AudioRecord.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        Log.i(TAG, "每毫秒的采样数量: " + numBytes * 1000.0f / sample_rate);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "no mic permission!" );
            return;
        }

        if (app_audio==null && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q && mainActivity!=null&& context!=null) {
            if(mainActivity.is_init_app_audio && mainActivity.use_system_audio==1) {

                AudioPlaybackCaptureConfiguration config = new AudioPlaybackCaptureConfiguration.Builder(mainActivity.mediaProjection[0])
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

                app_audio = new AudioRecord.Builder()
                        .setAudioPlaybackCaptureConfig(config)
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(sample_rate)
                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                .build())
                        .setBufferSizeInBytes(numBytes * 2)
                        .build();
            }
        }

        if(pure_mic_audio ==null ){
            if(mainActivity!=null) {
                if(mainActivity.identify_speaker==1  ) {
                    pure_mic_audio = new AudioRecord.Builder()
                            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                            .setAudioFormat(new AudioFormat.Builder()
                                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setSampleRate(sample_rate)
                                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                                    .build())
                            .setBufferSizeInBytes(numBytes * 2)
                            .build();
                }
            }
        }

        if (audio_rec == null) {
            if(mainActivity!=null) {
                if (mainActivity.audio_input_type == 1 )
                    audio_rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, numBytes * 2);
                else {
                    audio_rec = new AudioRecord(MediaRecorder.AudioSource.DEFAULT, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, numBytes * 2);
                }
                if (audio_rec.getState() != AudioRecord.STATE_INITIALIZED) {
                    audio_rec = new AudioRecord(MediaRecorder.AudioSource.MIC, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, numBytes * 2);
                    if (audio_rec.getState() != AudioRecord.STATE_INITIALIZED) {
                        audio_rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, numBytes * 2);
                    }
                }
            }
        }

        if (!has_reg_bt_reciver) {
            has_reg_bt_reciver = true;
            IntentFilter filter = new IntentFilter();
            filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            registerReceiver(bluetoothReceiver, filter);
            //unregisterReceiver(bluetoothReceiver);
        }
//
//        int sample_rate = 16000;
//        int numBytes = AudioRecord.getMinBufferSize(sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
//
//        audio_rec = new AudioRecord(MediaRecorder.AudioSource.MIC, sample_rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, numBytes * 2);
//        audio_rec.configRecord();
        // TODO: Start a new thread to read the audio data from the audio_rec
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
    private final IBinder mBinder = new LocalBinder();

    public class LocalBinder extends Binder {
        RecordService getService() {
            return RecordService.this;
        }
    }
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public  String load_info(String name) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context);
        return sharedPreferences.getString(name, "");
    }

    private void initModel() {
        Context context = getApplicationContext();
         model = new ZYinfoASR( getApplication().getAssets(), context );
    }
    public void setContext(Context context1){
        context = context1;
    }
    public void setMainActivity(MainActivity mainActivity1){
        mainActivity = mainActivity1;
    }
    public static void store_info(String name,String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.mcontext);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(name, value);
        editor.apply();
        editor.commit();
    }
    public void startRecording(){
        int micp = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO);
        if (micp == PackageManager.PERMISSION_DENIED) {
            if(mainActivity!=null && context!=null){
                Toast.makeText(context, "无麦克风权限，请重试", Toast.LENGTH_LONG).show();
                mainActivity.request_mic_permissions();
            }
            return;
        }
        model.init_ASR(context);
        configRecord();
        audio_rec.startRecording();
        if(app_audio!=null)
            app_audio.startRecording();
        if(pure_mic_audio!=null)
            pure_mic_audio.startRecording();
        is_recording = true;
        new Thread(() -> {
            model.reset(model.instance_id,true);
            processWave();
        }) .start();

        if(mainActivity!=null) {
            try {
                mainActivity.result_view.post(new Runnable() {
                    @Override
                    public void run() {
                        mainActivity.result_view.scrollTo(0, 0);
                    }
                });
                idx = 0;
                mainActivity.rec_btn.setText(R.string.stop);
            }catch (Exception e){}
        }

        Log.d(TAG, "开始录音");
    }

    public void stopRecording(){
        is_recording = false;
        if(audio_rec!=null){
            audio_rec.stop();
            audio_rec.release();
            audio_rec = null;
        }

        if(app_audio!=null){
            app_audio.stop();
            app_audio.release();
            app_audio = null;
        }
        if(pure_mic_audio!=null){
            pure_mic_audio.stop();
            pure_mic_audio.release();
            pure_mic_audio = null;
        }

        if(mainActivity!=null) {
            try {
                mainActivity.rec_btn.setText(R.string.start);
            }catch (Exception e){}
        }
        Log.i(TAG, "Stopped recording");
    }
    private void click_action() {
        if (!is_recording) {
            startRecording();
        } else {
            stopRecording();
        }
    }


    public void onclick() {
        if (is_init_model == 0) {
            is_init_model = 1;
            if(context!=null)
                Toast.makeText(context, "正在载入中，请稍后", Toast.LENGTH_LONG).show();
            if (mainActivity != null){
                mainActivity.rec_btn.setText("载入中");
            }
            Thread thread = new Thread(() -> {
                initModel();
                is_init_model = 2;
                if (mainActivity != null){
                    mainActivity.runOnUiThread(this::click_action);
                }
            });
            thread.start(); // Start the thread
        }

        if (is_init_model != 2) {
            return;
        }
        click_action();
    }

    private void processWave() {
        Log.d(TAG, "processWave");

        double time_s = 0.1;
        int bufferSize = (int) (time_s * sample_rate);
        short[] buffer = new short[bufferSize];
        short[] buffer_app = new short[bufferSize];
        short[] buffer_pure_mic = new short[bufferSize];
        new Thread(() -> {
            AssetManager asm =  context.getAssets();
            long blank_start_time = 0;
            float avg_v_min = 10;
            int frame_counter = 0;
            int  judge_time = 500;
            int comma_counter = 0;
            int speaker_id = 0;
            int last_speaker_id = 0;
            float sum_speaker0=0;
            float sum_speaker1=0;
            ArrayList<Float> avg_v_list= new ArrayList<Float>();
            ArrayList<String> recognized_text_comma = new ArrayList<String>();
            while (is_recording) {
                int read = audio_rec.read(buffer, 0, buffer.length);
                float sum = 0;
                if(app_audio!=null && read>0){
                    int read2 = app_audio.read(buffer_app, 0, buffer_app.length);
                    if(read2>0){
                        for(int i=0;i<read2 && i<read;i++){
                            int merge =  (int)(buffer[i]) + (int)(buffer_app[i]) ;
                            if(merge >32766)
                                merge = 32766;
                            else if(merge <-32766)
                                merge = -32766;
                            buffer[i] = (short) merge;
                            sum += Math.abs(merge);
                        }
                    }
                }
                int current_speaker_id = 0;
                float avg_v3 =0;
                float sum3 =0;
                if(pure_mic_audio!=null && mainActivity!=null){
                    if( mainActivity.identify_speaker == 1) {
                        int read3 = pure_mic_audio.read(buffer_pure_mic, 0, buffer_app.length);
                        if (read3 > 0) {

                            for (int i = 0; i < bufferSize && i < read; i++) {
                                sum3 += Math.abs(buffer[i]);
                            }
                            avg_v3 = sum3 / read3;
                            if (avg_v3 > avg_v_min * 2) {
                                current_speaker_id = 1;
                                speaker_id = 1;
                            }
                        }
                    }
                }

                if (read>0) {
                    try {
                        model.inputData(model.instance_id, buffer, asm, model.account, model.applicationId);
                        if (sum < 1) {
                            for (int i = 0; i < bufferSize && i < read; i++) {
                                sum += Math.abs(buffer[i]);
                            }
                        }
                        float avg_v = (float) sum / bufferSize;
                        if (mainActivity.audio_input_type == 0 && mainActivity.identify_speaker == 1) {
                            if (sum / read > avg_v_min * 2 && current_speaker_id == 0) {
                                speaker_id = 0;
                            }
                        }

                        while (model.Ready(model.instance_id)) {
                            model.decode(model.instance_id);
                        }
                        boolean is_sentence_end = model.isSentenceEnd(model.instance_id);
                        String text = model.getRecognizeText(model.instance_id);
                        text = text.replace("<unk>", "");
                        if (frame_counter > 5) {
                            avg_v_list.add(avg_v);
                        }
                        frame_counter++;
                        if (frame_counter > 17 && frame_counter % 200 == 18) {
                            avg_v_min = Collections.min(avg_v_list);
                            avg_v_list.clear();
                            if (avg_v_min < 10) {
                                avg_v_min = 10;
                            }
                            if (avg_v_min > 400) {
                                avg_v_min = 400;
                            }
                        }
                        if (mainActivity.use_system_audio == 1) {
                            if (avg_v3 > avg_v_min * 2) {
                                sum_speaker1 += 1;
                            } else if (avg_v > avg_v_min * 2) {
                                sum_speaker0 += 1;
                            }
                        }else{
                            sum_speaker1 += Math.log10(avg_v3);
                            sum_speaker0 += Math.log10(avg_v)  - Math.log10(avg_v3);
                        }
                        //Log.i("zyasr-mic", "average mic:" + avg_v+" pure mic:" + avg_v3 +" avg_v_min:"+avg_v_min);

                        if(frame_counter %75 ==74){
                            if(comma_counter ==0 ){
                                judge_time -= 100;
                                if (judge_time<100){
                                    judge_time = 100;
                                }
                            }else{
                                judge_time +=100;
                                if (judge_time<500){
                                    judge_time = 500;
                                }
                            }
                            comma_counter = 0;
                        }

                        //80 ~1300
                        if (avg_v < avg_v_min * 2) {
                            if (blank_start_time == 0) {
                                blank_start_time = new Date().getTime();
                            } else if (new Date().getTime() - blank_start_time > judge_time && text.trim().length() > 1) {
                                if(mainActivity.identify_speaker == 1) {
                                    float db_avg = sum_speaker1*1000/(new Date().getTime() - blank_start_time);
                                    //new break
                                    if (mainActivity.use_system_audio == 1) {
                                        if (sum_speaker0 > sum_speaker1) speaker_id = 0;
                                        else speaker_id = 1;
                                    } else {
                                        if (db_avg <150)speaker_id = 1;
                                        else speaker_id = 0;
                                    }

//                                    Log.i("zyasr-mic", "text:" + text + " s0:" + sum_speaker0 +
//                                            ",s1:" + sum_speaker1 +" db_avg:"+db_avg+ ",avg_v:" + avg_v + " pure mic:" + avg_v3 + " avg_v_min:" + avg_v_min);
                                }

                                if (recognized_text_comma.size() > 0) {
                                    String last_with_comma = recognized_text_comma.get(recognized_text_comma.size() - 1);
                                    //last_with_comma = last_with_comma.replace("，","");
                                    if (!text.equals(last_with_comma)) {
                                        recognized_text_comma.add(text);
                                        blank_start_time = 0;
                                    }
                                } else {
                                    recognized_text_comma.add(text);
                                    blank_start_time = 0;
                                }


                                sum_speaker0 = 0;
                                sum_speaker1 = 0;
                            }
                        } else {
                            blank_start_time = 0;
                        }
                        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");
                        String formattedTime = sdf.format(new Date());

                        text= text.toLowerCase(Locale.ENGLISH);
                        //System.out.println(formattedTime);
                        String text1 = text;
                        if (recognized_text_comma.size() > 2) {
                            for (int i = recognized_text_comma.size() - 2; i >= 0; i--) {
                                text1 = text1.replace(recognized_text_comma.get(i), recognized_text_comma.get(i) + "，");
                                comma_counter ++;
                            }
                        }

                        if (mainActivity.identify_speaker == 1 && speaker_id != last_speaker_id){
                            if (is_sentence_end && text.trim().length() > 1) {
                                model.reset(model.instance_id, false);
                                recognized_text_comma.clear();
                                if(speaker_id ==0){
                                    reognized_text += formattedTime + " 其他说话人：" + text1 + "";
                                }else{
                                    reognized_text += formattedTime + " 说话人 1：" + text1 + "";
                                }
                                idx += 1;
                                last_speaker_id = speaker_id;
                            }
                        }else {

                            if (is_sentence_end && text.trim().length() > 1) {
                                model.reset(model.instance_id, false);

                                if (!text.trim().isEmpty()) {

                                    recognized_text_comma.clear();
                                    if(mainActivity.identify_speaker == 0){
                                        reognized_text += formattedTime + " " + text1 + "";
                                    }else {
                                        if (speaker_id == 0) {
                                            reognized_text += formattedTime + " 其他说话人：" + text1 + "";
                                        } else {
                                            reognized_text += formattedTime + " 说话人 1：" + text1 + "";
                                        }
                                    }
                                    idx += 1;
                                }
                            }
                        }

                        text1 = text;
                        if (recognized_text_comma.size() >= 1) {
                            for (int i = recognized_text_comma.size() - 1; i >= 0; i--) {
                                text1 = text1.replace(recognized_text_comma.get(i), recognized_text_comma.get(i) + "，");
                            }
                        }
                        String final_Show_text = text1;
                        if(mainActivity!=null) {
                            mainActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    mainActivity.result_view.setText(final_Show_text);
                                }
                            });
                        }

                    } catch (Exception e) {

                    }
                }
            }
        }).start();

    }

    private String createNotificationChannel(String channelId, String channelName) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    channelName,
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setLightColor(Color.BLUE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            manager.createNotificationChannel(channel);
            return channelId;
        }
        return "";
    }


    private final BroadcastReceiver bluetoothReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();

            if (action.equals(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, BluetoothHeadset.STATE_DISCONNECTED);

                if (state == BluetoothHeadset.STATE_CONNECTED || state == BluetoothHeadset.STATE_DISCONNECTED) {
                    if(is_recording){
                        stopRecording();
                        configRecord();
                        startRecording();
                    }
                }
            }
        }
    };

}

class ZYinfoASR {
    public long instance_id;
    public String account = "zymeeting";
    String applicationId ="";

    public ZYinfoASR(AssetManager mgr, Context context) {

        applicationId =   "com.zyinfo.meeting";
        //采样率固定是 16000 ,第二个参数是 CPU 线程。第四个参数是断句时间。
        float zyAsr[] = {16000,2,0,4,2.0f};
        instance_id = CreateASR(mgr, zyAsr);
        Log.e("zyasr", "initASR " );
        this.init_ASR(context);
        new Thread(() -> {
            //while(true){;
            try{
                runThread(instance_id);
            }catch (Exception e){
                Log.e("zyasr", "runThread error "+e.toString() );
            }
            Log.e(  "zyasr", "initASR finished" );
            //}789
        }).start();

    }

    static {
        System.loadLibrary("zyasr");
    }

    public void init_ASR(Context context) {
        final CountDownLatch latch = new CountDownLatch(1);
        Thread thread = new Thread(() -> {
            try{
                String url = "https://ai.zyinfo.pro:8900/initASR?account=" + account + "&appid=" + applicationId + "&t=" + (new Date().getTime());
                String result = httpGet(url);
                Log.e("zyasr", "initASR " + result);
                if (result == null) {
                    result = "";
                }
                activeAccount(instance_id, context.getAssets(), account, applicationId, result);
            }catch (Exception e){}

            latch.countDown();
        });
        thread.start();
        // 等待线程执行完毕
        try {
            latch.await();
        }catch (Exception e){}

    }

    public String httpGet(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(10000);

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                String result = new BufferedReader(new InputStreamReader(inputStream)).readLine();
                inputStream.close();
                return result;
            } else {
                throw new Exception("HTTP GET request failed with response code " + responseCode);
            }
        } catch (Exception e) {
            Log.e("zyasr","ent error"+e.getMessage());
        }
        return "";
    }

    public native long CreateASR( AssetManager assetManager, float[] data );
    public native boolean activeAccount(long instance_id,AssetManager assetManager,  String account, String appid, String data);

    public native boolean isSentenceEnd(long instance_id);

    public native void reset(long instance_id, boolean recreate);

    public native String getRecognizeText(long instance_id);

    public native String getMatchText(long instance_id);

    public native void inputData(long instance_id, short[] samples ,AssetManager assetManager, String account, String appid);

    public native boolean Ready(long instance_id);

    public native boolean runThread(long instance_id);
    public native void decode(long instance_id);
}