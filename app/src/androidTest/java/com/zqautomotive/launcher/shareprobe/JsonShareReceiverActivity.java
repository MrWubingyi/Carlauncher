package com.zqautomotive.launcher.shareprobe;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * SDK-only receiver: can launch outside instrumentation without the target APK's
 * Kotlin/AndroidX runtime. Receipts remain private to the test APK; no networking.
 */
public class JsonShareReceiverActivity extends Activity {
    private static final int MAX_BYTES = 4 * 1024 * 1024;
    private TextView resultView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        Button close = new Button(this);
        close.setText("Close JSON receipt");
        close.setOnClickListener(view -> finishAndRemoveTask());
        layout.addView(close);
        resultView = new TextView(this);
        resultView.setText("Reading JSON...");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(resultView);
        layout.addView(scroll);
        setContentView(layout);
        Intent incoming = getIntent();
        new Thread(() -> readShare(incoming), "json-share-probe").start();
    }

    private void readShare(Intent incoming) {
        JSONObject receipt = new JSONObject();
        try {
            receipt.put("receivedAt", System.currentTimeMillis());
            receipt.put("uid", Process.myUid());
            receipt.put("flags", incoming.getFlags());
            if (!Intent.ACTION_SEND.equals(incoming.getAction())
                    || !"application/json".equals(incoming.getType())) {
                throw new IllegalArgumentException("Expected ACTION_SEND application/json");
            }
            Uri uri = incoming.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null || !"content".equals(uri.getScheme())) {
                throw new IllegalArgumentException("Expected a content URI");
            }
            receipt.put("uri", uri.toString());
            receipt.put("clipMatches", incoming.getClipData() != null
                    && incoming.getClipData().getItemCount() == 1
                    && uri.equals(incoming.getClipData().getItemAt(0).getUri()));
            receipt.put("mime", getContentResolver().getType(uri));
            byte[] bytes;
            try (InputStream stream = getContentResolver().openInputStream(uri)) {
                if (stream == null) {
                    throw new IOException("No input stream");
                }
                bytes = readBounded(stream);
            }
            String json = new String(bytes, StandardCharsets.UTF_8);
            receipt.put("records", new JSONArray(json).length());
            receipt.put("content", json);
            receipt.put("bytes", bytes.length);
            receipt.put("sha256", sha256(bytes));
            receipt.put("writeDenied", writeDenied(uri));
            receipt.put("persistDenied", persistDenied(uri));
            receipt.put("success", true);
        } catch (Exception exception) {
            try {
                receipt.put("success", false);
                receipt.put("error", exception.getClass().getName());
            } catch (org.json.JSONException ignored) {
                // JSONObject receives only non-null fixed keys and primitive/string values.
            }
        }
        saveReceipt(receipt);
    }

    private boolean writeDenied(Uri uri) throws IOException {
        // Append mode avoids truncating the copy even if a regression grants write access.
        try (OutputStream stream = getContentResolver().openOutputStream(uri, "wa")) {
            return false;
        } catch (SecurityException expected) {
            return true;
        }
    }

    private boolean persistDenied(Uri uri) {
        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return false;
        } catch (SecurityException expected) {
            return true;
        }
    }

    private byte[] readBounded(InputStream stream) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = stream.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) {
                throw new IOException("Test receiver limit exceeded");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }

    private String sha256(byte[] bytes) throws java.security.NoSuchAlgorithmException {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format(Locale.ROOT, "%02x", value & 0xff));
        }
        return result.toString();
    }

    private void saveReceipt(JSONObject receipt) {
        String content = receipt.toString();
        File temporary = new File(getFilesDir(), "json-share-receipt.tmp");
        File destination = new File(getFilesDir(), "json-share-receipt.json");
        try (FileOutputStream stream = new FileOutputStream(temporary)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            runOnUiThread(() -> resultView.setText("Receipt write failed"));
            return;
        }
        if (!temporary.renameTo(destination)) {
            runOnUiThread(() -> resultView.setText("Receipt rename failed"));
            return;
        }
        runOnUiThread(() -> resultView.setText(content));
    }
}
