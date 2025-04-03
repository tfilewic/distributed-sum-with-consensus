package distributed;


import org.json.*;
import java.io.*;
import java.net.Socket;

/**
 * Node class
 * 
 * @author tfilewic 
 * @version 2025-02-20
*/
public class Node {

    private static boolean faulty = false;
    
    private static Socket socket = null;
    private static PrintWriter out = null;
    private static BufferedReader in = null;
    
    
    
    
    public static void main(String[] args) {          
        configure(args);
        connect();
        System.out.println("Node ready." + (faulty ? "   [faulty]": ""));
        
        // TODO
        boolean quit = false;
        while(!quit) {
            
            try {   
                JSONObject response;
                String requestString = in.readLine();
                if (requestString != null) {
                    
                    JSONObject request = new JSONObject(requestString);
                    System.out.println("\t\t\tnode received: " + request.toString());//debug
                    
                    if (request.has("type") && request.getString("type").equals("add")) {
                        response = add(request);
                    } else if (request.has("type") && request.getString("type").equals("check")) {
                        response = check(request);
                    } else {
                        response = Utilities.error("ERROR: unrecognized request");
                    }
                    
                    out.println(response.toString());   //send response
                    System.out.println("\t\t\tnode sent: " + response.toString());//debug
                }       
            } catch (Exception e ) { 
              //TODO
                //disconnect clienthandler
            }   
            
            // TODO  
            //get request
            //if sum
            //if consensus
            //if quit?
            
           
            
        }
    }
    
    
    /**
     * Handles the args
     */
    private static void configure(String[] args) {
        if (args.length > 0) {
            faulty = Boolean.parseBoolean(args[0]);
        }
    }
     
    /**
     * Connects to leader
     */
    private static void connect() {
        socket = Config.getSocket();
        out = Config.getOut(socket);
        in = Config.getIn(socket);
        handshake();
    }
    
    
    /**
     * Initial request to leader
     */
    private static void handshake() {
        JSONObject request = new JSONObject();
        request.put("type", "handshake");
        request.put("isNode", "true");
        out.println(request.toString());
        System.out.println("\t\t\tsent: " + request.toString());//debug
        try {
            String stringReceived = in.readLine();
            if (stringReceived == null) {
                System.err.println("ERROR: No handshake response received.");
                System.exit(0);
            }
            JSONObject response = new JSONObject(stringReceived);
            System.out.println("\t\t\treceived: " + response.toString());//debug
            if (! response.has("type") || ! response.getString("type").equals("handshake")) {
                System.err.println("ERROR: Malformed handshake response received.");
                System.exit(0);
            }
        } catch (IOException | JSONException e) {
            System.err.println("ERROR: handshake failed: " + e.getMessage());
            System.exit(0);
        }   
    }
    
    
    //TODO exceptions and errors
    private static JSONObject add(JSONObject request) {
        JSONArray valuesArray = request.getJSONArray("values"); 
        int delay = request.getInt("delay");     
        int sum = 0;           
        if (faulty) sum++;

        for (int i = 0; i < valuesArray.length(); i++) {
            sum += valuesArray.getInt(i);
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); //restore the interrupted status
            }
        }  
        
        //build and return response
        JSONObject response = new JSONObject();
        response.put("type", "add");
        response.put("sum", sum); 
        return response;     
    }
    
    
    //TODO exceptions and errors
    private static JSONObject check(JSONObject request) {
        JSONArray valuesArray = request.getJSONArray("values"); 
        int thatSum = request.getInt("sum");
       
        int thisSum = 0;           
        if (faulty) thisSum++;

        for (int i = 0; i < valuesArray.length(); i++) {
            thisSum += valuesArray.getInt(i);
        }  
        
        boolean verified = thisSum == thatSum;
        
        //build and return response
        JSONObject response = new JSONObject();
        response.put("type", "check");
        response.put("same", verified); 
        return response;     
    }
    
    

}