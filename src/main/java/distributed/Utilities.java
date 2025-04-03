package distributed;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import org.json.*;


/**
 * Utilities class for shared methods
 * 
 * @author tfilewic 
 * @version 2025-02-22
*/
public class Utilities {

    /**
     * Creates an error response with no message
     * @return the response
     */
    public static JSONObject error() {
        JSONObject response = new JSONObject();
        response.put("type", "error");
        return response;
    }
    
    /**
     * Creates an error response with a message
     * @param message  The message to send
     * @return the response
     */
    public static JSONObject error(String message) {
        JSONObject response = new JSONObject();
        response.put("type", "error");
        response.put("message", message);
        return response;
    }
    
    
    /**
     *  Closes connection resources 
     */
    public static void close(BufferedReader in, PrintWriter out, Socket socket) {
        System.out.println("closing connection");
        try {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.close();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            System.err.println("ERROR: failed to close resources: " + e.getMessage());
        }
    }
    
}
