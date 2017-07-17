package com.projecttango.examples.java.planefitting;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.midi.MidiManager;
import android.media.midi.MidiReceiver;
import android.net.Uri;
import android.nfc.Tag;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.AdapterView;
import android.widget.Button;
import android.content.Intent;
import android.view.*;
import android.widget.Spinner;
import android.widget.Toast;

import com.mobileer.miditools.MidiConstants;
import com.mobileer.miditools.MidiInputPortSelector;
import com.mobileer.miditools.MusicKeyboardView;

import java.io.IOException;

import static android.app.PendingIntent.getActivity;

public class ConfigActivity extends Activity {
    private static final String TAG = ConfigActivity.class.getSimpleName();
    Button mButton = null;
    private Intent mServiceIntent;
    double mWallDist;

    // MIDI attributes
    private MidiInputPortSelector mKeyboardReceiverSelector;
    private MidiManager mMidiManager;
    private int mChannel; // ranges from 0 to 15
    private int[] mPrograms = new int[MidiConstants.MAX_CHANNELS]; // ranges from 0 to 127
    private byte[] mByteBuffer = new byte[3];
    private static final int DEFAULT_VELOCITY = 64;


    @Override
    protected void onCreate(Bundle saveIntentState) {
        super.onCreate(saveIntentState);
        setContentView(R.layout.activity_config);

        mButton = (Button) findViewById(R.id.goCamera);
        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                // Start Plane Fitting Activity
//                Intent i = new Intent(v.getContext(), PlaneFittingActivity.class);
//                startActivity(i);

                /*
                 * Creates a new Intent to start the WallSensingService
                 * IntentService. Passes a URI in the
                 * Intent's "data" field.
                 */
                mServiceIntent = new Intent(v.getContext(), WallSensingService.class);
//                mServiceIntent.setData(Uri.parse(dataUrl));
                startService(mServiceIntent);
            }

        });

        // The filter's action is BROADCAST_ACTION
        IntentFilter statusIntentFilter = new IntentFilter(
                Constants.BROADCAST_WALLDISTANCE);

        // Adds a data filter for the HTTP scheme
//            statusIntentFilter.addDataScheme("http");

        // Instantiates a new DownloadStateReceiver
        DownloadStateReceiver mDownloadStateReceiver =
                new DownloadStateReceiver();
        // Registers the DownloadStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mDownloadStateReceiver,
                statusIntentFilter);


        // MIDI SETUP
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_MIDI)) {
            setupMidi();
        } else {
            Toast.makeText(this, "MIDI not supported!", Toast.LENGTH_LONG)
                    .show();
        }

        // Channel Spinners not needed
//        Spinner spinner = (Spinner) findViewById(R.id.spinner_channels);
//        spinner.setOnItemSelectedListener(new ChannelSpinnerActivity());


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


//    public class ChannelSpinnerActivity implements AdapterView.OnItemSelectedListener {
//        @Override
//        public void onItemSelected(AdapterView<?> parent, View view,
//                                   int pos, long id) {
//            mChannel = pos & 0x0F;
//            updateProgramText();
//        }
//
//        @Override
//        public void onNothingSelected(AdapterView<?> parent) {
//        }
//    }

    private void setupMidi() {
        mMidiManager = (MidiManager) getSystemService(MIDI_SERVICE);
        if (mMidiManager == null) {
            Toast.makeText(this, "MidiManager is null!", Toast.LENGTH_LONG)
                    .show();
            return;
        }

        // Setup Spinner that selects a MIDI input port.
        mKeyboardReceiverSelector = new MidiInputPortSelector(mMidiManager,
                this, R.id.spinner_receivers);

        // KEYBOARD not neccessary.
//        mKeyboard = (MusicKeyboardView) findViewById(R.id.musicKeyboardView);
//        mKeyboard.addMusicKeyListener(new MusicKeyboardView.MusicKeyListener() {
//            @Override
//            public void onKeyDown(int keyIndex) {
//                noteOn(mChannel, keyIndex, DEFAULT_VELOCITY);
//            }
//
//            @Override
//            public void onKeyUp(int keyIndex) {
//                noteOff(mChannel, keyIndex, DEFAULT_VELOCITY);
//            }
//        });
    }

    private void noteOff(int channel, int pitch, int velocity) {
        midiCommand(MidiConstants.STATUS_NOTE_OFF + channel, pitch, velocity);
    }

    private void noteOn(int channel, int pitch, int velocity) {
        midiCommand(MidiConstants.STATUS_NOTE_ON + channel, pitch, velocity);
    }

    private void midiCommand(int status, int data1, int data2) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        mByteBuffer[2] = (byte) data2;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 3, now);
    }

    private void midiCommand(int status, int data1) {
        mByteBuffer[0] = (byte) status;
        mByteBuffer[1] = (byte) data1;
        long now = System.nanoTime();
        midiSend(mByteBuffer, 2, now);
    }

    private void closeSynthResources() {
        if (mKeyboardReceiverSelector != null) {
            mKeyboardReceiverSelector.close();
            mKeyboardReceiverSelector.onDestroy();
        }
    }

    @Override
    public void onDestroy() {
        closeSynthResources();
        stopService(mServiceIntent);
        super.onDestroy();
    }

    private void midiSend(byte[] buffer, int count, long timestamp) {
        try {
            // send event immediately
            MidiReceiver receiver = mKeyboardReceiverSelector.getReceiver();
            if (receiver != null) {
                receiver.send(buffer, 0, count, timestamp);
            }
        } catch (IOException e) {
            Log.e(TAG, "mKeyboardReceiverSelector.send() failed " + e);
        }
    }

    // Broadcast receiver for receiving status updates from the IntentService
    private class DownloadStateReceiver extends BroadcastReceiver
    {
        // Prevents instantiation
        private DownloadStateReceiver() {
        }
        // Called when the BroadcastReceiver gets an Intent it's registered to receive
        @Override
        public void onReceive(Context context, Intent intent) {
        /*
         * Handle Intents here.
         */
            mWallDist = intent.getDoubleExtra(Constants.WALLDISTANCE, 0.0);
            Log.e(TAG, Double.toString(mWallDist));
        }
    }
}

