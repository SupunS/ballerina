/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.run;

import org.ballerinalang.test.BaseTest;
import org.ballerinalang.test.context.BallerinaTestException;
import org.ballerinalang.test.context.LogLeecher;
import org.ballerinalang.test.context.LogLeecher.LeecherType;
import org.testng.annotations.Test;

import java.io.File;

/**
 * This class tests invoking an entry function in a package via the Ballerina Run Command and the data binding
 * functionality.
 *testInvalidSourceArg:
 * e.g., ballerina run abc:nomoremain 1 "Hello World" data binding main
 *  where nomoremain is the following function
 *      public function nomoremain(int i, string s, string... args) {
 *          ...
 *      }
 */
public class PkgRunFunctionNegativeTestCase extends BaseTest {

    @Test(description = "test insufficient arguments")
    public void testInsufficientArguments() throws BallerinaTestException {
        LogLeecher errLogLeecher = new LogLeecher("ballerina: insufficient arguments to call the 'main' function",
                                                  LeecherType.ERROR);
        balClient.runMain((new File("src/test/resources/run/package")).getAbsolutePath(), "multiple_params",
                          new LogLeecher[]{errLogLeecher});
        errLogLeecher.waitForText(10000);
    }

    @Test(description = "test too many arguments")
    public void testTooManyArguments() throws BallerinaTestException {
        LogLeecher errLogLeecher = new LogLeecher("ballerina: too many arguments to call the 'main' function",
                                                  LeecherType.ERROR);
        balClient.runMain((new File("src/test/resources/run/package")).getAbsolutePath(), "no_params",
                          new String[]{}, new String[]{"extra"}, new LogLeecher[]{errLogLeecher});
        errLogLeecher.waitForText(10000);
    }
}
