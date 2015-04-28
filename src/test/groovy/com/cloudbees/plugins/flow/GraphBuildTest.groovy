/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Nicolas De Loof.
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

package com.cloudbees.plugins.flow
import hudson.model.Job
import hudson.model.ParametersAction
import hudson.model.StringParameterValue
import jenkins.model.Jenkins

import static hudson.model.Result.FAILURE
import static hudson.model.Result.SUCCESS

class GraphBuildTest extends DSLTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        Jenkins.getInstance().setQuietPeriod(0)
    }

    public void testGraphWithChainedEdges() {
        // given
        def jobs = createJobs(["job1", "job2", "job3"])

        // when
        def flow = run("""
            def jobsGraph = graph().addEdge("job1", "job2").addEdge("job2", "job3").withStartJobs(["job1"])
            build(jobsGraph)
        """)

        // then
        assertAllSuccess(jobs)
        assert SUCCESS == flow.result
    }

    public void testGraphWithNoEdges() {
        // given
        def jobs = createJobs(["job0", "job1", "job2"])

        // when
        def flow = run("""
            def jobsGraph = graph()
                .addVertex("job0")
                .addVertex("job1")
                .addVertex("job2")
                .withStartJobs(["job0", "job1"])
            build(jobsGraph)
        """)

        // then
        assertAllSuccess([jobs[0], jobs[1]])
        assert SUCCESS == flow.result
    }

    public void testGraphWithParallelEdges() {
        // given
        def jobs = createJobs(["job1", "job2", "job3"])

        // when
        def flow = run("""
            def jobsGraph = graph().withEdges(["job1", "job2"], ["job1", "job3"]).withStartJobs(["job1"])
            build(jobsGraph)
        """)

        // then
        assertAllSuccess(jobs)
        assert SUCCESS == flow.result
    }

    public void testGraphWithBranches() {
        // given
        def jobs = createJobs(["job1", "job2", "job3", "job4", "job5", "job6"])

        // when
        def flow = run("""
            def jobsGraph = graph(
                    ["job1", "job2"],
                    ["job1", "job3"],
                    ["job2", "job3"],
                    ["job3", "job4"],
                    ["job2", "job5"],
                    ["job1", "job6"],
                    ["job5", "job6"]).withStartJobs(["job1"])
            build(jobsGraph)
        """)
        // 1 -> 2 -> 3, 5 -> 4,6

        // then
        assertAllSuccess(jobs)
        assert SUCCESS == flow.result
        println flow.jobsGraph.edgeSet()
    }

    public void testStartJobFromBuildParam() {
        // given
        def jobs = createJobs(["job1", "job2", "job3"])
        def params = new ParametersAction(new StringParameterValue("INIT_JOB", "job1"))

        // when
        def flow = runWithParams("""
            build(graph(["job1", "job2"], ["job2", "job3"]).withStartJobs([params["INIT_JOB"]]))
        """, params)

        // then
        assertAllSuccess(jobs)
        assert SUCCESS == flow.result
    }

    public void testMultipleStartJobFromBuildParam() {
        // given
        def jobs = createJobs(["job0", "job1", "job2", "job3"])
        def params = new ParametersAction(new StringParameterValue("INIT_JOBS", "job1,job2,job3"))

        // when
        def flow = runWithParams("""
            def mustJobs = params["INIT_JOBS"].split(",").toList()
            build(graph(["job0", "job1"], ["job0", "job2"], ["job0", "job3"], ["job1", "job2"]).withStartJobs(mustJobs))
        """, params)

        // then
        assertAllSuccess([jobs[1], jobs[2], jobs[3]])
        assertAllRunOnce([jobs[1], jobs[2], jobs[3]])
        assertDidNotRun(jobs[0])
        assert SUCCESS == flow.result
    }

    public void testMultipleStartJobs() {
        // given
        def jobs = createJobs(["job0", "job1", "job2", "job3"])

        // when
        def flow = run("""
            build(graph(["job0", "job2"], ["job1", "job3"]).withStartJobs(["job0", "job3"]))
        """)

        // then
        assertAllSuccess([jobs[0], jobs[2], jobs[3]])
        assertDidNotRun(jobs[1])
        assert SUCCESS == flow.result
    }

    public void testBlankStartingJob() {
        // given
        def jobs = createJobs(["job0", "job1"])

        // when
        def flow = run("""
            build(graph(["job0", "job1"]).withStartJobs([""]))
        """)

        // then
        assertDidNotRun(jobs[0])
        assertDidNotRun(jobs[1])
        assert SUCCESS == flow.result
    }

    public void testMultipleStartJobs1() {
        // given
        def jobs = createJobs(["job0", "job1", "job2", "job3"])

        // when
        def flow = run("""
            build(graph(["job0", "job2"], ["job1", "job3"]).withStartJobs(["job1", "job3"]))
        """)

        // then
        assertAllSuccess([jobs[1], jobs[3]])
        assertDidNotRun(jobs[0])
        assertDidNotRun(jobs[2])
        assert SUCCESS == flow.result
    }

    public void testMultipleStartJobsWithParentChildRelation() {
        // given
        def jobs = createJobs(["job0", "job1", "job2", "job3"])

        // when
        def flow = run("""
            build(graph(["job0", "job1"], ["job0", "job2"], ["job1", "job3"]).withStartJobs(["job0", "job1"]))
        """)

        // then
        assertAllSuccess(jobs)
        assertAllRunOnce(jobs)
        assert SUCCESS == flow.result
    }

    public void testGraphLoadedFromURLString() {
        // given
        def URL graphURL = FlowGraphTest.class.getClassLoader().getResource("test-graph.properties")

        // given
        def jobs = createJobs(["job0", "job1", "job2", "job3", "job4"])

        // when
        def flow = run("""
            def jobsGraph = graph("${graphURL.toString()}").withStartJobs(["job2"])
            build(jobsGraph)
        """)

        // then
        assertAllSuccess([jobs[0], jobs[1], jobs[2]])
        assertAllRunOnce([jobs[0], jobs[1], jobs[2]])
        assertDidNotRun(jobs[3])
        assertDidNotRun(jobs[4])
        assert SUCCESS == flow.result
    }

    public void testBuildWithFailingJob() {
        // given
        def Job[] jobs = createJobs(["job0", "job1"])
        def willFail = createFailJob("willFail")

        // when
        def flow = run("""
            build(graph(["job0", "willFail"], ["willFail", "job1"]).withStartJobs(["job0"]))
        """)

        // then
        assertFailure(willFail)
        assert FAILURE == flow.result
        assertAllSuccess(jobs[0])
        assertDidNotRun(jobs[1])
    }

    public void testGraphWithBuildParams() {
        // given
        def jobs = createJobs(["job1", "job2"])

        // when
        def flow = run("""
            def bParams = [GRAPH_PARAM_1: "gparam1"]
            def jobsGraph = graph().addEdge("job1", "job2")
                .withParams(bParams)
                .withStartJobs(["job1"])
            build(jobsGraph)
        """)

        // then
        def build1 = assertAllSuccess(jobs[0])
        assertHasParameter(build1, "GRAPH_PARAM_1", "gparam1")

        // then
        def build2 = assertAllSuccess(jobs[1])
        assertHasParameter(build2, "GRAPH_PARAM_1", "gparam1")

        assert SUCCESS == flow.result
    }

    public void testNonExistingStartJobShouldBeIgnored() {
        // given
        def jobs = createJobs(["job0", "job1"])

        // when
        def flow = run("""
            def jobGraph = graph(["job0", "job1"]).withStartJobs(["job0", "non-existing"])
            build(jobGraph)
        """)

        // then
        assertAllSuccess([jobs[0], jobs[1]])
        assert SUCCESS == flow.result
    }

    public void testSuccessListener() {
        // given
        def jobs = createJobs(["job0", "job1", "job2"])

        // when
        def flow = run("""
            def bParams = [count:0]
            def jobsGraph = graph(["job0", "job1"], ["job1", "job2"])
                .withParams(bParams)
                .withStartJobs(["job0"])
                .onBuildSuccess({j -> bParams[j.name] = j.buildNumber})
                .onBuildSuccess({j -> bParams["count"] = bParams["count"] + 1})

            build(jobsGraph)
        """)

        // then
        def builds = assertAllSuccess([jobs[0], jobs[1], jobs[2]])

        // and
        assertHasParameter(builds[1], "job0", "1")
        assertHasParameter(builds[1], "count", "1")

        // and
        assertHasParameter(builds[2], "job0", "1")
        assertHasParameter(builds[2], "job1", "1")
        assertHasParameter(builds[2], "count", "2")

        assert SUCCESS == flow.result
    }

    public void testNonExistingJobsShouldBeIgnored() {
        // given
        def jobs = createJobs(["job0", "job1"])

        // when
        def flow = run("""
            build(graph(["job0", "job1"], ["job0", "non-existing-job"]).withStartJobs(["job0"]))
        """)

        // then
        assert SUCCESS == flow.result
        assertAllSuccess(jobs)
    }

    public void testFailedJobAbortsAllRunningJobs() {
        // given
        def Job[] jobs = createJobs(["job0"])
        def willFail = createFailJob("willFail")
        def delayJob = createDelayedJob("delayJob", 2000)
        def slowJob = createDelayedJob("slowJob", 12000)

        // when
        def flow = run("""
            build(graph(["job0", "delayJob"], ["job0", "slowJob"], ["delayJob", "willFail"]).withStartJobs(["job0"]))
        """)

        // then
        assert FAILURE == flow.result
        assertAllSuccess([jobs[0], delayJob])
        assertFailure(willFail)
        assertAborted(slowJob)
    }
}
