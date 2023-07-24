import java.net.*;
import java.io.*;
import java.util.*;

public class PeerToPeerSystem {
    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.out.println("Usage: java PeerToPeer <port> <peer1_port> <peer2_port> [filename1] [filename2] <Query_for_File>...");
            return;
        }
        // Generate random ID between 100 and 500
        
        int id = new Random().nextInt(500 - 100 + 1) + 100;
        System.out.println("Generated ID: " + id);
         // Parse command line arguments
        int port = Integer.parseInt(args[0]);
        int peer1Port = Integer.parseInt(args[1]);
        int peer2Port = Integer.parseInt(args[2]);
        List<String> filenames = List.of(args).subList(3, args.length-1);
        String query_file= args[args.length-1];
        // Create and start the node
        Node node = new Node(id, port, new int[]{peer1Port, peer2Port}, filenames,query_file);
        node.start();
        
        
    }
}



    class ConnectionHandler extends Thread {
        private Socket clientSocket;
        private int nodeId;
        public boolean isActive;
        private Node node;
        private int received_id=0;
        
    
        public ConnectionHandler(Socket clientSocket, int nodeId, Node node) {
            this.clientSocket = clientSocket;
            this.nodeId = nodeId;
            this.isActive = true;
            this.node = node;
        }
    
        public void run() {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
    
                // read messages from peer and procees and print them
                while (isActive) {
                    String peerMessage = in.readLine();
                    System.out.println("Node " + getNodeId() + " received: " + peerMessage);
                    if (peerMessage == null || peerMessage.contains("false")) {
                        isActive = false;
                        break;
                    }
                    if (peerMessage.contains("ID")){
                        String[] parts = peerMessage.split(":");
                        //String indexPart = parts[0].trim();
                        //int received_index = Integer.parseInt(indexPart);
                        String idPart = parts[1].trim();
                        received_id = Integer.parseInt(idPart);
                        node.addConnectedPeerIds(received_id);
                        node.decideLeader();
                        
                    }
                    if(peerMessage.contains("Leader")){
                        String[] parts = peerMessage.split(":");
                        String leaderPart = parts[1].trim();
                        if(leaderPart.equals("true")){
                            int leaderPort=Integer.parseInt(parts[2].trim());
                            node.setLeaderPort(leaderPort);
                            int leaderId=Integer.parseInt(parts[3].trim());
                            //node.setLeaderPort(leaderPort);
                            node.closeNonLeaderConnections(leaderPort,leaderId);
                            
                        }
                    }
                    if(peerMessage.contains("Files")){
                        String[] parts = peerMessage.split(":");
                        int file_port=Integer.parseInt(parts[2].trim());
                        String[] files= parts[3].trim().split(",");
                        if(node.isLeader())
                        {node.portFiles.put(file_port,files);
                            node.leaderReadyMessage();
                        }
                    }
                    if(peerMessage.equals("READY")){
                        //node.readyForFileQuery=true;
                        node.fileQueryByPeer();

                    }
                    if(peerMessage.contains("FILE_QUERY")){
                        String[] parts = peerMessage.split(":");
                        int from_port=Integer.parseInt(parts[2].trim());
                        String filename= parts[3].trim();
                        node.queryLeaderForFile(filename, from_port);
                    }
                    
                }
    
                // close the connection
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    
        public int getNodeId() {
            return nodeId;
        }
        public int getReceivedId(){
            return received_id;
        }
        public Socket getClienSocket(){
            return clientSocket;
        }
    }
    


class Node {
    private int id;
    private int port;
    private int[] peerPorts;
    private List<String> filenames;
    HashMap<Integer, String[]> portFiles;
    private ConnectionHandler[] connectionHandlers;
    PrintWriter[] out;
    private Boolean leader= false;
    private int leaderPort=0;
    Socket[] clientSockets;
    public boolean readyForFileQuery=false;
    private String queryFile;
    public int getLeaderPort() {
        return leaderPort;
    }
    public void leaderReadyMessage() {
        //send all peer a message that leader is ready [it have all the files in the system]
        for (PrintWriter p : out) {
            p.println("READY");
        }
    }
    public void queryLeaderForFile(String filename,int fromPort){
        int filePort=0;
        if(leader){
            //first check own files
            for (String f : filenames) {
                if (filename.equals(f)){
                    filePort=port;
                }    
            }
            if (filePort==0){
                for (int peer_port : portFiles.keySet()){
                    String[] peerFiles= portFiles.get(peer_port);
                    for (String f : peerFiles) {
                        if (filename.equals(f)){
                            filePort=peer_port;
                        }    
                    }
                }
            }
            for (int i = 0; i < clientSockets.length; i++) {
                Socket client= clientSockets[i];
                String msg="";
                if(filePort!=0 && client.getPort()==fromPort){
                    msg="file: "+filename+": port :"+filePort;
                }
                else{
                    msg="file: "+filename+" NOT FOUND";
                }
                try{
    
                    
                    out[i].println( msg);
                    
                
            }catch(Exception e){
    
            }
            }
           
        }
    }
    public void fileQueryByPeer(){
        
            System.out.println("Now you can query for a file");
            
            String filename= queryFile;
            for (int i = 0; i < clientSockets.length; i++) {
                Socket client= clientSockets[i];
    
                try{
                //OutputStream o=client.getOutputStream();
                if (client.getPort() == leaderPort) {
    
                    out[i].println( "FILE_QUERY : Port :"+port+":"+filename);
                    
                }
            }catch(Exception e){
    
            }
            }
        
    }
    public void setLeaderPort(int leaderPort) {
        this.leaderPort = leaderPort;
        System.out.println("leader set");
    }
    List<Integer>connectedPeerIds = new ArrayList<Integer>() ;
    public void setLeader(boolean leader) {
        this.leader = leader;
    }
    public boolean isLeader() {
        return leader;
    }
    public void addConnectedPeerIds(int idx){
        connectedPeerIds.add(idx);
        return;
    }
    public void decideLeader() {
        if (leaderPort == 0) {
            System.out.println("Deciding leader: " + connectedPeerIds.size());
            if (connectedPeerIds.size() == 2) {
                boolean smallerThanAllConnectedPeers = true;
                for (Integer idx : connectedPeerIds) {
                    if (idx < this.id) {
                        smallerThanAllConnectedPeers = false;
                        break;
                    }
                }
                this.leader = smallerThanAllConnectedPeers;
                if (this.leader) {
                    for (PrintWriter p : out) {
                        p.println("Leader: " + this.leader + ":" + this.port+":"+this.id);
                    }
                    this.leaderPort = this.port;
                    System.out.println("Leader status: " + this.leader);
                }
            } else {
                System.out.println("Not all peers are connected");
            }
        }
    }
    
    

    
    public Node(int id, int port, int[] peerPorts, List<String> filenames,String queryFile) {
        this.id = id;
        this.port = port;
        this.peerPorts = peerPorts;
        this.filenames = filenames;
        this.connectionHandlers = new ConnectionHandler[peerPorts.length];
        this.out = new PrintWriter[peerPorts.length];
        this.clientSockets = new Socket[peerPorts.length];
        this.portFiles= new HashMap<Integer,String[]>();
        this.queryFile=queryFile;
    }
    
    public void start() {
        try {
            // Create server socket
            ServerSocket serverSocket = new ServerSocket(port);

            // Create client sockets to connect to peers
            
            for (int i = 0; i < peerPorts.length; i++) {
                while (true) {
                    try {
                        clientSockets[i] = new Socket("localhost", peerPorts[i]);
                        break;
                    } catch (ConnectException e) {
                        // Connection failed, retry after a delay
                        Thread.sleep(1000);
                    }
                }
            }

            // Create output stream for each socket
            
            for (int i = 0; i < clientSockets.length; i++) {
                out[i] = new PrintWriter(clientSockets[i].getOutputStream(), true);
            }

            
            //Send ID to all peers
            for (PrintWriter p : out) {
                p.println("ID :" + id );
            }

            
            

            // Accept incoming connections
            while (true) {
                Socket clientSocket = serverSocket.accept();

                // Create a separate handler to handle the connection
                ConnectionHandler handler = new ConnectionHandler(clientSocket, id, this);
                int index = findFreeHandlerIndex();
                if (index != -1) {
                    connectionHandlers[index] = handler;
                    handler.start();
                } else {
                    // If no free handler slot is available, reject the connection
                    clientSocket.close();
                }
                
    

            }
            

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }



    }

    private int findFreeHandlerIndex() {
        for (int i = 0; i < connectionHandlers.length; i++) {
            if (connectionHandlers[i] == null || !connectionHandlers[i].isAlive()) {
                return i;
            }
        }
        return -1; // No free handler slot available
    }

    public synchronized void removeConnectionHandler(ConnectionHandler handler) {
        for (int i = 0; i < connectionHandlers.length; i++) {
            if (connectionHandlers[i] == handler) {
                connectionHandlers[i] = null;
                break;
            }
        }
    }
    public void closeNonLeaderConnections(int leaderPort,int leaderId) {
        System.out.println("Removing Connection: ");
        for (int i = 0; i < clientSockets.length; i++) {
            Socket client= clientSockets[i];

            try{
            //OutputStream o=client.getOutputStream();
            if (client.getPort() != leaderPort && port!=leaderPort) {
                System.out.println("Removing Connection: "+client.getPort());
                
                
                out[i].close();
                client.close();
                
                
            }
            else{
                //send filenames
                String fileString = String.join(",", filenames);
                
                out[i].println( "Files : Node Port :"+port+":"+fileString);
                
            }
        }catch(Exception e){

        }
        }
    }
}

