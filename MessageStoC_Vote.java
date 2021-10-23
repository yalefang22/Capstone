public class MessageStoC_Vote extends Message {
    public String userName;
    public String vote;
    public boolean boolVote;

    public MessageStoC_Vote(String userName, String vote) {
        this.userName = userName;
        this.vote = vote;
        if (vote.contains("yes")) {
            boolVote = true;
        }
        else if (vote.contains("no")) {
            boolVote = false;
        }
    }

    public MessageStoC_Vote(String vote) {
        this.vote = vote;
        if (vote.contains("yes")) {
            boolVote = true;
        }
        else if (vote.contains("no")) {
            boolVote = false;
        }
    }

    public String toString() {
        return userName + " voted: " + vote;
    }
}
