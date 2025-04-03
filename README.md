# Assignment 5 Distributed Algorithms
Tyler Filewich  tfilewic


# Running the program
node(s) and client both require the leader to be run first in order to establish a connection
append -q --console=plain to each command for cleaner display

## Leader
Run the Leader with:
    gradle leader 

## Client
Run the Client with:
    gradle client

## Node
note: leader requires at least 3 nodes to process client requests

run a single Node with:
    gradle node -Pfault=<boolean> 

    where:
        id is the number (integer) to assign the Node (default 0).
        fault is whether or not (boolean) the node should be be faulty (default false)  
        note: -Pfault=1 will also work as true  

OR 

run multiple nodes via bash script with:
    gradle nodes -Pcount=<int>  -Pfaults=<int> 
    
    where:
        count is the number (integer) of nodes to run (default 1).  The Leader requires 3 or more to process client requests.
        faults is the number (integer) of those nodes which should be faulty (default 0)   



 


# Program description
The program adds numbers using both sequential and distributed processing.  The user enters a delay time and any number of integers, one at a time, then enters any non-integer key to send the request.  After the calculation is performed, with a delay between each operation, the sum and the times in nanoseconds taken to complete each method are displayed.  The program requires at least 3 connected Nodes to perform this calculation.

It is structured with a single Client and multiple Nodes that all connect to a Leader.  The Leader has inner classes to handle these connections.  The Client is handled by a ClientHandler object, which runs a thread after a successful handshake and loops until the Client quits.  The Nodes are each handled by their own Adder and a ConsensusChecker objects.  These are created upon successful handshake and stored until the Leader receives a sum request from the client, at which point their threads are created and run for a single response.  Additional sum requests run new threads.

The Leader partitions the values in the sum request into equal parts, encapsulates them in PartialSum objects, and adds those to a queue.  Each Adder removes one PartialSum and sends its information to the 

Nodes can be declared faulty, which will cause them to return an incorrect sum.  The ConsensusCheckers verify the results of the previous Node's calculations- if they do not reach consensus, the sum is declared invalid and an error message is returned to the Client.


# Protocol

## HANDSHAKE

### Request:
Client, Node
    required:
        "type" : "handshake"
    optional:
        "isNode" : <boolean>    -- if the requester is a node

### Response:
Leader
    required:
        "type" : "handshake"

## SUM

### Request:
Client, Leader
    required:
        "type" : "sum"
        "delay" : <int>     -- delay in ms to add between each sum operation
        "values" : [<int>, <int>, ...]      --the list of numbers to sum

### Response:
Leader
    required:
        "type" : "sum"
        "sum" : <int>
        "singleTime" : <number>     -- time in nanoseconds
        "distributedTime" : <number>    -- time in nanoseconds




## ADD

### Request:
Leader
    required:
        "type" : "add"
        "delay" : <int>     -- delay in ms to add between each sum operation
        "values" : [<int>, <int>, ...]      --the list of numbers to sum

### Response:
Node
    required:
        "type" : "add"
        "sum" : <int>


## CHECK

### Request:
Leader
    required:
        "type" : "check"
        "sum" : <int>     -- the sum to verify
        "values" : [<int>, <int>, ...]      --the list of numbers to sum

### Response:
Node
    required:
        "type" : "check"
        "same" : <boolean>  -- if the node calculated the same result


## QUIT

### Request:
Client
    required:
        "type" : "quit"

### Response:
Leader
    required:
        "type" : "check"
        "same" : <boolean>  -- if the node calculated the same result


## ERROR RESPONSE
General response to be used for all errors
### Error Response:
Leader, Client, Node
    required:
        "type" : "error"
        "message" : <String>


# Workflow
d) (3 points) An explanation of the program’s intended workflow (or what was implemented if some parts didn’t work).
run leader, then run client and nodes
client and nodes send handshake request, receive response- nodes update their id from the response
clienthandler is created and thread run, adders, and consensus checkers are created and stored in a list
client enters '1' to select sum option
client is prompted and enters a delay
client is prompted and enters an integer- repeats until the client enters any other key
sum request is sent from client to leader
if valid, leader calculates the sum sequentially and times the calculation
leader divides the list into equal parts and creates partial sum objects to add to queue
leader creates and runs a thread for each adder, and waits for them to complete, timing the process
adders each pull a partial sum from the queue and create and sends a request to their respective node
if the request fails due to disconnected node or other problems, both handlers for that node are removed and the leader is updated, an error is returned to the client
nodes calculate the sum and send a response
adders update the distributedSum variable and set the sum in their partialSum object, then return it to the queue and complete
leader creates and runs a thread for each consensusChecker, and waits for them to complete
consensusCheckers cycle the queue until they find the partialSum completed by the node before them, then send a request to their respective node
if the request fails due to disconnected node or other problems, consensus is set to false
nodes calculate the sum check if it is the same as provided, then send their response
consensusCheckers set the consensus variable to false if the response was false and complete
leader checks the consensus variable and sends error response if false, or sum and times if true
client receives and displays response
client displays menu again- process repeats if sum is selected
if quit is selected, client sends quit request
leader receives request, responds, and closes client resources
client closes resources and exits



# Requirements fulfilled
e) (2 points) A list of the requirements you believe your program fulfills.

- [X] 1. (3 points) The Leader can be started via a Gradle task; explain how to do this in the README.
- [X] 2. (3 points) The Client can be started via a Gradle task; explain how to do this in the README.
- [X] 3. (3 points) Each Node can be started via a Gradle task; explain how to do this in the README. Important: there should be only one ’Node.java’ class, with all nodes in the system being instances of this single class.
- [X] 4. (3 points) Ensure at least 3 Nodes are connected to the Leader.
- [X] 5. (3 points) If there are fewer than 3 Nodes in the network, the Leader should send an error message to the Client and stop processing.
- [X] 6. (3 points) Client Input: The client should ask the user to input a list of numbers and a delay time, which it then sends to the leader. Example list: ’[1, 2, 3, 4, 5, ..., 15]’, delay: 50ms.
- [X] 7. (5 points) Single Sum Calculation: The leader should calculate the sum on its own, adding X milliseconds delay to each iteration to simulate a time-consuming calculation. For example, for the list ’[1, 2, 3, 4]” with a delay of 50ms, it would add: ’1+2 (sleep 50ms), 3+3 (sleep 50ms), 6+4 (sleep 50ms)’, resulting in a computation time and final sum.
- [X] 8. (5 points) Leader Divides List: The leader should divide the list into equal parts, e.g., with 3 nodes: Node 1: ’[1, 2, 3, 4, 5]’ Node 2: ’[6, 7, 8, 9, 10]’ Node 3: ’[11, 12, 13, 14, 15]’
- [X] 9. Distributed Sum Calculation:
    - [X] (6 points) The leader sends each node a portion of the list along with the delay specified by the client.
    - [X] (7 points) This should be threaded so that nodes calculate in parallel, not sequentially.
    - [X] (5 points) Each node computes the sum of its portion, applying the delay between each addition as the leader did.
    - [X] (4 points) After receiving all partial sums from the nodes, the leader combines these and calculates the total sum and time taken for distributed processing.
- [X] 10. (4 points) Performance Comparison: The leader compares the time taken for single sum processing with distributed processing and prints the result. If node communication is not threaded, the distributed version will generally be slower. Perform this comparison regardless of threading status, but note that threading is likely to show distributed advantages, depending on the delay time chosen.
- [X] 11. (5 points) Simulating Faulty Nodes: Nodes can simulate faults, in which case they will calculate the sum of the given list incorrectly. Use a Gradle flag ‘-pFault=1‘ to make a node perform an incorrect calculation.
- [X] 12. Consensus Check for Result Verification:
    - [X] (4 points) The leader sends each node the sum and list from another node (e.g., shifting the list to the next node).
    - [X] (3 points) Sending this information should also be threaded.
    - [X] (4 points) Each node recalculates the sum (with or without delay) and compares it with the sum received from the leader.
    - [X] (4 points) Nodes return a true/false (or yes/no, or similar) response to the leader to indicate agreement with the results. Nodes should always respond "yes" if there are no faulty nodes in the network.
    - [X] (5 points) If all nodes agree on the result, the leader sends the final sum and computation times to the client. If consensus fails, the leader sends an error message to the client.
- [X] 13. (3 points) Client Output: The client displays the results clearly, showing the sum and calculation times.
- [X] 14. Error Handling: Ensure robust error handling, informative messaging, and no program crashes. Up to 10% of points may be deducted for poor error handling.
- [X] 15. (5 points) Analysis: In your README, analyze whether distributing the calculation provided any advantages. Test with different delays and list sizes, and explain whether distribution was faster or slower and why.

# Analysis
Though it would be lessened with more network latency, there is a clear performance advantage to using the distributed calculation.  Only at the minimum delay with the minimum number of nodes and values does the sequential processing outperform the distributed (and only by about 30%).  With any change at all- adding a fourth node, increasing the delay by 10ms, or giving even one of the nodes more than a single value- makes the distributed calculation faster.  


# Screencast
f) (4 points) A screencast showing all functionality. Go through each requirement you fulfilled to demonstrate functionality in the screencast before we test it ourselves.
https://youtu.be/nL1gql-PASo