package distributed;

import org.json.*;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

/**
 * Client class
 *
 * @author tfilewic 
 * @version 2025-02-22
*/
public class Client {
    
    private static Socket socket = null;
    private static PrintWriter out = null;
    private static BufferedReader in = null;
    private static final Scanner scanner = new Scanner(System.in);
    
    public static void main(String[] args) {
        System.out.println("Client started...");

        connect();

        boolean quit = false;

        while (!quit) {
            int choice = getChoice();
            if (choice == 1) {
                sum();
            } else {
                quit();
                quit = true;
            }
            JSONObject response = getResponse();
            printResponse(response);
        }
        Utilities.close(in, out, socket);
    }
    
    
    private static void sum() {
        
        int delay = getDelay();
        JSONArray values = getValues();
        
        JSONObject request = new JSONObject();
        request.put("type", "sum");
        request.put("delay", delay);
        request.put("values", values);
        
        out.println(request.toString());    //send request
        System.out.println("\t\t\tsent: " + request.toString());   //DEBUG
    } 
    
    
    private static int getChoice() {
        int choice = 0;
        while (choice != 1 && choice != 2) {
            printMenu();
            
            if (scanner.hasNextInt()) {
                choice = scanner.nextInt();
                scanner.nextLine();
            } else {
                scanner.nextLine();
            }
        }
        return choice;
    }
    
    private static void printMenu() {
        System.out.println("Enter an option:");
        System.out.println("1 - sum");
        System.out.println("2 - quit");
        
    }
    
    
    private static void connect() {
        socket = Config.getSocket();
        out = Config.getOut(socket);
        in = Config.getIn(socket);
        handshake();
    }
    
    
    private static void handshake() {
        JSONObject request = new JSONObject();
        request.put("type", "handshake");
        out.println(request.toString());    //send request
        System.out.println("\t\t\tsent: " + request.toString());//debug
        
        try {
            String stringReceived = in.readLine();
            if (stringReceived == null) {
                System.err.println("ERROR: No handshake response received.");
                System.exit(0);
            }
            JSONObject response = new JSONObject(stringReceived);
            System.out.println("\t\t\treceived: " + response.toString());//debug
            if (response.has("type") && response.getString("type").equals("handshake")) {
                System.out.println();
            } else {
                System.err.println("ERROR: Malformed handshake response received.");
                System.exit(0);
            }
        } catch (IOException | JSONException e) {
            System.err.println("ERROR: handshake failed: " + e.getMessage());
            System.exit(0);
        }
        
    }
    
    private static int getDelay() {
        int delay = 0;
        while (delay < 10 || delay > 1000) {
            System.out.println("Enter a delay in milliseconds (10-1000)");
            
            if (scanner.hasNextInt()) {
                delay = scanner.nextInt();
                scanner.nextLine();
            } else {
                scanner.nextLine();
            }
        }
        return delay;
    }
    
    private static JSONArray getValues() {
        JSONArray values = new JSONArray();     
        boolean done = false;

        while (!done) {
            System.out.println("Input an integer to add to sum, or any other key(s) to finish");
            if (scanner.hasNextInt()) {
                values.put(scanner.nextInt());
                scanner.nextLine();
            } else {
                scanner.nextLine();
                done = true;
                System.out.println("values entered: " + values.toString());
            }      
        }
        return values;
    }
    
    private static void printResponse(JSONObject response) {       
        if (response == null) return;
        
        try {
            if (response.has("type")) {
                switch (response.getString("type")) {
                    case "error":
                        if (response.has("message")) {
                            System.out.println(response.getString("message"));
                        }
                        break;
                    case "sum":
                        if (response.has("sum")) {
                            int sum = response.getInt("sum");
                            System.out.println("\nSUM: " + sum);       
                        }
                        if (response.has("singleTime")) {
                            long singleTime= response.getLong("singleTime");
                            System.out.printf("%-33s %12d ns\n", "sequential processing time:", singleTime);     
                        }
                        if (response.has("distributedTime")) {
                            long distributedTime= response.getLong("distributedTime");
                            System.out.printf("%-33s %12d ns%n", "distributed processing time:", distributedTime);      
                        }
                        System.out.println();
                        break;                    
                }
            }       
        } catch (JSONException | NumberFormatException e) {
            System.err.println("ERROR: malformed response");
        }   
    }
    
    
    private static JSONObject getResponse() {
        try{
            String stringReceived = in.readLine();
            if (stringReceived == null) {
                throw new IOException("received null response");
            }
            JSONObject response = new JSONObject(stringReceived);  
            System.out.println("\t\t\treceived: " + response.toString());//debug  
            return response;
        } catch (JSONException | IOException | NumberFormatException e) {
            System.err.println("ERROR: malformed response: " + e.getMessage());
            return null;
        }  
    }
    
    private static void quit() {
        JSONObject request = new JSONObject();
        request.put("type", "quit");
        out.println(request.toString());    //send request
        System.out.println("\t\t\tsent: " + request.toString());//debug
    }
    
}