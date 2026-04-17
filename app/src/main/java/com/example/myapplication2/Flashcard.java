package com.example.myapplication2;

import java.io.Serializable;

public class Flashcard implements Serializable {

    public String id;
    public String question;
    public String answer;
    public String subject;
    public String createdBy;   // uid of the user who created this card
    public long   timestamp;

    /** Required for Firebase deserialization. */
    public Flashcard() {}

    public Flashcard(String id, String question, String answer,
                     String subject, String createdBy) {
        this.id        = id;
        this.question  = question;
        this.answer    = answer;
        this.subject   = subject;
        this.createdBy = createdBy;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId()       { return id; }
    public String getQuestion() { return question != null ? question : ""; }
    public String getAnswer()   { return answer   != null ? answer   : ""; }
    public String getSubject()  { return subject  != null ? subject  : ""; }
}
