/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.sling.osgi.installer.it;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.apache.sling.osgi.installer.OsgiInstaller;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.osgi.framework.Bundle;

@RunWith(JUnit4TestRunner.class)
public class BundleDependenciesTest extends OsgiInstallerTestBase {

    @org.ops4j.pax.exam.junit.Configuration
    public static Option[] configuration() {
    	return defaultConfiguration();
    }
    
    @Before
    public void setUp() {
        setupInstaller();
    }
    
    @After
    public void tearDown() {
        super.tearDown();
    }
    
    // needsB bundle requires testB, try loading needsB first,
    // then testB, and verify that in the end needsB is started     
    @Test
    public void testBundleDependencies() throws Exception {
        final String testB = "osgi-installer-testB";
        final String needsB = "osgi-installer-needsB";
        
        assertNull("TestB bundle must not be present at beginning of test", findBundle(testB));
        
        // without testB, needsB must not start
        {
            resetCounters();
            installer.addResource(getInstallableResource(getTestBundle(BUNDLE_BASE_NAME + "-needsB.jar")));
            waitForInstallerAction(OsgiInstaller.OSGI_TASKS_COUNTER, 2);
            final Bundle b = findBundle(needsB);
            assertNotNull(needsB + " must be installed", b);
            assertFalse(needsB + " must not be started, testB not present", b.getState() == Bundle.ACTIVE);
        }
        
       // now install testB -> needsB must start
        {
            resetCounters();
            installer.addResource(getInstallableResource(getTestBundle(BUNDLE_BASE_NAME + "-testB-1.0.jar")));
            waitForInstallerAction(OsgiInstaller.OSGI_TASKS_COUNTER, 2);
            assertNotNull(testB + " must be installed", findBundle(testB));
            final Bundle b = findBundle(needsB);
            assertNotNull(needsB + " must still be installed", b);
            assertTrue(needsB + " must be started now that testB is installed", b.getState() == Bundle.ACTIVE);
        }
    }
}