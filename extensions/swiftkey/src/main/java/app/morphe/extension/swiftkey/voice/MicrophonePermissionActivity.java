package app.morphe.extension.swiftkey.voice;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Toast;

/** Permission dialog in the SAME SwiftKey APK; never starts recording automatically. */
public final class MicrophonePermissionActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            finish();
        } else if (state == null) {
            requestPermissions(new String[] { Manifest.permission.RECORD_AUDIO }, 1);
        }
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != 1) return;
        boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        Toast.makeText(this, granted ? "Microphone allowed. Tap Start in the keyboard to record."
            : "Microphone permission is required. You can allow it in SwiftKey app settings.",
            Toast.LENGTH_LONG).show();
        finish();
    }

    public static void request(android.content.Context context) {
        context.startActivity(new Intent(context, MicrophonePermissionActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }
}
