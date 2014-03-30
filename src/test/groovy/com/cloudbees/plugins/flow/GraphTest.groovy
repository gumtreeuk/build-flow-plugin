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

import jenkins.model.Jenkins

import static hudson.model.Result.SUCCESS

class GraphTest extends DSLTestCase {

    @Override
    protected void setUp() throws Exception {
        super.setUp()
        Jenkins.getInstance().setQuietPeriod(0)
    }

//    public void testGraphWithChainedEdges() {
//        // given
//        def jobs = createJobs(["job1", "job2", "job3"])
//
//        // when
//        def flow = run("""
//            build(graph("job1").addEdge("job1", "job2").addEdge("job2", "job3"))
//        """)
//
//        // then
//        assertAllSuccess(jobs)
//        assert SUCCESS == flow.result
//    }

    public void testGraphWithParallelEdges() {
        // given
        def jobs = createJobs(["job1", "job2", "job3"])

        // when
        def flow = run("""
            build(graph("job1").withEdges(["job1", "job2"], ["job1", "job3"]))
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
            build(graph("job1")
                .withEdges(
                    ["job1", "job2"],
                    ["job1", "job3"],
                    ["job2", "job3"],
                    ["job3", "job4"],
                    ["job2", "job5"],
                    ["job1", "job6"],
                    ["job5", "job6"]))
        """)
        // 1 -> 2 -> 3, 5 -> 4,6

        // then
        assertAllSuccess(jobs)
        assert SUCCESS == flow.result
        println flow.jobsGraph.edgeSet()
    }
}
