package com.example.android.beam;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcAdapter.CreateBeamUrisCallback;
import android.nfc.NfcAdapter.CreateNdefMessageCallback;
import android.nfc.NfcAdapter.OnNdefPushCompleteCallback;
import android.nfc.NfcEvent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;


public class Beam extends Activity implements CreateNdefMessageCallback,
        OnNdefPushCompleteCallback, CreateBeamUrisCallback, RadioGroup.OnCheckedChangeListener {
    private static final String TAG = "Beam";
    NfcAdapter mNfcAdapter;
    TextView mInfoText;
    RadioGroup mRadioGroup;
    File mPng;
    private static final int MESSAGE_SENT = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mInfoText = (TextView) findViewById(R.id.textView);
        // Check for available NFC Adapter
        mNfcAdapter = NfcAdapter.getDefaultAdapter(this);
        if (mNfcAdapter == null) {
            mInfoText = (TextView) findViewById(R.id.textView);
            mInfoText.setText("NFC is not available on this device.");
        } else {
            // Register callback to listen for message-sent success
            mNfcAdapter.setOnNdefPushCompleteCallback(this, this);

            mRadioGroup = (RadioGroup) findViewById(R.id.radioGroup);
            mRadioGroup.setOnCheckedChangeListener(this);

            savePng();
        }
    }

    private void savePng() {
        String filename = "ic_launcher.png";
        File dir = getFilesDir();
        mPng = new File(dir, filename);
        Log.d(TAG, "mJpeg is " + (mPng.exists() ? " exists." : "not exists."));
        if (!mPng.exists()) {
            Drawable d = getResources().getDrawable(R.drawable.ic_launcher);
            Bitmap bitmap = ((BitmapDrawable) d).getBitmap();
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos);
            byte[] data = baos.toByteArray();
            FileOutputStream fos = null;
            try {
                // 送信するファイルは他のプロセスでも読み取れるようにしておくこと
                fos = openFileOutput(filename, Context.MODE_WORLD_READABLE);
                fos.write(data);
                Log.d(TAG, "file saved to '" + mPng.getPath() + "'");
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /**
     * Implementation for the CreateNdefMessageCallback interface
     */
    @Override
    public NdefMessage createNdefMessage(NfcEvent event) {
        Log.d(TAG, "createNdefMessage");
        NdefMessage msg = null;
        int checkedId = mRadioGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.radio0) {
            Time time = new Time();
            time.setToNow();
            String text = ("Beam me up!\n\n" +
                    "Beam Time: " + time.format("%H:%M:%S"));
            msg = new NdefMessage(NdefRecord.createMime(
                    "application/com.example.android.beam", text.getBytes())
             /**
              * The Android Application Record (AAR) is commented out. When a device
              * receives a push with an AAR in it, the application specified in the AAR
              * is guaranteed to run. The AAR overrides the tag dispatch system.
              * You can add it back in to guarantee that this
              * activity starts when receiving a beamed message. For now, this code
              * uses the tag dispatch system.
              */
              //,NdefRecord.createApplicationRecord("com.example.android.beam")
            );
        } else if (checkedId == R.id.radio1) {
            msg = new NdefMessage(NdefRecord.createUri("http://www.atmarkit.co.jp/"));
        }
        return msg;
    }

    /**
     * Implementation for the OnNdefPushCompleteCallback interface
     */
    @Override
    public void onNdefPushComplete(NfcEvent arg0) {
        Log.d(TAG, "onNdefPushComplete");
        // A handler is needed to send messages to the activity when this
        // callback occurs, because it happens from a binder thread
        mHandler.obtainMessage(MESSAGE_SENT).sendToTarget();
    }

    /** This handler receives a message from onNdefPushComplete */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case MESSAGE_SENT:
                Toast.makeText(getApplicationContext(), "Message sent!", Toast.LENGTH_LONG).show();
                break;
            }
        }
    };

    @Override
    public void onResume() {
        super.onResume();
        // Check to see that the Activity started due to an Android Beam
        if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(getIntent().getAction())) {
            processIntent(getIntent());
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        // onResume gets called after this to handle the intent
        setIntent(intent);
    }

    /**
     * Parses the NDEF Message from the intent and prints to the TextView
     */
    void processIntent(Intent intent) {
        Parcelable[] rawMsgs = intent.getParcelableArrayExtra(
                NfcAdapter.EXTRA_NDEF_MESSAGES);
        // only one message sent during the beam
        NdefMessage msg = (NdefMessage) rawMsgs[0];
        // record 0 contains the MIME type, record 1 is the AAR, if present
        mInfoText.setText(new String(msg.getRecords()[0].getPayload()));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // If NFC is not available, we won't be needing this menu
        if (mNfcAdapter == null) {
            return super.onCreateOptionsMenu(menu);
        }
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_settings:
                Intent intent = new Intent(Settings.ACTION_NFCSHARING_SETTINGS);
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.radio0 || checkedId == R.id.radio1) {
            mNfcAdapter.setBeamPushUrisCallback(null, this);
            mNfcAdapter.setNdefPushMessageCallback(this, this);
        } else {
            mNfcAdapter.setNdefPushMessageCallback(null, this);
            mNfcAdapter.setBeamPushUrisCallback(this, this);
        }
    }

    @Override
    public Uri[] createBeamUris(NfcEvent event) {
        Log.d(TAG, "createBeamUris");
        Uri[] uri = null;
        if (mRadioGroup.getCheckedRadioButtonId() == R.id.radio2) {
            uri = new Uri[] { Uri.fromFile(mPng) };
        }
        return uri;
    }
}
