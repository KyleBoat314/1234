package com.boathouse.budget;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.InputType;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int REQUEST_PICK_PAYCHECK = 1001;

    private WebView webView;
    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private ListenerRegistration budgetListener;
    private DocumentReference budgetRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        if (auth.getCurrentUser() != null) {
            showBudget();
        } else {
            showLogin();
        }
    }

    private void showLogin() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(48, 80, 48, 48);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setBackgroundColor(Color.rgb(245, 247, 250));

        TextView title = new TextView(this);
        title.setText("Boat House Budget");
        title.setTextSize(28);
        title.setTextColor(Color.rgb(22, 50, 79));
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, 40);

        EditText email = new EditText(this);
        email.setHint("Email");
        email.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        );

        EditText password = new EditText(this);
        password.setHint("Password");
        password.setInputType(
                InputType.TYPE_CLASS_TEXT |
                InputType.TYPE_TEXT_VARIATION_PASSWORD
        );

        Button signIn = new Button(this);
        signIn.setText("Sign In");

        Button createAccount = new Button(this);
        createAccount.setText("Create Account");

        TextView info = new TextView(this);
        info.setText(
                "\nUse the same Boat House Budget account on both phones to share one budget."
        );
        info.setGravity(Gravity.CENTER);
        info.setTextColor(Color.DKGRAY);

        root.addView(title);
        root.addView(email);
        root.addView(password);
        root.addView(signIn);
        root.addView(createAccount);
        root.addView(info);

        setContentView(root);

        signIn.setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            if (e.isEmpty() || p.isEmpty()) {
                toast("Enter an email and password.");
                return;
            }

            auth.signInWithEmailAndPassword(e, p)
                    .addOnSuccessListener(result -> showBudget())
                    .addOnFailureListener(error ->
                            toast("Sign in failed: " + error.getMessage()));
        });

        createAccount.setOnClickListener(v -> {
            String e = email.getText().toString().trim();
            String p = password.getText().toString();

            if (e.isEmpty() || p.length() < 6) {
                toast("Enter an email and a password of at least 6 characters.");
                return;
            }

            auth.createUserWithEmailAndPassword(e, p)
                    .addOnSuccessListener(result -> showBudget())
                    .addOnFailureListener(error ->
                            toast("Account creation failed: " + error.getMessage()));
        });
    }

    private void showBudget() {
        if (auth.getCurrentUser() == null) {
            showLogin();
            return;
        }

        budgetRef = db.collection("users")
                .document(auth.getCurrentUser().getUid())
                .collection("budget")
                .document("shared");

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(
                        "Android.appReady(JSON.stringify(state));",
                        null
                );
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
    }

    private void initializeCloud(String localJson) {
        budgetRef.get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && snapshot.getString("json") != null) {
                        sendCloudToWeb(snapshot.getString("json"));
                    } else {
                        saveToCloud(localJson);
                    }

                    startBudgetListener();
                })
                .addOnFailureListener(error -> {
                    toast("Cloud connection error: " + error.getMessage());
                    startBudgetListener();
                });
    }

    private void startBudgetListener() {
        if (budgetListener != null) {
            budgetListener.remove();
        }

        budgetListener = budgetRef.addSnapshotListener((snapshot, error) -> {
            if (error != null || snapshot == null || !snapshot.exists()) {
                return;
            }

            String json = snapshot.getString("json");

            if (json != null) {
                sendCloudToWeb(json);
            }
        });
    }

    private void saveToCloud(String json) {
        if (budgetRef == null || json == null) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("json", json);
        data.put("updatedAt", System.currentTimeMillis());

        budgetRef.set(data)
                .addOnFailureListener(error ->
                        toast("Sync failed: " + error.getMessage()));
    }

    private void sendCloudToWeb(String json) {
        if (webView == null || json == null) {
            return;
        }

        String quoted = JSONObject.quote(json);

        runOnUiThread(() ->
                webView.evaluateJavascript(
                        "applyCloudState(" + quoted + ");",
                        null
                )
        );
    }

    private void choosePaycheckImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivityForResult(intent, REQUEST_PICK_PAYCHECK);
    }

    @Override
    protected void onActivityResult(
            int requestCode,
            int resultCode,
            Intent data
    ) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != REQUEST_PICK_PAYCHECK ||
                resultCode != RESULT_OK ||
                data == null ||
                data.getData() == null) {
            return;
        }

        Uri imageUri = data.getData();

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);

            TextRecognizer recognizer =
                    TextRecognition.getClient(
                            TextRecognizerOptions.DEFAULT_OPTIONS
                    );

            toast("Reading paycheck...");

            recognizer.process(image)
                    .addOnSuccessListener(result -> {
                        String text = result.getText();

                        recognizer.close();

                        if (text == null || text.trim().isEmpty()) {
                            toast("No text was found in that image.");
                            return;
                        }

                        sendPaycheckTextToWeb(text);
                    })
                    .addOnFailureListener(error -> {
                        recognizer.close();
                        toast("Could not read paycheck: " + error.getMessage());
                    });

        } catch (Exception e) {
            toast("Could not open image: " + e.getMessage());
        }
    }

    private void sendPaycheckTextToWeb(String text) {
        if (webView == null) {
            return;
        }

        String quoted = JSONObject.quote(text);

        runOnUiThread(() ->
                webView.evaluateJavascript(
                        "handlePaycheckOcr(" + quoted + ");",
                        null
                )
        );
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (budgetListener != null) {
            budgetListener.remove();
        }

        super.onDestroy();
    }

    private void toast(String message) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_LONG
        ).show();
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void appReady(String json) {
            runOnUiThread(() ->
                    initializeCloud(json)
            );
        }

        @JavascriptInterface
        public void syncBudget(String json) {
            saveToCloud(json);
        }

        @JavascriptInterface
        public void importPaycheck() {
            runOnUiThread(() ->
                    choosePaycheckImage()
            );
        }

        @JavascriptInterface
        public void signOut() {
            runOnUiThread(() -> {
                if (budgetListener != null) {
                    budgetListener.remove();
                    budgetListener = null;
                }

                auth.signOut();
                showLogin();
            });
        }

        @JavascriptInterface
        public void saveBackup(String json) {
            runOnUiThread(() -> {
                try {
                    ContentValues values = new ContentValues();

                    values.put(
                            MediaStore.Downloads.DISPLAY_NAME,
                            "boat-house-budget-backup.json"
                    );

                    values.put(
                            MediaStore.Downloads.MIME_TYPE,
                            "application/json"
                    );

                    values.put(
                            MediaStore.Downloads.IS_PENDING,
                            1
                    );

                    Uri uri = getContentResolver().insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values
                    );

                    if (uri == null) {
                        throw new Exception(
                                "Could not create download."
                        );
                    }

                    try (OutputStream out =
                                 getContentResolver()
                                         .openOutputStream(uri)) {

                        out.write(
                                json.getBytes(
                                        StandardCharsets.UTF_8
                                )
                        );
                    }

                    values.clear();

                    values.put(
                            MediaStore.Downloads.IS_PENDING,
                            0
                    );

                    getContentResolver().update(
                            uri,
                            values,
                            null,
                            null
                    );

                    toast("Backup saved to Downloads");

                } catch (Exception e) {
                    toast(
                            "Backup failed: " +
                            e.getMessage()
                    );
                }
            });
        }
    }
}
