// This process talks to CMD ORCHESTRATOR query thread to get the status of command orchestrator
// It talks via socket, gets the status, displays it and exits.
import java.util.*; 
import java.io.*; 
import java.net.*; 

public class cmd_orch_status extends Thread
{
    static int port = 8001;
    static Socket s = null;
    protected static BufferedReader in;
    static String line;

    public static void main(String[] args)
    {
        Thread t = Thread.currentThread();
        t.setName("Command Orchestrator status query thread");

        System.out.println("This thread is : " + t.getName());

        // Implement a client using sockets
        try {
            // Create a socket to communicate to the specified host and port
            s = new Socket("localhost", port);

            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintStream sout = new PrintStream(s.getOutputStream());

            // Tell the user that we've connected
            System.out.println("Connected to " + s.getInetAddress()
                       + ":"+ s.getPort());

            // Send it to the server
            sout.println("STATUS");
            Thread.sleep(2000);

            for(;;) {
                line = in.readLine();
                if (line == null) {
                    break;
                } else {
                    // Print out the cmd orchestrator status on the console
                    System.out.println(line);
                }
            }
        }
        catch (IOException e) { System.err.println(e); }
        catch (Exception   e) { System.err.println(e); }

        // Always be sure to close the socket
        finally {
            try { if (s != null) s.close(); } catch (IOException e2) { ; }
        }
    }
}
