package com.example.modestyrent_app;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class UnreadCounterManager {
    private static final String TAG = "UnreadCounterManager";
    private static final String PREFS_NAME = "ChatPrefs";
    private static final String KEY_TOTAL_UNREAD = "total_unread";

    private static UnreadCounterManager instance;
    private SharedPreferences prefs;
    private DatabaseReference userUnreadRef;
    private ValueEventListener unreadListener;
    private OnUnreadCountChangeListener listener;

    private int totalUnreadCount = 0;

    public interface OnUnreadCountChangeListener {
        void onUnreadCountChanged(int totalCount);
    }

    private UnreadCounterManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        totalUnreadCount = prefs.getInt(KEY_TOTAL_UNREAD, 0);
    }

    public static synchronized UnreadCounterManager getInstance(Context context) {
        if (instance == null) {
            instance = new UnreadCounterManager(context.getApplicationContext());
        }
        return instance;
    }

    public void setListener(OnUnreadCountChangeListener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onUnreadCountChanged(totalUnreadCount);
        }
    }

    public void removeListener() {
        this.listener = null;
    }

    public void startListening() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Log.w(TAG, "No authenticated user, cannot listen for unread counts");
            return;
        }

        userUnreadRef = FirebaseDatabase.getInstance()
                .getReference("userUnreadCounts")
                .child(currentUser.getUid());

        if (unreadListener != null) {
            userUnreadRef.removeEventListener(unreadListener);
        }

        unreadListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Integer count = snapshot.getValue(Integer.class);
                    if (count != null) {
                        totalUnreadCount = count;
                        prefs.edit().putInt(KEY_TOTAL_UNREAD, count).apply();
                        Log.d(TAG, "Unread count updated: " + count);

                        if (listener != null) {
                            listener.onUnreadCountChanged(count);
                        }
                    }
                } else {
                    totalUnreadCount = 0;
                    prefs.edit().putInt(KEY_TOTAL_UNREAD, 0).apply();
                    if (listener != null) {
                        listener.onUnreadCountChanged(0);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load unread count: " + error.getMessage());
            }
        };

        userUnreadRef.addValueEventListener(unreadListener);
    }

    public void stopListening() {
        if (userUnreadRef != null && unreadListener != null) {
            userUnreadRef.removeEventListener(unreadListener);
        }
    }

    public int getTotalUnreadCount() {
        return totalUnreadCount;
    }

    public void setTotalUnreadCount(int count) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser != null) {
            DatabaseReference ref = FirebaseDatabase.getInstance()
                    .getReference("userUnreadCounts")
                    .child(currentUser.getUid());
            ref.setValue(count);
        }
    }

    public void incrementUnreadCount() {
        setTotalUnreadCount(totalUnreadCount + 1);
    }

    public void decrementUnreadCount(int decrementBy) {
        int newCount = Math.max(0, totalUnreadCount - decrementBy);
        setTotalUnreadCount(newCount);
    }

    public void clearUnreadCount() {
        setTotalUnreadCount(0);
    }
}