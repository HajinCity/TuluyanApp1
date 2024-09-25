package com.example.tuluyanapp;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.facebook.AccessToken;
import com.facebook.CallbackManager;
import com.facebook.FacebookCallback;
import com.facebook.FacebookException;
import com.facebook.login.LoginManager;
import com.facebook.login.LoginResult;

import java.util.Arrays;
import java.util.Objects;

public class userLogin extends AppCompatActivity {

    private static final int RC_SIGN_IN = 9001;
    private EditText editTextEmail, editTextPassword;
    private ProgressBar progressBar;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;  // Firestore instance
    private GoogleSignInClient mGoogleSignInClient;
    private CallbackManager mCallbackManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_login);

        // Initialize Firebase Auth
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();  // Initialize Firestore

        // Initialize views
        editTextEmail = findViewById(R.id.editTextTextEmailAddress2);
        editTextPassword = findViewById(R.id.editTextTextPassword2);
        ImageView googleSignInButton = findViewById(R.id.google_sign_in_button);
        ImageView facebookLoginButton = findViewById(R.id.facebook_login_button);
        progressBar = new ProgressBar(this);

        // Configure Google Sign-In
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        mGoogleSignInClient = GoogleSignIn.getClient(this, gso);

        googleSignInButton.setOnClickListener(v -> {
            Intent signInIntent = mGoogleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        // Configure Facebook Login
        mCallbackManager = CallbackManager.Factory.create();
        facebookLoginButton.setOnClickListener(v -> {
            LoginManager.getInstance().logInWithReadPermissions(userLogin.this, Arrays.asList("email", "public_profile"));
            LoginManager.getInstance().registerCallback(mCallbackManager, new FacebookCallback<LoginResult>() {
                @Override
                public void onSuccess(LoginResult loginResult) {
                    handleFacebookAccessToken(loginResult.getAccessToken());
                }

                @Override
                public void onCancel() {
                    Toast.makeText(userLogin.this, "Facebook sign-in canceled.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onError(@NonNull FacebookException error) {
                    Toast.makeText(userLogin.this, "Facebook sign-in failed.", Toast.LENGTH_SHORT).show();
                }
            });
        });

        // Set up login button click listener
        findViewById(R.id.button4).setOnClickListener(v -> {
            String email = editTextEmail.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (TextUtils.isEmpty(email)) {
                editTextEmail.setError("Email is required.");
                return;
            }

            if (TextUtils.isEmpty(password)) {
                editTextPassword.setError("Password is required.");
                return;
            }

            progressBar.setVisibility(View.VISIBLE); // Show progress bar when login starts

            // Authenticate user
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(userLogin.this, task -> {
                        progressBar.setVisibility(View.GONE); // Hide progress bar after login completes
                        if (task.isSuccessful()) {
                            // Check if the user exists in tenantcollection
                            checkTenantInFirestore();
                        } else {
                            Toast.makeText(userLogin.this, "Authentication failed: " + Objects.requireNonNull(task.getException()).getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        });

        // Set up "Create Account" link click listener
        TextView textViewCreateAccount = findViewById(R.id.textView11);
        textViewCreateAccount.setOnClickListener(v -> {
            Intent intent = new Intent(userLogin.this, userCreateAcc.class);
            startActivity(intent);
        });

        // Set up "Forgot Password" link click listener
        TextView textViewForgotPassword = findViewById(R.id.textView10);
        textViewForgotPassword.setOnClickListener(v -> {
            Intent intent = new Intent(userLogin.this, userfgp.class);
            startActivity(intent);
        });
    }

    private void checkTenantInFirestore() {
        String userId = Objects.requireNonNull(mAuth.getCurrentUser()).getUid();

        db.collection("tenantcollection").document(userId)
                .get()
                .addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
                    @Override
                    public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                        if (task.isSuccessful()) {
                            DocumentSnapshot document = task.getResult();
                            if (document != null && document.exists()) {
                                Toast.makeText(userLogin.this, "Login successful.", Toast.LENGTH_SHORT).show();
                                // Redirect to another activity (e.g., main dashboard)
                                startActivity(new Intent(userLogin.this, MainActivity3.class));
                                finish();
                            } else {
                                Toast.makeText(userLogin.this, "No tenant data found.", Toast.LENGTH_LONG).show();
                                // Optionally, log out the user
                                mAuth.signOut();
                            }
                        } else {
                            Toast.makeText(userLogin.this, "Failed to check tenant data: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                        }
                    }
                });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        // Google Sign-In result
        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }

        // Facebook Sign-In result
        mCallbackManager.onActivityResult(requestCode, resultCode, data);
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            firebaseAuthWithGoogle(account.getIdToken());
        } catch (ApiException e) {
            Toast.makeText(userLogin.this, "Google sign-in failed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void firebaseAuthWithGoogle(String idToken) {
        AuthCredential credential = GoogleAuthProvider.getCredential(idToken, null);
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkTenantInFirestore();  // Check tenant data after Google sign-in
                    } else {
                        Toast.makeText(userLogin.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void handleFacebookAccessToken(AccessToken token) {
        AuthCredential credential = com.google.firebase.auth.FacebookAuthProvider.getCredential(token.getToken());
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, task -> {
                    if (task.isSuccessful()) {
                        checkTenantInFirestore();  // Check tenant data after Facebook sign-in
                    } else {
                        Toast.makeText(userLogin.this, "Authentication Failed.", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
