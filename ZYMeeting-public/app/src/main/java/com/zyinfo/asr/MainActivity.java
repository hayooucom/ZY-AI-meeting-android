package com.zyinfo.asr;

import static com.zyinfo.asr.RecordService.userid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.util.Log;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import android.content.Context;
import android.content.res.AssetManager;
import android.widget.Toast;

import com.zyinfo.asr.R;

public class MainActivity extends AppCompatActivity {
    public static Context mcontext = null;
    public static MainActivity mainActivity = null;
    private static final String TAG = "zyasr";

    public Button rec_btn;
    public Button clear_btn;
    public TextView result_view;
    private static final int GET_AUDIO_PERMMISION = 1000;

    private RecordService mService = null;
    private String[] need_permissions = {
            Manifest.permission.RECORD_AUDIO};
    private static WebView myWebView;
    private WebAppInterface mWebAppInterface = null;
    public   MediaProjectionManager mediaProjectionManager =null;
    public final MediaProjection[] mediaProjection = new MediaProjection[1];
    public boolean is_init_app_audio = false;

    public int audio_input_type =0;
    public int identify_speaker =0;
    public int use_system_audio =0 ;

    private  ActivityResultLauncher<Intent> startMediaProjection = null;
    private  String murl= "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mcontext = this.getApplicationContext();
        mainActivity = this;
        setContentView(R.layout.activity_main);

        result_view = findViewById(R.id.show_text);
        result_view.setMovementMethod(new ScrollingMovementMethod());

        rec_btn = findViewById(R.id.rec_btn);
        rec_btn.setOnClickListener(v -> onclick());

        clear_btn = findViewById(R.id.clear_btn);
        clear_btn.setOnClickListener(v -> onclick_clear_btn());

        load_setting();

        startMediaProjection = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                Intent intent1 = result.getData();
                if (result.getResultCode() == Activity.RESULT_OK && intent1 != null) {

                    mediaProjection[0] = mediaProjectionManager
                            .getMediaProjection(result.getResultCode(), intent1);
                    is_init_app_audio = true;
                    if(mService!=null){
                        mService.projection_data= intent1;
                        reset_audio_input();
                    }
                }
            }
        );

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            need_permissions = append_string(need_permissions, Manifest.permission.BLUETOOTH_CONNECT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            need_permissions = append_string(need_permissions, Manifest.permission.POST_NOTIFICATIONS);
        }
        mediaProjectionManager =  (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        ActivityCompat.requestPermissions(this, need_permissions, GET_AUDIO_PERMMISION);

        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
//
//        WebView myWebView = new WebView(mcontext);
//        setContentView(myWebView);
        Button set_btn = findViewById(R.id.set_btn);
        set_btn.setOnClickListener(v -> onclick_set_btn());

        myWebView = (WebView) findViewById(R.id.webview);
        WebSettings webSettings = myWebView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webSettings.setAllowContentAccess(true);
        webSettings.setAllowFileAccess(true);
        webSettings.setAllowFileAccessFromFileURLs(true);
        mWebAppInterface = new WebAppInterface(this);
        myWebView.addJavascriptInterface(mWebAppInterface, "Android");
        myWebView.setWebChromeClient(new WebChromeClient() {
            public boolean onConsoleMessage(ConsoleMessage cm) {
                Log.d("MyApplication", cm.message() + " -- From line "
                        + cm.lineNumber() + " of "
                        + cm.sourceId());
                return true;
            }

            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                view.loadUrl(url);
                return true;
            }
        });
        userid = load_info("userid");
        if (userid.equals("")) {
            userid = "ad-" + (new Date().getTime());
            store_info("userid", userid);
        }
        reload_web(get_ai_meeting_url());
        config_audio();
        start_rec_service();
    }
    private void load_setting(){
        String set_x = load_info("audio_input_type");
        if (set_x.equals("")){
            audio_input_type = 0;
        }else{
            audio_input_type = Integer.parseInt(set_x);
        }
        set_x = load_info("identify_speaker");
        if (set_x.equals("")){
            identify_speaker = 0;
        }else{
            identify_speaker = Integer.parseInt(set_x);
        }
        set_x = load_info("use_system_audio");
        if (set_x.equals("")){
            use_system_audio = 0;
        }else{
            use_system_audio = Integer.parseInt(set_x);
        }

    }
    private String get_ai_meeting_url(){
        return "https://v.zyinfo.pro/ai/meeting/?from=app&d=android&use=app_recognize&v=1&user_id="+userid;
    }
    private String get_ai_assistant(){
        return "https://ai.zyinfo.pro/?from=app&d=android&use=app_recognize&v=1&user_id="+userid;
    }
    private String[] append_string(String[] array,String str){
        // 创建一个新的数组，长度比原数组大1
        String[] newArray = Arrays.copyOf(array, array.length + 1);
        // 在新数组的最后一个位置添加字符串
        newArray[newArray.length - 1] = str;
        return newArray;
        //System.out.println(Arrays.toString(newArray));
    }

    private void reset_audio_input(){
        if(mService!=null){
            mService.setContext(mcontext);
            mService.setMainActivity(mainActivity);
            if(mService.is_recording) {
                mService.stopRecording();
                mService.configRecord();
                mService.startRecording();
            }
        }
    }

    private void config_audio() {
        // 获取AudioManager实例
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

        // 设置音频输入源为默认
        audioManager.setMode(AudioManager.MODE_NORMAL);
        audioManager.setMicrophoneMute(false);
        audioManager.setSpeakerphoneOn(false);

        // 检查是否已连接蓝牙设备
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
            // 获取已连接的蓝牙设备列表
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(mcontext, "无蓝牙连接权限", Toast.LENGTH_LONG).show();
                return;
            }
            Set<BluetoothDevice> devices = bluetoothAdapter.getBondedDevices();
            for (BluetoothDevice device : devices) {
                // 检查设备类型是否为蓝牙耳机
                if (device.getBluetoothClass().getMajorDeviceClass() == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                    // 设置音频输入源为蓝牙耳机
                    audioManager.setBluetoothScoOn(true);
                    audioManager.startBluetoothSco();
                    break;
                }
            }
        }

        // 如果没有连接蓝牙设备，则使用机内麦克风
        if (!audioManager.isBluetoothScoOn()) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setMicrophoneMute(false);
            audioManager.setSpeakerphoneOn(false);
        }

        // 录制系统内部音频
        audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        audioManager.setMicrophoneMute(false);
        audioManager.setSpeakerphoneOn(true);

    }    private void start_rec_service(){
        if(mService == null){
            Intent intent = new Intent(this, RecordService.class);
            //if (bindService(intent, mConnection, Context.BIND_AUTO_CREATE) ) {
            if(isMyServiceRunning(RecordService.class))  { // 服务已经启动
            } else {
                // 服务尚未启动，尝试启动它
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                }else{
                    startService(intent);
                }
            }

            Intent serviceIntent = new Intent(mcontext, RecordService.class);
            bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private  void onclick(){
        boolean result = request_mic_permissions();
        if (!result) {
            Log.e(TAG, "打开麦克风失败");
            return;
        }
        start_rec_service();

        if( mService != null){
            mService.onclick();
        }
    }
    public static void reload_web(String url) {
        myWebView.loadUrl(url);
    }
    private  void bind_service(){

    }
    private Context.BindServiceFlags myBinder;
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            RecordService.LocalBinder binder = (RecordService.LocalBinder) service;
            mService = binder.getService();
            mService.setContext(mcontext);
            mService.setMainActivity(mainActivity);

            mWebAppInterface.mService = mService;
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(mService!=null){
            mService.stopRecording();
            mService.setMainActivity(null);
            mService.setContext(null);
        }
        unbindService(mConnection);
        mService = null;
    }



//    private View createInputView() {
//        View view = getLayoutInflater().inflate(R.layout.input_dialog, null);
//        EditText useridEditText = (EditText) view.findViewById(R.id.userid);
//        return view;
//    }

    private  void onclick_set_btn(){
        final String[] items = { "设置用户名","使用AI会议记录","使用AI语音助手","设置麦克风输入（默认）","设置为麦克风降噪输入","获取系统音频，在线会议识别更准确","开启/关闭说话人检测"};
        if(audio_input_type ==0){
            items[3] += " [当前]";
        }
        if(audio_input_type ==1){
            items[4] += " [当前]";
        }
        if(use_system_audio ==1){
            items[5] += " [已开启]";
        }else{
            items[5] += " [已关闭]";
        }
        if(identify_speaker ==1){
            items[6] += " [已开启]";
        }else{
            items[6] += " [已关闭]";
        }
        androidx.appcompat.app.AlertDialog.Builder listDialog =
                new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this);
        listDialog.setTitle("更多设置");
        listDialog.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // which 下标从0开始
                if(which == 0){
                    showInputDialog();
                }else if(which == 1) {
                    load_setting();
                    reload_web(get_ai_meeting_url());
                }else if(which == 2) {
                    audio_input_type = 1;
                    identify_speaker = 0;
                    reload_web(get_ai_assistant());
                }else if(which == 3){
                    audio_input_type = 0;
                    store_info("audio_input_type","0");
                    reset_audio_input();
                    Toast.makeText(mcontext, "已设置为默认麦克风输入，同时会采集扬声器声音，便于线上会议。已存储设置。", Toast.LENGTH_LONG).show();
                }else if(which == 4){
                    audio_input_type = 1;
                    store_info("audio_input_type","1");
                    reset_audio_input();
                    Toast.makeText(mcontext, "已设置为自动增益、降噪的麦克风输入功能，只适合近距离。不会采集扬声器声音（线上会议需配合开启获取系统音频功能）。已存储设置。", Toast.LENGTH_LONG).show();
                }else if(which == 5){
                    String notice = "关闭";
                    if(use_system_audio ==1){
                        use_system_audio = 0;

                        identify_speaker = 0;
                        store_info("identify_speaker",String.valueOf(identify_speaker));

                    }else{
                        use_system_audio = 1;
                        notice = "开启";
                    }

                    store_info("use_system_audio",String.valueOf(use_system_audio));
                    request_mic_permissions();
                    reset_audio_input();
                    Toast.makeText(mcontext, "已"+notice+" 系统内部音频输入，APP线上会议识别更准确。已存储设置。", Toast.LENGTH_LONG).show();
                }else if(which == 6){
                    String notice = "关闭";
                    if(identify_speaker ==1){
                        identify_speaker = 0;
                    }else{
                        if(use_system_audio == 0){
                            Toast.makeText(mcontext, "开启失败，请使用开启系统音频输入功能，进入线上会议时使用。", Toast.LENGTH_LONG).show();
                            return;
                        }
                        identify_speaker = 1;
                        notice = "开启";
                    }

                    store_info("identify_speaker",String.valueOf(identify_speaker));
                    reset_audio_input();
                    Toast.makeText(mcontext, "已"+notice+"纯麦克风输入，自动增益、降噪。不会采集扬声器声音（可能不支持APP线上会议类型）。已存储设置。", Toast.LENGTH_LONG).show();
                }
            }
        });
        listDialog.show();
    }
    private void showInputDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("请输入用户名");
        builder.setMessage("每月最多可设置3次");
        EditText view = new EditText(mcontext);
        view.setMinimumHeight(40);
        view.setMinimumWidth(200);
        view.setText(userid);
        builder.setView(view.getRootView());
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                //String userid = ((EditText) builder.getView().findViewById(R.id.userid)).getText().toString();
                String set_userid_counter = load_info("set_userid_counter");
                if (set_userid_counter.equals("")){
                    set_userid_counter = "0";
                }
                int cnt = Integer.valueOf(set_userid_counter) +1;
                String last_set_userid_counter = load_info("last_set_userid_counter");
                if(last_set_userid_counter=="")
                    last_set_userid_counter = "0";
                if(new Date().getTime() - Long.parseLong(last_set_userid_counter)> 86400*31 && cnt>3){
                    cnt = 0;
                }
                if (cnt <= 3 && userid!=view.getText().toString()){
                    userid = view.getText().toString();
                    store_info("userid",userid);
                    store_info("set_userid_counter",String.valueOf(cnt));
                    store_info("last_set_userid_counter",String.valueOf(new Date().getTime()));
                }else{
                    Toast.makeText(mcontext, "设置用户次数已用完", Toast.LENGTH_LONG).show();
                }
                reload_web(get_ai_meeting_url());
                // 在这里处理用户名，例如将其存储到 SharedPreferences
                // ...
            }
        });
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    public static   String load_info(String name) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.mcontext);
        return sharedPreferences.getString(name, "");
    }

    public static void store_info(String name,String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.mcontext);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(name, value);
        editor.apply();
        editor.commit();
    }


    private void onclick_clear_btn(){
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("提示");
        builder.setMessage("是否确定清空识别内容？");
        builder.setPositiveButton("确定", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 点击确定按钮后的操作
                if (mService != null){
                    mService.reognized_text = "\n";
                }
                Toast.makeText(mcontext, "语音识别内容已清空", Toast.LENGTH_LONG).show();
            }
        });
        builder.setNegativeButton("取消", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // 点击取消按钮后的操作
            }
        });
        builder.show();

    }
    public boolean request_mic_permissions() {
        if (ActivityCompat.checkSelfPermission( this, Manifest.permission.RECORD_AUDIO ) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, need_permissions, GET_AUDIO_PERMMISION);
            return false;
        }
        if(!is_init_app_audio && use_system_audio ==1){
           startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent());
        }
        return true;
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean permissionToRecordAccepted = false;
        if (requestCode == GET_AUDIO_PERMMISION && grantResults.length>0) {
            permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
        }

        if (!permissionToRecordAccepted) {
            Log.e(TAG, "请授予麦克风权限");
            Toast.makeText(mcontext, "请授予麦克风权限", Toast.LENGTH_LONG).show();
        }else{
            if(mService!=null){
                mService.configRecord();
            }
            Log.i(TAG, "有录音权限");
        }
    }


    @Override
    protected void onResume() {
        super.onResume();


    }

    @Override
    protected void onPause() {
        super.onPause();

        //unregisterReceiver(bluetoothReceiver);
    }
}

