package net.mtgsaber.uni_projects.cs4504groupproject;

// Custom libraries
import net.mtgsaber.lib.events.AsynchronousEventManager;
import net.mtgsaber.lib.events.Event;
import net.mtgsaber.lib.events.EventManager;

// other project classes
import net.mtgsaber.uni_projects.cs4504groupproject.config.PeerObjectConfig;
import net.mtgsaber.uni_projects.cs4504groupproject.events.DownloadCommandEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.events.ShutdownEvent;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Logging;
import net.mtgsaber.uni_projects.cs4504groupproject.util.Utils;

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

        int exit_option = -1;

        // create map between P2PClient objects and their hosts
        final Map<String, PeerObject> p2pClientSpace = new HashMap<>();

        // set up event and thread managers
        final AsynchronousEventManager eventManager = new AsynchronousEventManager();
        final Thread eventManagerThread = new Thread(eventManager, "Main_EventManagerThread");
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


        do {


            System.out.println("Commands Menu \n-----------\n");
            System.out.println("(1) Download file" +
                    "(2) Create peer" +
                    "(3) Review/Create logs" +
                    "(4) Shutdown target client"+
                    "(0) Exit client"); //can come back to this one

            int user_command = scan.nextInt();
            //switch statement to take argument for menu of commands
            switch (user_command) {
                case 1:
                    System.out.println("Menu Options \n------------\n");
                    System.out.println("(1) Add Remote Client, Local Client, Add Remote Group, Target Resource, Add Path Name\n (2)Push Download\n");
                    int command_choice = scan.nextInt();
                    switch (command_choice) {


                        //make input mandatory : default casing
                        case 1:
                            System.out.println("Add Remote Client");
                            remote_client = scan.nextLine();
                            System.out.println("Add Local Client");
                            local_client = scan.nextLine();
                            System.out.println("Add Remote Group");
                            remote_group = scan.nextLine();
                            System.out.println("Add Target Resource");
                            target_resource = scan.nextLine();
                            System.out.println("Add Path Name");
                            path_name = scan.nextLine();
                        case 2:
                            //"download \"FileX\" from \"user1\" of \"group1\" to \"user2\" as \"/path/to/file\""
                            System.out.println("Download \"" + target_resource + "\" from \"" + remote_client + "\" of \" " + remote_group + "\" to \"" + local_client + "\" as \" " + path_name + "\"");
                            System.out.println("Are you ready to push the dowload? y/n");
                            char download_choice = scan.nextLine().charAt(0);
                            switch (download_choice) {
                                case 'y':
                                case 'Y':
                                    Event e = new DownloadCommandEvent(local_client, new File(path_name), remote_client, remote_group, target_resource);
                                    eventManager.push(e);
                                    break;
                                case 'n':
                                case 'N':
                                    System.out.println("Returning to Menu");
                                    break;
                                default:
                                    System.out.println("Wrong or invalid input");

                            }
                            System.out.println("Error: Incorrect input try again");

                    }break;
                case 2:
                    System.out.println("What is file location of your peer?: ");
                    String configFileLoc = scan.nextLine();
                    createPeer(p2pClientSpace, eventManager, configFileLoc);
                    break;
                case 3:
                    System.out.println("(1)Review Logs\n(2)Add Log");
                    int log_choice = scan.nextInt();
                    if(log_choice == 1){

                    }else if(log_choice == 2){
                        System.out.println("What message do you want to add");
                        String log_message= scan.next();
                        Logging.log(Level.INFO, log_message);
                        System.out.println("\n\nLog message \"" + log_message + "\" was pushed.");

                    } else{
                        System.out.println("Invalid input..\nReturning to Menu.");
                    }

                    break;
                case 4:
                    //shutdown indv client
                    String client_name;
                    System.out.println("\n\nEnter target client to shut down");
                    client_name = scan.next();
                    System.out.println("Shutting down client \"" + client_name + "\" in...");
                    int countdown = 3;
                    while (countdown >0) {
                        try {
                            Thread.sleep(1000);
                            System.out.println(countdown-- + "...");
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    eventManager.push(new ShutdownEvent(client_name));
                    break;
                case 0:
                    exit_option = 0;
                    break;
                default:
                    System.out.println("Error: Incorrect input try again");

            }
        }while(exit_option != 0);

        // software shutdown
        shutdown(p2pClientSpace, eventManager, eventManagerThread, scan);
    }


    /**
     * Creates a peer instance, starts its threads, and hooks its event handlers for the
     * @param peerSpace
     * @param eventManager
     * @param configFileLoc
     */
    private static void createPeer(Map<String, PeerObject> peerSpace, EventManager eventManager, String configFileLoc) {
        try {
            PeerObjectConfig config = new PeerObjectConfig(new File(configFileLoc)); // this is the line that produces the exceptions being caught.
            PeerObject client = new PeerObject(config, eventManager);
            client.start();
            peerSpace.put(client.getRoutingData().NAME, client);
            for (String eventName : client.getCentralEventNames())
                eventManager.addHandler(eventName, client);
        } catch (Exception ex) {
            // TODO: change to catch specific exceptions and do something with them.

        }
    }

    /**
     * Closes all resources and threads.
     */
    private static void shutdown(Map<String, PeerObject> peerSpace, AsynchronousEventManager eventManager, Thread eventManagerThread, Scanner inputScanner) {
        inputScanner.close();
        for (String clientNameKey : peerSpace.keySet()) {
            Logging.log(Level.INFO, "Issuing shutdown command to peer \"" + clientNameKey + "\"...");
            eventManager.push(new ShutdownEvent(clientNameKey));
        }
        eventManager.shutdown();
        Utils.joinThreadForShutdown(eventManagerThread);

        //TODO: really should find a way to call .join() on all existing threads, but it should be okay for now (Andrew).
        Logging.shutdown(); // record that we've shut down
    }

    /**
     * Creates a scanner either on System.in or the file provided in args[0]. If the path provided does not exist, this returns null.
     * @param args
     * @return a new Scanner object if no args or a valid path as first arg, or null if an invalid path as first arg.
     */
    private static Scanner getInputScanner(String[] args) {
        if (args == null || args.length == 0 || args[0].equals(""))
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
