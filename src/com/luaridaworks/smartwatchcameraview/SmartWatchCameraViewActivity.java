package com.luaridaworks.smartwatchcameraview;

import android.app.Activity;
import android.os.Bundle;
import android.view.Window;
import android.view.WindowManager;

public class SmartWatchCameraViewActivity extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);

		//タイトルを非表示
		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(new SmartWatchCameraView(this));
    }
}