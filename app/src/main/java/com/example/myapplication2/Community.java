package com.example.myapplication2;

public class Community {

    public String id;
    public String name;
    public String category;
    public String creatorUid;
    public long   createdAt;

    /** Required for Firebase deserialization. */
    public Community() {}

    public Community(String id, String name, String category, String creatorUid) {
        this.id         = id;
        this.name       = name;
        this.category   = category;
        this.creatorUid = creatorUid;
        this.createdAt  = System.currentTimeMillis();
    }

    public String getId()       { return id       != null ? id       : ""; }
    public String getName()     { return name     != null ? name     : ""; }
    public String getCategory() { return category != null ? category : ""; }
}
