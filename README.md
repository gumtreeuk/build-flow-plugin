Jenkins Build Flow Plugin
=========================

Fork of https://github.com/jenkinsci/build-flow-plugin with additional DSL for defining a flow in form of a directional
graph where vertices defines jobs to run and edges represents dependencies between jobs. It only works for non cyclic
graphs.

### Gumtree Release

Push your changes and build a new version it using http://jenkins.qa.gt.ecg.so/view/All/job/jenkins-build-flow-plugin/.

### Usage

#### Start a build from the root job
Assume a graph ```job0 -> job1 -> job2``` and we want to start from the root ```job0```
```
build(graph(["job0", "job1"], ["job1", "job2"]), ["job0"])
```

#### Start a build from a non root job
2nd argument to the build method represents a job(s) to start build from (all jobs on the left side of those jobs
will not be executed)
```
build(graph(["job0", "job1"], ["job1", "job2"]), ["job1"])
```

#### Start a build from a non root job but force build on parent jobs(upstream jobs)
3nd argument to the build method represents a job(s) that we would like to build if any of the start job depends on them. 
Jobs that are not required by start jobs will be ignored. 
```
def mustJobs = ["j2"]
def shouldJobs = ["j0"]
def dGraph = graph(["jRoot", "j0"], ["j0", "j1"], ["j0", "jOut"], ["j1", "j2"], ["j2", "j3"])
build(dGraph, mustJobs, shouldJobs)
```

will build j0,j1,j2,j3 - for each of them there is a path from/to j2 
will not build jOut - there no path from/to j2
will not build jRoot 
    - is not a node on any path from any of the should build job to any of the must build job
    - it is parent of j2 but its build is not force because it's not in the shouldBuild jobs list 

#### Read a start job from a build param
Assume there is build param ```START_JOB= "job2"```
```
build(graph(["job0", "job1"], ["job1", "job2"]), [params["START_JOB"]])
```

#### Read start jobs from a build list parameter
Assume there is a build param ```START_JOBS="job1,job2"```
```
def mustJobs = params["START_JOBS"].split(",").toList()
build(graph(["job0", "job1"], ["job1", "job2"]), mustJobs)
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
build(graph("http://jenkins.example.com/example-graph.properties"), ["job5"])
```
