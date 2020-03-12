package org.jenkinsci.plugins.qftest;

import hudson.FilePath;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class QFTestCommandLineBuilder extends ExtendedArgumentListBuilder {


    public static enum RunMode {
        RUN("-run"),
        GENREPORT("-genreport"),
        GENDOC("-gendoc");

        private final String str;

        RunMode(String str) {
            this.str = str;
        }

        @Override
        public String toString() {
            return str;
        }
    }

    public QFTestCommandLineBuilder(String binary, RunMode aMode) {
        this.add(binary);
        for (RunMode mode : RunMode.values()) {
            if (mode == aMode) {
                this.presetArg(PresetType.ENFORCE, mode.toString());
            } else {
                this.presetArg(PresetType.DROP, mode.toString());
            }
        }
    }

    public QFTestCommandLineBuilder(String binary) {
        this(binary, RunMode.RUN);
    }


    public int addSuiteConfig(FilePath workspace, Suites aSuite) throws IOException, InterruptedException {
        this.addTokenized(aSuite.getCustomParam());
        List<String> suites = aSuite.getExpandedPaths(workspace)
                //.peek(s -> listener.getLogger().println("HERE: " + s))
                .map(p -> p.getRemote())
                .collect(Collectors.toList());
        this.add(suites);
        return suites.size();
    }
}
