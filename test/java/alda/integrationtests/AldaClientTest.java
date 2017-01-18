package alda.integrationtests;

import alda.AldaClient;
import alda.testutils.AldaServerInfo;
import alda.testutils.TestEnvironment;
import alda.testutils.TestEnvironmentStatus;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

public class AldaClientTest {

    private final ByteArrayOutputStream stdOutContent = new ByteArrayOutputStream();

    @BeforeClass
    public static void checkTestEnvironment() {
        if (TestEnvironment.getStatus() == TestEnvironmentStatus.STOPPED){
            fail("This test class should be started via the IntegrationTestsSuite which takes care of starting and stopping the TestEnvironment.");
        }
    }

    @Test
    public void listProcessesOutput() throws Exception {

        // Redirect StdOut
        PrintStream oldStdOut = System.out;
        System.setOut(new PrintStream(stdOutContent));
        try {

            AldaClient.listProcesses(30);

            Map<Integer, Integer> serverPortsFound = new HashMap<>();
            Pattern serverPortPattern = Pattern.compile("\\[(.*?)\\]");
            Pattern serverBackendPortPattern = Pattern.compile("backend port\\:(.*?)\\)");

            String[] stdOutLines = stdOutContent.toString().split("\n");
            for (String line : stdOutLines){
                // Format: [27716] Server up (2/2 workers available, backend port: 39998)
                if (line.contains("Server up")){
                    Matcher sp = serverPortPattern.matcher(line);
                    if (sp.find()){
                        int srvPort = Integer.parseInt(sp.group(1));
                        int numberOfWorkers = 0;

                        Matcher sbp = serverBackendPortPattern.matcher(line);
                        if (sbp.find()){
                            int srvBackendPort = Integer.parseInt(sbp.group(1).trim());
                            numberOfWorkers = parseOutputForNumberOfWorkersOnBackendPort(stdOutLines, srvBackendPort);
                        }

                        serverPortsFound.put(srvPort, numberOfWorkers);
                    }
                }
            }

            for(AldaServerInfo server: TestEnvironment.getRunningServers()) {
                assertTrue("Running server not listed.", serverPortsFound.containsKey(server.getPort()) );
                assertEquals("Number of workers for server don't match.", server.getNumberOfWorkers(), serverPortsFound.get(server.getPort()).intValue() );
            }

        } finally {
            // Reset StdOut
            System.setOut(oldStdOut);
            System.out.println(stdOutContent);
        }

    }

    private int parseOutputForNumberOfWorkersOnBackendPort(String[] lines, int srv_backend_port) {
        int workersFound = 0;
        for (String line : lines) {
            // Format: [46672] Worker (pid: 6368)
            if (line.contains("["+srv_backend_port+"] Worker")) {
                workersFound++;
            }
        }
        return workersFound;
    }

}