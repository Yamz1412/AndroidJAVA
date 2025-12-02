package com.app.SalesInventory;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class EditProfil extends BaseActivity {

    public static final String TAG = "EditProfil";
    EditText ProfilName, ProfilEmail, ProfilPhone;
    Button savebtn, btnChangePhoto;
    ImageView imgAvatarEdit;
    FirebaseAuth fAuth;
    FirebaseFirestore fStore;
    FirebaseUser user;
    FirebaseStorage fStorage;
    StorageReference storageRef;
    Uri selectedImageUri;

    ActivityResultLauncher<String> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_profil);

        ProfilName = findViewById(R.id.ProfilNameE);
        ProfilEmail = findViewById(R.id.ProfilEmailTE);
        ProfilPhone = findViewById(R.id.ProfilPhoneTE);
        savebtn = findViewById(R.id.SaveProfile);
        btnChangePhoto = findViewById(R.id.btnChangePhoto);
        imgAvatarEdit = findViewById(R.id.imgAvatarEdit);

        fAuth = FirebaseAuth.getInstance();
        fStore = FirebaseFirestore.getInstance();
        fStorage = FirebaseStorage.getInstance();
        user = fAuth.getCurrentUser();
        storageRef = fStorage.getReference();

        if (user == null) {
            Intent intent = new Intent(EditProfil.this, SignInActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
            return;
        }

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        Glide.with(this)
                                .load(uri)
                                .placeholder(R.drawable.avatarprofil)
                                .error(R.drawable.avatarprofil)
                                .circleCrop()
                                .into(imgAvatarEdit);
                    }
                });

        Intent data = getIntent();
        String name = data.getStringExtra("name");
        String email = data.getStringExtra("email");
        String phone = data.getStringExtra("phone");
        String photoUrl = data.getStringExtra("photoUrl");

        if (name == null) name = "";
        if (email == null || email.isEmpty()) email = user.getEmail() != null ? user.getEmail() : "";
        if (phone == null) phone = "";

        ProfilName.setText(name);
        ProfilPhone.setText(phone);
        ProfilEmail.setText(email);

        loadCurrentAvatar(photoUrl);

        btnChangePhoto.setOnClickListener(v -> {
            imagePickerLauncher.launch("image/*");
        });

        savebtn.setOnClickListener(v -> {
            String newName = ProfilName.getText().toString().trim();
            String newEmail = ProfilEmail.getText().toString().trim();
            String newPhone = ProfilPhone.getText().toString().trim();

            if (newName.isEmpty() || newEmail.isEmpty() || newPhone.isEmpty()) {
                Toast.makeText(EditProfil.this, "All fields are required", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedImageUri != null) {
                uploadAvatarAndSaveProfile(newName, newEmail, newPhone, selectedImageUri);
            } else {
                saveProfileData(newName, newEmail, newPhone, null);
            }
        });
    }

    private void loadCurrentAvatar(String photoUrlExtra) {
        Uri authPhoto = user.getPhotoUrl();
        String url;
        if (photoUrlExtra != null && !photoUrlExtra.isEmpty()) {
            url = photoUrlExtra;
        } else if (authPhoto != null && authPhoto.toString().length() > 0) {
            url = authPhoto.toString();
        } else {
            url = null;
        }

        if (url != null && !url.isEmpty()) {
            Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.avatarprofil)
                    .error(R.drawable.avatarprofil)
                    .circleCrop()
                    .into(imgAvatarEdit);
        } else {
            imgAvatarEdit.setImageResource(R.drawable.avatarprofil);
        }
    }

    private void uploadAvatarAndSaveProfile(String name, String email, String phone, Uri imageUri) {
        String uid = user.getUid();
        StorageReference avatarRef = storageRef.child("users/" + uid + "/avatar.jpg");
        avatarRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> avatarRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    String downloadUrl = uri.toString();
                    saveProfileData(name, email, phone, downloadUrl);
                }).addOnFailureListener(e -> {
                    Toast.makeText(EditProfil.this, "Failed to get image URL", Toast.LENGTH_SHORT).show();
                    saveProfileData(name, email, phone, null);
                }))
                .addOnFailureListener(e -> {
                    Toast.makeText(EditProfil.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    saveProfileData(name, email, phone, null);
                });
    }

    private void saveProfileData(String name, String email, String phone, String photoUrl) {
        String uid = user.getUid();
        DocumentReference docref = fStore.collection("users").document(uid);
        Map<String, Object> edited = new HashMap<>();
        edited.put("email", email);
        edited.put("Email", email);
        edited.put("name", name);
        edited.put("Name", name);
        edited.put("phone", phone);
        edited.put("Phone", phone);
        if (photoUrl != null && !photoUrl.isEmpty()) {
            edited.put("photoUrl", photoUrl);
        } else if (user.getPhotoUrl() != null) {
            edited.put("photoUrl", user.getPhotoUrl().toString());
        }

        docref.set(edited, SetOptions.merge()).addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void aVoid) {
                user.updateEmail(email).addOnSuccessListener(new OnSuccessListener<Void>() {
                    @Override
                    public void onSuccess(Void unused) {
                        Toast.makeText(EditProfil.this, "Profile updated", Toast.LENGTH_SHORT).show();
                        Intent intent = new Intent(getApplicationContext(), Profile.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    }
                }).addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(@NonNull Exception e) {
                        Toast.makeText(EditProfil.this, e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        }).addOnFailureListener(e -> Toast.makeText(EditProfil.this, "Failed to update profile", Toast.LENGTH_SHORT).show());
    }
}