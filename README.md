Deprecated
==========

You should consider the pipeline plugin as a replacement as it is actively maintained:
* https://wiki.jenkins-ci.org/display/JENKINS/Pipeline+Plugin
* https://github.com/jenkinsci/pipeline-plugin


Jenkins Build Flow Plugin
=========================

Fork of https://github.com/jenkinsci/build-flow-plugin with additional DSL for defining a flow in form of a directional
graph where vertices defines jobs to run and edges represents dependencies between jobs. It only works for non cyclic 
graphs.

### Usage

#### Start build from the root job
Assume a graph ```job0 -> job1 -> job2``` and we want to start from root ```job0```
```
build(graph(["job0", "job1"], ["job1", "job2"]), "job0")
```

#### Start build from a non root job
2nd argument to the build method represents a job(s) to start build from (all jobs on the right side of those jobs
will not be executed)
```
build(graph(["job0", "job1"], ["job1", "job2"]), "job1")
```

#### Read start job from a build param
Assume there is build param ```START_JOB= "job2"```
```
build(graph(["job0", "job1"], ["job1", "job2"]), getParamValue("START_JOB"))
```

#### Read start jobs from a build list parameter
Assume there is a build param ```START_JOBS="job1,job2"```
```
build(graph(["job0", "job1"], ["job1", "job2"]), getParamValues("START_JOBS"))
```

#### Load a graph definition from java property file

Assume there is a property file at ```http://jenkins.example.com/example-graph.properties``` location with
the following content where key is a source(upstream) job and value is comma separated list of target(downstream) jobs:

```
job0=job1,job2,job5
job2=job3
job5=job6,job7
```

then it can be used to create a graph like this:

```
build(graph("http://jenkins.example.com/example-graph.properties"), "job5")
```
