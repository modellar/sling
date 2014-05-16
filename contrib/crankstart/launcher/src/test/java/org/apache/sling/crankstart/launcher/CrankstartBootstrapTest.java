package org.apache.sling.crankstart.launcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.net.ServerSocket;
import java.util.Random;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.sling.commons.testing.junit.Retry;
import org.apache.sling.commons.testing.junit.RetryRule;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/** Verify that we can start the Felix HTTP service
 *  with a {@link CrankstartBootstrap}. 
 */
public class CrankstartBootstrapTest {
    
    private static final int port = getAvailablePort();
    private static final HttpClient client = new HttpClient();
    private static Thread crankstartThread;
    private static String baseUrl = "http://localhost:" + port;
            
    @Rule
    public final RetryRule retryRule = new RetryRule();
    
    private static int getAvailablePort() {
        int result = -1;
        ServerSocket s = null;
        try {
            try {
                s = new ServerSocket(0);
                result = s.getLocalPort();
            } finally {
                if(s != null) {
                    s.close();
                }
            }
        } catch(Exception e) {
            throw new RuntimeException("getAvailablePort failed", e);
        }
        return result;
    }
    
    private final static String CRANKSTART = 
        "classpath mvn:org.apache.felix/org.apache.felix.framework/4.4.0\n"
        + "classpath mvn:org.slf4j/slf4j-api/1.6.2\n"
        + "classpath mvn:org.ops4j.pax.url/pax-url-aether/1.6.0\n"
        + "classpath mvn:org.ops4j.pax.url/pax-url-commons/1.6.0\n"
        + "classpath mvn:org.apache.sling/org.apache.sling.crankstart.core/0.0.1-SNAPSHOT\n"
        + "classpath mvn:org.apache.sling/org.apache.sling.crankstart.api/0.0.1-SNAPSHOT\n"
        + "osgi.property org.osgi.service.http.port ${http.port}\n"
        + "osgi.property org.osgi.framework.storage " + getOsgiStoragePath() + "\n"
        + "start.framework\n"
        + "bundle mvn:org.apache.felix/org.apache.felix.http.jetty/2.2.0\n"
        + "bundle mvn:org.apache.felix/org.apache.felix.eventadmin/1.3.2\n"
        + "bundle mvn:org.apache.felix/org.apache.felix.scr/1.8.2\n"
        + "bundle mvn:org.apache.sling/org.apache.sling.commons.osgi/2.2.1-SNAPSHOT\n"
        + "bundle mvn:org.apache.sling/org.apache.sling.commons.log/2.1.2\n"
        + "bundle mvn:org.apache.sling/org.apache.sling.crankstart.test.services/0.0.1-SNAPSHOT\n"
        + "bundle mvn:org.apache.felix/org.apache.felix.configadmin/1.6.0\n"
        + "start.all.bundles\n"
        + "config org.apache.sling.crankstart.testservices.SingleConfigServlet\n"
        + "  path=/single\n"
        + "  message=doesn't matter\n"
        + "log felix http service should come up at http://localhost:${http.port}\n"
    ;
    
    @BeforeClass
    public static void setup() {
        final GetMethod get = new GetMethod(baseUrl);
        System.setProperty("http.port", String.valueOf(port));
        
        try {
            client.executeMethod(get);
            fail("Expecting connection to " + port + " to fail before starting HTTP service");
        } catch(IOException expected) {
        }
        
        crankstartThread = new Thread() {
            public void run() {
                try {
                    new CrankstartBootstrap(new StringReader(CRANKSTART)).start();
                } catch(Exception e) {
                    fail("CrankstartBootstrap exception:" + e);
                }
            }
        };
        crankstartThread.setDaemon(true);
        crankstartThread.start();
    }
    
    @AfterClass
    public static void cleanup() throws InterruptedException {
        crankstartThread.interrupt();
        crankstartThread.join();
    }
    
    @Test
    @Retry(timeoutMsec=10000, intervalMsec=250)
    public void testHttpRoot() throws Exception {
        final GetMethod get = new GetMethod(baseUrl);
        client.executeMethod(get);
        assertEquals("Expecting page not found at " + get.getURI(), 404, get.getStatusCode());
    }
    
    @Test
    @Retry(timeoutMsec=10000, intervalMsec=250)
    public void testSingleConfigServlet() throws Exception {
        final GetMethod get = new GetMethod(baseUrl + "/single");
        client.executeMethod(get);
        assertEquals("Expecting success " + get.getURI(), 200, get.getStatusCode());
    }
    
    @Test
    @Retry(timeoutMsec=10000, intervalMsec=250)
    @Ignore("TODO - activate once we support config factories")
    public void testConfigFactoryServlet() throws Exception {
    }
    
    private static String getOsgiStoragePath() {
        final File tmpRoot = new File(System.getProperty("java.io.tmpdir"));
        final Random random = new Random();
        final File tmpFolder = new File(tmpRoot, System.currentTimeMillis() + "_" + random.nextInt());
        if(!tmpFolder.mkdir()) {
            fail("Failed to create " + tmpFolder.getAbsolutePath());
        }
        tmpFolder.deleteOnExit();
        return tmpFolder.getAbsolutePath();
    }
}