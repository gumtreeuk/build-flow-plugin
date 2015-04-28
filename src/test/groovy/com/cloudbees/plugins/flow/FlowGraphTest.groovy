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

import com.google.common.collect.Sets
import junit.framework.TestCase

class FlowGraphTest extends TestCase {

    public void testRealGumtreeGraph() {
        // given
        def InputStream graphIs = FlowGraphTest.class.getClassLoader().getResourceAsStream("real-gumtree-graph.properties")

        // when
        def actualGraph = FlowGraph.createFromPropertyFile(graphIs)

        def vertices = "gt-flow-bapi-client,gt-flow-bapi-server".split(",")

        // then
        vertices.each { vertex ->
            println("checking: ${vertex}")
            assertTrue(actualGraph.containsVertex(vertex))
        }
    }

    public void testCreateGraphFromPropertyFileInputStream() {
        // given
        def InputStream graphIs = FlowGraphTest.class.getClassLoader().getResourceAsStream("test-graph.properties")

        // when
        def actualGraph = FlowGraph.createFromPropertyFile(graphIs)

        // then
        assertTrue(actualGraph.containsVertex("job0"))
        assertTrue(actualGraph.containsVertex("job1"))
        assertTrue(actualGraph.containsVertex("job2"))
        assertTrue(actualGraph.containsVertex("job3"))
        assertTrue(actualGraph.containsVertex("job4"))
        assertTrue(actualGraph.containsVertex("job5"))

        assertTrue(actualGraph.containsEdge("job1", "job0"))

        assertTrue(actualGraph.containsEdge("job2", "job0"))
        assertTrue(actualGraph.containsEdge("job2", "job1"))

        assertTrue(actualGraph.containsEdge("job3", "job0"))

        assertTrue(actualGraph.containsEdge("job4", "job1"))
    }

    public void testCreateInverseGraphFromPropertyFileInputStream() {
        // given
        def InputStream graphIs = FlowGraphTest.class.getClassLoader().getResourceAsStream("inverse-test-graph.properties")

        // when
        def actualGraph = FlowGraph.createFromPropertyFile(graphIs, true)

        // then
        assertTrue(actualGraph.containsVertex("job0"))
        assertTrue(actualGraph.containsVertex("job1"))
        assertTrue(actualGraph.containsVertex("job2"))
        assertTrue(actualGraph.containsVertex("job3"))
        assertTrue(actualGraph.containsVertex("job4"))
        assertTrue(actualGraph.containsVertex("job5"))

        assertTrue(actualGraph.containsEdge("job0", "job1"))
        assertTrue(actualGraph.containsEdge("job0", "job2"))
        assertTrue(actualGraph.containsEdge("job0", "job5"))

        assertTrue(actualGraph.containsEdge("job1", "job2"))
        assertTrue(actualGraph.containsEdge("job1", "job4"))

        assertTrue(actualGraph.containsEdge("job2", "job3"))
    }

    public void testCreateGraphFromPropertyFileURL() {
        // given
        def URL graphURL = FlowGraphTest.class.getClassLoader().getResource("test-graph.properties")

        // when
        def actualGraph = FlowGraph.createFromPropertyFileURL(graphURL)

        // then
        assertTrue(actualGraph.containsVertex("job0"))
        assertTrue(actualGraph.containsVertex("job1"))
        assertTrue(actualGraph.containsVertex("job2"))
        assertTrue(actualGraph.containsVertex("job3"))
        assertTrue(actualGraph.containsVertex("job4"))

        assertTrue(actualGraph.containsEdge("job1", "job0"))

        assertTrue(actualGraph.containsEdge("job2", "job0"))
        assertTrue(actualGraph.containsEdge("job2", "job1"))

        assertTrue(actualGraph.containsEdge("job3", "job0"))

        assertTrue(actualGraph.containsEdge("job4", "job1"))
    }

    public void testCreateGraphFromPropertyFileURLAsString() {
        // given
        def URL graphURL = FlowGraphTest.class.getClassLoader().getResource("test-graph.properties")


        // when
        def actualGraph = FlowGraph.createFromPropertyFileURL(graphURL.toString())

        // then
        assertTrue(actualGraph.containsVertex("job0"))
        assertTrue(actualGraph.containsVertex("job1"))
        assertTrue(actualGraph.containsVertex("job2"))
        assertTrue(actualGraph.containsVertex("job3"))
        assertTrue(actualGraph.containsVertex("job4"))

        assertTrue(actualGraph.containsEdge("job1", "job0"))

        assertTrue(actualGraph.containsEdge("job2", "job0"))
        assertTrue(actualGraph.containsEdge("job2", "job1"))

        assertTrue(actualGraph.containsEdge("job3", "job0"))

        assertTrue(actualGraph.containsEdge("job4", "job1"))
    }

    public void testIsNotChildOfAny() {
        // given
        def graph = new FlowGraph()
                .addEdge("job1", "job2")
                .addEdge("job1", "job3")
                .addEdge("job1", "job4")

        // when
        def isNotChild = graph.isNotChildOfAny("job2", Sets.newHashSet("job2", "job3", "job4"))

        // then
        assertTrue(isNotChild)
    }

    public void testPathExists() {
        // given
        def graph = new FlowGraph()
                .addEdge("job1", "job2")
                .addEdge("job1", "job3")
                .addEdge("job1", "job4")

        // when + then
        assertFalse(graph.pathExists("job2", "job2"))
        assertFalse(graph.pathExists("job3", "job2"))
        assertFalse(graph.pathExists("job4", "job2"))
    }

    public void testFindPaths() {
        // given
        def graph = new FlowGraph()
                .addEdge("job1", "job2")
                .addEdge("job1", "job3")
                .addEdge("job1", "job4")

        // when + then
        assertTrue(graph.findPath("job1", "job2").size() == 1)
        assertTrue(graph.findPath("job2", "job1") == null)
        assertTrue(graph.findPath("job2", "job2").size() == 0)
        assertTrue(graph.findPath("job2", "job2").isEmpty())
        assertTrue(graph.findPath("job2", "non-existing") == null)
        assertTrue(graph.findPath("non-existing", "job2") == null)
    }
}
