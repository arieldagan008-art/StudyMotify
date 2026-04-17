package com.example.myapplication2;

import java.util.HashMap;
import java.util.Map;

public class Resource {

    public String  id;
    public String  title;
    public String  type;           // "Exam" or "Summary"
    public String  url;
    public String  uploaderName;
    public String  uploaderUid;
    public long    timestamp;
    public long    likesCount;
    /** Map of uid → true for every user who has liked this resource. */
    public Map<String, Boolean> likes;

    /** Required for Firebase deserialization. */
    public Resource() {}

    public Resource(String id, String title, String type,
                    String url, String uploaderName, String uploaderUid) {
        this.id           = id;
        this.title        = title;
        this.type         = type;
        this.url          = url;
        this.uploaderName = uploaderName;
        this.uploaderUid  = uploaderUid;
        this.timestamp    = System.currentTimeMillis();
        this.likesCount   = 0;
        this.likes        = new HashMap<>();
    }

    public String  getId()           { return id           != null ? id           : ""; }
    public String  getTitle()        { return title        != null ? title        : ""; }
    public String  getType()         { return type         != null ? type         : "Other"; }
    public String  getUrl()          { return url          != null ? url          : ""; }
    public String  getUploaderName() { return uploaderName != null ? uploaderName : "Anonymous"; }
    public long    getLikesCount()   { return likesCount; }

    public boolean isLikedBy(String uid) {
        return uid != null && likes != null && Boolean.TRUE.equals(likes.get(uid));
    }
}
