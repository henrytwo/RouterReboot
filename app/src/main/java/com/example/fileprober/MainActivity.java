package com.example.routerreboot;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import org.apache.commons.codec.binary.Hex;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private Handler mainHandler = new Handler();

    private String log = "";

    private String username = "Admin";
    private String password = "";
    private String ip = "192.168.0.1";

    private boolean settingsConfirmed = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!settingsConfirmed) {
            setupDialog();
        }

    }

    private void setupDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();

        builder.setView(inflater.inflate(R.layout.dialog_setup, null))
                .setPositiveButton(R.string.save, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {

                        EditText ipText = (EditText) findViewById(R.id.ip);
                        EditText usernameText = (EditText) findViewById(R.id.username);
                        EditText passwordText = (EditText) findViewById(R.id.password);

                        ip = ipText.getText().toString();
                        username = usernameText.getText().toString();
                        password = passwordText.getText().toString();

                        Toast.makeText(MainActivity.this, "Settings saved!", Toast.LENGTH_LONG).show();


                    }
                }).show();
    }

    public void reboot(View view) {

        new AlertDialog.Builder(this)
                .setTitle("Are you sure")
                .setMessage("Do you really want to reboot the router? This will cause ~2mins of network downtime")
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog, int whichButton) {
                        log = "Router Reboot by Henry Tu\nJuly 2019 | henrytu.me\n\n";

                        TextView tv = (TextView) findViewById(R.id.textbox);
                        tv.setText("");

                        Thread t = new Thread(new NetworkStuff());
                        t.start();
                    }})
                .setNegativeButton(android.R.string.no, null).show();




    }

    class NetworkStuff implements Runnable {

        private static final String TAG = "NetworkStuff";

        private void updateLog(final String line) {
            mainHandler.post(new Runnable() {
                @Override
                public void run() {

                    Log.d("NetworkStuff", line);

                    log += "[Log] " + line + "\n";

                    TextView tv = (TextView) findViewById(R.id.textbox);
                    tv.setText(log);

                }
            });
        }

        public void run() {

            try {

                JSONObject challengeAndUID = request(false, null, "http://" + ip + "/authentication.cgi?captcha=&dummy=" + System.currentTimeMillis(), "");

                updateLog(challengeAndUID.toString());

                SecretKeySpec keySpec = new SecretKeySpec(password.getBytes(), "HmacMD5");

                Mac mac = Mac.getInstance("HmacMD5");
                mac.init(keySpec);

                byte[] result = mac.doFinal((username + challengeAndUID.getString("challenge")).getBytes());

                String passwordOut = Hex.encodeHexString(result).toUpperCase();


                JSONObject authRequest = request(true, String.format("id=%s&password=%s", username, passwordOut), "http://" + ip + "/authentication.cgi", "uid=" + challengeAndUID.getString("uid"));

                updateLog(authRequest.toString());

                try {
                    // Did it succeed?
                    authRequest.getString("key");

                    // If we made it this far, cookie is active!

                    JSONObject actionRequest = request(true, "EVENT=REBOOT", "http://" + ip + "/serviceasdsa.cgi", "uid=" + challengeAndUID.getString("uid"));

                    updateLog(actionRequest.toString());

                    if (actionRequest.getString("response").contains("OK")) {
                        updateLog("REBOOT COMMAND ISSUED");
                    } else {
                        updateLog("REBOOT FAILED");
                    }

                } catch (Exception e) {
                    e.printStackTrace();

                    updateLog("Authentication Failed");
                }

            } catch (Exception e) {
                e.printStackTrace();

                updateLog(e.getMessage());
            }
        }


        // Wonderful request function from PROJECT HUBG
        // General request method
        // Sends POST or GET request based on parameter
        public JSONObject request(boolean isPOST, String data, String destination, String cookie) {
            try {
                // Init connection

                URLConnection socket;

                socket = new URL(destination).openConnection();
                ((HttpURLConnection) socket).setRequestMethod(isPOST ? "POST" : "GET");

                // Config header
                socket.setConnectTimeout(5000);
                socket.setRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/75.0.3770.100 Safari/537.36");
                socket.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
                socket.setRequestProperty("Content-Type", "application/x-www-form-urlencoded'");
                socket.setRequestProperty("Referer", "http://" + ip + "/");
                socket.setRequestProperty("DNT", "1");

                if (cookie != null) {
                    Log.d("Cookie", "Cookie: " + cookie);
                    socket.setRequestProperty("Cookie", cookie);
                }

                // Flushes JSON object if POST
                if (isPOST) {
                    socket.setDoOutput(true);
                    OutputStreamWriter writer = new OutputStreamWriter(socket.getOutputStream());

                    writer.write(data);
                    writer.flush();
                    writer.close();
                }

                // Reads response
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                StringBuilder rawData = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    rawData.append(line);
                }

                //updateLog(rawData.toString());

                // If it can't parse, it's probably not real json
                try {
                    return new JSONObject(rawData.toString());
                } catch (Exception e) {
                    return new JSONObject("{'response': '" + rawData.toString() + "'}s");
                }

            } catch (Exception e) {
                e.printStackTrace();

                updateLog(e.getMessage());

                return null;
            }
        }
    }
}