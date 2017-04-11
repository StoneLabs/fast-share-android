package de.stonelabs.fastshare;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.database.Cursor;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.widget.Toast;

import org.json.*;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;
import com.ipaulpro.afilechooser.utils.FileUtils;

public class share extends AppCompatActivity
{
    private static final int REQUEST_CHOOSER = 0x0000c1f9; //arbitrary?
    private static final int REQUEST_PERMISS = 0x0000a86b; //arbitrary?

    private TextView messageTV = null;
    private TextView responseTV = null;
    private ProgressBar progressBar = null;
    private ProgressBar progressCircle = null;

    private String m_path = "UNKNOWN FILE";
    private String m_key = "UNKNOWN KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState); //MD5 Regex: /^[a-f0-9]{32}$/i
        setContentView(R.layout.activity_share);

        //Get GUI-elements
        messageTV = (TextView)findViewById(R.id.status);
        responseTV = (TextView)findViewById(R.id.response);
        progressBar = (ProgressBar)findViewById(R.id.progressBar);
        progressCircle = (ProgressBar)findViewById(R.id.progressCircle);
        progressCircle.setVisibility(View.INVISIBLE);
        clearTV();

        //Get file information
        Intent intent = getIntent();
        String action = intent.getAction();

        if (Intent.ACTION_SEND.equals(action)) {
            String type = intent.getType();
            if (type.startsWith("image/")) {
                Bundle extras = intent.getExtras();
                if (extras.containsKey(Intent.EXTRA_STREAM)) {
                    Uri uri = extras.getParcelable(Intent.EXTRA_STREAM);
                    String scheme = uri.getScheme();
                    if (scheme.equals("content")) {
                        ContentResolver contentResolver = getContentResolver();
                        Cursor cursor = contentResolver.query(uri, null, null, null, null);
                        cursor.moveToFirst();
                        m_path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));

                        initScan();
                    }
                }
            }
        }
        else if (Intent.ACTION_MAIN.equals(action))
        {
            // Create the ACTION_GET_CONTENT Intent
            Intent getContentIntent = FileUtils.createGetContentIntent();

            Intent chooserIntent = Intent.createChooser(getContentIntent, "Select a file");
            startActivityForResult(chooserIntent, REQUEST_CHOOSER);
        }
    }

    private void initScan()
    {
        //Initialize QR-Scan
        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setBeepEnabled(true);
        integrator.setPrompt("Scan an FAST-SHARE QR code to proceed..."); //Tell the user what to scan
        integrator.initiateScan();
    }

    //Clear both response text views
    private void clearTV()
    {
        messageTV.setText("");
        responseTV.setText("");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        switch (requestCode)
        {
            //Handle chosen file
            case REQUEST_CHOOSER:
            {
                if (resultCode == RESULT_OK)
                {
                    final Uri uri = intent.getData();

                    // Get the File path from the Uri
                    m_path = FileUtils.getPath(this, uri);

                    //Start the QR scan
                    initScan();
                }
                else
                    finish(); //Never happened yet
                break;
            }
            //Handle QR code result
            case IntentIntegrator.REQUEST_CODE:
            {
                IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);

                //Check QR code for being a valid MD5 hash
                m_key = scanResult.getContents();
                if (m_key == null || m_key.length() != 32) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(share.this, "Invalid QR-Code!",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    finish(); //Kill the app if not
                    return;
                }

                sendFile(); //Check for
                break;
            }
        }

    }

    private boolean askedForPermissions = false;
    private void sendFile()
    {
        // Check for necessary read permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
        {
            if (askedForPermissions) //Request permission only one time to not annoy the user
            {
                Toast.makeText(share.this, "Missing necessary permissions!",
                        Toast.LENGTH_LONG).show();
                finish(); //Kill the app without read permission
            }

            askedForPermissions = true;
            //Request permission
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQUEST_PERMISS);
        }
        else
        {
            //Send file
            final FileTransmitter transmitter = new FileTransmitter(m_path, m_key);
            new Thread(transmitter).start();

            progressCircle.setVisibility(View.VISIBLE);

            //Create background thread handling progress feedback
            new Thread(new Runnable() {
                @Override
                public void run() {
                    while (transmitter.getStatus() != FileTransmitter.Status.FAILURE &&
                            transmitter.getStatus() != FileTransmitter.Status.EXCEPTION &&
                            transmitter.getStatus() != FileTransmitter.Status.SUCCESS)
                        progressBar.setProgress((int) transmitter.getProgress());
                    progressBar.setProgress(100);

                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (transmitter.getStatus() == FileTransmitter.Status.FAILURE) {
                                String toast = "unknown";
                                try {
                                    JSONObject obj = new JSONObject(transmitter.getResponse());
                                    toast = obj.getString("error");

                                } catch (Exception ex) {
                                }

                                Toast.makeText(share.this, "Error: " + toast,
                                        Toast.LENGTH_LONG).show();
                                finish();
                            } else if (transmitter.getStatus() == FileTransmitter.Status.SUCCESS) {
                                Toast.makeText(share.this, "File sent!",
                                        Toast.LENGTH_LONG).show();
                                finish();
                            } else if (transmitter.getStatus() == FileTransmitter.Status.EXCEPTION) {
                                progressCircle.setVisibility(View.INVISIBLE);
                                messageTV.setText(transmitter.getDetails());
                            }
                        }
                    });

                }
            }).start(); //Start the background thread
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults)
    {
        switch (requestCode)
        {
            case REQUEST_PERMISS:
                    sendFile(); //Send files checked if the permission was granted
                                //or not. Checking it here is not necessary.
        }
    }
}
