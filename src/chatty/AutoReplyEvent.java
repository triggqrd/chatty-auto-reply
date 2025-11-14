package chatty;

import java.util.Objects;

/**
 * Immutable event that captures details of a sent auto-reply for logging purposes.
 */
public class AutoReplyEvent {

    private final long matchedAtMillis;
    private final long sentAtMillis;
    private final String profileName;
    private final String triggerName;
    private final String channel;
    private final String user;
    private final String replyText;

    public AutoReplyEvent(long matchedAtMillis, long sentAtMillis, String profileName,
                          String triggerName, String channel, String user, String replyText) {
        this.matchedAtMillis = matchedAtMillis;
        this.sentAtMillis = sentAtMillis;
        this.profileName = Objects.requireNonNull(profileName);
        this.triggerName = Objects.requireNonNull(triggerName);
        this.channel = Objects.requireNonNull(channel);
        this.user = Objects.requireNonNull(user);
        this.replyText = Objects.requireNonNull(replyText);
    }

    public long getMatchedAtMillis() {
        return matchedAtMillis;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }

    public long getDelayMillis() {
        return sentAtMillis - matchedAtMillis;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getTriggerName() {
        return triggerName;
    }

    public String getChannel() {
        return channel;
    }

    public String getUser() {
        return user;
    }

    public String getReplyText() {
        return replyText;
    }
}
