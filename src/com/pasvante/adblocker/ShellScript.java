package com.pasvante.adblocker;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

import android.content.Context;


public class ShellScript {
	
	public static final String DEFAULT_SCRIPT_NAME = "script.sh";
	public static final int DEFAULT_TIMEOUT_MILISECONDS = 40000;
	
	private static boolean hasRoot = false; // cache value


	/**
	 * Check if we have root access
	 * @param ctx mandatory context
     * @param showErrors indicates if errors should be alerted
	 * @return boolean true if we have root
	 */
	public static boolean hasRootAccess(Context ctx) {
		if (hasRoot) return true;
		try {
			// Run an empty script just to check root access
			if (runScript(ctx, "exit 0", true) == 0) {
				hasRoot = true;
				return true;
			}
		} catch (Exception e) {
		}
		return false;
	}

    /**
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
	 * @param ctx mandatory context
     * @param script the script to be executed
     * @return the script exit code
     * @throws IOException 
     */
	public static int runScript(Context ctx, String script, boolean asroot) throws IOException {
		if (null == script || null == ctx) {
			return -1;
		}
		File file = File.createTempFile("script-", ".sh", ctx.getCacheDir());
		int result = runScript(script, null, DEFAULT_TIMEOUT_MILISECONDS, file, asroot);
		file.delete();
		return result;
	}

	/**
     * Runs a script, wither as root or as a regular user (multiple commands separated by "\n").
     * @param script the script to be executed
     * @param res the script output response (stdout + stderr)
     * @param timeout timeout in milliseconds (-1 for none)
     * @param tempfile temporary file to hold the script; user should ensure it is unique between calls
     * @param asroot whether script should be run as root
     * @return the script exit code
     */
	public static int runScript(String script, StringBuilder res, long timeout, File tempfile, boolean asroot) {
		ScriptRunner runner = new ScriptRunner(tempfile, script, res, asroot);
		runner.start();
		try {
			if (timeout > 0) {
				runner.join(timeout);
			} else {
				runner.join();
			}
			if (runner.isAlive()) {
				// Timed-out
				runner.interrupt();
				runner.join(150);
				runner.destroy();
				runner.join(50);
			}
		} catch (InterruptedException ex) {}
		return runner.exitcode;
	}
	
	/**
	 * Internal thread used to execute scripts (as root or not).
	 */
	private static final class ScriptRunner extends Thread {
		private final File file;
		private final String script;
		private final StringBuilder res;
		private final boolean asroot;
		public int exitcode = -1;
		private Process exec;
		
		/**
		 * Creates a new script runner.
		 * @param file temporary script file
		 * @param script script to run
		 * @param res response output
		 * @param asroot if true, executes the script as root
		 */
		public ScriptRunner(File file, String script, StringBuilder res, boolean asroot) {
			this.file = file;
			this.script = script;
			this.res = res;
			this.asroot = asroot;
		}
		
		@Override
		public void run() {
			try {
				file.createNewFile();
				final String abspath = file.getAbsolutePath();
				// make sure we have execution permission on the script file
				Runtime.getRuntime().exec("chmod 777 "+abspath).waitFor();
				// Write the script to be executed
				final OutputStreamWriter out = new OutputStreamWriter(new FileOutputStream(file));
				if (new File("/system/bin/sh").exists()) {
					out.write("#!/system/bin/sh\n");
				}
				out.write(script);
				//if (!script.endsWith("\n")) out.write("\n");
				out.write("\nexit\n");
				out.flush();
				out.close();
				if (this.asroot) {
					// Create the "su" request to run the script
					exec = Runtime.getRuntime().exec("su -c "+abspath);
				} else {
					// Create the "sh" request to run the script
					exec = Runtime.getRuntime().exec("sh "+abspath);
				}
				if (null != res)
				{
					InputStreamReader r = new InputStreamReader(exec.getInputStream());
					final char buf[] = new char[1024];
					int read = 0;
					// Consume the "stdout"
					while ((read=r.read(buf)) != -1) {
						res.append(buf, 0, read);
					}
					// Consume the "stderr"
					r = new InputStreamReader(exec.getErrorStream());
					read=0;
					while ((read=r.read(buf)) != -1) {
						res.append(buf, 0, read);
					}
				}
				// get the process exit code
				if (exec != null) this.exitcode = exec.waitFor();
			} catch (InterruptedException ex) {
				if (res != null) res.append("\nOperation timed-out");
			} catch (Exception ex) {
				if (res != null) res.append("\n" + ex);
			} finally {
				destroy();
			}
		}
		
		/**
		 * Destroy this script runner
		 */
		public synchronized void destroy() {
			if (exec != null) exec.destroy();
			exec = null;
		}
	}
}
