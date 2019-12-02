// COMMAND ORCHESTRATOR --> JSON CMD file parser and dispatcher. Also, ORCHESTRATOR status query handler.

import java.io.FileReader;
import java.util.Iterator;
import java.util.Map;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;

import java.util.concurrent.Semaphore;

import java.util.*; 
import java.io.*; 
import java.net.*; 

// command object
class my_command
{
    private String command_name;   // name of the command
    private String service;        // actual Linux cmd to run (this is not done here for test purpose)
    private long   command_id;     // commmand id, not used actually - if needed can be used
    private long   execution_time; // simulating cmd execution by sleeping for this much time in msec
    private String[] dependencies; // dependent commands which need to be completed before this cmd can be run
    private int    dep_size;       // number of dependent cmds as per JSON input
    private int    curr_dep_size;  // actual number of dependent cmds at run time as commands get completed

    // public methods to populate/query the above variables.
    public my_command(String cmd, long id) {
        this.command_name = cmd;
        this.command_id   = id;
        this.dep_size     = 0;
        this.curr_dep_size = 0;
    }

    public String get_command_name() {
        return command_name;
    }

    public void set_command_id(long id) {
        this.command_id   = id;
    }

    public long get_command_id() {
        return command_id;
    }

    public void set_service(String service) {
        this.service = service;
    }

    public String get_service() {
        return service;
    }

    public void set_exec_time(long exec_time) {
        this.execution_time = exec_time;
    }

    public long get_exec_time() {
        return execution_time;
    }

    public void set_dep_size(int size) {
        this.dep_size = size;

        this.dependencies = new String[size];
    }

    public int get_dep_size() {
        return dep_size;
    }

    public int get_curr_dep_size() {
        return curr_dep_size;
    }
   
    public void set_dep_cmd(String cmd, int index) {
        this.dependencies[index] = cmd;
        if (cmd != null) curr_dep_size++;
        else curr_dep_size--;
    }

    public String get_dep_cmd(int index) {
        if (dep_size > 0) {
            return dependencies[index];
        } else {
            return null;
        }
    }
}

// Command Orchestrator main process

// -- the main process first spawns a cmd query thread, so a client can query orchestrator status
// -- the main thread then runs a while loop looking for JSON input command files (1 JSON file per command)
public class cmd_orch_main extends Thread
{
    static public Semaphore semaphore = new Semaphore(1);

    protected static BufferedReader in;

    protected static cmd_orch_query  coq;
    protected static cmd_orch_parser cop;

    // 3 hashmaps used to get the orchestrator state
    // -- RunningCmdMap  == has commands currently running
    // -- PendingCmdMap  == has commands that are waiting for dependent cmds to get over
    // -- CompleteCmdMap == has the commands that had completed execution.
    public static HashMap<String, my_command> RunningCmdMap  = new HashMap<>(); 
    public static HashMap<String, my_command> PendingCmdMap  = new HashMap<>(); 
    public static HashMap<String, my_command> CompleteCmdMap = new HashMap<>(); 

    // main process thread
    public static void main(String[] args)
    {
        Thread t = Thread.currentThread();
        t.setName("Command Orchestrator main");
        
        System.out.println("This thread is : " + t.getName());

        // Create the cmd status query handler thread
        coq  = new cmd_orch_query();
        coq.start();

        // Waiting for user to enter command file
        while (true) {
            String file;

            // print a prompt
            System.out.println("");
            System.out.println("Enter a command JSON file > "); 
            System.out.flush();

            try { 
                in = new BufferedReader(new InputStreamReader(System.in));
                file = in.readLine();
                
                // Spawn a new child thread to handle new command input
                cop = new cmd_orch_parser(file);
                cop.start();
            
                Thread.sleep(1000);
            }
            catch (Exception e) {
                System.err.println("Exception while reading command file: " + e); 
                break;
                // continue;
            }
        }

        System.out.println("Waiting for child threads to exit");
        try {
            cop.join();
            coq.join();
        } catch (InterruptedException e) {

        }
        System.out.println("Main process...exiting");
    }

}


class cmd_orch_parser extends cmd_orch_main implements Runnable
{
    int port = 8000; // hardcoded socket port for cmd exchange
    Socket s = null;
    String cmd_file;

    public cmd_orch_parser (String file)
    {
        Thread t = Thread.currentThread();
        t.setName("Command Orchestrator parser");

        System.out.println("This thread is : " + t.getName());

        // store the JSON cmd file associated with this thread
        this.cmd_file = file;
    }

    public void run()
    {
        my_command cmd;

        Thread t = Thread.currentThread();
        System.out.println("This thread is handling : " + cmd_file + " command file");

        try {
            Object obj = new JSONParser().parse(new FileReader(cmd_file));

            JSONObject jo = (JSONObject)obj;

            String cmd_name = (String) jo.get("command_name");
            //System.out.println(cmd_name);

            String service = (String) jo.get("service");
            //System.out.println(service);

            long    et   = (long) jo.get("exec_time");
            //System.out.println("exec time is " + et);

            long    cmd_id   = (long) jo.get("command_id");
            //System.out.println("value is " + cmd_id);

            // create my_command object and store there
            cmd = new my_command(cmd_name, cmd_id);
            cmd.set_exec_time(et);
            cmd.set_service(service);

            JSONArray dep_array = (JSONArray) jo.get("dependencies");
            //System.out.println("Dependencies:");
            //System.out.println("length is " + dep_array.size());
            cmd.set_dep_size(dep_array.size());
            int dep_cmds = 0;
            
            // coarse locking
            semaphore.acquire();
            for(Object cmd_tmp : dep_array)
            {
                String cn = cmd_tmp.toString();
                int a     = dep_array.indexOf(cn);    
                if (CompleteCmdMap.containsKey(cn)) {
                    cmd.set_dep_cmd(null, a);
                } else {
                    dep_cmds++;
                    cmd.set_dep_cmd(cn, a);
                }
                System.out.println("\t"+cmd_tmp.toString() + " index is " + a);
            }

            // check if any dependencies exist, if yes put cmd in pending queue
            // else put it in running queue
            if (dep_cmds > 0) {
                PendingCmdMap.put(cmd_name, cmd);
                System.out.println("Adding cmds to Pending map " + dep_cmds + " " + cmd_name);
                // System.out.println("Pending commands: " + PendingCmdMap.size());
                semaphore.release();
                return; // as we dont need to do anything further
            } else {
                RunningCmdMap.put(cmd_name, cmd);
                System.out.println("Adding cmds to Running map " + dep_cmds + " " + cmd_name);
                // System.out.println("Running commands: " + RunningCmdMap.size());
            }
            semaphore.release();

        } catch (FileNotFoundException e) {
           System.out.println("FileNotFoundException in " + t.getName());
           return;
        } catch (IOException e) {
           System.out.println("IOException in " + t.getName());
           return;
        } catch (Exception e) {
           System.out.println("Exception in " + t.getName());
           return;
        } 

        // Talk to cmd handler by sending out cmd and wait for finish reply


        // this flag is to handle commands dispatched from PendingMap when they become
        // ready after dependent commands get over (these dispatches are not concurrent
        // but serialized)
        boolean  cmd_dispatched = true;

        while (cmd_dispatched) {
        try {
            // Create a socket to communicate to the specified host and port
            s = new Socket("localhost", port);

            BufferedReader sin = new BufferedReader(new InputStreamReader(s.getInputStream()));
            PrintStream sout = new PrintStream(s.getOutputStream());
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    
            // Tell the user that we've connected
            //System.out.println("Connected to " + s.getInetAddress()
            //           + ":"+ s.getPort());

            String line;
            while(true) {
                line = cmd.get_command_name();
                if (line == null) break;
                sout.println(line);

                line = cmd.get_service();
                if (line == null) break;
                sout.println(line);

                long exec_time = cmd.get_exec_time();
                line = Long.toString(exec_time);
                if (line == null) break;
                sout.println(line);

                //System.out.println("Sent command to handler. Waiting for reply");
                Thread.sleep(1000);

                // Read a line from the server.  
                line = sin.readLine();
                // Check if connection is closed (i.e. for EOF)
                if (line == null) { 
                    System.out.println("Connection closed by server.");
                    break;
                }
                // And write the line to the console.
                System.out.println("command execution over " + line);

                // Put cmd in CompleteCmdHashMap
                semaphore.acquire();
                my_command run_cmd = RunningCmdMap.get(line);
                CompleteCmdMap.put(line, run_cmd);
                RunningCmdMap.remove(line);
                semaphore.release();
                break;
            }
        }
        catch (IOException e) { System.err.println(e); }
        catch (Exception   e) { System.err.println(e); }

        // Always be sure to close the socket
        finally {
            try { if (s != null) s.close(); } catch (IOException e2) { ; }
        }

        // check and see if any cmd can be dispatched from Pending Map
        cmd_dispatched = false;

        Iterator iterator = PendingCmdMap.entrySet().iterator();

        // Run through all the commands in PendingCmdMap, but dispatch one at a time
        // if any of them is ready (i.e. all dependencies are resolved)
        while (iterator.hasNext()) {
            Map.Entry me2 = (Map.Entry) iterator.next();
            // System.out.println("Key: "+me2.getKey() + " & Value: " + me2.getValue());

            String k = (String) me2.getKey();
            my_command v = (my_command)me2.getValue();
            int cmd_deps = 0;
            String cn; 

            try {
                semaphore.acquire();

                // If any command has got completed, set that dependency to NULL 
                for (int i = 0 ; (i < v.get_dep_size()) && (v.get_curr_dep_size() > 0); i++) {
                    cn = v.get_dep_cmd(i); 
                    if ( (cn != null) && CompleteCmdMap.containsKey(cn)) {
                        v.set_dep_cmd(null, i); 
                    }
                }

                // If all dependencies are resolved, dispatch the cmd via above code
                if ( (v.get_dep_size() > 0) && (v.get_curr_dep_size() == 0) ) { 
                    RunningCmdMap.put(k,v);
                    PendingCmdMap.remove(k);
                    cmd = (my_command)v;
                    System.out.println("Dispatching cmd: " + k + ", id is " + cmd.get_command_id());
                
                    cmd_dispatched = true;
                    semaphore.release();
                    break;
                }
            } catch (Exception e) {
                System.err.println("Semaphore acquire exception " + e);
            }
            semaphore.release();
        }
        }
    }
}


// This is the ORCHESTRATOR status query handler thread
// it is always running and waits for a request from cmd_orch_query process
class cmd_orch_query extends cmd_orch_main implements Runnable
{
    protected int port = 8001;  // port is hardcoded
    protected ServerSocket listen_socket;

    protected Socket client;
    protected BufferedReader in;
    protected PrintStream out;

    public cmd_orch_query() {
        Thread t = Thread.currentThread();
        t.setName("Command Orchestrator query");
        
        System.out.println("This thread is : " + t.getName());

        try { listen_socket = new ServerSocket(port); }
        catch (IOException e) { System.out.println("Exception creating server socket"); return;}
        System.out.println("Server: listening on port " + port);
    }   


    public void run()
    {
        try {
            while(true) {
                client = listen_socket.accept();
                System.out.println("Accepted a client connection");

                in = new BufferedReader(new InputStreamReader(client.getInputStream()));
                out = new PrintStream(client.getOutputStream());

                // Send the status of HashMap via the socket to cmd query process
                out.println("<================= Status output begin =================>");

                // Display Running, Pending and completed commands
                out.println("Running commands: " + RunningCmdMap.size());
                semaphore.acquire();
                RunningCmdMap.forEach((k,v) -> out.println("\t Command: is " + k + ", id is " + v.get_command_id()));
                semaphore.release();

                out.println("Pending commands: " + PendingCmdMap.size());
                semaphore.acquire();
                PendingCmdMap.forEach((k,v) -> out.println("\t Command: is " + k + ", id is " + v.get_command_id()));
                semaphore.release();

                out.println("Completed commands: " + CompleteCmdMap.size());
                semaphore.acquire();
                CompleteCmdMap.forEach((k,v) -> out.println("\t Command: is " + k + ", id is " + v.get_command_id()));
                semaphore.release();

                out.println("<================= Status output end ===================>");
                out.println("");

                client.close();
            }
        }
        catch (Exception e) {
            System.out.println("Exception while listening for connections");
        } 
    }
}

