package com.quintic.ble.ota;

import com.quintic.ble.ota.R;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity{
	private Button mBtnStart;
	private TextView mTextVersion;
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	super.onCreate(savedInstanceState);
    	setContentView(R.layout.activity_main);
    	mTextVersion=(TextView)findViewById(R.id.app_version);    	
    	mTextVersion.setText(getVersion(this));
    	mBtnStart=(Button)findViewById(R.id.buttonStart);

    	Display display = getWindowManager().getDefaultDisplay();
    	DisplayMetrics displayMetrics = new DisplayMetrics();
    	display.getMetrics(displayMetrics);
    	float screenWidth = displayMetrics.widthPixels;
    	float screenHeight = displayMetrics.heightPixels;
    	mBtnStart.setWidth((int)(screenWidth*0.35));
    	mBtnStart.setHeight((int)(screenHeight*0.10));
    	
    	mBtnStart.setOnClickListener(mloadFirmwareListener);
    	//to create firmware path
    	this.getExternalFilesDir("").getAbsolutePath();
    }
    public static String getVersion(Context context)  
    {  
        try {  
            PackageInfo pi=context.getPackageManager().getPackageInfo(context.getPackageName(), 0);  
            return pi.versionName;  
        } catch (NameNotFoundException e) {  
            // TODO Auto-generated catch block  
            e.printStackTrace();  
            return "Unknown version";  
        }  
    }      
    
    Button.OnClickListener mloadFirmwareListener=new Button.OnClickListener(){
	    public void onClick(View v)
	    {
	        final Intent intent = new Intent(MainActivity.this, DeviceScanActivity.class);
	        startActivity(intent);
	    }
    };    
}
