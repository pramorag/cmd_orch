// This is the COMMAND HANDLER process which talks to CMD ORCHESTRATOR main process via socket
// This spawns a new child thread for handling every command and then replies the completion via
// that connection
// The command is executed via sleep() call --> as this is a test 
import java.util.*;
import java.io.*;
import java.net.*;

public class cmd_orch_handler extends Thread
{
    public final static int DEFAULT_PORT = 8000; // port hardcoded to talk to Orchestrator main
    protected int port;
    protected ServerSocket listen_socket;

    // Create a ServerSocket to listen for connections on;  start the thread.
    public cmd_orch_handler(int port) {
        if (port == 0) port = DEFAULT_PORT;
        this.port = port;
        try { listen_socket = new ServerSocket(port); }
        catch (IOException e) { System.out.println("Exception creating server socket"); }
        System.out.println("Server: listening on port " + port);
        start();
    }

    // Keep waiting for a connection from Orchestrator threads
    public void run() {
        try {
            while(true) {
                Socket client_socket = listen_socket.accept();
                System.out.println("Accepted a client connection");
                Connection c = new Connection(client_socket);
            }
        }
        catch (IOException e) { 
            System.out.println("Exception while listening for connections");
        }
    }
    
    // Start the server up, listening on an optionally specified port
    public static void main(String[] args) {
        int port = 0;
        if (args.length == 1) {
            try { port = Integer.parseInt(args[0]);  }
            catch (NumberFormatException e) { port = 0; }
        }
        new cmd_orch_handler(port);
    }
}

// This class is the thread that handles all communication with a client
class Connection extends Thread {
    protected Socket client;
    protected BufferedReader in;
    protected PrintStream out;

    // Initialize the streams and start the thread
    public Connection(Socket client_socket) {
        client = client_socket;
        try { 
            in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            out = new PrintStream(client.getOutputStream());
        }
        catch (IOException e) {
            try { client.close(); } catch (IOException e2) { ; }
            System.err.println("Exception while getting socket streams: " + e);
            return;
        }
        this.start();
    }
    
    // Provide the service.
    public void run() {
        String line;
        String cmd_name;
        int len;
        try {
            for(;;) {
                // read in a line
                line = in.readLine();
                cmd_name = line;
                // and write out the command name
                System.out.println("Command is " + line);
                // read in a line
                line = in.readLine();
                // and write out the service to be run
                // System.out.println("Service is " + line);
                // read in a line
                line = in.readLine();
                // and write out the service to be run
                System.out.println("Time to run is " + line);

                long time_to_run = Long.parseLong(line);

                // cmd is simulated via sleep() call for specified duration
                try {
                // System.out.println("Sleeping for " + time_to_run + " msecs");
                Thread.sleep(time_to_run);
                } catch (Exception e) {

                }
                // System.out.println("Sleep done for " + time_to_run + " msecs");
                System.out.println("Sending reply for " + cmd_name);  
                out.println(cmd_name);
                break;
            }
        }
        catch (IOException e) { ; }
        finally { try {client.close();} catch (IOException e2) {;} }
    }
}
