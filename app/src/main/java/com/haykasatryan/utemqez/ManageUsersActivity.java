package com.haykasatryan.utemqez;

import android.os.Bundle;
import android.view.View;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ManageUsersActivity extends AppCompatActivity {

    private RecyclerView usersRecyclerView;
    private UserAdapter userAdapter;
    private final List<Map<String, Object>> userList = new ArrayList<>();
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_users);

        db = FirebaseFirestore.getInstance();
        usersRecyclerView = findViewById(R.id.usersRecyclerView);
        usersRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userAdapter = new UserAdapter(userList, this::updateUserAdminStatus, this::deleteUser);
        usersRecyclerView.setAdapter(userAdapter);

        fetchUsers();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    private void fetchUsers() {
        db.collection("users").get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    userList.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Map<String, Object> user = new HashMap<>();
                        user.put("uid", document.getId());
                        user.put("name", document.getString("name"));
                        user.put("email", document.getString("email"));
                        user.put("isAdmin", document.getBoolean("isAdmin"));
                        userList.add(user);
                    }
                    userAdapter.notifyDataSetChanged();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error loading users", Toast.LENGTH_SHORT).show());
    }

    private void updateUserAdminStatus(String uid, boolean isAdmin) {
        db.collection("users").document(uid)
                .update("isAdmin", isAdmin)
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User admin status updated", Toast.LENGTH_SHORT).show();
                    fetchUsers();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error updating user", Toast.LENGTH_SHORT).show());
    }

    private void deleteUser(String uid) {
        db.collection("users").document(uid)
                .delete()
                .addOnSuccessListener(aVoid -> {
                    Toast.makeText(this, "User deleted", Toast.LENGTH_SHORT).show();
                    fetchUsers();
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Error deleting user", Toast.LENGTH_SHORT).show());
    }
}