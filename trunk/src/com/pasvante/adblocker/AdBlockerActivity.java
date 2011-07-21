package com.pasvante.adblocker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.Toast;

import com.ri.hosts.MergeHosts;

public class AdBlockerActivity extends Activity {
	
	private static final String SCRIPT_TEMPLATE_PART1 = "mount -o remount,rw ";
	private static final String SCRIPT_TEMPLATE_PART2 = " /system\nmv ";
	private static final String SCRIPT_TEMPLATE_PART3 = ".bak\ncp ";
	private static final String SCRIPT_TEMPLATE_PART4 = "\nchmod 644 ";
	private static final String SCRIPT_TEMPLATE_PART5 = "\nmount -o remount,ro ";
	private static final String SCRIPT_TEMPLATE_PART6 = " /system";
	
	private static final String DEFAULT_HOSTS_LOCATION = "/system/etc/hosts";
	private static final String PREF_PATH_COUNT = "PREF_PATH_COUNT";
	private static final String PREF_PREVIOUS_PATH = "PREF_PREVIOUS_PATH";
	
	private static String SystemMountPoint = null;
	private LinearLayout mainLayout = null;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        setContentView(R.layout.main);
        
        Button button = (Button)findViewById(R.id.buttonApply); // this function has to be after the call to setContentView() 
        button.setOnClickListener(mCommandButtonClicked);
        
        button = (Button)findViewById(R.id.buttonAddEditText); // this function has to be after the call to setContentView() 
        button.setOnClickListener(mCommandButtonClicked);

        button = (Button)findViewById(R.id.buttonRemoveEditText); // this function has to be after the call to setContentView() 
        button.setOnClickListener(mCommandButtonClicked);

        mainLayout = (LinearLayout)findViewById(R.id.mainLayout);
        restorePreferences(); // needs mainLayout to be assigned
    }
    
    @Override
	protected void onPause() {
    	storePreferences();
		
		super.onPause();
	}
    
    private void restorePreferences() {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        int count = prefs.getInt(PREF_PATH_COUNT, 1);
        int currentCount = mainLayout.getChildCount();
        
        while (currentCount < count) {
			EditText editNew = new EditText(AdBlockerActivity.this);
			LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			mainLayout.addView(editNew, 1, params);
			currentCount = mainLayout.getChildCount();
        }
        
        for (int i = 0; i < currentCount; i++) {
            String previousPath = prefs.getString(PREF_PREVIOUS_PATH + String.valueOf(i), "");
            EditText editCtrl = (EditText)mainLayout.getChildAt(i);
    		editCtrl.setText(previousPath);
        }
    }
    
    private void storePreferences() {
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		SharedPreferences.Editor spe = prefs.edit();

        int currentCount = mainLayout.getChildCount();
		spe.putInt(PREF_PATH_COUNT, currentCount);

        for (int i = 0; i < currentCount; i++) {
            EditText editCtrl = (EditText)mainLayout.getChildAt(i);
    		spe.putString(PREF_PREVIOUS_PATH + String.valueOf(i), editCtrl.getText().toString());
        }
		
		spe.commit();
    }

	/**
     * Display a simple alert box
     * @param ctx context
     * @param msg message
     */
   	public static void alert(Context ctx, CharSequence msg) {
    	if (ctx != null) {
        	new AlertDialog.Builder(ctx)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(msg)
        	.show();
    	}
    }
   	
	/*
		mount -o remount,rw /dev/block/mtdblock3 /system
		mv /etc/hosts /etc/hosts.bak
		cp /mnt/sdcard/hosts.final /etc/hosts
		chmod 644 /etc/hosts
		mount -o remount,ro /dev/block/mtdblock3 /system
	 */
   	private static String makeScript(String srcFile, String dstFile)
   	{
   		if (null == SystemMountPoint) {
   			return null;
   		}
   		StringBuilder sb = new StringBuilder(SCRIPT_TEMPLATE_PART1);
   		sb.append(SystemMountPoint);
   		sb.append(SCRIPT_TEMPLATE_PART2);
   		sb.append(dstFile);
   		sb.append(" ");
   		sb.append(dstFile);
   		sb.append(SCRIPT_TEMPLATE_PART3);
   		sb.append(srcFile);
   		sb.append(" ");
   		sb.append(dstFile);
   		sb.append(SCRIPT_TEMPLATE_PART4);
   		sb.append(dstFile);
   		sb.append(SCRIPT_TEMPLATE_PART5);
   		sb.append(SystemMountPoint);
   		sb.append(SCRIPT_TEMPLATE_PART6);
   		return sb.toString();
   	}
    
   	private void ApplyHostsFile(String srcPath) {
		//EditText editCtrl = (EditText)findViewById(R.id.sourceFilePath);
		//String path = editCtrl.getText().toString();
		
		Resources res = getResources();

		// do stuff
		File f = new File(srcPath);
		if (!f.exists())
		{
			alert(AdBlockerActivity.this, res.getString(R.string.errSrcNotExist));
			return;
		}
		
		if (!ShellScript.hasRootAccess(AdBlockerActivity.this))
		{
			alert(AdBlockerActivity.this, res.getString(R.string.errNoRoot));
			return;
		}
		
		if (null == SystemMountPoint || SystemMountPoint.length() == 0)
		{
			getSystemMountPoint();
			if (null == SystemMountPoint)
			{
				alert(AdBlockerActivity.this, res.getString(R.string.errMountPoint));
				return;
			}
		}
		
		int result = -1;
		try {
			result = ShellScript.runScript(AdBlockerActivity.this, makeScript(srcPath, DEFAULT_HOSTS_LOCATION), true);
		}
		catch (IOException e) {
			result = -1;
		}
		
		if (0 != result) {				
			alert(AdBlockerActivity.this, res.getString(R.string.errScriptRun));
			return;
		}

		// 0 == result
		Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.okMessage, Toast.LENGTH_LONG);
		toast.show();
	}
 
   	private boolean copyStreamToFile(InputStream is, int inputSize, String fileName) {
   		boolean result = false;
   		FileOutputStream fos = null;
   		try {
   	   		ReadableByteChannel rbc = Channels.newChannel(is);
   	   	    fos = new FileOutputStream(fileName);
   	   	    fos.getChannel().transferFrom(rbc, 0, inputSize);
   	   	    result = true;
   		}
   		catch (Exception e) {   			
   		}
   		finally {
   			try {
   				if (null != fos) {
   					fos.close();
   				}
   			}
   			catch (Exception e) {   				
   			}
   		}
   		return result;
   	}
   	
   	private boolean downloadFile(String url, String suffix, File directory) {
   		if (!directory.exists()) {
   			return false;
   		}
   		
   		boolean result = false;
   		
   		String fileName = directory.getAbsolutePath() + File.separator + MergeHosts.HOSTS_FILES_PREFIX + suffix;

   		InputStream is = null;
   		try {
   			URL urlObj = new URL(url);
   			URLConnection conn = urlObj.openConnection();
   			int size = conn.getContentLength();
   			if(size < 0) {
   				// try a fictitious size
   				size = 1 << 24;
   			}
   			is = conn.getInputStream();
   	   	    result = copyStreamToFile(is, size, fileName);
   		}
   		catch (Exception e) {
   			//alert(AdBlockerActivity.this, e.toString());
   		}
   		finally {
   			try {
   				if (null != is) {
   	   				is.close();
   				}
   			}
   			catch (IOException e){   				
   			}
   		}
   		
   		File file = new File(fileName);
   		if (file.length() == 0) {
   			result = false;
   		}
   		if (!result && file.exists()) {
			file.delete();
   		}

   		return result;
   	}
   	
   	private class FileProcessor extends AsyncTask<Void, Void, Integer> {
   		private ProgressDialog dlg = null;

		@Override
		protected Integer doInBackground(Void... params) {
	   		int fileCount = 0;
	   		
	   		File dir = AdBlockerActivity.this.getDir("temp",0);

	        // download / copy to temporary files
	   		int currentCount = mainLayout.getChildCount();
	        for (int i = 0; i < currentCount; i++) {
	            EditText editCtrl = (EditText)mainLayout.getChildAt(i);
	    		String txt = editCtrl.getText().toString();
	    		if (txt.startsWith("http://")) {
	    			if (downloadFile(txt, String.valueOf(fileCount), dir)) {
	    				fileCount++;
	    			}
	    		}
	    		else {
	    			File srcFile = new File(txt);
	    			if (!srcFile.exists()) {
	    				continue;
	    			}
	    			if (downloadFile("file:" + srcFile.getAbsolutePath(), String.valueOf(fileCount), dir)) {
	    				fileCount++;
	    			}
	    		}
	        }
	        
	        if (0 == fileCount) {
	        	return fileCount;
	        }

	   		// merge temporary files
			{
				try {
					MergeHosts merger = new MergeHosts(dir);
					merger.mergeIt(MergeHosts.DEFAULT_HOSTS_FILENAME);
				}
				catch (Exception e) {
		        	return 0;
				}
			}
			return fileCount;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (null != dlg) {
				dlg.dismiss();
			}
			
			Resources res = getResources();

			if (0 == result) {
	        	alert(AdBlockerActivity.this, res.getString(R.string.errMerge));
	        	return;
			}
			
			File dir = AdBlockerActivity.this.getDir("temp",0);
	        String finalFileName = dir + File.separator + MergeHosts.DEFAULT_HOSTS_FILENAME; 
			
	   		ApplyHostsFile(finalFileName);
	   		
	   		// delete temporary files
	   		for (int i = 0; i < result; i++) {
	   			File file = new File(dir + File.separator + MergeHosts.HOSTS_FILES_PREFIX + String.valueOf(i));
	   			if (file.exists()) {
	   				file.delete();
	   			}
	   		}
	   		File finalFile = new File(finalFileName);
	   		if (finalFile.exists()) {
	   			finalFile.delete();
	   		}
		}

		@Override
		protected void onPreExecute() {
			Resources res = getResources();
			dlg = ProgressDialog.show(AdBlockerActivity.this, res.getString(R.string.processing), res.getString(R.string.pleaseWait), true, false);
		} 
		
   	}
   	
   	private void AddEditToLayout() {
   		if (mainLayout.getChildCount() >= 20) {
   			Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.tooManyMessage, Toast.LENGTH_LONG);
   			toast.show();
   			return;
   		}
		EditText editNew = new EditText(AdBlockerActivity.this);
		LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
		mainLayout.addView(editNew, 0, params);
   	}
   	
   	private void RemoveEditToLayout() {
   		if (mainLayout.getChildCount() <= 1) {
   			return;
   		}
		mainLayout.removeViewAt(0);
   	}
   	
	private OnClickListener mCommandButtonClicked = new OnClickListener() {		
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.buttonApply:
				new FileProcessor().execute((Void)null);
				break;
			case R.id.buttonAddEditText:
				AddEditToLayout();
				break;
			case R.id.buttonRemoveEditText:
				RemoveEditToLayout();
				break;
			}
		}
	};

	protected void getSystemMountPoint() {
		SystemMountPoint = null;
		try {
			StringBuilder sb = new StringBuilder();
			File file = File.createTempFile("script-", ".sh", AdBlockerActivity.this.getCacheDir());
			int result = ShellScript.runScript("mount", sb, ShellScript.DEFAULT_TIMEOUT_MILISECONDS, file, false);
			if (0 == result) {
				try {
					Pattern p = Pattern.compile("^(\\S+)\\s+/system\\s", Pattern.MULTILINE);
					Matcher m = p.matcher(sb.toString());
					if (m.find()) {
						SystemMountPoint = m.group(1);
					}
				}
				catch (Exception e) {					
				}
			}
			file.delete();
		}
		catch (IOException e) {			
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
	    MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.mainmenu, menu);
	    return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.itemAbout: {
        	new AlertDialog.Builder(AdBlockerActivity.this)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(R.string.aboutMessage)
        	.setTitle(R.string.app_name)
        	.show();
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}