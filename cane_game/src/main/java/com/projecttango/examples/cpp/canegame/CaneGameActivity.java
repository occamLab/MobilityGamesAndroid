/*
 * Copyright 2017 Paul Ruvolo. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// TODO: handle disconnects from the socket server (reset everything)
// TODO: PointCloud2 instead of PointCloud (faster)
package com.projecttango.examples.cpp.canegame;

import com.projecttango.examples.cpp.util.TangoInitializationHelper;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.YuvImage;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.IBinder;
import android.app.Activity;
import android.os.Bundle;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.graphics.Matrix;
import android.speech.tts.TextToSpeech;
import android.support.v4.content.ContextCompat;
import android.support.v4.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.support.v4.app.ActivityCompat;
import android.app.AlertDialog;


/**
 * Main activity controls Tango lifecycle.
 */
public class CaneGameActivity extends Activity implements OnItemSelectedListener{
    public static final String TAG = CaneGameActivity.class.getSimpleName();
    static final int SELECT_MUSIC_REQUEST = 10;
    private static final String CAMERA_PERMISSION = Manifest.permission.CAMERA;
    private static final int CAMERA_PERMISSION_CODE = 0;

    //
    // Tag Detection Variables
    //

    // set to true if you want more verbose control of tag detection
    boolean VERBOSE_UI_CONTROL = false;

    private boolean threadsStarted = false;

    private int globalSlot = 0;
    private double lastDisplayedImageTS = 0.0;

    private static int threadCount = 4;

    private Object fisheyeImageLock = new Object();
    private Object updateImageViewLock = new Object();

    // hardcoded for now
    private static int fisheyeImageWidth = 640;
    private static int fisheyeImageHeight = 480;

    private double targetFrameRate = 10.0;
    private double startingTimeStamp = -1.0;
    private int framesProcessed = 0;

    private Thread[] imagesFisheyeThread = new Thread[threadCount];

    //
    // Game Loop Variables
    //
    private boolean caneGameHasStarted = false;
    private boolean mIsPaused = true;
    Button startStopButton = null;
    Button selectMusicButton = null;


    //
    // Sound Specific Variables
    //
    Uri uriSound;
    Context contextSound;
    MediaPlayer mediaPlayer = null;
    public TextToSpeech textToSpeech;

    //
    // Cane Specific Variables
    //
    private int sweepCounter = 0;
    // distance in meters along cane shaft, btwn tag and tip
    public double tip2TagDistance = 29 * 0.0254;
    private double canePositionY;
    private double prevCanePositionY;
    private Set<Integer> rewardIncrements = new HashSet<Integer>(Arrays.asList(10, 20, 50, 100, 200));
    private ArrayMap<Integer, Boolean> doRewardAt = new ArrayMap<>();
    private ArrayMap<Integer, CheckBox> rewardAtCheckBoxes = new ArrayMap<>();


    /**
     * Check to see if we have the necessary permissions for this app; ask for them if we don't.
     *
     * @return True if we have the necessary permissions, false if we don't.
     */
    private boolean checkAndRequestPermissions() {
        if (!hasCameraPermission()) {
            requestCameraPermission();
            return false;
        }
        return true;
    }


    /**
     * Check to see of we have the necessary permissions for this app.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, CAMERA_PERMISSION) ==
                PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request the necessary permissions for this app.
     */
    private void requestCameraPermission() {
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, CAMERA_PERMISSION)) {
            showRequestPermissionRationale();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{CAMERA_PERMISSION},
                    CAMERA_PERMISSION_CODE);
        }
    }

    /**
     * If the user has declined the permission before, we have to explain that the app needs this
     * permission.
     */
    private void showRequestPermissionRationale() {
        final AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage("Cane game requires camera permission")
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        ActivityCompat.requestPermissions(CaneGameActivity.this,
                                new String[]{CAMERA_PERMISSION}, CAMERA_PERMISSION_CODE);
                    }
                })
                .create();
        dialog.show();
    }

    public static Bitmap rotateBitmap(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
    }

    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        String selected = parent.getItemAtPosition(pos).toString();
        synchronized (fisheyeImageLock) {
            targetFrameRate = Double.parseDouble(selected);
            // restart statistics and rate shaping
            startingTimeStamp = -1.0;
            globalSlot = 0;
            framesProcessed = 0;
        }
    }

    public void onNothingSelected(AdapterView parent) {
        // Do nothing.
    }

    public boolean successfullyDetected(double[] tagDetection) {
        // TangoJniNative.returnArrayFisheye will set tagDetection[0] to -1 if no tag was found
        return tagDetection[0] >= 0.0;
    }
    public double[] calcCaneTip(double[] tagPosition, double[] tagZNorm) {
        double[] tipPosition = new double[3];
        for (int i = 0; i < 3; i++) {
            // normal is pointing towards cane handle
            // opposite direction of normal is the cane tip
            tipPosition[i] =
                    tagPosition[i] - tip2TagDistance * tagZNorm[i];
        }
        return tipPosition;
    }

    // Project Tango Service connection.
    ServiceConnection mTangoServiceConnection = new ServiceConnection() {
        public void onServiceConnected(ComponentName name, IBinder service) {
            TangoJniNative.setBinder(service);
            TangoJniNative.setupConfig();
            TangoJniNative.connectCallbacks();
            TangoJniNative.connect();

            if (threadsStarted) {
                return;
            }

            for (int i = 0; i < imagesFisheyeThread.length; i++) {
                imagesFisheyeThread[i] = new Thread(new Runnable() {
                    public void run() {
                        while (true) {
                            final double ts;
                            boolean processFrame = false;

                            synchronized (fisheyeImageLock) {
                                ts = TangoJniNative.getFisheyeFrameTimestamp();
                                // this is not quite thread safe... need to synchronize the check and assignment
                                int slot = (int) Math.floor(ts * targetFrameRate);
                                if (slot > globalSlot) {
                                    globalSlot = slot;
                                    if (startingTimeStamp == -1.0) {
                                        startingTimeStamp = ts;
                                    }
                                    processFrame = true;
                                }
                            }
                            if (!processFrame) {
                                // it would be better to have some sort of signal sent to us, but
                                // instead we'll just sleep for a bit to avoid checking too frequently
                                try {
                                    Thread.sleep(5);
                                } catch (InterruptedException ex) {
                                    // thread was interrupted... no big deal
                                }
                                continue;
                            }
                            final int[] fisheyeStride = new int[1];
                            final byte[] fisheyePixels = new byte[fisheyeImageWidth*fisheyeImageHeight*3/2];
                            final double[] tagDetection = new double[8];  // 4 points with 2 coordinates each
                            final double[] tagPosition = new double[3];   // 3 coordinates, xyz
                            final double[] tagZNorm = new double[3];      // 3 components of Z unit vector

                            // grab the pixels and any tag detections
                            TangoJniNative.returnArrayFisheye(fisheyePixels, fisheyeStride,
                                    tagDetection, tagPosition, tagZNorm);


                            framesProcessed++;
                            int startSlot = (int) Math.floor(startingTimeStamp*targetFrameRate);
                            final double frameRateRatio = framesProcessed/((float)globalSlot - startSlot);
                            System.out.println("Frame rate goal " + targetFrameRate + " ratio " + frameRateRatio);
                            Log.i(TAG, "x: " + Double.toString(tagPosition[0])
                                    + " y: " + Double.toString(tagPosition[1])
                                    + " z: " + Double.toString(tagPosition[2]));

                            synchronized (updateImageViewLock) {
                                if (ts < lastDisplayedImageTS) {
                                    // no need to display the image, there is already a more recent one that has been displayed
                                    continue;
                                }

                                // mark that we are going to display this image, and put the display of it on the UI event queue
                                lastDisplayedImageTS = ts;

                                if (successfullyDetected(tagDetection)) {
                                    // update cane tip pose
                                    canePositionY = calcCaneTip(tagPosition, tagZNorm)[1];
                                }

                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (VERBOSE_UI_CONTROL) {
                                            TextView textView = (TextView) findViewById(R.id.frame_rate_text);
                                            textView.setText("Actual frame rate: " + String.format("%.1f", frameRateRatio*targetFrameRate));
                                        }

                                        // the fisheye image uses a stride that is not the same as the image width
                                        int[] strides = {fisheyeStride[0], fisheyeStride[0]};
                                        YuvImage fisheyeFrame = new YuvImage(fisheyePixels,
                                                                             android.graphics.ImageFormat.NV21,
                                                                             fisheyeImageWidth,
                                                                             fisheyeImageHeight,
                                                                             strides);
                                        // somewhat hacky method of getting the YUVImage to a BitMap
                                        ByteArrayOutputStream out = new ByteArrayOutputStream();
                                        fisheyeFrame.compressToJpeg(new Rect(0, 0, fisheyeImageWidth, fisheyeImageHeight), 80, out);
                                        byte[] imageBytes = out.toByteArray();
                                        Bitmap image = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
                                        Bitmap mutableBitmap = image.copy(Bitmap.Config.ARGB_8888, true);

                                        if (successfullyDetected(tagDetection)) {
                                            Canvas canvas = new Canvas(mutableBitmap);

                                            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                            paint.setColor(Color.rgb(255, 0, 0));
                                            paint.setStrokeWidth(4.0f);
                                            for (int j = 0; j < 4; j++) {
                                                canvas.drawLine((float) tagDetection[(2 * j) % tagDetection.length],
                                                        (float) tagDetection[(2 * j + 1) % tagDetection.length],
                                                        (float) tagDetection[(2 * j + 2) % tagDetection.length],
                                                        (float) tagDetection[(2 * j + 3) % tagDetection.length],
                                                        paint);
                                            }
                                        }
                                        ImageView iv = (ImageView) findViewById(R.id.fisheye_image);
                                        iv.setImageBitmap(rotateBitmap(mutableBitmap, -90.0f));
                                    }
                                });
                            }
                        }
                    }
                });
                imagesFisheyeThread[i].start();
            }
            threadsStarted = true;
        }

        public void onServiceDisconnected(ComponentName name) {
            // Handle this if you need to gracefully shut down/retry in the event
            // that Project Tango itself crashes/gets upgraded while running.
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        if (VERBOSE_UI_CONTROL) {
            Spinner frameRateSpinner = (Spinner)findViewById(R.id.frame_rate_spinner);
            Integer[] items = new Integer[30];
            for (int i = 0; i < items.length; i++) {
                items[i] = i+1;
            }
            ArrayAdapter<Integer> adapter = new ArrayAdapter<Integer>(this,android.R.layout.simple_spinner_item, items);
            frameRateSpinner.setAdapter(adapter);
            frameRateSpinner.setSelection(((int)targetFrameRate) - 1);      // subtract 1 since setSelection is by index, not by value
            frameRateSpinner.setOnItemSelectedListener(this);
        }
        else {
            // Hide the Verbose UI Elements
            TextView HelpTextView = (TextView) findViewById(R.id.frame_spinner_help);
            HelpTextView.setVisibility(View.INVISIBLE);

            Spinner frameRateSpinner = (Spinner)findViewById(R.id.frame_rate_spinner);
            frameRateSpinner.setVisibility(View.INVISIBLE);

            TextView rateTextView = (TextView) findViewById(R.id.frame_rate_text);
            rateTextView.setVisibility(View.INVISIBLE);
        }


        startStopButton = (Button) findViewById(R.id.startStopButton);

        //Select the music you want
        selectMusicButton = (Button) findViewById(R.id.selectMusic);
        selectMusicButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                // Pause the Game
                if(mIsPaused == false){
                    setButtonPause();
                    mIsPaused = true;
                }
                // Release the Media Player
                if(mediaPlayer != null){
                    mediaPlayer.release();
                }
                // Open the Pick External Music File activity
                mediaPlayer = null;
                Intent intent = new Intent(Intent.ACTION_PICK,
                        android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI);
                startActivityForResult(intent, SELECT_MUSIC_REQUEST);
            }
        });

        textToSpeech = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status != TextToSpeech.ERROR) {
                    textToSpeech.setLanguage(new Locale("eng", "usa"));
                    textToSpeech.setSpeechRate(1.5f);
                    textToSpeech.setPitch(1.618f);
                }
            }

        });

        for (int i: rewardIncrements) {
            switch (i) {
                case 10:
                    setRewardCheckBoxes(i, R.id.rewardAt10);
                case 20:
                    setRewardCheckBoxes(i, R.id.rewardAt20);
                case 50:
                    setRewardCheckBoxes(i, R.id.rewardAt50);
                case 100:
                    setRewardCheckBoxes(i, R.id.rewardAt100);
                case 200:
                    setRewardCheckBoxes(i, R.id.rewardAt200);

            }
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if(resultCode == RESULT_OK && requestCode == SELECT_MUSIC_REQUEST){
            uriSound = data.getData();
            contextSound = this;
            setVariable(contextSound, uriSound);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        TangoInitializationHelper.bindTangoService(this, mTangoServiceConnection);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        // TODO: need to handle this properly for cases when the activity is suspended by Android (e.g., when plugging into the charger)
        // Always call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    protected void onStop() {
        super.onStop();
    }



    @Override
    protected void onStart() {
        super.onStart();
        checkAndRequestPermissions();
    }

    @Override
    protected void onPause() {
        super.onPause();
        TangoJniNative.disconnect();
        unbindService(mTangoServiceConnection);
    }

    public void runCaneGame() {

        // At t = 0, there is no previous
        if (!caneGameHasStarted) {
            caneGameHasStarted = true;
            prevCanePositionY = canePositionY;
        }

        // the cane has passed the midline (y = 0)
        if (prevCanePositionY * canePositionY < 0) {
            sweepCounter++;

            // Reached a reward increment
            if ((rewardIncrements.contains(sweepCounter)) &&
                    (doRewardAt.get(sweepCounter))) {
                mediaPlayer.start();
            }
            else {
                // Count the sweeps when not playing music and not counting previous
                if (!textToSpeech.isSpeaking() && !mediaPlayer.isPlaying()) {
                    String utterance = Integer.toString(sweepCounter);
                    textToSpeech.speak(utterance, TextToSpeech.QUEUE_ADD, null, null);
                }

            }
        }

        prevCanePositionY = canePositionY;

        try {
            Thread.sleep(5);
        } catch (InterruptedException ex) {
            // thread was interrupted... no big deal
        }
    }
    
    public void setVariable(Context context, Uri uri){
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
            Log.e(TAG, "setting data source " + uri);
            mediaPlayer.setDataSource(context, uri);
            //mp3 will be started after completion of preparing...
            mediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                @Override
                public void onPrepared(MediaPlayer player) {
                    final Runnable runnable = new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG,"Started Thread");
                            while(mIsPaused != true){
                                runCaneGame();
                            }
                            Log.e(TAG,"Ended the Thread");
                        }
                    };

                    startStopButton.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {

                            if (mIsPaused) {
                                setButtonPause();
                                mIsPaused = false;

                                //Start the audio thread
                                Thread runThread = new Thread(runnable);
                                runThread.start();
                            }
                            else {
                                mediaPlayer.pause();
                                mIsPaused = true;
                                setButtonStart();
                                sweepCounter = 0;

                            }
                        }



                    });
                }

            });
            mediaPlayer.prepareAsync();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        setButtonStart();
    }

    public CompoundButton.OnCheckedChangeListener rewardAtCheckBoxListener = new CompoundButton.OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
            int id = compoundButton.getId();

            switch (id) {
                case R.id.rewardAt10:
                    doRewardAt.put(10, isChecked);
                case R.id.rewardAt20:
                    doRewardAt.put(20, isChecked);
                case R.id.rewardAt50:
                    doRewardAt.put(50, isChecked);
                case R.id.rewardAt100:
                    doRewardAt.put(100, isChecked);
                case R.id.rewardAt200:
                    doRewardAt.put(200, isChecked);
            }
        }

    };

    public void setRewardCheckBoxes(Integer i, int id) {
        CheckBox rewardAtCheckBox = (CheckBox) findViewById(id);
        rewardAtCheckBox.setOnCheckedChangeListener(rewardAtCheckBoxListener);
        rewardAtCheckBoxes.put(i, rewardAtCheckBox);
        doRewardAt.put(i, true);
        rewardAtCheckBox.setChecked(true);
    };

    //Set the play/puase button ready to go
    public void setButtonStart()
    {
        startStopButton.setText(R.string.start_button);
        startStopButton.setTextColor(0xFF00FF00); //green
        startStopButton.setBackgroundResource(R.drawable.start_button);
    }

    public void setButtonPause()
    {
        startStopButton.setText(R.string.pause_button);
        startStopButton.setTextColor(0xFFFF0000); //red
        startStopButton.setBackgroundResource(R.drawable.pause_button);
    }

    public void setButtonNeutral() {
        startStopButton.setText(R.string.neutral_button);
        startStopButton.setTextColor(0xFF808080); // gray
        startStopButton.setBackgroundResource(R.drawable.neutral_button);
    }
}