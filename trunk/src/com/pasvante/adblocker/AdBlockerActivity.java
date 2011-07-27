package com.pasvante.adblocker;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
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
	private static final String DEFAULT_BACKUP_PREFS_FNAME = "ad-blocker.pref";
	
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
   	private static String makeCopyScript(String srcFile, String dstFile)
   	{
   		if (null == SystemMountPoint) {
   			return null;
   		}
   		StringBuilder sb = new StringBuilder(SCRIPT_TEMPLATE_PART1);
   		sb.append(SystemMountPoint);
   		sb.append(SCRIPT_TEMPLATE_PART2);
   		sb.append(dstFile);
   		sb.append(" ");
   		// try to backup to sdcard
   		File sdcard = new File("/sdcard/");
   		if (sdcard.exists()) {
   			sb.append("/sdcard/hosts");
   		}
   		else {
   			// same folder as destination
   	   		sb.append(dstFile);
   		}
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
    
	/*
	cp /etc/hosts /sdcard/
	*/
	private static String makeCopyToSDcardScript(String srcFile, String destDir)
	{
		if (null == destDir || null == srcFile) {
			return null;
		}
		File sdcard = new File(destDir);
		if (!sdcard.exists()) {
	   		return null;
		}
		StringBuilder sb = new StringBuilder("cp ");
		sb.append(srcFile);
		sb.append(" ");
		sb.append(destDir);
		if (!destDir.endsWith(File.separator)) {
			sb.append(File.separator);
		}
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
			result = ShellScript.runScript(AdBlockerActivity.this, makeCopyScript(srcPath, DEFAULT_HOSTS_LOCATION), true);
		}
		catch (IOException e) {
			result = -1;
		}
		
		if (0 != result) {				
			alert(AdBlockerActivity.this, res.getString(R.string.errScriptRun));
			return;
		}

		// 0 == result
		Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.okHostsMessage, Toast.LENGTH_LONG);
		toast.show();
	}
   	
   	private static boolean isSDCardAvailable(boolean readOnly) {
   		String state = Environment.getExternalStorageState();
   		if (Environment.MEDIA_MOUNTED.equals(state)) {
   	        return true;
   	    } else if (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
   	    	if (readOnly) {
   	    		return true;
   	    	}
   	    	else {
   	    		return false;
   	    	}
   	    }
   		return false;
   	}
   	
   	private static String getSDCardDirectoryName(boolean readOnly) {
   		if (!isSDCardAvailable(readOnly)) {
   			return null;
   		}
        return Environment.getExternalStorageDirectory().getAbsolutePath();
   	}
   	
   	private static File getSDCardDirectory(boolean readOnly) {
   		if (!isSDCardAvailable(readOnly)) {
   			return null;
   		}
        return Environment.getExternalStorageDirectory();
   	}
   	
   	private void restorePrefsFromSDcard() {
		Resources res = getResources();

		String sdPath = getSDCardDirectoryName(true);
   		if (null == sdPath) {
			alert(AdBlockerActivity.this, res.getString(R.string.errSDRead));
   			return;
   		}
   		StringBuilder sb = new StringBuilder(sdPath);
   		if (!sdPath.endsWith(File.separator)) {
   			sb.append(File.separator);
   		}
   		sb.append(DEFAULT_BACKUP_PREFS_FNAME);

   		File inFile = new File(sb.toString());
   		if (!inFile.exists() || ! inFile.canRead()) {
			alert(AdBlockerActivity.this, res.getString(R.string.errSrcNotExist));
   			return;
   		}
   		
   		ArrayList<String> prefs = new ArrayList<String>();
   		
   		FileInputStream fileIS = null;
   		BufferedReader input = null;
   		try {
   	   		fileIS = new FileInputStream(inFile);
   	   		input = new BufferedReader(new InputStreamReader(fileIS));
   	        String line = null;
   	        while (( line = input.readLine()) != null) {
   	        	if (line.length() > 0) {
   	        		prefs.add(line);
   	        	}
   	        }
   		}
   		catch (IOException e) {
   		}
   		finally {
   			if (null != input) {
   				try {
   	   				input.close();
   				}
   				catch (IOException e) {
   				}
   			}
   		}
        
        if (prefs.size() == 0) {        	
			alert(AdBlockerActivity.this, res.getString(R.string.errReadPrefs));
   			return;
        }

		int currentCount = mainLayout.getChildCount();
   		
        while (currentCount < prefs.size()) {
			EditText editNew = new EditText(AdBlockerActivity.this);
			LayoutParams params = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.WRAP_CONTENT);
			mainLayout.addView(editNew, 1, params);
			currentCount = mainLayout.getChildCount();
        }
        
        for (int i = 0; i < currentCount; i++) {
            //String previousPath = prefs.getString(PREF_PREVIOUS_PATH + String.valueOf(i), "");
            EditText editCtrl = (EditText)mainLayout.getChildAt(i);
    		editCtrl.setText(prefs.get(i));
        }

        Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.okRestorePrefsMessage, Toast.LENGTH_LONG);
		toast.show();
   	}
   	
   	private void backupPrefsToSDcard() {
		Resources res = getResources();
        int currentCount = mainLayout.getChildCount();

		String sdPath = getSDCardDirectoryName(false);
   		if (null == sdPath) {
			alert(AdBlockerActivity.this, res.getString(R.string.errSDWrite));
   			return;
   		}
   		StringBuilder sb = new StringBuilder(sdPath);
   		if (!sdPath.endsWith(File.separator)) {
   			sb.append(File.separator);
   		}
   		sb.append(DEFAULT_BACKUP_PREFS_FNAME);
   		
   		File out = new File (sb.toString());
   		PrintWriter w = null;
   		boolean writeError = true;
   		try {
   	   		w = new PrintWriter(out);
   	        for (int i = 0; i < currentCount; i++) {
   	            EditText editCtrl = (EditText)mainLayout.getChildAt(i);
   	            w.println(editCtrl.getText().toString());
   	        }
   	   		writeError = false;
   		}
   		catch (IOException e) {   			
			writeError = true;
   		}
   		finally {
   			if (null != w) {
				w.close();
   			}
   		}
   		if (writeError) {
   			alert(AdBlockerActivity.this, res.getString(R.string.errWritePrefs));
   			return;
   		}

   		Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.okBackupPrefsMessage, Toast.LENGTH_LONG);
		toast.show();
   	}
   	
   	private void backupHostsToSDcard() {
		Resources res = getResources();
   		File src = new File(DEFAULT_HOSTS_LOCATION);

		if (!src.exists()) {
			alert(AdBlockerActivity.this, res.getString(R.string.errSrcNotExist));
   			return;
   		}
   		File sdcard = getSDCardDirectory(false);
   		if (null == sdcard || !sdcard.exists()) {
			alert(AdBlockerActivity.this, res.getString(R.string.errSDWrite));
   			return;
   		}
   		if (src.canRead()) {
   			InputStream is;
			try {
				is = new FileInputStream(src);
			} catch (FileNotFoundException e) {
				alert(AdBlockerActivity.this, res.getString(R.string.errSrcNotExist));
				return;
			}
			String sdPath = getSDCardDirectoryName(false);
	   		if (null == sdPath) {
				alert(AdBlockerActivity.this, res.getString(R.string.errSDWrite));
	   			return;
	   		}
	   		StringBuilder sb = new StringBuilder(sdPath);
	   		if (!sdPath.endsWith(File.separator)) {
	   			sb.append(File.separator);
	   		}
	   		sb.append("hosts");
   			if (copyStreamToFile(is, (int) src.length(), sb.toString())) {
   				Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.okBackupHostsMessage, Toast.LENGTH_LONG);
   				toast.show();
   				return;
   			}
   			// fall back on root copy
   		}

		// no rights, try with root access
		Toast toast = Toast.makeText(AdBlockerActivity.this, R.string.tryingRoot, Toast.LENGTH_LONG);
		toast.show();

		if (!ShellScript.hasRootAccess(AdBlockerActivity.this))
		{
			alert(AdBlockerActivity.this, res.getString(R.string.errNoRoot));
			return;
		}
		int result = -1;
		try {
			result = ShellScript.runScript(AdBlockerActivity.this, makeCopyToSDcardScript(DEFAULT_HOSTS_LOCATION, getSDCardDirectoryName(false)), true);
		}
		catch (IOException e) {
			result = -1;
		}
			
		if (0 != result) {				
			alert(AdBlockerActivity.this, res.getString(R.string.errScriptRun));
			return;
		}

		// 0 == result
		toast = Toast.makeText(AdBlockerActivity.this, R.string.okBackupHostsMessage, Toast.LENGTH_LONG);
		toast.show();
   	}
 
   	private static boolean copyStreamToFile(InputStream is, int inputSize, String fileName) {
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
			String title = getResources().getString(R.string.app_name);
			PackageInfo pinfo;
			try {
				pinfo = getPackageManager().getPackageInfo(getPackageName(), 0);
				title += " " + pinfo.versionName;
			} catch (NameNotFoundException e) {
			}
        	new AlertDialog.Builder(AdBlockerActivity.this)
        	.setNeutralButton(android.R.string.ok, null)
        	.setMessage(R.string.aboutMessage)
        	.setTitle(title)
        	.show();
			return true;
		}
		case R.id.itemBackup: {
			backupHostsToSDcard();
			backupPrefsToSDcard();
			return true;
		}
		case R.id.itemRestore: {
			restorePrefsFromSDcard();
			return true;
		}
		default:
			return super.onOptionsItemSelected(item);
		}
	}

}