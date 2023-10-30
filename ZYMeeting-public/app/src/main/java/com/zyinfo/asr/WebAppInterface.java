package com.zyinfo.asr;


import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;

public class WebAppInterface {
    Context mContext;
    public RecordService mService = null;

    /** Instantiate the interface and set the context */
    WebAppInterface(Context c) {
        mContext = c;
    }

    /** Show a toast from the web page */
    @JavascriptInterface
    public void alert(String toast) {
        Toast.makeText(mContext, toast, Toast.LENGTH_LONG).show();
    }

    @JavascriptInterface
    public String getRecognizedText(){
        if(mService!=null){
            return mService.reognized_text;
        }
        return "";
    }

    @JavascriptInterface
    public void setRecognizedText(String reognized_text1){
        if(mService!=null){
            mService.reognized_text = reognized_text1;
        }
    }
    @JavascriptInterface
    public void  store_info(String name,String value) {
        MainActivity.store_info(name,value);
    }
    @JavascriptInterface
    public String  load_info(String name ) {
        return MainActivity.load_info(name);
    }
    @JavascriptInterface
    public void clear_recognize() {
        if(mService!=null){
            mService.reognized_text = "";
        }
    }

    @JavascriptInterface
    public void new_speaker() {
        @SuppressLint("SimpleDateFormat") SimpleDateFormat sdf = new SimpleDateFormat("[HH:mm:ss]");
        String formattedTime = sdf.format(new Date());
        if(mService!=null){
            mService.reognized_text += "\n\n";
        }
    }

    @JavascriptInterface
    public void openLink(String url) {
        MainActivity.reload_web(url);
        
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        mContext.startActivity(i);
    }
}
