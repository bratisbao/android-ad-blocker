package com.ri.hosts;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Application to merge host files from various sources into one, distinct new hosts file 
 * 
 * @author Rudolf
 */
public class MergeHosts {
	private File folder = null; 
	public static final String DEFAULT_HOSTS_FILENAME = "hosts.final";
	public static final String HOSTS_FILES_PREFIX = "hosts.";
	
	public MergeHosts(File folder) throws IOException {
		this.folder = folder;
		
		if (!folder.exists() || !folder.isDirectory() || !folder.canRead()){
			throw new IOException("ERROR - Invalid folder: " + folder.getCanonicalPath());
		}
	}
	
	public void mergeIt(String OutputFileName) throws Exception {
		List<File> hostFiles = new ArrayList<File>();
		File files[] = folder.listFiles();
		for (File file : files){
			if (file.getName().toLowerCase().startsWith(HOSTS_FILES_PREFIX) && !file.getName().toLowerCase().endsWith("zip") && !file.getName().toLowerCase().endsWith("rar") && !file.getName().toLowerCase().endsWith("final")){
				hostFiles.add(file);
			}
		}
		
		if (hostFiles.size() == 0){
			throw new Exception("ERROR - No hosts files found matching pattern \"hosts.*\"");
		}
		
		Pattern p = Pattern.compile("^\\S+\\s+(\\S+)", 0);

		Set<String> distinctDomains = new TreeSet<String>();
		for (File file : hostFiles){
			
			FileReader fr = null;
			BufferedReader br = null;
			try {
				fr = new FileReader(file);
				br = new BufferedReader(fr);
				
				String line = null;
				while ((line = br.readLine()) != null){
					if (line.startsWith("#")) {
						continue;
					}
					
					int commentPos = line.indexOf('#');
					
					if (-1 != commentPos) {
						line = line.substring(0, commentPos);
					}
					
					Matcher m = p.matcher(line);
					if (m.find()) {
						String domain = m.group(1);
						if (null == domain || 0 == domain.length()) {
							continue;
						}
						distinctDomains.add(domain.toLowerCase());
					}
				}

			} catch (IOException ioe){
				throw new Exception("ERROR - Unable to parse " + file.getName() + ": " + ioe.getMessage());
			} finally {
				try {
					br.close();
				} catch (Throwable t){
					//consume
				}
				try {
					fr.close();
				} catch (Throwable t){
					//consume
				}
			}
		}
		
		FileWriter fw = null;
		BufferedWriter bw = null;
		File finalHosts = null;
		if (null == OutputFileName) {
			OutputFileName = DEFAULT_HOSTS_FILENAME;
		}
		try {
			finalHosts = new File(folder.getAbsolutePath() + File.separator + OutputFileName);
			fw = new FileWriter(finalHosts, false);
			bw = new BufferedWriter(fw);
			
			distinctDomains.remove("localhost");
			bw.write("127.0.0.1 localhost\n");

			for (String domain : distinctDomains){
				bw.write("127.0.0.1 " + domain + "\n");
			}
		} catch (IOException ioe){
			throw ioe;
		} finally {
			try {
				bw.flush();
			} catch (Throwable t){
				//consume
			}
			try {
				fw.flush();
			} catch (Throwable t){
				//consume
			}
			try {
				bw.close();
			} catch (Throwable t){
				//consume
			}
			try {
				fw.close();
			} catch (Throwable t){
				//consume
			}
		}
	}
}
