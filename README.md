# cmd_orch
A simple command orchestrator application in Java

This Command Orchestrator framework has been designed in the following way using 3 processes / classes.

1) cmd_orch_main()
   -- This is the main process for parsing JSON command files and dispatching the commands in the JSON files
       to cmd_orch_handler() process via socket connection
   -- Each JSON input file contains one command and this is parsed by a child thread and if there are no
      command dependencies / all dependencies are resolved - the command is dispatched via socket to
      cmd_orch_handler()
   -- This process deals with 2 types of threads
      (i) cmd_orch_parser() ---> this is a child thread and handles the JSON file parsing and dispatching
                                  of command. "Each" thread handles one JSON input file.
      (ii) cmd_orch_query() ---> this thread always keeps running waiting for a connection from cmd_orch_status()
                                 process. This reads the various HashMaps containing commands and displays the status

2) cmd_orch_handler()
   -- This is the main process for handling a command dispatched by cmd_orch_main()
   -- This spawns a child thread for "each" command input so that commands can be run CONCURRENTLY
   -- The command is simulated via a sleep() call and after sleep duration, an acknowledgement is sent back
      to cmd_orch_main() to indicate completion of command execution

3) cmd_orch_status()
   -- This is a process for sending a status query to cmd_orch_main() via a socket connection
   -- It gets the response which it prints out on the console.

COMMAND structure (JSON file format)
------------------------------------

Example:

{
    "command_name": "COMMAND 3",    --> name of the command, used as a key in HashMap
    "command_id": 3,                --> command id, not actually used here as name is enough
    "service": "tty",               --> sample command, not actually run here; instead sleep() is called
    "exec_time" : 40000,            --> command simulation via a sleep() for this msec duration
    "dependencies": [               --> dependent commands. Here, COMMAND 3 will be dispatched for execution only
     "COMMAND 1",                       if COMMAND_1 and COMMAND_2 have finished execution
     "COMMAND 2"
    ]
}

COMMAND class (to store a command in a HashMap)
-----------------------------------------------

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
...
...
}


COMPILATION and RUNNING:
------------------------

1) The files are compiled in the following way:
   (a) javac -cp /usr/share/java/json-simple-1.1.jar cmd_orch_main.java
   (b) javac cmd_orch_handler.java
   (c) javac cmd_orch_status.java

2) The classes are run in this order:
   (a) java cmd_orch_handler
   (b) java -cp .:/usr/share/java/json-simple-1.1.jar cmd_orch_main
   (c) java cmd_orch_status

SAMPLE EXECUTION steps:
----------------------

1) Each JSON file has one command and the cmd_orch_main() takes only one file as an argument.

On running "java -cp .:/usr/share/java/json-simple-1.1.jar cmd_orch_main" :

"
pramod@ubuntu:~/java_test/spirent$ java -cp .:/usr/share/java/json-simple-1.1.jar cmd_orch_main
This thread is : Command Orchestrator main
This thread is : Command Orchestrator query
Server: listening on port 8001

Enter a command JSON file > 
cmd_1.json
This thread is : Command Orchestrator parser
This thread is handling : cmd_1.json command file
Adding cmds to Running map 0 COMMAND 1

Enter a command JSON file > 
cmd_2.json
This thread is : Command Orchestrator parser
This thread is handling : cmd_2.json command file
Adding cmds to Running map 0 COMMAND 2

Enter a command JSON file > 
cmd_3.json
This thread is : Command Orchestrator parser
This thread is handling : cmd_3.json command file
	COMMAND 1 index is 0
	COMMAND 2 index is 1
Adding cmds to Pending map 2 COMMAND 3

Enter a command JSON file > 
cmd_4.json
This thread is : Command Orchestrator parser
This thread is handling : cmd_4.json command file
	COMMAND 1 index is 0
Adding cmds to Pending map 1 COMMAND 4

Enter a command JSON file > 
cmd_5.json
This thread is : Command Orchestrator parser
This thread is handling : cmd_5.json command file
	COMMAND 6 index is 0
Adding cmds to Pending map 1 COMMAND 5
"

COMMAND ORCHESTRATOR status output
----------------------------------

1) Please see "output.txt" file attached

DESIGN considerations
---------------------

1) Programming in Java formally for the very first time. Reason being that it was easier to use JSON Object Parser class
   readily available in Java.

2) Using sockets as IPC between Java processes.

3) Using a single semaphore in cmd_orch_main() to prevent concurrent access to any of the HashMaps.

4) Using HashMap to store commands. The HashMap is indexed via "command_name" as the key. 
   The HashMaps used are:
   --> Running  == to store commands currently dispatched and executing in cmd_orch_handler() and waiting for reply
   --> Pending  == to store commands which have command dependencies and hence not dispatched yet
   --> Complete == the commands which have completed execution in cmd_orch_handler() and acknowledged by it

5) Using child threads per command to handle concurrent command execution

6) Only caveat I see here is that for pending commands, they are sort of serialized even though the dependencies
   are resolved. This needs to be enhanced but needs some thinking on the design. Haven't had much time to do it.

7) Doing a coarse locking using semaphore(), so a big chunk of processing is locked via semaphore(). Need to explore
   further if we can do any fine-grained locking.

8) A few error handling and enhancements can be done like handling multiple input JSON files, invalid file names etc.
   These are not done here and the exception handling needs to be enhanced for this.

9) JAVA version used on Ubuntu 18.04 VM
"
pramod@ubuntu:~/java_test/spirent$ java --version 
openjdk 11.0.4 2019-07-16
OpenJDK Runtime Environment (build 11.0.4+11-post-Ubuntu-1ubuntu219.04)
OpenJDK 64-Bit Server VM (build 11.0.4+11-post-Ubuntu-1ubuntu219.04, mixed mode, sharing)
"
