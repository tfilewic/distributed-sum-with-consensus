package distributed;

import java.io.*;
import org.json.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.List;
import java.util.Queue;


/**
 * Leader class
 * 
 * @author tfilewic 
 * @version 2025-02-22
*/
public class Leader {
    
    private static final int MIN_NODES= 3;  //minimum number of nodes required to process sum request
    
    private static ServerSocket server = null; //server
    private static ClientHandler clientHandler = null;  //client handler connected to client

    private static List<Adder> adders = new ArrayList<>();  //list of adders connected to nodes 
    private static List<ConsensusChecker> consensusCheckers = new ArrayList<>();    //list of consensus checkers connected to nodes
    private static final Object newConnectionLock = new Object();   //mutex to block nodes from connecting while manipulating node lists
    private static final Object nodeLock = new Object();    //mutex to process both node handler lists at once
    
    private static List<Integer> values = new ArrayList<>();    //the values from the sum request for sequential processing
    private static Queue<PartialSum> partialSums;   //the values from the sum request partitioned for parallel processing
    private static final Object sumLock = new Object();    //mutex for partialSums

    private static int delay;   //delay in ms between each '+' operation
    private static final AtomicInteger distributedSum = new AtomicInteger();    //results from adder calculation
    private static volatile boolean consensus;  //result from consensus check
    private static boolean broken = false; //to handle node thread errors

    
    public static void main(String[] args) {
        
        System.out.println("Leader started...");
        server = Config.getServer();

        while (true) {
            acceptConnection();
        }
    }
    
    
    
    /**
     * Accepts an incoming connection and creates an appropriate Handler object if handshake succeeds
     */
    private static void acceptConnection() {
        Socket socket = null;
        BufferedReader in = null;
        PrintWriter out= null;
        
        try {
            socket = server.accept();
            in = Config.getIn(socket);
            out = Config.getOut(socket);
            
            String requestString = in.readLine();
            if (requestString != null) {
                
                //getRequest
                JSONObject request = new JSONObject(requestString);
                System.out.println("\t\t\treceived: " + request.toString());
                if (request.has("type") && request.getString("type").equals("handshake")) {
                    
                    JSONObject response = new JSONObject();
                    response.put("type", "handshake");
                    
                    if (request.has("isNode") && request.getBoolean("isNode")){
                        Adder adder = new Adder(socket, in, out);
                        ConsensusChecker consensusChecker = new ConsensusChecker(socket, in, out);
                        synchronized (newConnectionLock) {
                            adders.add(adder);
                            consensusCheckers.add(consensusChecker);   
                        }
                        
                        System.out.println("Node" + getNodeId(adder) + " connected.");

                    } else {
                        if (clientHandler != null) {
                            System.err.println("ERROR: only one client connection permitted");
                            response = Utilities.error("ERROR: Only one client connection permitted");
                            out.println(response.toString());
                           
                            return;
                        }
                        clientHandler = new ClientHandler(socket, in, out);
                        Thread clientThread = new Thread(clientHandler);
                        clientThread.start();                    
                    }     
                    out.println(response.toString());
                    System.out.println("\t\t\tsent: " + response.toString());//debug
                } 
            }
            
        } catch (IOException e) {
                    System.out.println("ERROR: failed to establish connection: " + e.getMessage());
                    Utilities.close(in, out, socket);
        }
    }
    
   
    /**
     * Handles a "sum" request
     * 
     * @return a repsonse to the "sum" request
     */
    private static JSONObject sum() {
        broken = false;
        consensus = true;
        long singleTime = 0;
        long distributedTime = 0;

        //count nodes
        if (!enoughNodes()) {
            return Utilities.error("ERROR: At least 3 Nodes are required to process this request.");
        }

        //get single sum and time
        long start = System.nanoTime();
        int singleSum = singleSum();
        long end = System.nanoTime();
        singleTime = end - start;
        
        synchronized (newConnectionLock)  { //block new nodes from connecting until distributedSum() and consensusCheck() complete
            //sets distributedSum, distributedTime, and consensus
            try {
                distributedTime = distributedSum();
            } catch (RuntimeException e) {
                return Utilities.error(e.getMessage());
            }
            
            //print times
            System.out.printf("%-33s %12d ns\n", "sequential processing time:", singleTime);     
            System.out.printf("%-33s %12d ns%n", "distributed processing time:", distributedTime);
            String winner = distributedTime < singleTime ? "distributed" : "sequential";
            System.out.println(winner + " wins!\n");
            
            try {
                consensus = checkConsensus();               
            } catch (RuntimeException e) {
                return Utilities.error("ERROR: " + e.getMessage());
            }
        }
       
        
        if (!consensus) {
            return Utilities.error("ERROR: Failed to establish node consensus.");
        }
        
        //build and return response
        JSONObject response = new JSONObject();
        response.put("type", "sum");
        response.put("sum", singleSum);
        response.put("singleTime", singleTime);
        response.put("distributedTime", distributedTime);
        return response;
    }
    
    
    /**
     * Calculates the singleSum
     */
    private static int singleSum() {
        int sum = 0;
        for (Integer value : values) {
            sum += value;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); //restore the interrupted status
            }
        }
        return sum;
    }
    
    /**
     * Spins Adder threads to calculate the distributedSum
     * 
     * @return the elapsed time to complete the sum
     */
    private static long distributedSum() {
        long start = System.nanoTime(); //start time
        distributedSum.set(0);

        partition();    //divide list
        
        List<Thread> threads = new ArrayList<>(); //stores thread references so they can be joined
        
        //spin Adder threads
        for (Adder node : adders) {
            Thread thread = new Thread(node);
            threads.add(thread);
            thread.start();
        }
        
        //wait for threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                
            }
        }      
        
        if (broken) throw new RuntimeException("ERROR: node failed.");
        
        System.out.println("distributed sum: " + distributedSum.get());

        long end = System.nanoTime();
        long distributedTime = end - start;
        return distributedTime;
    }
    
    /**
     * Runs adder threads to check sum consensus
     *
     * @return if consensus was established
     */
    private static boolean checkConsensus() {
        consensus = true;
        
        List<Thread> threads = new ArrayList<>(); //stores thread references so they can be joined
        
        //spin Adder threads
        for (ConsensusChecker node : consensusCheckers) {
            Thread thread = new Thread(node);
            threads.add(thread);
            thread.start();
        }
        
        //wait for threads to complete
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }     
        return consensus;
    }
    
    /**
     * Divides values list into equally sized PartialSum objects with default sum 0 and stores them in partialSums
     */
    private static void partition() {
        partialSums = new ConcurrentLinkedQueue<>();
        int nodes = getNodeCount();

        int totalSize = values.size();
        int base = totalSize / nodes;
        int remainder = totalSize % nodes;

        int start = 0;
        for (int i = 0; i < nodes; i++) {
            int end = start + base;
            if (remainder > 0) {
                end++;
                remainder--;
            }
            List<Integer> partition = new ArrayList<>(values.subList(start, end));
            PartialSum partialSum = new PartialSum(partition, 0);
            partialSums.add(partialSum);

            start = end;
        }
    }

    
    /**
     * Checks if enough nodes are connected to process a SUM request
     */
    private static boolean enoughNodes() {
        return getNodeCount() >= MIN_NODES;
    }
    
    /**
     * Dynamically assigns an id to the node handler
     * @param handler The Adder or ConsensusChecker object that contains the node connection
     * @return the id
     */
    private static int getNodeId(Handler handler) {
        if (handler instanceof Adder) {
            return adders.indexOf(handler) + 1;
        } else if (handler instanceof ConsensusChecker) {
            return consensusCheckers.indexOf(handler) + 1;
        }
        return 0;  //not found
    }
    
    /**
     * Gets the number of connected nodes
     * @return the number of connected nodes
     */
    private static int getNodeCount() {
        return adders.size();
    }
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    /**
     * Abstract class for Node handlers
     */
    private static abstract class Handler implements Runnable{
        protected Socket socket = null;
        protected BufferedReader in = null;
        protected PrintWriter out = null;

        public Handler(Socket socket, BufferedReader in, PrintWriter out) {
            this.socket = socket;
            this.in = in;
            this.out = out;
        }
        
        public abstract void run();      
    }
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    private static class ClientHandler extends Handler {
        
        public ClientHandler(Socket socket, BufferedReader in, PrintWriter out) {
            super(socket, in, out);
            System.out.println("Client connected");
        }
        
        /**
         * Handles client requests
         */
        @Override
        public void run() {
            System.out.println("ClientHandler thread started...");

            boolean quit = false;
            
            try {
                while (!quit) {
   
                    JSONObject response;
                    String requestString = in.readLine();
                    if (requestString != null) {
                        
                        JSONObject request = new JSONObject(requestString);
                        System.out.println("\t\t\treceived: " + request.toString());
                        
                        if (request.has("type") && request.getString("type").equals("sum")) {
                            response = sum(request);
                        } else if (request.has("type") && request.getString("type").equals("quit")) {
                            response = quit(request);
                            quit = true;
                        } else {
                            response = Utilities.error("ERROR: Invalid request: " + request.toString());
                        }                        
                        out.println(response.toString());   //send response
                        System.out.println("\t\t\tsent: " + response.toString());
                    }    
                }
            } catch (Exception e ) { 
                System.err.println("ERROR: Client connection failed" + e.getMessage());
            } finally {
                Utilities.close(in, out, socket);
                clientHandler = null;
            }
        }
        
        /**
         * Handles the sum request
         * @param request The sum request
         */
        private static JSONObject sum(JSONObject request) {    
            JSONObject response = new JSONObject();
            
            if (!request.has("delay") || !request.has("values")) {
                return Utilities.error("ERROR: Invalid request: " + request.toString());
            }
            try {
                //parse delay
                delay = request.getInt("delay");
                
                //parse values
                JSONArray valuesArray = request.getJSONArray("values"); 
                values = new ArrayList<>();
                for (int i = 0; i < valuesArray.length(); i++) {
                    values.add(valuesArray.getInt(i));
                }
                //get sum
                response = Leader.sum();
            } catch (JSONException | NumberFormatException e) {
                return Utilities.error("ERROR: Failed to parse request: " + e.getMessage());
            }         
            return response;
        }

        private static JSONObject quit(JSONObject request) {
            JSONObject response = new JSONObject();
            response.put("type", "quit");
            return response;
        }
      
    }
    
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    
    /**
     * Class to handle Node threads for calculating sums
     */
    private static class Adder extends Handler {

        public Adder(Socket socket, BufferedReader in, PrintWriter out) {
            super(socket, in, out);
        }
        
        @Override
        public void run() {
            int id = getNodeId(this);
            System.out.println("Adder" + id + " thread started...");
            
            //get the values to add from a partial sum object
            PartialSum partialSum = partialSums.remove();   
            JSONArray values = new JSONArray();
            List<Integer> numbers = partialSum.getNumbers();
            for (int number : numbers) {
                values.put(number);
            }
            
            //build request
            JSONObject request = new JSONObject();
            request.put("type", "add");
            request.put("delay", delay);
            request.put("values", values);
            
            int timeout = delay * values.length() + 2000; 
            int result = 0; //the calculated sum
            
            try {
                //send request to node
                out.println(request.toString());
                System.out.println("\t\t\tsent: " + request.toString());//debug       
                
                socket.setSoTimeout(timeout);  //set timeout
                
                //get response
                String stringReceived = in.readLine();
                JSONObject response = new JSONObject(stringReceived);
                System.out.println("\t\t\treceived: " + response.toString());//debug
                
                //get result from response      
                if (response.has("type") && response.getString("type").equals("add")) {
                    result = response.getInt("sum");
                }        
            } catch (SocketTimeoutException e) {
                broken = true;
                String error = "ERROR: Node" + id + " add request timed out.";
                System.err.println(error + " Removing node...");
                synchronized (nodeLock) {
                    id = getNodeId(this); //get id again
                    adders.remove(this);    //remove from adders
                    consensusCheckers.remove(id - 1); //remove from checkers
                }
                Utilities.close(in, out, socket);   //close connection
                throw new RuntimeException(error);
            } catch  (IOException | NullPointerException e) {
                broken = true;
                String error = "ERROR: Node" + id + " not connected: " + e.getMessage();
                System.err.println(error);
                Utilities.close(in, out, socket);
                synchronized (nodeLock) {
                    id = getNodeId(this); //get id again
                    adders.remove(this);    //remove from adders
                    consensusCheckers.remove(id - 1); //remove from checkers
                }
                Utilities.close(in, out, socket);   //close connection
                throw new RuntimeException(error);
            } catch (JSONException e) {
                broken = true;
                String error = "ERROR: failed to calculate distributed sum " + e.getMessage();
                System.err.println(error);
                throw new RuntimeException(error);
            }
            partialSum.setSum(result);  //set sum in partialSum object
            partialSum.setId(id);   //set addedBy id
            partialSums.add(partialSum);    //enqueue
            distributedSum.addAndGet(result);   //update total disributedSum
        }
    }
    
    
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    
    
    
    /**
     * Class to handle Node threads for checking consensus
     */
    private static class ConsensusChecker extends Handler {

        public ConsensusChecker(Socket socket, BufferedReader in, PrintWriter out) {
            super(socket, in, out);
        }
        
        @Override
        public void run() {
            int id = getNodeId(this);
            System.out.println("ConsensusChecker" + id + " thread started...");
            
            PartialSum partialSum= getPartialSumToVerify(); //dequeue a PartialSum
            
            //get values
            JSONArray values = new JSONArray();
            List<Integer> numbers = partialSum.getNumbers();
            for (int number : numbers) {
                values.put(number);
            }
            
            //get sum
            int sum = partialSum.getSum();
            
            //build request
            JSONObject request = new JSONObject();
            request.put("type", "check");
            request.put("sum", sum);
            request.put("values", values);
            
            try {
                out.println(request.toString());
                System.out.println("\t\t\tsent: " + request.toString());  //debug

                socket.setSoTimeout(2000);  //set timeout
                
                String stringReceived = in.readLine();
                JSONObject response = new JSONObject(stringReceived);
                System.out.println("\t\t\treceived: " + response.toString());//debug
                if (! response.getBoolean("same")) {
                    consensus = false;
                }
            } catch (IOException | NullPointerException| JSONException e) {   
                consensus = false;
                System.err.println("ERROR: failed to check consensus: " + e.getMessage());
            }

        }
        
        
        /**
         * Cycles the partialSums queue to find one that was counted by a different node
         * 
         * @return the PartialSum
         */
        private PartialSum getPartialSumToVerify() {
            PartialSum partialSum;
            synchronized (sumLock) {
                partialSum = partialSums.remove(); //dequeue a PartialSum         
                boolean oneLess = checkId(partialSum.getId());  //check its id
 
                //cycle queue to get the correct id, if necessary
                while (! oneLess) {
                    partialSums.add(partialSum); //put it back
                    partialSum = partialSums.remove(); //get the next one
                    oneLess = checkId(partialSum.getId()); //check the id
                }
            }
            return partialSum;
        }
        
        
        /**
         * Checks if an id is one less than this id
         *
         */
        private boolean checkId(int thatId) {
            int nodes = getNodeCount();
            int thisId = getNodeId(this);
            boolean oneLess = thisId - thatId == 1;
            boolean wrapAround = (thisId == 1 ) && (thatId == nodes);
                    
            return oneLess || wrapAround;
        }
        
            
    }
        
    
    
}


