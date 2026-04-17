package com.example.myapplication2;

public class Message {

    public String id;
    public String senderId;
    public String senderName;
    public String text;
    public long   timestamp;

    /** Required for Firebase deserialization. */
    public Message() {}

    public Message(String id, String senderId, String senderName, String text) {
        this.id         = id;
        this.senderId   = senderId;
        this.senderName = senderName;
        this.text       = text;
        this.timestamp  = System.currentTimeMillis();
    }

    public String getId()         { return id         != null ? id         : ""; }
    public String getSenderId()   { return senderId   != null ? senderId   : ""; }
    public String getSenderName() { return senderName != null ? senderName : "Anonymous"; }
    public String getText()       { return text       != null ? text       : ""; }
}
