package com.cloudbees.plugins.flow

import groovy.transform.Synchronized
import hudson.security.ACL
import org.acegisecurity.context.SecurityContextHolder

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class FlowGraphExecutor {
    private final FlowDelegate flowDSL
    private final FlowGraph graph

    private final runningBuilds = new HashSet<String>()
    private final waitingBuilds = new HashSet<String>()
    private final completed = new ArrayList<String>()

    private final pool = Executors.newCachedThreadPool()

    private boolean started = false

    FlowGraphExecutor(FlowDelegate flowDSL, FlowGraph graph) {
        this.flowDSL = flowDSL
        this.graph = graph
    }

    def execute(String... startVertices) {
        if (!started) {
            started = true
            flowDSL.println("starting a graph base build for the following graph: " + graph.toString())
            flowDSL.println("the build will start from the following vertices: " + startVertices)

            startVertices.each {vertex ->
                waitingBuilds.add(vertex)
                startWaitingBuilds()
            }

            waitForCompletionAndShutdown()
        }
    }

    @Synchronized
    def onBuildCompleted(JobInvocation jobInvocation) {
        completed.add(jobInvocation.name)
        runningBuilds.remove(jobInvocation.name)
        graph.getOutgoingEdgesOf(jobInvocation).each { edge -> waitingBuilds.add(edge.target) }

        startWaitingBuilds()
    }

    private def startWaitingBuilds() {
        def buildsToStart = waitingBuilds.findAll { waitingBuild -> hasNoRunningParent(waitingBuild) }

        buildsToStart.each { buildToStart ->
            if (graph.isNotChildOfAny(buildToStart, buildsToStart)) {
                build([:], buildToStart)
            }
        }
    }

    @Synchronized
    def onBuildStart(String jobName) {
        runningBuilds.add(jobName)
        waitingBuilds.remove(jobName)
    }

    def hasNoRunningParent(String waitingBuild) {
        graph.isNotChildOfAny(waitingBuild, runningBuilds)
    }

    @Override
    def String toString() {
        return underlying.toString()
    }

    private def build(Map args, String jobName) {
        def currentState = flowDSL.flowRun.state
        Closure<JobInvocation> track_closure = {
            def ctx = ACL.impersonate(ACL.SYSTEM)
            try {
                flowDSL.flowRun.state = new FlowState(currentState, graph)
                onBuildStart(jobName)
                def jobInvocation = flowDSL.build(args, jobName)
                onBuildCompleted(jobInvocation)
                jobInvocation
            } catch (Exception e) {
                throw e;
            }
            finally {
                SecurityContextHolder.setContext(ctx)
            }
        }

        pool.submit(track_closure as Callable<JobInvocation>)
    }

    private def isCompleted() {
        runningBuilds.isEmpty() && waitingBuilds.isEmpty()
    }

    private def waitForCompletionAndShutdown() {
        while(!isCompleted()) {
            Thread.sleep(1000)
        }

        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.DAYS)

        flowDSL.println("the build has been completed")
    }



}
