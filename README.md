[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/qftest.svg)](https://plugins.jenkins.io/qftest)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/qftest-plugin.svg?label=changelog)](https://github.com/jenkinsci/qftest-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/qftest.svg?color=blue)](https://plugins.jenkins.io/qftest)

# The QF-Test Jenkins plugin

QF-Test can extensively controlled via command line options, which allows its integration in various Continous Integration and Testing frameworks.
Because of this, QF-Test can easily be integrated in a Jenkins pipeline via a sh statement (with the command line parameters chosen appropriatley).
In most scenarios, this plugin makes it even easier to perform GUI tests on your application within a jenkins build pipeline:


## Configuration

The QF-Test plugin supports both: Traditional *Freestyle Projects* and the newer *Pipeline concept*.
Apart from this config, the helptags shown on the Freestyle Project configuration page might help to understand the configuration options.
For the same reason, we recommend the usage of the [snippet generator](https://jenkins.io/doc/book/pipeline/getting-started/#snippet-generator) when developping a new build pipeline.


### Example configuration

This section describes a sample configuration, which should just work in most scenarios:

We assume a QF-Test installation in `/opt/qftest-latest` and state it explicitly (which is not always needed, cf. the [QF-Test binary location](#qf-test-binary-location) for the details). Our testsuites to be run are placed in a folder names `suites` in our Jenkins workspace.
Assume that the suite `suites/special/suite.qft` requires special command line arguments  when run (in our example the `-verbose` flag) and therefore goes in a separate group (as discussed in the [Input](#input-control) section).


This setup translates to the following build step in a Freestyle project.

 ![](Screenshot_settings.png)

The corresponding pipeline step would look like this:

```
	QFTest (
		customPath: '/opt/qftest-latest/bin/qftest'
		suitefield: [
			[customParam: '', suitename: 'suites/*.qft' ],
			[customParam: '-verbose', suitename: 'suites/special/suite.qft' ]
		],
	)
```

This settings suffice to integrate QF-Test in a jenkins project, the (reference)[#reference] describes additinal configuration tweaks.

### Reference

The following tables give a more extensive overview of all possible configuration options of the QF-Test step.

#### QF-Test binary location
In order to call QF-Test during a Jenkins build, Jenkins must know the location to your QF-Test installation.
It can be specified on the global configuration page (Jenkins -> Manage Jenkins) for windows and unix machines separately. If not set, the `PATH` variable is queried.

 Finally, the QF-Test installation can also be set individually for each QF-Test build step. This is done by the `customPath` attribute (Configure QF-Test binary option in a Freestyle project) which will always take precedence.


#### General config options

| Parameter | Optional | Desciption |
| --- | --- | --- |
| customPath | yes | Path to QF-Test executable. Specify this to override [global settings](#qf-test-binary-location). |
| reportDirectory | yes | Directory in which QF-Test output files are stored in. The [directory structure is explained here](#report_directory_structure)


#### Input control
The `suitefield` list defines against which suites and under which conditions the SUT is tested. The list contains arbitrary many (but at leaQFst one) independent entries. Each list entry in turn is a map with two keys:

| Key | Optional | Desciption |
| --- | --- | --- |
| customParam | no | Additional command line parameters passed to QF-Test for test execution. Due to its presets, the plugin produces meaningful result even when this field is left empty (`""`) |
| suitename | no | Test suite(s) to be run. [Ant-style globbing](https://ant.apache.org/manual/dirtasks.html) is supported. If the expression resolves to a directory, all test suites found in the hiearchy below the given starting point will be considered. |

For a more fine-grained control which suites and test cases to run, use the "__-suitesfile__" as very last argument of the `customParam` field. The suitename field then specifies the path to the suitesfile.


#### Result control

The most severe "dirty" QF-Test outcome (within this order: warnings, errors, exceptions, unrecoverable failures during QF-Test call) will be mapped to a corresponding Jenkins build result. This mapping can be customized:

| Key | Optional | Default value |
| --- | --- | --- |
onTestWarning  | yes | SUCCESS |
onTestError	| yes | FAILURE |
onTestException | yes | FAILURE |
onTestFailure | yes | FAILURE |


#### Report directory structure

Using a well defined runlog directory is needed to identify the produced run logs. It also sets up a consistent interface to other Jenkins plugins:

The general structure is as follows:<br/>
* `<reportDirectory>/qrz`: QF-Test run logs. They are automatically attached to the current Jenkins build.
* `<reportDirectory>/html`: QF-Test html report. Internally, its processed further by the Jenkins `publishHTML` plugin.


## The QF-Test build step

When run, the QF-Test build step tests your SUT by means of all testsuites referred to by the suitelistst. Runlogs that are produced during the Jenkins build will be marked as build artifacts and listed as such on the build summary page. In addition, a QF-Test report is generated covering all tests that have been run.

![](./Screenshot_result.png)

*Screenshot of the build summary page. The highlighted lins lead to the report and the runlogs that are generated during the QF-Test build step*

Finally, the under [result control](#result-control) presented mechanism determines how outcomes of the QF-Test run influence the final Jenkins build result.


### Debugging and further development
The actual test execution (i.e. the current Jenkins build) can be monitored via the `Console Log` facility.
Among other status information, the QF-Test program calls that were generated behind the scenes by the Jenkins plugin should appear here.

If your build process finshes with unexpected or even none results, this log provides additional information for debugging.

If the QF-Test application scenario within your Jenkins environment is beyond the scope of this plugin, the generated programm calls shown in the log can still be used as a starting point for your own developments. The adapted command lines can then be invoked directly via the `sh` build step (effectively replacing the QF-Test Jenkins plugin step.)



# Pipeline examples
The following scripts shows two pipeline use-cases that might be a good starting point when developping your own pipeline project.

Each of them forms a complete example, written in [__declarative syntax__](https://jenkins.io/doc/book/pipeline/syntax/).

### Minimal

The code below contains a complete -- while minimal -- pipeline invoking QF-Test. We work with a persistent workspace (replace `@WORKSPACE` with your actual workspace. After *building* of your project (if needed), the *QF-Test* `stage` will run all testsuites that can be found within or in any subfolder of the folder _suites_.)

If not otherwise configured, you still have to specify the path of your QF-Test installation.

QF-Test itself will be run verbosely.

```
pipeline {
  agent {
    node {
      customWorkspace '@WORKSPACE@'  
      label 'master'
    }
  }
  stages {

    /*
   stage('Building') {
     //Build your project here
    }
    */

    stage('QF-Test') {
      steps {
        QFTest ([
           //customPath: '@PATH-TO-QF-TEST-INSTALLATION' /*if required*/
           [customParam: '-verbose', suitename: 'exampleTestSuite.qft']
          ])
      }
    }

  }
}
```

### Parallel test execution
In this scenario we delegate the actual testing to *working nodes* (which requires the nodes to be correctly configured) and registered at the jenkins master (labeled here as *executor*).

Test suites are grabbed from the jenkins master (located there again in the suites folder) and deployed on the executors by means of jenkins stash mechanism.


```
pipeline {
  agent none
  stages {

    stage('grapSuites') {
      agent {
        node {
          customWorkspace '@WORKSPACE@/suites'
          label 'master'
        }
      }
      steps {
        stash 'suites-stash'
      }
    }

    stage('QF-Test') {
      agent {
        node {
          label 'agent'
        }
      }
      steps {
        unstash 'suites-stash'
        QFTest ([
           //customPath: '@PATH-TO-QF-TEST-INSTALLATION' /*if required*/
          [customParam: '', suitename: '.']
        ])
      }
      post {
        always {
          dir('suites') {
            deleteDir()
          }
        }
      }
    }

  }
}

```
In the end the pipeline performs a cleanup of the node workspace.


### Further steps
Instead of persistently storing the testsuites in a dedicated folder and pointing the jenkins workspace to it, the testsuites could be checked out of version control system on demand. For this, jenkins offers the more general `checkout` or simply the `git` step.


# Changelog
## v2.0
This version brings an overall renewal of the QF-Test jenkins plugin. Its primary goal is to proivide an **out-of the box integration** of QF-Test within Jenkins builds including **pipeline projects**. On the contrary, support for too specialized use-cases has been dropped:

* Full support of Jenkins pipeline mode
* The QF-Test influence on the general Jenkins build result is know configurable
* Consistency fixes when handling multiple independent suite name / parameter pairs
* No post steps invocations are required any more. The entire functionality is now provided by the plugin itself:
	* QF-Test execution including report creation
	* Publishing of run logs and the test report on the Jenkins build result page
	* Setting the Jenkins build result
* **Breaking change**: The default location of the QF-Test log directory changed. (This will also break legacy configurations with the now obsolete post build steps relaying on explicit log path)
* **Breaking change**: Direct support for QF-Test runs in *daemon mode* has been dropped. It can still be configured via the `customParam` option. In an Jenkins build environment we recommend the usage of *Jenkins agents* instead.
