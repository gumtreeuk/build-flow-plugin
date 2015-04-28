/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc., Nicolas De Loof.
 *                     Cisco Systems, Inc., a California corporation
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
import hudson.model.*

import static hudson.model.Result.SUCCESS

class ExtraFeaturesTest extends DSLTestCase {

    public void testLoadBuildParametersFromPropertiesFile() {
        // given
        def URL buildParamsProps = FlowGraphTest.class.getClassLoader().getResource("test-build-params.properties")

        // given
        FreeStyleProject job1 = createJob("job1")
        job1.addProperty(parameters(strParam("JOB_PARAM_1", "jparam1")))

        // when
        def flowBuildParams = new ParametersAction(new StringParameterValue("FLOW_PARAM_1", "fparam1"))
        def flow = runWithParams("""
            bParams = loadProperties("${buildParamsProps.toString()}")
            bParams.putAll(getParams())
            println "build paramteres: " + bParams

            j1 = build(bParams, "job1")
        """, flowBuildParams)
        println flow.log

        // then
        def build = assertSuccess(job1)
        assertHasParameter(build, "JOB_PARAM_1", "jparam1")
        assertHasParameter(build, "FLOW_PARAM_1", "fparam1")
        assertHasParameter(build, "TEST_PARAM_1", "tparam1")
        assertHasParameter(build, "TEST_PARAM_2", "tparam2")
        assert SUCCESS == flow.result
    }

    private StringParameterDefinition strParam(String name, String value) {
        new StringParameterDefinition(name, value);
    }

    private ParametersDefinitionProperty parameters(StringParameterDefinition... params) {
        new ParametersDefinitionProperty(params)
    }
}
