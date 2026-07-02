package com.example.slagalica.data.model;

/**
 * Poruka u regionalnom četu — čvor u Realtime Database putanji
 * {@code chats/{regionKey}/{messageId}}.
 */
public class Message {

    private String id;
    private String senderUid;
    private String senderName;
    private String text;
    private long timestamp;

    /** Obavezan prazan konstruktor za Realtime Database. */
    public Message() {
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getSenderName() { return senderName; }
    public void setSenderName(String senderName) { this.senderName = senderName; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
