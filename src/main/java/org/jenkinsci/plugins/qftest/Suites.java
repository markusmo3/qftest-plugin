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

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author QFS, Sebastian Kleber
 */
public class Suites extends AbstractDescribableImpl<Suites> {

	private final String suitename;
	private final String customParam;

	/**
	 * CTOR
	 * 
	 * @param suitename
	 *            Name of the testsuite or folder containing suites
	 * @param customParam
	 *            Command Line Arguments
	 */
	@DataBoundConstructor
	public Suites(String suitename, String customParam) {
		this.suitename = suitename;
		this.customParam = customParam;
	}

	/**
	 * Returns the name of the testsuite or a folder containing suites.
	 * 
	 * @return name of the testsuite or folder containing suites
	 */
	public String getSuitename() {
		return suitename;
	}

	/**
	 * Returns the command line arguments.
	 * 
	 * @return command line arguments
	 */
	public String getCustomParam() {
		return customParam;
	}

	/**
	 * Implementation of descriptor
	 */
	@Extension
	public static class DescriptorImpl extends Descriptor<Suites> {
		/*
		 * (non-Javadoc)
		 * 
		 * @see hudson.model.Descriptor#getDisplayName()
		 */
		public String getDisplayName() {
			return "";
		}
	}
}
