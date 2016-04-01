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
            def jobsGraph = graph().addEdge("job1", "job2").addEdge("job2", "job3").withMustBuildJobs(["job1"])
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
                .withMustBuildJobs(["job0", "job1"])
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
            def jobsGraph = graph().withEdges(["job1", "job2"], ["job1", "job3"]).withMustBuildJobs(["job1"])
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
                    ["job5", "job6"]).withMustBuildJobs(["job1"])
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
            build(graph(["job1", "job2"], ["job2", "job3"]).withMustBuildJobs([params["INIT_JOB"]]))
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
            build(graph(["job0", "job1"], ["job0", "job2"], ["job0", "job3"], ["job1", "job2"]).withMustBuildJobs(mustJobs))
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
            build(graph(["job0", "job2"], ["job1", "job3"]).withMustBuildJobs(["job0", "job3"]))
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
            build(graph(["job0", "job1"]).withMustBuildJobs([""]))
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
            build(graph(["job0", "job2"], ["job1", "job3"]).withMustBuildJobs(["job1", "job3"]))
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
            build(graph(["job0", "job1"], ["job0", "job2"], ["job1", "job3"]).withMustBuildJobs(["job0", "job1"]))
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
            def jobsGraph = graph("${graphURL.toString()}").withMustBuildJobs(["job2"])
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
            build(graph(["job0", "willFail"], ["willFail", "job1"]).withMustBuildJobs(["job0"]))
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
                .withMustBuildJobs(["job1"])
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
            def jobGraph = graph(["job0", "job1"]).withMustBuildJobs(["job0", "non-existing"])
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
                .withMustBuildJobs(["job0"])
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
            build(graph(["job0", "job1"], ["job0", "non-existing-job"]).withMustBuildJobs(["job0"]))
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
            build(graph(["job0", "delayJob"], ["job0", "slowJob"], ["delayJob", "willFail"]).withMustBuildJobs(["job0"]))
        """)

        // then
        assert FAILURE == flow.result
        assertAllSuccess([jobs[0], delayJob])
        assertFailure(willFail)
        assertAborted(slowJob)
    }

    public void testChangedDependantAndDependOnJobAreBuild() {
        // given
        // job0 -> job1 -> job3 -> job6
        //              -> job4
        //      -> job2
        // job5
        def jobs = createJobs(["job0", "job1", "job2", "job3", "job4", "job5", "job6"])

        // when
        // I want to build job3
        // + job0 has new changes which I want to use in job3 (so I want to build job2 too)
        // + job5 has changes but I don't need them in job3 (so I don't want to build job5)
        def flow = run("""
            build(graph(["job0", "job1"], ["job0", "job2"], , ["job1", "job3"], ["job1", "job4"], ["job3", "job6"])
                .withMustBuildJobs(["job3"])
                .withModifiedJobs(["job0", "job5"]))
        """)

        // then
        // job0 - has changed AND is dependOn job oj job3 (the must build job) AND buildDependOnJobs is true (default value)
        // job1 - has not changed but is on the path between job0 to job3
        // job3 - is the must build job
        // job6 - has not changed but is dependant of job3 AND buildDependantJobs is true (default value)
        assertAllSuccess([jobs[0], jobs[1], jobs[3], jobs[6]])

        // job2 - has been modified but is not used by any must build job
        // job4 - has not been modified AND is not used by any must build job
        // job5 - has been modified but is not used by any must build job
        assertAllDidNotRun([jobs[2], jobs[4], jobs[5]])
        assert SUCCESS == flow.result
    }

    public void testNoDependantJobAreBuild() {
        // given
        // job0 -> job1 -> job3 -> job6
        //              -> job4
        //      -> job2
        // job5
        def jobs = createJobs(["job0", "job1", "job2", "job3", "job4", "job5", "job6"])

        // when
        // I want to build job3
        // + job0 has new changes which I want to use in job3 (so I want to build job2 too)
        // + job5 has changes but I don't need them in job3 (so I don't want to build job5)
        def flow = run("""
            build(graph(["job0", "job1"], ["job0", "job2"], , ["job1", "job3"], ["job1", "job4"])
                .withBuildDependantJobs(false)
                .withMustBuildJobs(["job1", "job3"])
                .withModifiedJobs(["job0", "job1", "job2", "job3", "job4", "job5"]))
        """)

        // then
        // job0 - has been modified AND is dependOn job for job1 AND buildDependOnJobs is true (default value)
        // job1 - is the must build job
        // job3 - is the must build job
        assertAllSuccess([jobs[0], jobs[1], jobs[3]])

        // job2 - has been modified but is not used by any must build job
        // job4 - has been modified AND is dependant of job1 but buildDependantJobs is false
        // job5 - has been modified but is not used by any must build job
        // job6 - has been modified AND is dependant of job3 but buildDependantJobs is false
        assertAllDidNotRun([jobs[2], jobs[4], jobs[5], jobs[6]])
        assert SUCCESS == flow.result
    }

    public void testNoDependantAndNoDependOnJobsShouldBeBuild() {
        // given
        // job0 -> job1 -> job3 -> job6
        //              -> job4
        //      -> job2
        // job5
        // job7 -> job6
        def jobs = createJobs(["job0", "job1", "job2", "job3", "job4", "job5", "job6", "job7"])

        // when
        def flow = run("""
            build(graph(["job0", "job1"], ["job0", "job2"], ["job1", "job3"], ["job1", "job4"], ["job3", "job6"], ["job7", "job6"])
                .withBuildDependOnJobs(false)
                .withBuildDependantJobs(false)
                .withMustBuildJobs(["job1", "job6"])
                .withModifiedJobs(["job0", "job1", "job2", "job3", "job4", "job5", "job6"]))
        """)

        // then
        // job1 - is a must build job
        // job3 - is on the path between job1 and job6 so it must be build
        // job6 - is a must build job
        assertAllSuccess([jobs[1], jobs[3], jobs[6]])

        // job0 - has been modified AND is dependOn job for both (job1 and job6) but buildDependOnJobs is false
        // job2 - has been modified but it's not used by any must build jobs
        // job4 - has been modified AND is dependant of job1 but buildDependantJobs is false
        // job5 - has been modified but it's not used by any must build jobs
        // job7 - has been modified AND is dependOn job of job6 but buildDependOnJobs is false
        assertAllDidNotRun([jobs[0], jobs[2], jobs[4], jobs[5], jobs[7]])
        assert SUCCESS == flow.result
    }
}
