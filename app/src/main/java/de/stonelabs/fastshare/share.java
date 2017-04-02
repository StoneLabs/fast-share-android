package de.stonelabs.fastshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;
import android.database.Cursor;
import android.provider.MediaStore;
import android.content.ContentResolver;
import android.widget.Toast;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class share extends AppCompatActivity
{
    private TextView pathTV = null;
    private TextView keyTV = null;
    private TextView messageTV = null;
    private TextView responseTV = null;

    private String m_path = "UNKNOWN FILE";
    private String m_key = "UNKNOWN KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState); //MD5 Regex: /^[a-f0-9]{32}$/i
        setContentView(R.layout.activity_share);

        pathTV = (TextView)findViewById(R.id.path);
        keyTV = (TextView)findViewById(R.id.key);
        messageTV = (TextView)findViewById(R.id.status);
        responseTV = (TextView)findViewById(R.id.response);

        clearTV();

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action))
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

                        pathTV.setText(m_path);

                        IntentIntegrator integrator = new IntentIntegrator(this);
                        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
                        integrator.setBeepEnabled(true);
                        integrator.initiateScan();
                    }
                }
            }
    }

    private void clearTV()
    {
        pathTV.setText("Looks like there was an unexpected exception!");
        keyTV.setText("");
        messageTV.setText("");
        responseTV.setText("");
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        m_key = scanResult.getContents();
        keyTV.setText(m_key);

        final FileTransmitter transmitter = new FileTransmitter(
                m_path,
                m_key);

        Thread thread = new Thread(transmitter);
        thread.start();

        while ( transmitter.getStatus() != FileTransmitter.Status.FAILURE   &&
                transmitter.getStatus() != FileTransmitter.Status.EXCEPTION &&
                transmitter.getStatus() != FileTransmitter.Status.SUCCESS)  {}

        runOnUiThread(new Runnable() {
            public void run() {
                messageTV.setText(transmitter.getDetails());
                responseTV.setText(transmitter.getResponse());
                Toast.makeText(share.this, transmitter.getStatus().toString(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }
}
