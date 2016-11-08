/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.quintic.ble.ota;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import com.quintic.ble.ota.R;
import com.quintic.libota.otaManager;
import com.quintic.libota.BluetoothLeInterface;
import com.quintic.libota.bleGlobalVariables;
import com.quintic.libota.bleGlobalVariables.otaResult;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity {
    private final static String TAG = DeviceControlActivity.class.getSimpleName();
    private String mDefaultFirmwarePath=null;
    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    public static final String SELETED_FILE_NAME = "SELETED_FILE_NAME";
    public static final int  UPDATE_DATA= 1;
    public static final int  ERROR_CODE= 2;
    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;    
    private Button mLoadOTAUpdate;
    private ArrayList<HashMap<String, Object>> mFilelist=null;    
    private boolean mConnected = false;
    private static ProgressDialog progressDialog;
    private otaManager updateManager=new otaManager();
    private boolean mStopUpdate=false;
    private static BluetoothGatt mBluetoothGatt;
    private static BluetoothManager mBluetoothManager;
    private static BluetoothAdapter mBluetoothAdapter;
    
    /**
     * Initializes a reference to the local Bluetooth adapter.
     *
     * @return Return true if the initialization is successful.
     */
    private boolean initialize() {
        // For API level 18 and above, get a reference to BluetoothAdapter through
        // BluetoothManager.
        if (mBluetoothManager == null) {
            mBluetoothManager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
            if (mBluetoothManager == null) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }
        
        return true;
    }    
    public boolean connect(final String address) {
        if (mBluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
            return false;
        }
        /*workaround for BLE reconnect,20140731.
        // Previously connected device.  Try to reconnect.
        if (mDeviceAddress != null && address.equals(mDeviceAddress)
                && mBluetoothGatt != null) {
            Log.d(TAG, "Trying to use an existing mBluetoothGatt for connection.");
            if (mBluetoothGatt.connect()) {
        	mDeviceAddress=address;
                return true;
            } else {
                return false;
            }
        }        
         */
        final BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        if (device == null) {
            Log.w(TAG, "Device not found.  Unable to connect.");
            return false;
        }      
        mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        
        Log.d(TAG, "Trying to create a new connection.");
        return true;
    }
    public void disconnect() {
        if (mBluetoothAdapter == null || mBluetoothGatt == null) {
            Log.w(TAG, "BluetoothAdapter not initialized");
            return;
        }
        mBluetoothGatt.disconnect();        
    }
    public void close() {
        if (mBluetoothGatt == null) {
            return;
        }
        mBluetoothGatt.close();
        mBluetoothGatt = null;
    }
    
    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
        	mConnected = true;   
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
                // Attempts to discover services after successful connection.
                Log.i(TAG, "Connected to GATT server and attempting to start service discovery:" +
                        mBluetoothGatt.discoverServices());
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
        	mConnected = false; 
                updateManager.otaStop();
                mStopUpdate=true;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();        	
        	Log.d(TAG,"disconnected callback");
        	/*workaround for BLE reconnect,20140731.*/
        	close();
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
        	displayOtaServiceInfo();  
            } else {
                Log.w(TAG, "onServicesDiscovered received: " + status);
            }
        }
          
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {      
        	if(status==BluetoothGatt.GATT_SUCCESS)
        		updateManager.notifyWriteDataCompleted();
        	else{            		            		
        		String errCode="Gatt write fail,errCode:"+String.valueOf(status);
        		SendUpdateMsg(ERROR_CODE,"ERROR_CODE",errCode);
        		mStopUpdate=true;
        	}            
            
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
        	byte[] notifyData=characteristic.getValue();            	
        	updateManager.otaGetResult(notifyData);
        }
    };
    
    private void clearUI() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            	mDataField.setText(R.string.ota_not_supported);
            }
        });        
    }
	private static String otaError2String(otaResult ret)
	{
		switch(ret)
		{
			case OTA_RESULT_SUCCESS:
				return "SUCCESS";	    
			case OTA_RESULT_PKT_CHECKSUM_ERROR:
				return "Transmission is failed,firmware checksum error";
			case OTA_RESULT_PKT_LEN_ERROR:
				return "Transmission is failed,packet length error";	    
			case OTA_RESULT_DEVICE_NOT_SUPPORT_OTA:
				return "The OTA function is disabled by the server";	    
			case OTA_RESULT_FW_SIZE_ERROR:
				return "Transmission is failed,firmware file size error";
			case OTA_RESULT_FW_VERIFY_ERROR: 	
				return "Transmission is failed,verify failed";				
			case OTA_RESULT_OPEN_FIRMWAREFILE_ERROR:
				return "Open firmware file failed";
			case OTA_RESULT_META_RESPONSE_TIMEOUT:
				return "Wait meta packet response timeout";
			case OTA_RESULT_DATA_RESPONSE_TIMEOUT:
				return "Wait data packet response timeout";
			case OTA_RESULT_SEND_META_ERROR:
				return "Send meta data error";
			case OTA_RESULT_RECEIVED_INVALID_PACKET:
			    	return "Transmission is failed,received invalid packet";
			case OTA_RESULT_INVALID_ARGUMENT:     	
			default:
				return "Unknown error";
		}
	}    
    private void displayOtaServiceInfo()
    {
    	final boolean ret=isOtaServiceSupported();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
            	if(ret)
            		mDataField.setText(R.string.ota_support);
            	else
            		mDataField.setText(R.string.ota_not_supported);
            }
        });      	
    }
    private boolean isOtaServiceSupported()
    {
    	if(mBluetoothGatt.getService(bleGlobalVariables.UUID_QUINTIC_OTA_SERVICE)!=null)
    		return true;
    	return false;
    }
    private static String generateDisplayMsg(String title,int elapsedTime,int byteRate)
    {
    	return new String(title+"\n"+elapsedTime+" s"+"\n"+byteRate+" Bps");
    }
    private void startOtaUpdate(String filename)
    {
        updateInstance ins=new updateInstance();
        ins.bleInterfaceInit(mBluetoothGatt);
	if(updateManager.otaStart(filename,ins)==otaResult.OTA_RESULT_SUCCESS)
	{       			
		updateProgress("OTA Update",generateDisplayMsg("Updating...",0,0));		
	}else
	{
		Log.e(TAG,"onListItemClick:Faild to otaStart");
	}							
    }
	private int getFirmwareFileList(ArrayList<HashMap<String, Object>> list, String Extension) 
	{
		String Path=mDefaultFirmwarePath;
		File current=new File(Path);
		if(!current.exists())
		{		
			Log.e(TAG, Path+":No such file or directory");
			return -1;
		}
		if(!current.canRead())
		{
			Log.e(TAG, ":No permission to open "+Path);
			return -2;				
		}
	    File[] files =current.listFiles();
	    Log.i(TAG, "List files under "+Path+":");
	    for (File f:files)
	    {
	        if (f.isFile())
	        {
	    		if(!current.canRead())
	    		{
	    			Log.w(TAG, ":No permission to read file "+Path+",skipped!");
	    			continue;				
	    		}
	            if (f.getPath().substring(f.getPath().length() - Extension.length()).equals(Extension)) 
	            {
	            	Log.i(TAG,"add file: "+ f.getName()+" size: "+f.length());
	            	HashMap<String, Object> item = new HashMap<String, Object>();
	            	item.put("filename",f.getName());
	            	item.put("filesize",String.valueOf(f.length())+" bytes");
	            	list.add(item);
	            }
	        }
	    }
	    if(list.isEmpty())
	    {
		Toast.makeText(getApplicationContext(), Path+" is empty", Toast.LENGTH_LONG).show();
	    	return -3;
	    }
	    return 0;
	}		

	

    private class FileArrayAdapter extends BaseAdapter {       
	private LayoutInflater mInflater;        
        public FileArrayAdapter(Context context) {
            this.mInflater = LayoutInflater.from(context);
        }
        public int getCount() {
    		return mFilelist.size();
        }
        @Override
        public boolean areAllItemsEnabled() {
    		return false;
        }
        public Object getItem(int position) {
    		return null;
        }
        public long getItemId(int position) {
    		return position;
        }
    
        @Override
        public View getView(int position, View convertView, ViewGroup parent) { 
            	ViewHolder holder;
        	if (convertView == null) {
        	    convertView = mInflater.inflate(R.layout.file_list, null);
        	    holder=new ViewHolder(); 
        	    holder.FileName =(TextView) convertView.findViewById(R.id.ItemFileName);
        	    holder.FileSize = (TextView) convertView.findViewById(R.id.ItemFileSize);
        	    //holder.FileName.setEllipsize(android.text.TextUtils.TruncateAt.END);
        	    //holder.FileName.setMarqueeRepeatLimit(1);
        	    convertView.setTag(holder); 
        	}else
        	{
        	    holder = (ViewHolder)convertView.getTag();
        	}
        	HashMap<String, Object>item=mFilelist.get(position);
		
		String fileName=(String)item.get("filename");		//get file path
		String fileSize=(String)item.get("filesize");		//get file size
		
        	holder.FileName.setText(fileName);
        	holder.FileSize.setText(fileSize);
    	
    	return convertView;
        }
        public final class ViewHolder{
            public TextView FileName;
            public TextView FileSize;
        }
    }  	
    private void listBinFiles()
    {
        mFilelist=new ArrayList<HashMap<String, Object>>();
        getFirmwareFileList(mFilelist,"bin");
        ListView lv = (ListView)findViewById(R.id.filelist);
        
        FileArrayAdapter FileAdapter=new FileArrayAdapter(this);
        lv.setAdapter(FileAdapter);  
	lv.setOnItemClickListener(new OnItemClickListener(){  			  			     
		@Override  
		public void onItemClick(AdapterView<?> parent, View view,int position, long id) 
		{  
			HashMap<String, Object>item=mFilelist.get(position);
				
			String filename=(String)item.get("filename");		//get file path
			String filePath=mDefaultFirmwarePath+"/"+filename;
			startOtaUpdate(filePath);			
		} 
	});  		
    }
    Button.OnClickListener mloadFirmwareListener=new Button.OnClickListener(){
	    public void onClick(View v)
	    {
    		if(!mConnected)
    		{
    			Toast.makeText(getApplicationContext(), "Connect bluetooth fisrt", Toast.LENGTH_SHORT).show();
    			return ;
    		}
    		if(!isOtaServiceSupported())
    		{
    			Toast.makeText(getApplicationContext(), "The BLE server doesn't support Qunitic service", Toast.LENGTH_SHORT).show();
    			return ;
    		}    		    		  
    		listBinFiles();
	    }
    };
    
    public void updateProgress(String title,String message)
    {
    	mStopUpdate=false;
    	
        Thread updateThread=new Thread(update);
        updateThread.start();
    	
        progressDialog = new ProgressDialog(this);
        progressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        progressDialog.setMessage(message);
        progressDialog.setTitle(title);  
/*
        //progressDialog.       
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", 
                new DialogInterface.OnClickListener() { 
                    public void onClick(DialogInterface dialog, 
                            int whichButton) { 
                    	mStopUpdate=true;
                    	mBluetoothLeService.disconnect();
                    } 
                });     
*/        
        //progressDialog.       
        progressDialog.setButton(DialogInterface.BUTTON_POSITIVE, "OK", 
                new DialogInterface.OnClickListener() { 
                    public void onClick(DialogInterface dialog, 
                            int whichButton) { 
                    	mStopUpdate=true;
                    	finish();
                    } 
                });
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setProgress(0);
        progressDialog.setMax(100);        
        
        progressDialog.show();        
    }    
	private static Handler mHandler = new Handler()
	{
	    int percent=0;int byteRate=0;int elapsedTime=0;   
        public void handleMessage(Message msg) {   
        	     	
        	if (!Thread.currentThread().isInterrupted()) {
        		switch(msg.what)
        		{
        			case UPDATE_DATA:
        				int[] data=msg.getData().getIntArray("UPDATE_DATA");
		        		percent=data[0];	
		        		byteRate=data[1];
		        		elapsedTime=data[2];
		        		//Log.d(TAG,"per:"+percent+" bps:"+byteRate+" time:"+elapsedTime);        		
		        		if(percent<progressDialog.getMax())
		        		{	
			            	progressDialog.setProgress(percent);
			            	progressDialog.setMessage(generateDisplayMsg("Updating...",elapsedTime,byteRate));
			            }else
			            {	                
			            	progressDialog.setProgress(percent);
			            	progressDialog.setMessage(generateDisplayMsg("Update Success",elapsedTime,byteRate));	            	
			            }
        				break;
        			case ERROR_CODE:
        				String errStr="Update Fail: "+msg.getData().getString("ERROR_CODE");
        				progressDialog.setProgress(percent);
		            	progressDialog.setMessage(generateDisplayMsg(errStr,elapsedTime,byteRate));        				
        				break;
        		}	                	                	            
            }
        }		
	};
    private void SendUpdateMsg(int type,String key,int[] value)
    {
		Message msg=new Message();
		msg.what=type;
		msg.getData().putIntArray(key, value);
		if(mHandler!=null)
			mHandler.sendMessage(msg);			
    }	
    private void SendUpdateMsg(int type,String key,String str)
    {
		Message msg=new Message();
		msg.what=type;
		msg.getData().putString(key, str);
		if(mHandler!=null)
			mHandler.sendMessage(msg);			
    }	
    Runnable update=new Runnable()
	{
		public void run()
		{			    		    	
	    	int[] extra=new int[8];
	    	while(!mStopUpdate)
	    	{
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	        	if(!Thread.currentThread().isInterrupted()) 
	        	{	
	        		otaResult ret=updateManager.otaGetProcess(extra);
	        		if(ret==otaResult.OTA_RESULT_SUCCESS)
	        			SendUpdateMsg(UPDATE_DATA,"UPDATE_DATA",extra);
	        		else
	        		{
	        			updateManager.otaStop();
	        			mStopUpdate=true;	
	        			SendUpdateMsg(ERROR_CODE,"ERROR_CODE",otaError2String(ret));
	        		}
		        }
        	}
	    }
	};
    
    private class updateInstance extends BluetoothLeInterface
    {
    	@Override
    	public boolean bleInterfaceInit(BluetoothGatt bluetoothGatt)
    	{
    	    return super.bleInterfaceInit(bluetoothGatt); 
    	}
    }	
	
	
	protected void onListItemClick(ListView l, View v, int position, long id) {
        final Intent intent = new Intent(this, DeviceScanActivity.class);
        startActivity(intent);
  	}	    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
        mDefaultFirmwarePath=this.getExternalFilesDir("").getAbsolutePath();
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_name)).setText(mDeviceName); 
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);                
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.ota_not_supported);
        mLoadOTAUpdate=(Button)findViewById(R.id.load_otaupdate_btn);
        mLoadOTAUpdate.setOnClickListener(mloadFirmwareListener);
        
        getActionBar().setTitle(R.string.title_devices);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        if (!initialize()) {
            Log.e(TAG, "Unable to initialize Bluetooth");
            finish();
        }		
    }

    @Override
    protected void onResume() {
        super.onResume();        
        if(mConnected == false)
        {
            updateConnectionState(R.string.disconnected);
            invalidateOptionsMenu();
    	    connect(mDeviceAddress);
        }                    
    }

    @Override
    protected void onPause() {
        super.onPause();          
    }

    @Override
    protected void onDestroy() {;
	close();
        super.onDestroy();  
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:            	
                connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }
}
