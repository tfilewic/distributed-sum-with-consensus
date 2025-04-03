package distributed;


import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;



/**
 * Config class to hold common connection configuration
 * SER321 Assignment 5
 * 
 * @author tfilewic 
 * @version 2025-02-20
*/
public class Config {
    
    private static final String HOST = "localhost";
    protected static final int PORT = 8888;
    

    /**
     * Creates and connects a socket to the default host and port
     * @return the connected socket
     */
    protected static Socket getSocket() {
        Socket socket = null;

        try {
            socket = new Socket(HOST, PORT);

        } catch (IOException e) {
            System.err.println(
                    "ERROR: Failed to connect to " + HOST + ":" + PORT + ".\n" + e.getMessage() + "\nExiting...");
            System.exit(0);
        }

        if (socket == null) {
            System.err.println("ERROR: Connection refused by " + HOST + ":" + PORT);
            System.exit(0);
        }
        System.out.println("Connected to " + HOST + ": " + PORT);
        return socket;
    }

    /**
     * Creates a printWriter
     * @param socket The connected socket
     * @return the printWriter
     */
    protected static PrintWriter getOut(Socket socket) {
        PrintWriter out = null;

        if (socket == null) {
            System.err.println("ERROR: Socket is null. Exiting...");
            System.exit(0);
        }

        try {
            out = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create PrintWriter:\n" + e.getMessage() + "\nExiting...");
            System.exit(0);
        }

        if (out == null) {
            System.err.println("ERROR: Failed to create PrintWriter.  Exiting...");
            System.exit(0);
        }
        return out;
    }

    
    /**
     * Creates a buffered reader
     * @param socket The connected socket
     * @return the buffered reader
     */
    protected static BufferedReader getIn(Socket socket) {
        BufferedReader in = null;
        
        if (socket == null) {
            System.err.println("ERROR: Socket is null. Exiting...");
            System.exit(0);
        }
        
        try {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.err.println("ERROR: Failed to create BufferedReader:\n" + e.getMessage() + "\nExiting...");
            System.exit(0);
        }
        if (in == null) {
            System.err.println("ERROR: Failed to create BufferedReader.  Exiting...");
            System.exit(0);
        }
        return in;
    }
    
    /**
     * Creates a server socket
     * @return the server socket
     */
    protected static ServerSocket getServer() {
        ServerSocket server = null;

        try {
            server = new ServerSocket(PORT);
        } catch (IOException e) {
            System.err.println("ERROR: Failed to start server: " + e.getMessage());
            System.exit(0);
        }

        if (server == null) {
            System.err.println("ERROR: Failed to create server.");
            System.exit(0);
        }
        System.out.println("Listening on port: " + PORT);
        return server;
    }


}