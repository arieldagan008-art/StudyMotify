package com.example.myapplication2;

public class SharedLink {

    public String id;
    public String resourceName;
    public String linkUrl;
    public String authorUid;
    public String authorEmail;
    public String communityName;
    public long   timestamp;

    /** Required for Firebase deserialization. */
    public SharedLink() {}

    public SharedLink(String id, String resourceName, String linkUrl,
                      String authorUid, String authorEmail, String communityName) {
        this.id            = id;
        this.resourceName  = resourceName;
        this.linkUrl       = linkUrl;
        this.authorUid     = authorUid;
        this.authorEmail   = authorEmail;
        this.communityName = communityName;
        this.timestamp     = System.currentTimeMillis();
    }

    public String getId()            { return id            != null ? id            : ""; }
    public String getResourceName()  { return resourceName  != null ? resourceName  : ""; }
    public String getLinkUrl()       { return linkUrl       != null ? linkUrl       : ""; }
    public String getAuthorEmail()   { return authorEmail   != null ? authorEmail   : "Anonymous"; }
    public String getCommunityName() { return communityName != null ? communityName : ""; }
}
