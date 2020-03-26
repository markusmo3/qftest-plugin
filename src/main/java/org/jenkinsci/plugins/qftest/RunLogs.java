//import org.jenkinsci.plugins.qftest.Suites;
package org.jenkinsci.plugins.qftest;

public class RunLogs extends Suites {

    public RunLogs(String customParam) {
        super("", customParam);
    }

    @Override
    protected String directorySearchString() {
        return "*.q??";
    }
}
