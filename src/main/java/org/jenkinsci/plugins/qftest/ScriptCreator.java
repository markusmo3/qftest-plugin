/*
 * The MIT License
 *
 * Copyright (c) 2017 Quality First Software GmbH
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jenkinsci.plugins.qftest;

import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Executor;
import hudson.remoting.VirtualChannel;
import hudson.tasks.CommandInterpreter;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.io.File;
import java.io.FileFilter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 * This class creates a Batch/Shell script with all the information given by
 * the user
 *
 * @author QFS, Sebastian Kleber
 */
public class ScriptCreator {

	private StringBuilder script;
	private CommandInterpreter scriptFile;
	private final ArrayList<Suites> suitefield;
	private final String qfPath;
	private final String qfPathUnix;
	private final boolean customPathSelected;
	private final boolean customReportsSelected;
	private final boolean daemonSelected;
	private final String customPath;
	private final String customReports;
	private final String daemonhost;
	private final String daemonport;
	private final EnvVars envVars;
	private final boolean isUnix;
	private final BuildListener listener;
	private final AbstractBuild build;
	private String separator;

	/**
	 * CTOR
	 *  @param suitefield
	 *            Contains name of testsuites and their command line arguments
	 * @param qfPath
	 *            global path for Windows jobs
	 * @param qfPathUnix
 *            global path for Unix jobs
	 * @param customPathSelected
*            if specific QF-Test version should be used
	 * @param customReportsSelected
*            if custom report directory should be used
	 * @param daemonSelected
*            If daemon should be called
	 * @param customPath
*            Path to specific QF-Test directory for a job
	 * @param customReports
*            Reports will now be saved in WORKSPACE/customReports instead
*            of WORKSPACE/qftestJenkinsReports
	 * @param daemonhost
*            Daemonhost
	 * @param daemonport
*            Daemonport
	 * @param isUnix
	 * @param envVars
	 * @param listener
	 * @param build
	 */
	public ScriptCreator(ArrayList<Suites> suitefield, String qfPath,
						 String qfPathUnix, boolean customPathSelected,
						 boolean customReportsSelected, boolean daemonSelected,
						 String customPath, String customReports, String daemonhost,
						 String daemonport, boolean isUnix, EnvVars envVars, BuildListener listener, AbstractBuild build) {
		this.suitefield = suitefield;
		this.envVars = envVars;
		if (qfPath == null)
			this.qfPath = "";
		else
			this.qfPath = qfPath;
		if (qfPathUnix == null)
			this.qfPathUnix = "";
		else
			this.qfPathUnix = qfPathUnix;
		this.customPathSelected = customPathSelected;
		this.customReportsSelected = customReportsSelected;
		this.daemonSelected = daemonSelected;
		this.customPath = customPath;
		this.customReports = customReports;
		this.daemonhost = daemonhost;
		this.daemonport = daemonport;
		this.isUnix = isUnix;
		this.listener = listener;
		this.build = build;
		if (!isUnix) {
			separator = "\\";	
		} else {
			separator = "/";
		}	

		if (!this.isUnix) {
			this.createScript();
		} else {
			this.createShell();
		}
			
	}

	/**
	 * Returns the commandInterpreter
	 *
	 * @return complete script which will be executed
	 */
	public CommandInterpreter getScript() {
		return scriptFile;
	}

	/**
	 * Creates script for batch file by calling several methods.
	 */
	private void createScript() {
		script = new StringBuilder();
		script.append("@echo off\n");
		logdir();
		deleteLogs();
		qftPath();
		runner();
		genreport();
		genDoc();
		setMark();
		popd();
		scriptFile = new BatchFile(script.toString());
	}

	/**
	 * Sets two variables containing the paths where the reports will be created
	 */
	private void logdir() {
		String reports = "qftestJenkinsReports";
		if (customReportsSelected)
			reports = customReports;
		script.append("echo [qftest plugin] Setting directories...\n");
		script.append("set logdir="+getWorkspaceDir()+"\\");
		script.append(reports);
		script.append("\\%JOB_NAME%\\%BUILD_NUMBER%\n");
		script.append("set deletedir=\"");
		script.append(reports);
		script.append("\\%JOB_NAME%\\\"\n");
	}

	/**
	 * Will delete every folder containing logs, which isn't used anymore
	 */
	private void deleteLogs() {
		script.append("echo [qftest plugin] Deleting temporary files...\n");
		script.append("(for /f %%f in ('dir /b %deletedir%') do (\n");
		script.append("if exist %deletedir%%%f\\deleteMark (\n");
		script.append("sleep 1\n");
		script.append("rd /s /q %deletedir%%%f\n");
		script.append("))) > nul 2>&1\n");
	}

	/**
	 * Sets the path to the QF-Test installation Priority: 1.customPath 2.qfPath
	 * 3.default version If default version gets used and cannot find
	 * qftestc.exe, look in System32 and SysWOW64 for qftestc.exe
	 */
	private void qftPath() {
		String path = "";
		script.append("echo [qftest plugin] Setting path for QF-Test...\n");
		if (customPathSelected) {
			path = customPath;
		} else if (!qfPath.isEmpty()) {
			path = qfPath;
		} else {
			script.append("where qftestc  > nul 2>&1\n");
			script.append("if %errorlevel% == 1 (\n");
			script.append("if exist \"%windir%\\System32\\qftestc.exe\" ( pushd %windir%\\System32\\ )\n");
			script.append("if exist \"%windir%\\SysWOW64\\qftestc.exe\" ( pushd %windir%\\SysWOW64\\ )\n");
			script.append("if not exist \"qftestc.exe\" ( echo [qftest plugin] ERROR: Couldn't find QF-Test!"
					+ " Please enter the path to your QF-Test version in the global settings. )\n");
			script.append(")\n");
		}
		if (!path.isEmpty()) {
			script.append("pushd \"");
			script.append(path);
			script.append("\"\n");
		}
		if (customPathSelected || !qfPath.isEmpty()) {
			script.append("if not exist \"qftest.exe\" cd bin\n");
			script.append("if not exist \"qftest.exe\" echo [qftest plugin]"
					+ " ERROR: Couldn't find qftest.exe\n");
			script.append("if not exist \"qftest.exe\" set qfError=-10\n");
		}
	}

	/**
	 * Calls QF-Test and runs all the suites with their CLAs
	 */
	private void runner() {
		String slash = "\\";
		for (Suites s : suitefield) {
			String suiteName = s.getSuitename();
			suiteName = envVars.expand(suiteName);
			if (suiteName.contains(".qft")) {
				script.append("echo [qftest plugin] Running test-suite ");
				script.append(s.getSuitename());
				script.append(" ...\n");
			} else {
				if (daemonSelected) {
					script.append("echo [qftest plugin] WARNING: You want to run "
							+ "all test-suites in folder \\");
					script.append(suiteName);
					script.append("\\ in daemon mode.\n");
					script.append("echo [qftest plugin] WARNING: Daemon mode does "
							+ "not support the execution of multiple test-suites at "
							+ "once, so only one test-suite will be run.\n");
				} else if (!suiteName.isEmpty()){
					if (suiteName.contains("/")) {
						slash = "/";
					}
					script.append("echo [qftest plugin] Running all test-suites in"
							+ " directory %WORKSPACE%");
					script.append(slash);
					script.append(suiteName);
					script.append(slash);
					script.append(" ...\n");
				} else {
					if (s.getCustomParam().contains("suitesfile")) {
						script.append("echo [qftest plugin] Running test-suites defined in suitesfile ...\\n");
					}
				}
			}
			script.append("@echo on\n");
			script.append("qftestc -batch -exitcodeignoreexception -nomessagewindow ");
			addDaemonParamsIfNeeded();
			
			List<String> matchList = getCustomParamsAsList(s.getCustomParam());
			
			boolean customRunLogSet = false;
			boolean customRunIdSet = false;
			boolean suitesFileProvided = false;			
			for (Iterator<String> iterator = matchList.iterator(); iterator.hasNext();) {
				boolean ignoreParam = false;
			    String param = iterator.next();
				if (param.contains("-runlog")) {
					customRunLogSet = true;
				} else if (param.contains("-runid")) {
					customRunIdSet = true;
				} else if (param.contains("-report")) {
					if (reportParamHasValue(param)) {
						ignoreParam = true;
						//ignore
					}
				} else if (param.contains("-gendoc")) {
					ignoreParam = true;
					//ignore
				} else if (param.contains("-testdoc")) {
					ignoreParam = true;
				} else if (param.contains("-pkgdoc")) {
					ignoreParam = true;
				}else if (param.contains("-suitesfile")) {
					suitesFileProvided = true;
				}
				if ( !ignoreParam) {
					script.append(envVars.expand(param));
				}
			}
			if (!customRunLogSet) {
				script.append(" -runlog \"%logdir%\\logs\\log_+b\"");
			}
			if (!customRunIdSet) {
				script.append(" -runid \"%JOB_NAME%-%BUILD_NUMBER%-+y+M+d+h+m+s\"");
			}
			
			if (!suitesFileProvided) {
				if (daemonSelected) {
					String suite = s.getSuitename();
					suite = envVars.expand(suite);
					if ( suite.startsWith("/") || suite.startsWith("\\")) {
						script.append(" \""+ suite+"\" ");
					} else {
						script.append(" \""+getWorkspaceDir()+ "/" + suite+"\" ");
					}
				} else {
					appendSuites(suiteName);
				}
			}
			script.append("\n@echo off\n");
		}
		script.append("if %errorlevel% LSS 0 ( set qfError=%errorlevel% )\n");
	}

	/**
	 * Generates reports in
	 * "WORKSPACE\qftestJenkinsReports\JOB_NAME\BUILD_NUMBER\" (or customReports
	 * instead of qftestJenkinsReports)
	 */
	private void genreport() {
		script.append("echo [qftest plugin] Generating reports...\n");
		script.append("@echo on\n");
		script.append("qftestc -batch -genreport ");
		
		boolean customreportHTML = false;
		boolean customreportJUnit = false;
		for (Suites s : suitefield) {
			List<String> matchList = getCustomParamsAsList(s.getCustomParam());
			for (Iterator<String> iterator = matchList.iterator(); iterator.hasNext();) {
				boolean ignoreParam = false;
				String param = iterator.next();
				if (param.contains("-report.html")) {
					customreportHTML = true;
				} else if (param.contains("-report.junit")) {
					customreportJUnit = true;
				} else if (param.contains("-runlog")) {
	                  //ignore runlog param in report generation
					ignoreParam = true;
				} else if (param.contains("-runid")) {
	                  //ignore runid param in report generation
						ignoreParam = true;
				} else if (param.contains("-gendoc")) {
	                  //ignore gendoc param in report generation
						ignoreParam = true;
				} else if (param.contains("-testdoc")) {
						ignoreParam = true;
				} else if (param.contains("-pkgdoc")) {
					ignoreParam = true;
				}
				
				if (!ignoreParam) {
					script.append(" "+envVars.expand(param));
				}
			}	
		}
		
		if (!customreportHTML) {
			script.append(" -report.html \"%logdir%\\html\"");
		}
		if (!customreportJUnit) {
			script.append(" -report.junit \"%logdir%\\junit\"");
		}
		script.append(" \"%logdir%\\logs\"\n\n");
		script.append("@echo off\n");
		script.append("if %errorlevel% LSS 0 ( set qfError=%errorlevel% )\n");
	}
	
	private void genDoc() {

		for (Suites s : suitefield) {
			List<String> matchList = getCustomParamsAsList(s.getCustomParam());
			if (matchList.contains("-gendoc")) {
				script.append("echo [qftest plugin] Generating documentation...\n");
				script.append("@echo on\n");
				script.append("qftestc -batch");
				
				for (Iterator<String> iterator = matchList.iterator(); iterator.hasNext();) {
					String param = iterator.next();
					script.append(" "+envVars.expand(param));
				}	
				
				String suiteName = s.getSuitename();
				appendSuites(suiteName);
				script.append("\n@echo off\n");
			}
		}
	}

	/**
	 * Signals other builds, that this build is done
	 */
	private void setMark() {
		script.append("echo delete > \"%logdir%\\deleteMark\" \n");
	}

	/**
	 * Script finished
	 */
	private void popd() {
		script.append("popd\n");
		script.append("set errorlevel=%qfError%\n");
		script.append("echo [qftest plugin] Done\n");
	}

	/**
	 * Creates a run script for shell command call
	 */
	private void createShell() {
		script = new StringBuilder();
		logdirShell();
		deleteLogsShell();
		qftPathShell();
		runnerShell();
		genreportShell();
		genDoc();
		setMarkShell();
		System.out.println(script.toString());
		scriptFile = new Shell(script.toString());
	}

	/**
	 * @see logdir()
	 */
	private void logdirShell() {
		String reports = "qftestJenkinsReports";
		if (customReportsSelected) {
			reports = customReports;
		}
		script.append("LOGDIR=\""+getWorkspaceDir()+"/");
		script.append(reports);
		script.append("/$JOB_NAME/$BUILD_NUMBER\"\n");
		script.append("DELETEDIR=\""+getWorkspaceDir()+"/");
		script.append(reports);
		script.append("/$JOB_NAME/\"\n");
		script.append("CURDIR=\""+getWorkspaceDir()+"\"\n");
	}

	/**
	 * @see deleteLogs()
	 */
	private void deleteLogsShell() {
		script.append("for d in \"$DELETEDIR\"*/ ; do\n");
		script.append("if [ -f \"$d\"deleteMark ]; then\n");
		script.append("sleep 1\n");
		script.append("rm -rf \"$d\"\n");
		script.append("fi\n");
		script.append("done\n");
	}

	/**
	 * @see qftPath()
	 */
	private void qftPathShell() {
		String path = qfPathUnix;
		if (customPathSelected)
			path = customPath;
		if (customPathSelected || !qfPathUnix.isEmpty()) {
			script.append("cd \"");
			script.append(path);
			script.append("\"\n");
			script.append("[ ! -f \"$PWD/qftest\" ] && cd bin\n");
			script.append("[ ! -f \"$PWD/qftest\" ] && echo \"[qftest plugin]"
					+ " ERROR: Couldn't find qftest.exe\"\n");
			script.append("[ ! -f \"$PWD/qftest\" ] && exit -10\n");
		}
	}
	
	private boolean reportParamHasValue(String param)
	{
		return param.startsWith("-report") 
		||	param.startsWith("-report.html")
		||  param.startsWith("-report.junit")
		||  param.startsWith("-report.xml")
		||  param.startsWith("-report.name");
	}

	/**
	 * @see runner()
	 */
	private void runnerShell() {
		for (Suites s : suitefield) {
			if (customPathSelected || !qfPathUnix.isEmpty()) {
				script.append("./");
			}
			script.append("qftest -batch -exitcodeignoreexception -nomessagewindow ");
			addDaemonParamsIfNeeded();
			
			List<String> matchList = getCustomParamsAsList(s.getCustomParam());
			
			boolean customRunLogSet = false;
			boolean customRunIdSet = false;
			boolean suitesFileProvided = false;
			for (Iterator<String> iterator = matchList.iterator(); iterator.hasNext();) {
				boolean ignoreParam = false;
			    String param = iterator.next();
				if (param.contains("-runlog")) {
					customRunLogSet = true;
				} else if (param.contains("-runid")) {
					customRunIdSet = true;
				} else if (param.contains("-report")) {
					if (reportParamHasValue(param)) {
						//simply ignore param
						ignoreParam = true;
					}
				} else if (param.contains("-gendoc")) {
					ignoreParam = true;
					//ignore
				} else if (param.contains("-testdoc")) {
					ignoreParam = true;
				} else if (param.contains("-pkgdoc")) {
					ignoreParam = true;
				} else if (param.contains("-suitesfile")) {
					suitesFileProvided = true;
				}
				if (!ignoreParam) {
					script.append(envVars.expand(param));
				}
			}
			if (!customRunLogSet) {
				script.append(" -runlog \"$LOGDIR/logs/log_+b\"");
			}
			if (!customRunIdSet) {
				script.append(" -runid \"$JOB_NAME-$BUILD_NUMBER-+y+M+d+h+m+s\"");
			}
			
			if (!suitesFileProvided) {
				String suite = s.getSuitename();
				suite = envVars.expand(suite);
				if (daemonSelected) {
					if ( suite.startsWith("/") || suite.startsWith("\\")) {
						script.append(" \""+ suite+"\" ");
					} else {
						script.append(" \""+getWorkspaceDir() + "/" + suite+"\" ");
					}
				} else {
					appendSuites(suite);
				}
			}
			script.append("\n");	
		}
	}
	
	String getWorkspaceDir() 
	{
		String workspace =  envVars.expand("$WORKSPACE");
		if (workspace == null) {
			listener.getLogger().println("[qftest plugin] ERROR: Can't determine Workspace");
		}
		return workspace;
	}
	
	boolean isUnixPath(FilePath file)
	{
		return file.getRemote().indexOf("\\")==-1;
	}
	
	void appendSuites(String suitename) {
		String workspacedir = getWorkspaceDir();  // probably the same as build.getWorkspace();
        FilePath ws = build.getWorkspace();
        if (ws == null) {
			listener.getLogger().println("[qftest plugin] ERROR: unable to determine workspace");
        		return;
        } else {
        		listener.getLogger().println("[qftest plugin] INFO: using workspace: " + ws);
        }
		VirtualChannel channel = ws.getChannel();
		if (channel == null) {
			listener.getLogger().println("[qftest plugin] ERROR: unable to get the build channel");
			return;
		}
		FilePath file = new hudson.FilePath(channel, suitename);
		
		try {
			if (file.exists() && !file.isDirectory()) {
				script.append(" \""+suitename+"\"");
			} else {
				//prepend workspace folder to see if the file can be found there
				FilePath fileInWorkSpace = new FilePath(channel, workspacedir + separator + suitename);
				
				if (fileInWorkSpace.exists() && !fileInWorkSpace.isDirectory()) {
					script.append(" \""+fileInWorkSpace.absolutize().toString()+"\"");				
				} else { //probably a folder or special placeholder
					FilePath [] files = null;
					if (file.isDirectory()) {
						files = getAllSuitesInDirectory(file);
					} else {
						if (fileInWorkSpace.isDirectory()) {
							listener.getLogger().println("[qftest plugin] DEBUG: suite param is a folder, trying to determine files ...");
							files = getAllSuitesInDirectory(fileInWorkSpace);
						} else if (suitename.contains("*")) {
							int index = suitename.indexOf("*");
							String subfolder = "";
							if (index > 0) {
								subfolder = suitename.substring(0, index);
							}
							//look for all files in folder
							files = getAllSuitesInDirectory(new FilePath(channel, workspacedir+separator+subfolder));
						} else {
							listener.getLogger().println("[qftest plugin] ERROR: this point should never be reached (paranoia)");
							listener.getLogger().println("[qftest plugin] ERROR: filepath: " + fileInWorkSpace);
							System.err.println("this point should never be reached");
							script.append("\n\n");	
							return;
						}
					}
					if (files != null) {
						for (FilePath qftfile : files) {
						    script.append(" \""+qftfile+"\"");
						}	
					} else {
						listener.getLogger().println("[qftest plugin] ERROR: no files found in folder.");
					}
				}
			}
		} catch (IOException | InterruptedException e) {
			listener.getLogger().println("[qftest plugin] ERROR: Encountered an issue while accessing the files: "+ e);
			e.printStackTrace();
		}	

		script.append("\n");	
	}	
	
	
	FilePath[] getAllSuitesInDirectory(FilePath dir) 
	{
		listener.getLogger().println("[qftest plugin] DEBUG: getAllSuitesInDirectory - " + dir);
		FilePath[] files = null;
		try {
			files = dir.list("*.qft");
			listener.getLogger().println("[qftest plugin] DEBUG: getAllSuitesInDirectory - found " + files.length + " files.");
			if (files.length > 0) {
				for (FilePath file : files) {
					listener.getLogger().println("[qftest plugin] DEBUG: getAllSuitesInDirectory - found file:" + file);
				}
			}			
		} catch (IOException | InterruptedException e) {
			listener.getLogger().println("[qftest plugin] ERROR: Can't determine all test-suites in directory. \n" + e);
		}
		return files;
	}
	
	/**
	 * splits the given string at all parameters
	 * TODO this should be implemented a bit more stable so that whitespace+"-"
	 * is ignored during split if surrounded by quotes
	 * @param params
	 * @return
	 */
	private static List<String> getCustomParamsAsList(String params) {
		List<String> matchList = new ArrayList<String>();
		String[] p = params.split(" -");
		for (int i = 0; i < p.length; i++) {
			if (i > 0) {
				matchList.add(" -"+p[i]);
			} else {
				matchList.add(p[i]);
			}
			
		}
		return matchList;
	}

	private void addDaemonParamsIfNeeded() {
		if (daemonSelected) {
			script.append("-calldaemon -daemonhost ");
			script.append(daemonhost);
			script.append(" -daemonport ");
			script.append(daemonport);
			script.append(" ");
		}
	}

	/**
	 * @see genreport()
	 */
	private void genreportShell() {
		if (customPathSelected) {
			script.append("./");
		}
		script.append("qftest -batch -genreport ");
		boolean customreportHTML = false;
		boolean customreportJUnit = false;
		for (Suites s : suitefield) {
			List<String> matchList = getCustomParamsAsList(s.getCustomParam());
			for (Iterator<String> iterator = matchList.iterator(); iterator.hasNext();) {
				boolean ignoreParam = false;
			    String param = iterator.next();
				if (param.contains("-report.html")) {
					customreportHTML = true;
				} else if (param.contains("-report.junit")) {
					customreportJUnit = true;
				} else if (param.contains("-runlog")) {
                  //ignore runlog param in report generation
					ignoreParam = true;
				} else if (param.contains("-runid")) {
	                  //ignore runid param in report generation
						ignoreParam = true;
				} else if (param.contains("-gendoc")) {
	                  //ignore gendoc param in report generation
						ignoreParam = true;
				} else if (param.contains("-testdoc")) {
					ignoreParam = true;
				} else if (param.contains("-pkgdoc")) {
					ignoreParam = true;
				}
				
				if (!ignoreParam) {
					script.append(" "+envVars.expand(param));
				}
			}	
		}
		
		if (!customreportHTML) {
			script.append(" -report.html \"$LOGDIR/html\"");
		}
		if (!customreportJUnit) {
			script.append(" -report.junit \"$LOGDIR/junit\"");
		}
		script.append(" \"$LOGDIR/logs\"\n");
	}

	/**
	 * @see setMark()
	 */
	private void setMarkShell() {
		script.append("touch \"$LOGDIR/deleteMark\"\n");
		script.append("cd \"$CURDIR\"\n");
	}
}
