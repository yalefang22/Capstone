import java.io.EOFException;
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
    private boolean runningVoteKick;
    private int yesVotes = 0;
    private int noVotes = 0;
    private ClientConnectionData userToKick;

    public ChatServerSocketListener(Socket socket, List<ClientConnectionData> clientList, boolean runningVoteKick) {
        this.socket = socket;
        this.clientList = clientList;
        this.runningVoteKick = runningVoteKick;
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
        broadcast(new MessageStoC_Chat(client.getUserName(), m.msg));
        if (m.msg.startsWith("/users")) {
            //broadcast(new MessageStoC_Chat(client.getUserName(), Integer.toString(clientList.size())), client);
            broadcast(new MessageStoC_Chat("Users: "));
            for (ClientConnectionData client: clientList) {
                broadcast(new MessageStoC_Chat(client.getUserName()));
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
            yesVotes++;
            broadcast(new MessageStoC_Chat(client.getUserName() + " voted yes!"));
            client.setVoted(true);
            checkVotes(userToKick);
        }
        else {
            noVotes++;
            broadcast(new MessageStoC_Chat(client.getUserName() + " voted no!"));
            client.setVoted(true);
            checkVotes(userToKick);
        }
    }

    private void callVoteKick(String m) {

        String u = "";
        try {
            u = m.substring(6);
        } catch (StringIndexOutOfBoundsException e) {
            broadcast(new MessageStoC_Chat("User does not exist!"));
            return;
        }

        boolean uInList = false;
        for (int i = 0; i < clientList.size(); i++) {
            if (clientList.get(i).getUserName().equals(u)) {
                uInList = true;
            }
        }

        if (!uInList) {
            broadcast(new MessageStoC_Chat("User does not exist!"));
        }
        else {
            for (int i = 0; i < clientList.size(); i++) {
                if (clientList.get(i).getUserName().equals(u)) {
                    userToKick = clientList.get(i);
                }
                clientList.get(i).setVoted(false);
            }
            yesVotes=0;
            noVotes=0;
            runningVoteKick = true;

            int votesNecessary = 1 + clientList.size()/2;
            if (votesNecessary == 1)
                broadcast(new MessageStoC_Chat("Vote kick has started! " + votesNecessary + " yes vote is necessary to kick " + u + ". Vote with \"/vote yes\" or \"/vote no\""));
            else
                broadcast(new MessageStoC_Chat("Vote kick has started! " + votesNecessary + " yes votes are necessary to kick " + u + ". Vote with \"/vote yes\" or \"/vote no\""));

            for (int i = 0; i < clientList.size(); i++) {
                if (clientList.get(i).getUserName().equals(u)) {
                    userToKick = clientList.get(i);
                }
            }
            checkVotes(userToKick);

        }
    }

    private void checkVotes(ClientConnectionData u){
        if (yesVotes > clientList.size() / 2) {
            //Remove client from clientList
            broadcast(new MessageStoC_Chat(u.getUserName() + " will be kicked!"));
            clientList.remove(u);

            // Notify everyone that the user left.
            broadcast(new MessageStoC_Exit(u.getUserName()));
            try {
                u.getSocket().close();
            } catch (IOException ex) {
            }
        }
        if (noVotes > clientList.size()/2) {
            if (noVotes == 1) {
                broadcast(new MessageStoC_Chat(noVotes + " person voted no! " + u.getUserName() + " will not be kicked!"));
            }
            else
                broadcast(new MessageStoC_Chat(noVotes + " people voted no! " + u.getUserName() + " will not be kicked!"));
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
            broadcast(new MessageStoC_Welcome(joinMessage.userName));

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

            // Notify everyone that the user left.
            broadcast(new MessageStoC_Exit(client.getUserName()), client);

            try {
                client.getSocket().close();
            } catch (IOException ex) {}
        }
    }

}
