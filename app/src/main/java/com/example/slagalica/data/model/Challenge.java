package com.example.slagalica.data.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Izazov — solo prolazak kroz svih šest igara, sa ulogom u zvezdama i žetonima.
 * RTDB POJO čvor na putanji {@code challenges/{challengeId}}.
 */
public class Challenge {

    private String id;
    private String hostUid;
    private String hostName;
    /**
     * Regionalni ključ (vidi {@link com.example.slagalica.util.RegionKey}) —
     * koristi se za filtriranje otvorenih izazova po regionu.
     */
    private String region;
    private int stakeStars;
    private int stakeTokens;
    /** "open" | "playing" | "finished" */
    private String status;
    private long createdAt;
    private Map<String, ChallengeParticipant> participants;

    /** Obavezan prazan konstruktor za Realtime Database. */
    public Challenge() {
        participants = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getHostUid() { return hostUid; }
    public void setHostUid(String hostUid) { this.hostUid = hostUid; }

    public String getHostName() { return hostName; }
    public void setHostName(String hostName) { this.hostName = hostName; }

    public String getRegion() { return region; }
    public void setRegion(String region) { this.region = region; }

    public int getStakeStars() { return stakeStars; }
    public void setStakeStars(int stakeStars) { this.stakeStars = stakeStars; }

    public int getStakeTokens() { return stakeTokens; }
    public void setStakeTokens(int stakeTokens) { this.stakeTokens = stakeTokens; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public Map<String, ChallengeParticipant> getParticipants() { return participants; }
    public void setParticipants(Map<String, ChallengeParticipant> participants) { this.participants = participants; }
}
