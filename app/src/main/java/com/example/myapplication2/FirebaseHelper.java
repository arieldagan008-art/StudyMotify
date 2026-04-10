package com.example.myapplication2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class FirebaseHelper {

    private static FirebaseHelper instance;

    private final FirebaseAuth auth;
    private final FirebaseDatabase database;

    private FirebaseHelper() {
        auth = FirebaseAuth.getInstance();
        database = FirebaseDatabase.getInstance();
    }

    public static synchronized FirebaseHelper getInstance() {
        if (instance == null) {
            instance = new FirebaseHelper();
        }
        return instance;
    }

    // --- Auth ---

    public FirebaseAuth getAuth() {
        return auth;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public void signOut() {
        auth.signOut();
    }

    // --- Realtime Database ---

    public FirebaseDatabase getDatabase() {
        return database;
    }

    /** Returns a reference to the root of the database. */
    public DatabaseReference getRootRef() {
        return database.getReference();
    }

    /** Returns a reference to /users/{uid} for the currently signed-in user. */
    public DatabaseReference getCurrentUserRef() {
        FirebaseUser user = getCurrentUser();
        if (user == null) {
            throw new IllegalStateException("No user is currently signed in.");
        }
        return database.getReference("users").child(user.getUid());
    }

    /** Returns a reference to any top-level node by name (e.g. "goals", "tasks"). */
    public DatabaseReference getRef(String node) {
        return database.getReference(node);
    }
}
