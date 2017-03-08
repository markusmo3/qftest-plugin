/*
 * The MIT License
 *
 * Copyright (c) 2015 Quality First Software GmbH
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
import hudson.tasks.CommandInterpreter;
import hudson.tasks.BatchFile;
import hudson.tasks.Shell;

import java.util.ArrayList;

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
	 */
	public ScriptCreator(ArrayList<Suites> suitefield, String qfPath,
						 String qfPathUnix, boolean customPathSelected,
						 boolean customReportsSelected, boolean daemonSelected,
						 String customPath, String customReports, String daemonhost,
						 String daemonport, boolean isUnix, EnvVars envVars) {
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
		if (!isUnix)
			this.createScript();
		else
			this.createShell();
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
		suitedir();
		deleteLogs();
		qftPath();
		runner();
		genreport();
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
		script.append("set logdir=%CD%\\");
		script.append(reports);
		script.append("\\%JOB_NAME%\\%BUILD_NUMBER%\n");
		script.append("set deletedir=\"");
		script.append(reports);
		script.append("\\%JOB_NAME%\\\"\n");
	}

	/**
	 * Sets a suiteX variable for every suite/folder the user entered In case a
	 * single test-suite was entered, the path for the suite will be saved. If a
	 * folder got stated, then look at every file in this folder. If the file is
	 * a suite, add the path to this suite to the suiteX variable (Users can
	 * enter "*" to run every suite in the workspace)
	 */
	private void suitedir() {
		int i = 0;
		for (Suites s : suitefield) {
			if (s.getSuitename().contains(".qft")) {
				script.append("set suite");
				script.append(i);
				script.append("=\"%CD%\\");
				script.append(s.getSuitename());
				script.append("\"\n");
			} else {
				script.append("setlocal EnableDelayedExpansion\n");
				script.append("set \"var=\"\n");
				script.append("set \"var2=\"\n");
				script.append("set \"isqft=.qft\"\n");
				script.append("set \"isbak=.bak\"\n");
				script.append("FOR /F \"delims=\" %%a in ('dir /b \"%CD%\\");
				if (!s.getSuitename().equals("*"))
					script.append(s.getSuitename());
				script.append("\\\"') do (\n");
				script.append("set \"var2=%%a\"\n");
				script.append("IF \"!var2:%isqft%=!\" NEQ \"!var2!\" ");
				script.append("IF \"!var2:%isbak%=!\" == \"!var2!\" ");
				script.append("set \"var=!var!\"%CD%\\");
				if (!s.getSuitename().equals("*"))
					script.append(s.getSuitename());
				script.append("\\%%a\" \"\n)\n");
				script.append("set suite");
				script.append(i);
				script.append("=!var!\n");
			}
			i++;
		}
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
		int i = 0;
		String slash = "\\";
		for (Suites s : suitefield) {
			String suiteName = s.getSuitename();
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
				} else {
					if (suiteName.contains("/")) {
						slash = "/";
					}
					script.append("echo [qftest plugin] Running all test-suites in"
							+ " directory %WORKSPACE%");
					script.append(slash);
					script.append(suiteName);
					script.append(slash);
					script.append(" ...\n");
				}
			}
			script.append("@echo on\n");
			script.append("qftestc -batch -run -exitcodeignoreexception -nomessagewindow ");
			addDaemonParamsIfNeeded();
			String[] customParams = s.getCustomParam().split(" ");
			boolean customRunLogSet = false;
			boolean customRunIdSet = false;
			for (int j = 0; j < customParams.length; j++) {
				String param = customParams[j];
				if (param.equalsIgnoreCase("-runlog")) {
					script.append("-runlog \"%logdir%\\logs\\"+envVars.expand(customParams[j+1])+"\" ");
					customRunLogSet = true;
					j++;
				} else if (param.equalsIgnoreCase("-runid")) {
					script.append("-runid \""+ envVars.expand(customParams[j+1])+"\" ");
					customRunIdSet = true;
					j++;
				} else if (param.startsWith("-report")) {
					if (reportParamHasValue(param)) {
						j++;
					}
				} else {
					script.append(envVars.expand(customParams[j])+" ");
				}
			}
			if (!customRunLogSet) {
				script.append("-runlog \"%logdir%\\logs\\log_+b\" ");
			}
			if (!customRunIdSet) {
				script.append("-runid \"%JOB_NAME%-%BUILD_NUMBER%-+y+M+d+h+m+s\" ");
			}
			script.append("%suite");
			script.append(i++);
			script.append("%\n");
			script.append("@echo off\n");
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
			String[] customParams = s.getCustomParam().split(" ");
			for (int j = 0; j < customParams.length; j++) {
				String param = customParams[j];
				if (param.equalsIgnoreCase("-report.html")) {
					script.append( param+" "+envVars.expand(customParams[j+1])+" ");
					customreportHTML = true;
					j++;
				} else if (param.equalsIgnoreCase("-report.junit")) {
					script.append( param+" "+envVars.expand(customParams[j+1])+" ");
					customreportJUnit = true;
					j++;
				} else {
					script.append(envVars.expand(customParams[j])+" ");
				}
			}	
		}
		
		if (!customreportHTML) {
			script.append("-report.html \"%logdir%\\html\" ");
		}
		if (!customreportJUnit) {
			script.append("-report.junit \"%logdir%\\junit\" ");
		}
		script.append("\"%logdir%\\logs\"\n\n");
		script.append("@echo off\n");
		script.append("if %errorlevel% LSS 0 ( set qfError=%errorlevel% )\n");
	}

	/**
	 * Signals other builds, that this build is done
	 */
	private void setMark() {
		script.append("echo delete > %logdir%\\deleteMark \n");
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
		suitedirShell();
		deleteLogsShell();
		qftPathShell();
		runnerShell();
		genreportShell();
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
		script.append("LOGDIR=\"$PWD/");
		script.append(reports);
		script.append("/$JOB_NAME/$BUILD_NUMBER\"\n");
		script.append("DELETEDIR=\"$PWD/");
		script.append(reports);
		script.append("/$JOB_NAME/\"\n");
		script.append("CURDIR=\"$PWD\"\n");
	}

	/**
	 * @see suitedir()
	 */
	private void suitedirShell() {
		int i = 0;
		for (Suites s : suitefield) {
			script.append("SUITE");
			script.append(i++);
			script.append("=\"");
			script.append(s.getSuitename());
			script.append(s.getSuitename().endsWith(".qft") ? "\"\n"
					: "/*.qft\"\n");
		}
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
		return param.equalsIgnoreCase("-report") 
		||	param.equalsIgnoreCase("-report.html")
		||  param.equalsIgnoreCase("-report.junit")
		||  param.equalsIgnoreCase("-report.xml")
		||  param.equalsIgnoreCase("-report.name");
	}

	/**
	 * @see runner()
	 */
	private void runnerShell() {
		int i = 0;
		for (Suites s : suitefield) {
			if (customPathSelected || !qfPathUnix.isEmpty()) {
				script.append("./");
			}
			script.append("qftest -batch -exitcodeignoreexception -nomessagewindow ");
			addDaemonParamsIfNeeded();

			String[] customParams = s.getCustomParam().split(" ");
			boolean customRunLogSet = false;
			boolean customRunIdSet = false;
			for (int j = 0; j < customParams.length; j++) {
				String param = customParams[j];
				if (param.equalsIgnoreCase("-runlog")) {
					script.append("-runlog \"$LOGDIR/logs/"+envVars.expand(customParams[j+1])+"\" ");
					customRunLogSet = true;
					j++;
				} else if (param.equalsIgnoreCase("-runid")) {
					script.append("-runid \""+ envVars.expand(customParams[j+1])+"\" ");
					customRunIdSet = true;
					j++;
				} else if (param.startsWith("-report")) {
					if (reportParamHasValue(param)) {
						j++;
					}
				} else {
					script.append(envVars.expand(customParams[j])+" ");
				}
			}
			if (!customRunLogSet) {
				script.append("-runlog \"$LOGDIR/logs/log_+b\" ");
			}
			if (!customRunIdSet) {
				script.append("-runid \"$JOB_NAME-$BUILD_NUMBER-+y+M+d+h+m+s\" ");
			}
			script.append("$CURDIR/$SUITE");
			script.append(i++);
			script.append("\n");
		}
	}

	private void addDaemonParamsIfNeeded() {
		if (daemonSelected) {
			script.append("-calldaemon -daemonhost ");
			script.append(daemonhost);
			script.append(" -daemonport ");
			script.append(daemonport);
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
			String[] customParams = s.getCustomParam().split(" ");
			for (int j = 0; j < customParams.length; j++) {
				String param = customParams[j];
				if (param.equalsIgnoreCase("-report.html")) {
					script.append( param+" "+envVars.expand(customParams[j+1])+" ");
					customreportHTML = true;
					j++;
				} else if (param.equalsIgnoreCase("-report.junit")) {
					script.append( param+" "+envVars.expand(customParams[j+1])+" ");
					customreportJUnit = true;
					j++;
				} else {
					script.append(envVars.expand(customParams[j])+" ");
				}
			}	
		}
		
		if (!customreportHTML) {
			script.append("-report.html \"$LOGDIR/html\" ");
		}
		if (!customreportJUnit) {
			script.append("-report.junit \"$LOGDIR/junit\" ");
		}
		script.append("\"$LOGDIR/logs\"\n");
	}

	/**
	 * @see setMark()
	 */
	private void setMarkShell() {
		script.append("touch $LOGDIR/deleteMark\n");
		script.append("cd $CURDIR\n");
	}
}
