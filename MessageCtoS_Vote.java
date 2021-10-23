public class MessageCtoS_Vote extends Message {
    public String vote;
    public boolean boolVote;
    public MessageCtoS_Vote(String vote) {
        this.vote = vote.toLowerCase();
        if (vote.contains("yes")) {
            boolVote = true;
        }
        else if (vote.contains("no")) {
            boolVote = false;
        }
    }
}
