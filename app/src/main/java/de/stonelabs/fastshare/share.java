package de.stonelabs.fastshare;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.widget.TextView;

import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

public class share extends AppCompatActivity
{
    TextView pathTV = null;
    TextView keyTV = null;

    private String m_fileUri = "UNKNOWN FILE";
    private String m_key = "UNKNOWN KEY";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share);

        pathTV = (TextView)findViewById(R.id.path);
        keyTV = (TextView)findViewById(R.id.key);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action))
            if (type.startsWith("image/"))
                m_fileUri = ((Uri)intent.getParcelableExtra(Intent.EXTRA_STREAM)).getPath();

        setTexts();

        IntentIntegrator integrator = new IntentIntegrator(this);
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE_TYPES);
        integrator.setBeepEnabled(true);
        integrator.initiateScan();
    }

    private void setTexts()
    {
        pathTV.setText(m_fileUri);
        keyTV.setText(m_key);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        m_key = scanResult.getContents();
        setTexts();
    }
}
