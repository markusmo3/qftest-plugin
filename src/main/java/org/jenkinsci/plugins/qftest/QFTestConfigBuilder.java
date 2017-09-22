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
import hudson.Extension;
import hudson.Launcher;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;

import java.io.IOException;
import java.util.ArrayList;

import net.sf.json.JSONObject;

import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.StaplerRequest;

/**
 *
 * This class saves all the configurations made by the user and executes a
 * script created by the ScriptCreator Class when a build is performed
 * 
 * @author QFS, Sebastian Kleber
 */
public class QFTestConfigBuilder extends Builder {

	private boolean suitesEmpty;
	private final boolean customReportTempDirectory;
	private final boolean specificQFTestVersion;
	private final boolean daemonSelected;
	private final ArrayList<Suites> suitefield;
	private final String daemonhost;
	private final String daemonport;
	private final String customPath;
	private final String customReports;

	// Constructor gets called when the user saves the job configuration.
	// config.jelly sends the parameters
	/**
	 * CTOR
	 * 
	 * @param suitefield
	 *            Contains name of testsuites and their command line arguments
	 * @param customPath
	 *            Path to specific QF-Test directory for a job
	 * @param customReports
	 *            Reports will now be saved in WORKSPACE/customReports instead
	 *            of WORKSPACE/qftestJenkinsReports
	 * @param daemon
	 *            Contains daemonhost and daemonport
	 */
	@DataBoundConstructor
	public QFTestConfigBuilder(JSONObject[] suitefield, JSONObject customPath,
			JSONObject customReports, JSONObject daemon) {
		// If specific QF-Test version is selected, save the path
		if (customPath != null && !customPath.getString("path").isEmpty()) {
			specificQFTestVersion = true;
			this.customPath = customPath.getString("path");
		} else {
			specificQFTestVersion = false;
			this.customPath = "";
		}
		// If the folder for temporary reports got renamed, save the new name
		if (customReports != null
				&& !customReports.getString("directory").isEmpty()) {
			customReportTempDirectory = true;
			this.customReports = customReports.getString("directory");
		} else {
			customReportTempDirectory = false;
			this.customReports = "";
		}
		// If the job should be run on a daemon, save the host and port
		if (daemon != null && !daemon.getString("daemonhost").isEmpty()
				&& !daemon.getString("daemonport").isEmpty()) {
			daemonSelected = true;
			this.daemonhost = daemon.getString("daemonhost");
			this.daemonport = daemon.getString("daemonport");
		} else {
			daemonSelected = false;
			this.daemonhost = "";
			this.daemonport = "";
		}
		// For every field the user added, a new class Suites will be created
		// with the name of the suite/folder and custom parameters
		this.suitefield = new ArrayList<Suites>();
		Suites suite;
		if (suitefield != null) {
			suitesEmpty = false;
			for (JSONObject obj : suitefield) {
				if (obj.getString("suitename").isEmpty()) {
					suitesEmpty = true;
				}
				suite = new Suites(obj.getString("suitename"),
						obj.getString("customParam"));
				this.suitefield.add(suite);
			}
		} else {
			this.suitesEmpty = true;
		}
	}

	// Jelly is able to get the attributes via ${instance.attributename}
	/**
	 * Returns the specified QF-Test installation path.
	 * 
	 * @return specific QF-Test installation path for this job
	 */
	public String getCustomPath() {
		return customPath;
	}

	/**
	 * Returns the report directory.
	 * 
	 * @return name of custom report directory for this job
	 */
	public String getCustomReports() {
		return customReports;
	}

	/**
	 * Whether the temporary generated reports should be stored in a custom
	 * directory
	 * 
	 * @return true - if "Change temporary directory for reports" is selected,
	 *         false otherwhise
	 */
	public boolean getCustomReportTempDirectory() {
		return customReportTempDirectory;
	}

	/**
	 * Whether to use a specific QF-Test version
	 * 
	 * @return true - if "Use specific QF-Test version" is selected, false
	 *         otherwhise
	 */
	public boolean getSpecificQFTestVersion() {
		return specificQFTestVersion;
	}

	/**
	 * Whether tests should be run on a daemon or local.
	 * 
	 * @return true - if "Run test-suites on daemon" is selected, false
	 *         otherwhise
	 */
	public boolean getRunOnDaemonSelected() {
		return daemonSelected;
	}

	/**
	 * Returns the defined daemonhost.
	 * 
	 * @return daemonhost
	 */
	public String getDaemonhost() {
		return daemonhost;
	}

	/**
	 * Returns the defined daemonport.
	 * 
	 * @return daemonport
	 */
	public String getDaemonport() {
		return daemonport;
	}

	/**
	 * Returns an ArrayList containing all names of test-suites and command line
	 * arguments
	 * 
	 * @return ArrayList with test-suite names and command line arguments
	 */
	public ArrayList<Suites> getSuitefield() {
		return suitefield;
	}

	@Override
	@SuppressWarnings("rawtypes")
	public boolean perform(AbstractBuild build, Launcher launcher,
			BuildListener listener) {
		// If there are no suites added, or have an empty textfield, fail the
		// build
		if (suitesEmpty) {
			listener.getLogger()
					.println(
							"[qftest plugin] ERROR: No suites were added to be run by this "
									+ "plugin or a textfield for the suitename is empty");
			return false;
		}
		// If the name of the folder for the temporary Reports contains any
		// illegal characters, fail the build
		if (customReports.contains(":") || customReports.contains("*")
				|| customReports.contains("?") || customReports.contains("<")
				|| customReports.contains("|") || customReports.contains(">")) {
			listener.getLogger()
					.println(
							"[qftest plugin] ERROR: The name you"
									+ " set for the temporary reports contains one or more of these"
									+ " illegal characters: * ? < > | :");
			return false;
		}
		// get envvars
		EnvVars envVars = new EnvVars();
		try {
			envVars = build.getEnvironment(launcher.getListener());
		} catch (IOException e) {
			listener.getLogger().println("[qftest plugin] ERROR: Can't read EnvVars" + e);
		} catch (InterruptedException e) {
			listener.getLogger().println("[qftest plugin] ERROR: Can't read EnvVars" + e);
		}
		// Create a new script with the correct config
		ScriptCreator script = new ScriptCreator(suitefield, getDescriptor()
				.getQfPath(), getDescriptor().getQfPathUnix(),
				specificQFTestVersion, customReportTempDirectory,
				daemonSelected, customPath, customReports, daemonhost,
				daemonport, launcher.isUnix(), envVars, listener, build);
		try {
			return script.getScript().perform(build, launcher, listener);
		} catch (InterruptedException e) {
			listener.getLogger().println(
					"[qftest plugin] ERROR: " + "Couldn't perform build: " + e);
			return false;
		}
	}

	// Descriptor is needed to access global variables
	/*
	 * (non-Javadoc)
	 * 
	 * @see hudson.tasks.Builder#getDescriptor()
	 */
	@Override
	public DescriptorImpl getDescriptor() {
		return (DescriptorImpl) super.getDescriptor();
	}

	/**
	 * Implementation of descriptor
	 */
	@Extension
	public static final class DescriptorImpl extends
			BuildStepDescriptor<Builder> {

		private String qfPath;
		private String qfPathUnix;

		public DescriptorImpl() {
			load();
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.tasks.BuildStepDescriptor#isApplicable(java.lang.Class)
		 */
		@Override
		@SuppressWarnings("rawtypes")
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			// Indicates that this builder can be used with all kinds of project
			// types
			return true;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		@Override
		public String getDisplayName() {
			return "Run QF-Test";
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * hudson.model.Descriptor#configure(org.kohsuke.stapler.StaplerRequest,
		 * net.sf.json.JSONObject)
		 */
		@Override
		public boolean configure(StaplerRequest req, JSONObject formData)
				throws FormException {

			qfPath = formData.getString("qfPath");
			qfPathUnix = formData.getString("qfPathUnix");

			save();
			return super.configure(req, formData);
		}

		/**
		 * Returns the defined QF-Test installation path (Windows) in the global
		 * setting.
		 * 
		 * @return path to QF-Test installation (Windows)
		 */
		public String getQfPath() {
			return qfPath;
		}

		/**
		 * Returns the defined QF-Test installation path (Unix) in the global
		 * setting.
		 * 
		 * @return path to QF-Test installation (Unix)
		 */
		public String getQfPathUnix() {
			return qfPathUnix;
		}
	}
}
