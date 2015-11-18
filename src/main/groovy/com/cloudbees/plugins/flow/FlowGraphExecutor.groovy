package com.cloudbees.plugins.flow
import com.cloudbees.plugins.extras.ColoredNote
import com.google.common.collect.Lists
import groovy.transform.Synchronized
import hudson.console.HyperlinkNote
import hudson.model.Result
import hudson.security.ACL
import org.acegisecurity.context.SecurityContextHolder

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FlowGraphExecutor {
    private final FlowDelegate flowDSL

    /**
     * Graph defining dependencies between jobs
     */
    private final FlowGraph graph

    /**
     * Jobs that must be build during the build execution
     */
    private final List<String> mustBuildJobs

    private final runningBuilds = new HashSet<String>()
    private final waitingJobs = new HashSet<String>()
    private final completed = new ArrayList<String>()
    private final failedBuilds = new ArrayList<String>()

    private final pool = Executors.newCachedThreadPool()

    FlowGraphExecutor(FlowDelegate flowDSL, FlowGraph graph) {
        this.flowDSL = flowDSL
        this.graph = graph
        if ("EVERYTHING" == graph.getBuildMode()) {
            this.mustBuildJobs = filterOnlyExistingJobs(Lists.newArrayList(graph.getVertices()))
        } else {
            this.mustBuildJobs = filterOnlyExistingJobs(graph.getStartJobs())
        }
    }

    def execute() {
        if (mustBuildJobs != null && mustBuildJobs.size() > 0) {
            logNotice("Starting a graph base build for the graph: " + graph.toString())
            logNotice("The build will start from " + mustBuildJobs + " vertices")

            buildAll(mustBuildJobs)
        }

        awaitCompletionAndShutdown()
    }

    @Synchronized
    private def buildAll(Collection<String> jobs) {
        addToBuildQueue(jobs)

        def readyToBuild = waitingJobs.findAll { build -> hasNoRunningParent(build) }

        readyToBuild.each { build ->
            if (graph.isNotChildOfAny(build, readyToBuild)) {
                this.build(graph.getParams(), build)
            }
        }
    }

    private def addToBuildQueue(Collection<String> jobs) {
        def validJobs = jobs.findAll { v ->
            !v.trim().isEmpty() && isConnectedToAnyMustBuildJob(v) && !runningBuilds.contains(v) && !completed.contains(v)
        }

        waitingJobs.addAll(validJobs)

        if (waitingJobs.size() == 0 && runningBuilds.size() != 0) {
            logNotice("No waiting builds. Running builds [" + runningBuilds.size() + "]: " + runningBuilds.join(", "))
        } else {
            log("Adding to waiting builds: " + validJobs + ". Waiting builds [${waitingJobs.size()}]: " + waitingJobs.join(", "))
        }
    }

    @Synchronized
    private def handleBuildStart(String jobName) {
        runningBuilds.add(jobName)
        waitingJobs.remove(jobName)
    }

    @Synchronized
    def handleBuildCompleted(JobInvocation jobInvocation) {

        if (Result.SUCCESS != jobInvocation.result) {
            handleBuildFailed(jobInvocation.name, jobInvocation, null)
        } else {
            completed.add(jobInvocation.name)
            logSuccess("Job ${linkToBuild(jobInvocation)} has finished with status " + jobInvocation.result
                    + ". No of completed builds: " + completed.size()
                    + ". No of running builds: " + runningBuilds.size())
            runningBuilds.remove(jobInvocation.name)

            graph.getSuccessListeners().each{ listener -> listener(jobInvocation) }

            if (!failed) {
                def childJobs = graph.getOutgoingEdgesOf(jobInvocation).collect { edge -> edge.target }
                buildAll(childJobs)
            }
        }
    }

    @Synchronized
    def handleBuildFailed(String jobName, JobInvocation jobInvocation, Exception e) {
        def jobNotFoundException = JobNotFoundException.isAssignableFrom(e.getClass())
        if (jobInvocation != null) {
            logError("Job ${linkToBuild(jobInvocation)} has finished with status " + jobInvocation.result)
        } else {
            if (jobNotFoundException) {
                logError("Job $jobName has failed because no job with such name exists. This job will be ingnored.")
            } else {
                logError("Job $jobName has failed. Exception:  " + e)
            }
        }

        runningBuilds.remove(jobName)
        if (!jobNotFoundException) {
            waitingJobs.clear();
            failedBuilds.add(jobName)
            flowDSL.flowRun.state.result = Result.FAILURE
//            abortRunningBuilds(jobName)
            flowDSL.fail()
        }
    }

    private def abortRunningBuilds(String causedByJob) {
        if (runningBuilds != null && !runningBuilds.isEmpty()) {
            for(String name: runningBuilds) {
                def job = flowDSL.flowRun.findJob(name)
                if (job.isPresent()) {
                    logError("Aborting $name because $causedByJob job has failed")
                    job.get().build.executor.interrupt()
                }
            }
        }
    }

    private def build(Map args, String jobName) {
        handleBuildStart(jobName)
        def currentState = flowDSL.flowRun.state
        Closure<JobInvocation> track_closure = {
            def ctx = ACL.impersonate(ACL.SYSTEM)
            def JobInvocation jobInvocation = null
            try {
                flowDSL.flowRun.state = new FlowState(currentState, graph)
                jobInvocation = flowDSL.build(args, jobName)
                handleBuildCompleted(jobInvocation)
                jobInvocation
            } catch (Exception e) {
                handleBuildFailed(jobName, jobInvocation, e)
                throw e;
            }
            finally {
                SecurityContextHolder.setContext(ctx)
            }
        }

        pool.submit(track_closure as Callable<JobInvocation>)
    }

    @Synchronized
    private def isCompleted() {
        runningBuilds.isEmpty() && waitingJobs.isEmpty()
    }

    private def awaitCompletionAndShutdown() {
        while(!isCompleted()) {
            Thread.sleep(1000)
        }

        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.DAYS)

        if (failed) {
            logError("The following builds have failed: " + failedBuilds.join(", "))
            flowDSL.flowRun.state.result = Result.FAILURE
            flowDSL.fail()
        } else {
            logSuccess("The build has been completed")
        }
    }

    private def getFailed() {
        return !failedBuilds.isEmpty()
    }

    def hasNoRunningParent(String job) {
        graph.isNotChildOfAny(job, runningBuilds)
    }

    /**
     * Checks if the job is connected to (is parent or child) any of the must build jobs
     *
     * @param job the jobs to check
     */
    def boolean isConnectedToAnyMustBuildJob(String job) {
        mustBuildJobs.contains(job) || graph.isParentOfAny(job, mustBuildJobs) || graph.isChildOfAny(job, mustBuildJobs)
    }

    private def filterOnlyExistingJobs(Collection<String> jobs) {
        return jobs.findAll { vertex ->
            if (graph.containsVertex(vertex)) {
                return true
            } else {
                logError("${vertex} is unknow job and will be ignored")
            }
        }
    }

    private def linkToBuild(JobInvocation jobInvocation) {
        return HyperlinkNote.encodeTo('/' + jobInvocation.build.getUrl(), jobInvocation.build.project.getName())
//        return jobInvocation.build.getFullDisplayName().replaceAll("#", "")
    }

    private def logError(String message) {
        def text = ColoredNote.redNote("[graph] $message")
        flowDSL.println("${text}")
    }

    private def logNotice(String message) {
        def text = ColoredNote.blueNote("[graph] $message")
        flowDSL.println("${text}")
    }

    private def logSuccess(String message) {
        def text = ColoredNote.greenNote("[graph] $message")
        flowDSL.println("${text}")
    }

    private def log(String message) {
        flowDSL.println("[graph] ${message}")
    }
}
