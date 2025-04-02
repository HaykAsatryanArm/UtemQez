package com.haykasatryan.utemqez;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private TextView userName, userEmail;
    private ImageView profileImage;
    private Button btnLogout;
    private Cloudinary cloudinary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Initialize FirebaseAuth
        mAuth = FirebaseAuth.getInstance();

        // Initialize UI elements
        userName = findViewById(R.id.profileName);
        userEmail = findViewById(R.id.profileEmail);
        profileImage = findViewById(R.id.profilePicture);
        btnLogout = findViewById(R.id.btnLogout);

        // Initialize Cloudinary
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", "deor2c9as",
                "api_key", "882433745744291",
                "api_secret", "tPtmtB9HaZ7pTv6dn7lEJJssOh0"
        ));

        // Get the current user
        FirebaseUser user = mAuth.getCurrentUser();

        if (user != null) {
            userName.setText(user.getDisplayName());
            userEmail.setText(user.getEmail());
            loadProfilePicture(user.getEmail());
        }

        // On-click listener to handle profile picture change
        profileImage.setOnClickListener(v -> pickImage.launch(new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI)));

        // Logout button
        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(ProfileActivity.this, LoginActivity.class));
            finish();
        });
    }

    // Image picker using ActivityResultLauncher
    private final ActivityResultLauncher<Intent> pickImage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    try {
                        InputStream inputStream = getContentResolver().openInputStream(imageUri);
                        Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                        profileImage.setImageBitmap(bitmap);
                        uploadImageToCloudinary(bitmap);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                    }
                }
            }
    );

    // Upload image to Cloudinary
    private void uploadImageToCloudinary(Bitmap bitmap) {
        new Thread(() -> {
            try {
                File file = convertBitmapToFile(bitmap);
                Map uploadResult = cloudinary.uploader().upload(file, ObjectUtils.emptyMap());
                String imageUrl = (String) uploadResult.get("url");
                saveProfilePictureToFirestore(imageUrl);
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Convert Bitmap to File
    private File convertBitmapToFile(Bitmap bitmap) {
        File file = new File(getCacheDir(), "profile_picture.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return file;
    }

    // Save image URL to Firestore
    private void saveProfilePictureToFirestore(String imageUrl) {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            FirebaseFirestore db = FirebaseFirestore.getInstance();
            db.collection("users").document(user.getUid())
                    .update("profilePicture", imageUrl)
                    .addOnSuccessListener(aVoid -> runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Profile picture updated", Toast.LENGTH_SHORT).show()))
                    .addOnFailureListener(e -> runOnUiThread(() -> Toast.makeText(ProfileActivity.this, "Failed to update profile picture", Toast.LENGTH_SHORT).show()));
        }
    }

    // Load profile picture from Firestore
    private void loadProfilePicture(String email) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (!queryDocumentSnapshots.isEmpty()) {
                        String imageUrl = queryDocumentSnapshots.getDocuments().get(0).getString("profilePicture");
                        Log.d("ProfileActivity", "Fetched Image URL: " + imageUrl);

                        if (imageUrl != null && !imageUrl.isEmpty()) {
                            Log.d("ProfileActivity", "Loading image with Picasso...");
                            Picasso.get()
                                    .load(imageUrl.replace("http://", "https://"))
                                    .placeholder(R.drawable.user)
                                    .error(R.drawable.user)
                                    .into(profileImage, new com.squareup.picasso.Callback() {
                                        @Override
                                        public void onSuccess() {
                                            Log.d("Picasso", "Image loaded successfully");
                                        }

                                        @Override
                                        public void onError(Exception e) {
                                            Log.e("Picasso", "Failed to load image: " + e.getMessage());
                                            profileImage.setImageResource(R.drawable.user);
                                        }
                                    });
                        } else {
                            Log.e("ProfileActivity", "Image URL is null or empty!");
                            profileImage.setImageResource(R.drawable.user);
                        }
                    } else {
                        Log.e("ProfileActivity", "No user found with this email!");
                        profileImage.setImageResource(R.drawable.user);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e("ProfileActivity", "Firestore query failed", e);
                    profileImage.setImageResource(R.drawable.user);
                });
    }
}
