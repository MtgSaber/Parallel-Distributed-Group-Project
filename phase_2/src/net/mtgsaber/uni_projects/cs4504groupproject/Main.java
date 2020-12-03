package net.mtgsaber.uni_projects.cs4504groupproject;

// Custom libraries
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;
import net.mtgsaber.uni_projects.cs4504groupproject.config.Config;
import net.mtgsaber.uni_projects.cs4504groupproject.events.DownloadCommandEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.events.ShutdownEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;

// Java libraries
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Level;

public class Main {
    public static void main(String[] args) {
        Scanner scan = new Scanner(System.in);
        // initialization

        //variables
        String remote_client = "default";
        String local_client = "default";
        String remote_group = "default";
        String target_resource = "default";
        String path_name = "default";

        // create map between P2PClient objects and their hosts
        final Map<String, PeerObject> p2pClientSpace = new HashMap<>();

        // set up event and thread managers
        final AsynchronousEventManager eventManager = new AsynchronousEventManager();
        final Thread eventManagerThread = new Thread(eventManager);
        eventManager.setThreadInstance(eventManagerThread);
        eventManagerThread.start(); // start thread manager

        try {
            Logging.start(new FileOutputStream(args[0]), true); // log file access attempt
        } catch (FileNotFoundException e) {
            //TODO: handle the exception
//            Logging.start(e.toString(), true); // log exception
            System.out.println("File not found: " + e.toString());
        }

        //UI creation
        // create this client
//        createPeer()

        // initiate UI
        //eventManager.push(new P2PClient.FileDownloadEvent("localClientName", new File("localFileDestination"), "remotePeerName", "remotePeerGroup", "remoteResource"));


        //notes for ui
        /*user can type "create client", client1 download fileX from client2
        need instance of start download, eventmanager in main

        //String command = "download \"FileX\" from \"user1\" of \"group1\" to \"user2\" as \"/path/to/file\"";
        //for each menu command, create event like this and push to event manager
        Event e = new DownloadCommandEvent("user2", new File("path/to/file"), "user1", "group1", "FileX");
        eventManager.push(e);
        Logging.log(Level.INFO, "This is a debugging message");


        */
        //display menu of commands
        Logging.log(Level.INFO, "Hello World");

        System.out.println("Commands Menu \n-----------\n");
        System.out.println("(1) Download file" +
                "(2) Send file" +
                "(3) Create peer" +
                "(4) Review logs" +""); //can come back to this one

        int user_command = scan.nextInt();
        //switch statement to take argument for menu of commands
        switch (user_command){
            case 1:
                System.out.println("Menu Options \n------------\n");
                System.out.println("(1) Add Remote Client\n" +
                        "(2) Add Local Client\n"+
                        "(3) Add Remote Group\n" +
                        "(4) Target Resource\n"+
                        "(5) Add Path Name\n"+
                        "(0) Push Download\n");

                int download_Option = scan.nextInt();
                switch(download_Option) {//make input mandatory : default casing
                    case 1:
                        remote_client = scan.nextLine();
                        break;
                    case 2:
                        local_client = scan.nextLine();
                        break;
                    case 3:
                        remote_group = scan.nextLine();
                        break;
                    case 4:
                        target_resource = scan.nextLine();
                        break;
                    case 5:
                        path_name = scan.nextLine();
                        break;
                    case 0:
                       //"download \"FileX\" from \"user1\" of \"group1\" to \"user2\" as \"/path/to/file\""
                        System.out.println("Download \"" + target_resource + "\" from \"" +remote_client+"\" of \" "+ remote_group +"\" to \"" + local_client + "\" as \" " + path_name+ "\"");

                        Event e = new DownloadCommandEvent(local_client, new File(path_name), remote_client, remote_group, target_resource);
                        eventManager.push(e);
                        break;

                    default:
                        System.out.println("Error: Incorrect input try again.");
                }
                break;
            case 2: // can be taken out
                System.out.println("Menu Options \n------------\n");
                System.out.println("(1) Add Local Client \n" +
                        "(2) Add Local Client \n"+
                        "(3) Add Remote Group\n" +
                        "(4) Target Resource\n" +
                        "(5) Add Path Name \n" +
                        "(0) Push Send");

                int send_Option = scan.nextInt();
                switch(send_Option){
                    case 1:
                        remote_client = scan.nextLine();
                        break;
                    case 2:
                        local_client = scan.nextLine();
                        break;
                    case 3:
                        remote_group = scan.nextLine();
                        break;
                    case 4:
                        target_resource = scan.nextLine();
                        break;
                    case 5:
                        path_name = scan.nextLine();
                        break;
                    case 0:
                        System.out.println("send \"" + target_resource + "\" from \"" +remote_client+"\" of \" "+ remote_group +"\" to \"user2\" as \"/path/to/file\"");
                        //Event e = new SendCommandEvent(local_client, new File(path_name), remote_client, remote_group, target_resource);
                        //eventManager.push(e);
                        break;
                }
                break;
            case 3:
                //case3
                break;
            case 4:
                //case4
                break;
        }





















        // software shutdown
        shutdown(p2pClientSpace, eventManager, scan);
    }

    private static void createPeer(Map<String, PeerObject> peerSpace, EventManager eventManager, String configFileLoc) {
        try {
            Config config = new Config(new File(configFileLoc)); // this is the line that produces the exceptions being caught.
            PeerObject client = new PeerObject(config, eventManager);
            client.start();
            peerSpace.put(client.getName(), client);
            for (String eventName : client.getCentralEventNames())
                eventManager.addHandler(eventName, client);
        } catch (Exception ex) {
            // TODO: change to catch specific exceptions and do something with them.
        }
    }

    /**
     * Closes all resources and threads.
     */
    private static void shutdown(Map<String, PeerObject> peerSpace, AsynchronousEventManager eventManager, Scanner inputScanner) {
        inputScanner.close();
        for (String clientNameKey : peerSpace.keySet()) {
            Logging.log(Level.INFO, "Issuing shutdown command to peer \"" + clientNameKey + "\"...");
            eventManager.push(new ShutdownEvent(clientNameKey));
        }
        eventManager.shutdown();
        //TODO: really should find a way to call .join() on all existing threads, but it should be okay for now.
        Logging.shutdown(); // record that we've shut down
    }

    /**
     * Creates a scanner either on System.in or the file provided in args[0]. If the path provided does not exist, this returns null.
     * @param args
     * @return a new Scanner object if no args or a valid path as first arg, or null if an invalid path as first arg.
     */
    private static Scanner getInputScanner(String[] args) {
        if (args.length == 0 || args[0].equals(""))
            return new Scanner(System.in);
        try {
            File inputScript = new File(args[0]);
            return new Scanner(inputScript);
        } catch (FileNotFoundException fnfex) {
            Logging.log(Level.SEVERE, "InputScript: no such file \"" + args[0] + "\". Shutting down.");
        }
        return null;
    }
}
