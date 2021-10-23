import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChatServerSocketListener  implements Runnable {
    private Socket socket;

    private ClientConnectionData client;
    private List<ClientConnectionData> clientList;
    private List<String> clientListNames;
    private boolean runningVoteKick;
    private int votes = 0;

    public ChatServerSocketListener(Socket socket, List<ClientConnectionData> clientList) {
        this.socket = socket;
        this.clientList = clientList;
        this.clientListNames = new ArrayList<>();
        this.runningVoteKick = false;
    }


    private void setup() throws Exception {
        ObjectOutputStream socketOut = new ObjectOutputStream(socket.getOutputStream());
        ObjectInputStream socketIn = new ObjectInputStream(socket.getInputStream());
        String name = socket.getInetAddress().getHostName();

        client = new ClientConnectionData(socket, socketIn, socketOut, name);
        clientList.add(client);

        System.out.println("added client " + name);

    }

    private void processChatMessage(MessageCtoS_Chat m) {
        System.out.println("Chat received from " + client.getUserName() + " - broadcasting");
        broadcast(new MessageStoC_Chat(client.getUserName(), m.msg), client);
        if (m.msg.startsWith("/users")) {
            //broadcast(new MessageStoC_Chat(client.getUserName(), Integer.toString(clientList.size())), client);
            broadcast(new MessageStoC_Chat("Users: "));
            for (String names: clientListNames) {
                broadcast(new MessageStoC_Chat(names));
            }
            System.out.println();
        }
        if (m.msg.startsWith("/kick")) {
            callVoteKick(m.msg);
        }

    }

    private void processVoteMessage(MessageCtoS_Vote m) {
        if (!runningVoteKick) {
            broadcast(new MessageStoC_Chat("No vote is running!"));
            return;
        }
        if (client.getVoted()) {
            broadcast(new MessageStoC_Chat(client.getUserName() + " has already voted!"));
            return;
        }
        if (m.boolVote) {
            votes++;
            broadcast(new MessageStoC_Chat(client.getUserName() + " voted yes!"));
            client.setVoted(true);
            checkVotes();
        }
        else {
            broadcast(new MessageStoC_Chat(client.getUserName() + " voted no!"));
            client.setVoted(true);
            checkVotes();
        }
    }

    private void callVoteKick(String m) {
        String userToKick = "";
        try {
            userToKick = m.substring(6);
        } catch (StringIndexOutOfBoundsException e) {
            broadcast(new MessageStoC_Chat("User does not exist!"));
            return;
        }
        if (!clientListNames.contains(userToKick)) {
            broadcast(new MessageStoC_Chat("User does not exist!"));
        }
        else {
            String message = "Vote kick has started! " + clientListNames.size()/2 + " votes are necessary to kick " + userToKick;
            votes=0;
            runningVoteKick = true;
            broadcast(new MessageStoC_Chat("Vote kick has started! Vote with \"/vote yes\" or \"/vote no\" to kick " + userToKick));
            checkVotes();

        }
    }

    private void checkVotes() {
        if (votes > clientList.size()/2) {

        }
    }

    /**
     * Broadcasts a message to all clients connected to the server.
     */
    public void broadcast(Message m, ClientConnectionData skipClient) {
        try {
            System.out.println("broadcasting: " + m);
            for (ClientConnectionData c : clientList){
                // if c equals skipClient, then c.
                // or if c hasn't set a userName yet (still joining the server)
                if ((c != skipClient) && (c.getUserName()!= null)){
                    c.getOut().writeObject(m);
                }
            }
        } catch (Exception ex) {
            System.out.println("broadcast caught exception: " + ex);
            ex.printStackTrace();
        }
    }

    public void broadcast(Message m) {
        try {
            System.out.println("broadcasting: " + m);
            for (ClientConnectionData c : clientList){
                // if c equals skipClient, then c.
                // or if c hasn't set a userName yet (still joining the server)
                if (c.getUserName() != null)
                    c.getOut().writeObject(m);

            }
        } catch (Exception ex) {
            System.out.println("broadcast caught exception: " + ex);
            ex.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            setup();
            ObjectInputStream in = client.getInput();

            MessageCtoS_Join joinMessage = (MessageCtoS_Join)in.readObject();
            client.setUserName(joinMessage.userName);
            clientListNames.add(client.getUserName());
            broadcast(new MessageStoC_Welcome(joinMessage.userName), client);

            while (true) {
                Message msg = (Message) in.readObject();
                if (msg instanceof MessageCtoS_Quit) {
                    break;
                }
                else if (msg instanceof MessageCtoS_Vote) {
                    processVoteMessage((MessageCtoS_Vote) msg);
                }
                else if (msg instanceof MessageCtoS_Chat) {
                    processChatMessage((MessageCtoS_Chat) msg);
                }
                else {
                    System.out.println("Unhandled message type: " + msg.getClass());
                }
            }
        } catch (Exception ex) {
            if (ex instanceof SocketException) {
                System.out.println("Caught socket ex for " +
                        client.getName());
            } else {
                System.out.println(ex);
                ex.printStackTrace();
            }
        } finally {
            //Remove client from clientList
            clientList.remove(client);
            clientListNames.remove(client.getUserName());

            // Notify everyone that the user left.
            broadcast(new MessageStoC_Exit(client.getUserName()));

            try {
                client.getSocket().close();
            } catch (IOException ex) {}
        }
    }

}
