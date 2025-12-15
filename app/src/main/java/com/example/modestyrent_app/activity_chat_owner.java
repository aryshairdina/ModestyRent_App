package com.example.modestyrent_app;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class activity_chat_owner extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1001;
    private static final String TAG = "ChatOwnerActivity";

    private RecyclerView rvChatMessages;
    private TextInputEditText etMessage;
    private FloatingActionButton btnSend;
    private ImageView backButton, btnAttachLeft;
    private TextView tvOwnerName;
    private ProgressBar progressBar;

    private ChatAdapter chatAdapter;
    private final List<ChatMessage> chatMessages = new ArrayList<>();

    private String chatId, ownerId, productId;
    private String currentUserId;
    private DatabaseReference chatRoomRef, messagesRef, usersRef;
    private StorageReference storageRef;

    private ChildEventListener messagesListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_owner);

        // 🔒 AUTH GUARD (rule-related only)
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            finish();
            return;
        }
        // 🔒 END auth guard

        initializeViews();
        getIntentData();
        initializeFirebase();
        setupRecyclerView();
        loadChatMessages();
        setupClickListeners();
        loadOwnerInfo();
        initializeChatRoom();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (messagesRef != null && messagesListener != null) {
            messagesRef.removeEventListener(messagesListener);
        }
    }

    private void initializeViews() {
        rvChatMessages = findViewById(R.id.rvChatMessages);
        etMessage = findViewById(R.id.etMessage);
        btnSend = findViewById(R.id.btnSend);
        backButton = findViewById(R.id.backButton);
        btnAttachLeft = findViewById(R.id.btnAttachLeft);
        tvOwnerName = findViewById(R.id.tvOwnerName);
        progressBar = findViewById(R.id.progressBar);
    }

    private void getIntentData() {
        chatId = getIntent().getStringExtra("chatId");
        ownerId = getIntent().getStringExtra("ownerId");
        productId = getIntent().getStringExtra("productId");

        Log.d(TAG, "Chat ID: " + chatId);
        Log.d(TAG, "Owner ID: " + ownerId);
        Log.d(TAG, "Product ID: " + productId);

        if (chatId == null || ownerId == null) {
            Toast.makeText(this, "Error: Missing chat information", Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void initializeFirebase() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            Toast.makeText(this, "Please login to continue", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        currentUserId = currentUser.getUid();
        Log.d(TAG, "Current User ID: " + currentUserId);

        FirebaseDatabase database = FirebaseDatabase.getInstance();
        chatRoomRef = database.getReference("chats").child(chatId);
        messagesRef = chatRoomRef.child("messages");
        usersRef = database.getReference("users");
        storageRef = FirebaseStorage.getInstance().getReference("chat_files");
    }

    /**
     * Make sure chat room exists and has participants/current user + owner.
     * This helps activity_chat_list detect the chat reliably.
     */
    private void initializeChatRoom() {
        chatRoomRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) {
                    // Create new chat
                    Map<String, Object> chatRoomData = new HashMap<>();
                    Map<String, Object> participants = new HashMap<>();
                    participants.put(currentUserId, true);
                    participants.put(ownerId, true);

                    chatRoomData.put("participants", participants);
                    chatRoomData.put("createdAt", System.currentTimeMillis());
                    chatRoomData.put("productId", productId);
                    chatRoomData.put("lastMessage", "");
                    chatRoomData.put("lastMessageTime", 0L);
                    chatRoomData.put("lastMessageSender", "");

                    chatRoomRef.setValue(chatRoomData)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat room created"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to create chat room: " + e.getMessage()));
                } else {
                    // Chat exists → ensure participants map and productId are set
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("participants/" + currentUserId, true);
                    updates.put("participants/" + ownerId, true);
                    if (!snapshot.hasChild("productId") && productId != null) {
                        updates.put("productId", productId);
                    }
                    chatRoomRef.updateChildren(updates)
                            .addOnSuccessListener(aVoid -> Log.d(TAG, "Chat room participants ensured"))
                            .addOnFailureListener(e -> Log.e(TAG, "Failed to ensure participants: " + e.getMessage()));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to check chat room: " + error.getMessage());
            }
        });
    }

    private void setupRecyclerView() {
        chatAdapter = new ChatAdapter(chatMessages, currentUserId);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        rvChatMessages.setLayoutManager(layoutManager);
        rvChatMessages.setAdapter(chatAdapter);
        rvChatMessages.setHasFixedSize(false);
        rvChatMessages.setNestedScrollingEnabled(true);
    }

    private void loadChatMessages() {
        Log.d(TAG, "Loading messages from: messages");

        // 1) Load existing messages once
        messagesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatMessages.clear();
                for (DataSnapshot messageSnapshot : snapshot.getChildren()) {
                    ChatMessage message = messageSnapshot.getValue(ChatMessage.class);
                    if (message != null) {
                        chatMessages.add(message);
                        Log.d(TAG, "Loaded message id=" + message.getMessageId() + " type=" + message.getType());
                    }
                }
                Log.d(TAG, "Total messages loaded (singleValue): " + chatMessages.size());
                chatAdapter.notifyDataSetChanged();

                if (!chatMessages.isEmpty()) {
                    rvChatMessages.post(() -> rvChatMessages.scrollToPosition(chatMessages.size() - 1));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to load existing messages: " + error.getMessage());
            }
        });

        // 2) Listen for new messages in realtime
        messagesListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);
                if (message != null && !messageExists(message.getMessageId())) {
                    chatMessages.add(message);
                    chatAdapter.notifyItemInserted(chatMessages.size() - 1);
                    rvChatMessages.scrollToPosition(chatMessages.size() - 1);
                    Log.d(TAG, "New message added: id=" + message.getMessageId() + " total=" + chatMessages.size());
                } else {
                    Log.d(TAG, "onChildAdded - message null or exists. snapshot key=" + snapshot.getKey());
                }
            }

            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {
                Log.e(TAG, "Failed to listen for new messages: " + error.getMessage());
            }
        };

        messagesRef.addChildEventListener(messagesListener);
    }

    private boolean messageExists(String messageId) {
        if (messageId == null) return false;
        for (ChatMessage message : chatMessages) {
            if (message.getMessageId() != null && message.getMessageId().equals(messageId)) {
                return true;
            }
        }
        return false;
    }

    private void setupClickListeners() {
        backButton.setOnClickListener(v -> finish());

        // Attach icon → pick image (image only)
        btnAttachLeft.setOnClickListener(v -> openImagePicker());

        // Send button → send text
        btnSend.setOnClickListener(v -> sendTextMessage());

        etMessage.setOnClickListener(v -> {
            if (!chatMessages.isEmpty()) {
                rvChatMessages.smoothScrollToPosition(chatMessages.size() - 1);
            }
        });
    }

    // Open image picker
    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*"); // only images
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        Intent chooser = Intent.createChooser(intent, "Select Image to Send");
        startActivityForResult(chooser, PICK_IMAGE_REQUEST);
    }

    // Handle image picker result
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            Uri fileUri = data.getData();
            if (fileUri != null) {
                Log.d(TAG, "Image selected: " + fileUri);
                uploadImage(fileUri);
            } else {
                Toast.makeText(this, "Failed to get image", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Upload image to Storage and send as message
    private void uploadImage(Uri imageUri) {
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setTitle("Uploading Image");
        progressDialog.setMessage("Please wait...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        String fileName = "img_" + System.currentTimeMillis();
        StorageReference fileRef = storageRef.child(chatId).child(fileName + ".jpg");

        fileRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot ->
                        fileRef.getDownloadUrl().addOnSuccessListener(uri -> {
                            progressDialog.dismiss();
                            String downloadUrl = uri.toString();
                            Log.d(TAG, "Image uploaded: " + downloadUrl);
                            sendImageMessage(downloadUrl);
                        }).addOnFailureListener(e -> {
                            progressDialog.dismiss();
                            Log.e(TAG, "Failed to get download URL: " + e.getMessage());
                            Toast.makeText(this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                        })
                )
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Log.e(TAG, "Upload failed: " + e.getMessage());
                    Toast.makeText(this, "Upload failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                })
                .addOnProgressListener(snapshot -> {
                    double progress = (100.0 * snapshot.getBytesTransferred()) / snapshot.getTotalByteCount();
                    progressDialog.setMessage("Uploaded " + (int) progress + "%");
                });
    }

    private void sendImageMessage(String imageUrl) {
        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Failed to send image", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSenderId(currentUserId);
        message.setFileUrl(imageUrl);
        message.setFileType("image");
        message.setTimestamp(System.currentTimeMillis());
        message.setType("image");
        message.setMessage(""); // no text

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    updateLastMessage("[Image]");
                    Log.d(TAG, "Image message sent: " + messageId);
                    rvChatMessages.post(() -> rvChatMessages.scrollToPosition(chatMessages.size() - 1));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to send image message: " + e.getMessage());
                });
    }

    // Send text message
    private void sendTextMessage() {
        String messageText = etMessage.getText() != null ? etMessage.getText().toString().trim() : "";
        if (TextUtils.isEmpty(messageText)) {
            Toast.makeText(this, "Message cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }

        String messageId = messagesRef.push().getKey();
        if (messageId == null) {
            Toast.makeText(this, "Failed to send message", Toast.LENGTH_SHORT).show();
            return;
        }

        ChatMessage message = new ChatMessage();
        message.setMessageId(messageId);
        message.setSenderId(currentUserId);
        message.setMessage(messageText);
        message.setTimestamp(System.currentTimeMillis());
        message.setType("text");
        message.setFileUrl(null);
        message.setFileType(null);

        messagesRef.child(messageId).setValue(message)
                .addOnSuccessListener(aVoid -> {
                    etMessage.setText("");
                    updateLastMessage(messageText);
                    Log.d(TAG, "Text message sent: " + messageId);
                    rvChatMessages.post(() -> rvChatMessages.scrollToPosition(chatMessages.size() - 1));
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(this, "Failed to send message: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "Failed to send message: " + e.getMessage());
                });
    }

    private void updateLastMessage(String lastMessageRaw) {
        // Optional: shorten preview for chat list
        String preview = lastMessageRaw;
        if (preview != null && preview.length() > 60) {
            preview = preview.substring(0, 57) + "...";
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("lastMessage", preview != null ? preview : "");
        updates.put("lastMessageTime", System.currentTimeMillis());
        updates.put("lastMessageSender", currentUserId);

        chatRoomRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> Log.d(TAG, "Last message updated"))
                .addOnFailureListener(e -> Log.e(TAG, "Failed to update last message: " + e.getMessage()));
    }

    private void loadOwnerInfo() {
        usersRef.child(ownerId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String ownerName = snapshot.child("fullName").getValue(String.class);
                    if (ownerName != null && !ownerName.isEmpty()) {
                        tvOwnerName.setText(ownerName);
                    } else {
                        tvOwnerName.setText("Owner");
                    }
                } else {
                    tvOwnerName.setText("Owner");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(activity_chat_owner.this, "Failed to load owner info", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
