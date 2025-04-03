# Distributed Sum with Consensus
This was an assignment from a distributed systems class.  It implements a distributed, concurrent client-server system in Java using socket communication and a simple custom protocol.  A leader coordinates multiple worker nodes to perform distributed sum calculations with optional fault simulation and consensus checking, while also computing the result locally for comparison.

# Technologies Used

- **Java**: Core language
- **Sockets**: TCP communication between client, leader, and nodes
- **Multithreading**: Concurrent node handling and sum calculation
- **JSON (org.json)**: Data format for communication
- **Gradle**: Build automation

  
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
The program adds numbers using both sequential and distributed processing.  The user enters a delay time and any number of integers, one at a time, then then signals the request by entering a non-integer key.  After the calculation is performed, with a delay between each operation, the sum and the times in nanoseconds taken to complete each method are displayed.  The program requires at least 3 connected Nodes to perform this calculation.

It is structured with a single Client and multiple Nodes that all connect to a Leader.  The Leader has inner classes to handle these connections.  The Client is handled by a ClientHandler object, which runs a thread after a successful handshake and loops until the Client quits.  The Nodes are each handled by their own Adder and ConsensusChecker objects.  These are created upon successful handshake and stored until the Leader receives a sum request from the client, at which point their threads are created and run for a single response.  Additional sum requests run new threads.

The Leader partitions the values in the sum request into equal parts, encapsulates them in PartialSum objects, and adds those to a queue.  Each Adder removes one PartialSum and sends its information to a connected Node for processing.

Nodes can be declared faulty, which will cause them to return an incorrect sum.  The ConsensusCheckers verify the results of the previous Node's calculations- if they do not reach consensus, the sum is declared invalid and an error message is returned to the Client.
  
