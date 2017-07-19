package com.projecttango.examples.java.planefitting;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
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
    private boolean mIsPaused = true;
    private Intent mServiceIntent;
    double mWallDist = -1.0;
    Button selectButton = null;
    Spinner mSelectSpinner = null;
    MediaPlayer mediaPlayer = null;
    double distSelect = 0.0;
    Uri uriSound;
    Context contextSound;

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

        //Intalizing variable mSelectSpinner
        mSelectSpinner = (Spinner) findViewById(R.id.spinnerDistance);

        mButton = (Button) findViewById(R.id.startStopButton);
        mButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                /*
                 * Creates a new Intent to start the WallSensingService
                 * IntentService. Passes a URI in the
                 * Intent's "data" field.
                 */
                if (mIsPaused) {
                    String selected = mSelectSpinner.getSelectedItem().toString();
                    distSelect = Double.parseDouble(selected);
                    Log.w(TAG, "Starting Wall Sensing Service");
                    mIsPaused = false;

                    // Send work request to do wall sensing with an intent
                    mServiceIntent = new Intent(v.getContext(), WallSensingService.class);
                    startService(mServiceIntent);
                }
                else {
                    Log.w(TAG, "Stopping Wall Sensing Service");

                    // Since the original wall sensing intent is a forever running job, we can't
                    // send another work request action to stop the service. It would forever be
                    // queued. Instead, we use local broadcasters to communicate the stop request
                    // as a workaround.
                    Intent stopServiceIntent =
                            new Intent(Constants.BROADCAST_WALLSENSINGSERVICE_STOP)
                                    // Puts the status into the Intent
                                    .putExtra(Constants.WALLSENSINGSERVICE_STOP, true);
                    LocalBroadcastManager.getInstance(v.getContext()).sendBroadcast(stopServiceIntent);
                    mIsPaused = true;
                }
            }

        });


        //Select the music you want
        selectButton = (Button) findViewById(R.id.selectMusic);
        selectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mediaPlayer = null;
                Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, 10);
            }
        });

        // The filter's action is BROADCAST_WALLDISTANCE
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == RESULT_OK && requestCode == 10){
            uriSound = data.getData();
            contextSound = this;
            runGame(contextSound, uriSound);
        }
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
            Log.e(TAG, Double.toString(distSelect));
            Log.e(TAG, Double.toString(mWallDist));

        }
    }

    public void runGame(Context context, Uri uri){
        if (context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            // Should we show an explanation?
            if (shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                // Explain to the user why we need to read the contacts
            }
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, 11331);
            // MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE is an
            // app-defined int constant that should be quite unique
        }
        mediaPlayer =  new MediaPlayer();
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            mediaPlayer.setDataSource(context, uri);
            mediaPlayer.prepareAsync();
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        Runnable runnable = new Runnable() {
            @Override
            public void run() {
                while(mIsPause != true){
                    if(mWallDist < distSelect && mWallDist > 0){
                        if(mediaPlayer.isPlaying() != true){
                            mediaPlayer.start();
                        }
                    }
                    else{
                        mediaPlayer.pause();
                    }
                }
            }
        };

//        while(distSelect < 5.0){
//            if(mWallDist < distSelect && mWallDist > 0){
//                if(mediaPlayer.isPlaying()){
//                    mediaPlayer.start();
//                }
//            }
//            else{
//                mediaPlayer.pause();
//            }
//        }
    }

    public void play(MediaPlayer media) {

        media.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if(mp.isPlaying()){
                    mp.start();
                }
            }
        });
    }
}
