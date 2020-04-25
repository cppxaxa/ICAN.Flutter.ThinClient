package com.ican.thinclient006;

import android.annotation.TargetApi;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Date;
import java.util.EventObject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodChannel;
import ml.QaAnswer;
import ml.QaClient;

public class MainActivity extends FlutterActivity {
    // UI elements.
    private static final int REQUEST_RECORD_AUDIO = 13;
    private static final String LOG_TAG = MainActivity.class.getSimpleName();

    // Working variables.
    short[] recordingBuffer = new short[RECORDING_LENGTH];
    int recordingOffset = 0;
    boolean shouldContinue = true;
    private Thread recordingThread;
    boolean shouldContinueRecognition = true;
    private Thread recognitionThread;
    private final ReentrantLock recordingBufferLock = new ReentrantLock();

    private List<String> labels = new ArrayList<String>();
    private List<String> displayedLabels = new ArrayList<>();
    private RecognizeCommands recognizeCommands = null;
    private Interpreter tfLite;

    private static final int SAMPLE_RATE = 16000;
    private static final int SAMPLE_DURATION_MS = 1000;
    private static final int RECORDING_LENGTH = (int) (SAMPLE_RATE * SAMPLE_DURATION_MS / 1000);
    private static final long AVERAGE_WINDOW_DURATION_MS = 1000;
    private static final float DETECTION_THRESHOLD = 0.50f;
    private static final int SUPPRESSION_MS = 1500;
    private static final int MINIMUM_COUNT = 3;
    private static final long MINIMUM_TIME_BETWEEN_SAMPLES_MS = 15;
    private static final String LABEL_FILENAME = "file:///android_asset/conv_actions_labels.txt";
    private static final String MODEL_FILENAME = "file:///android_asset/conv_actions_frozen.tflite";

    private long lastProcessingTimeMs;
    private Handler handler = new Handler();
    private TextView selectedTextView = null;
    private HandlerThread backgroundThread;
    private Handler backgroundHandler;

    private QaClient qaClient;

    private static final String CHANNEL = "com.ican.thinclient/stream";
    public static final String TAG = "NativeBackend";
    public static HotwordDetectionEvent hotwordDetectionEvent = new HotwordDetectionEvent();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        new MethodChannel(flutterEngine.getDartExecutor().getBinaryMessenger(), CHANNEL)
            .setMethodCallHandler(
                (call, result) -> {
                    // Note: this method is invoked on the main thread.
                    if (call.method.equals("getBatteryLevel")) {
                        int batteryLevel = getBatteryLevel();

                        if (batteryLevel != -1) {
                            result.success(batteryLevel);
                        } else {
                            result.error("UNAVAILABLE", "Battery level not available.", null);
                        }
                    }
                    else if (call.method.equals("initHotwordDetection")) {
                        try {
                            initHotwordDetection();
                        }
                        catch (Exception e)
                        {
                            result.error("HotwordFailure", e.getMessage(), e.getStackTrace());
                        }
                        result.success("Init Hotword Detection Success");
                    }
                    else if (call.method.equals("initQaClient")) {
                        initQaClient();
                        result.success("success");
                    }
                    else if (call.method.equals("unloadQaClient")) {
                        unloadQaClient();
                        result.success("success");
                    }
                    else if (call.method.equals("getAnswerInPassage")) {
                        String passage = call.argument("passage");
                        String question = call.argument("question");

                        Toast.makeText(getApplicationContext(), passage + "\n\n" + question, Toast.LENGTH_SHORT).show();

                        try {
                            List<QaAnswer> answers = answerQuestion(question, passage);
                            if (answers.size() > 0 && answers.get(0).pos.logit > 3) {
                                result.success(answers.get(0).text);
                            }
                            else {
                                result.error("failed", "failed to find answer", "");
                            }
                        } catch (Exception ex) {
                            result.error("failed to find answer with exception", ex.getMessage(), ex.getStackTrace());
                        }
                    }
                    else {
                        result.notImplemented();
                    }
                }
            );
    }

    private void initHotwordDetection() {
        String actualLabelFilename = LABEL_FILENAME.split("file:///android_asset/", -1)[1];
        Log.i(LOG_TAG, "Reading labels from: " + actualLabelFilename);
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(getAssets().open(actualLabelFilename)));
            String line;
            while ((line = br.readLine()) != null) {
                labels.add(line);
                if (line.charAt(0) != '_') {
                    displayedLabels.add(line.substring(0, 1).toUpperCase() + line.substring(1));
                }
            }
            br.close();
        } catch (IOException e) {
            throw new RuntimeException("Problem reading label file!", e);
        }

        // Set up an object to smooth recognition results to increase accuracy.
        recognizeCommands =
                new RecognizeCommands(
                        labels,
                        AVERAGE_WINDOW_DURATION_MS,
                        DETECTION_THRESHOLD,
                        SUPPRESSION_MS,
                        MINIMUM_COUNT,
                        MINIMUM_TIME_BETWEEN_SAMPLES_MS);

        String actualModelFilename = MODEL_FILENAME.split("file:///android_asset/", -1)[1];
        try {
            tfLite = new Interpreter(loadModelFile(getAssets(), actualModelFilename));
            tfLite.setNumThreads(1);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        tfLite.resizeInput(0, new int[]{RECORDING_LENGTH, 1});
        tfLite.resizeInput(1, new int[]{1});

        // Start the recording and recognition threads.
        requestMicrophonePermission();
        startRecording();
        startRecognition();
    }

    /** Memory-map the model file in Assets. */
    private static MappedByteBuffer loadModelFile(AssetManager assets, String modelFilename)
            throws IOException {
        AssetFileDescriptor fileDescriptor = assets.openFd(modelFilename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private int getBatteryLevel() {
        int batteryLevel = -1;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BatteryManager batteryManager = (BatteryManager) getSystemService(BATTERY_SERVICE);
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
        } else {
            Intent intent = new ContextWrapper(getApplicationContext()).
                    registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            batteryLevel = (intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100) /
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        }

        return batteryLevel;
    }

    private void requestMicrophonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(
                    new String[] {android.Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startRecording();
            startRecognition();
        }
    }

    public synchronized void startRecording() {
        if (recordingThread != null) {
            return;
        }
        shouldContinue = true;
        recordingThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                record();
                            }
                        });
        recordingThread.start();
    }

    public synchronized void stopRecording() {
        if (recordingThread == null) {
            return;
        }
        shouldContinue = false;
        recordingThread = null;
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private void record() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);

        // Estimate the buffer size we'll need for this device.
        int bufferSize =
                AudioRecord.getMinBufferSize(
                        SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            bufferSize = SAMPLE_RATE * 2;
        }
        short[] audioBuffer = new short[bufferSize / 2];

        AudioRecord record =
                new AudioRecord(
                        MediaRecorder.AudioSource.DEFAULT,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferSize);

        if (record.getState() != AudioRecord.STATE_INITIALIZED) {
            Log.e(LOG_TAG, "Audio Record can't initialize!");
            return;
        }

        record.startRecording();

        Log.v(LOG_TAG, "Start recording");

        // Loop, gathering audio data and copying it to a round-robin buffer.
        while (shouldContinue) {
            int numberRead = record.read(audioBuffer, 0, audioBuffer.length);
            int maxLength = recordingBuffer.length;
            int newRecordingOffset = recordingOffset + numberRead;
            int secondCopyLength = Math.max(0, newRecordingOffset - maxLength);
            int firstCopyLength = numberRead - secondCopyLength;
            // We store off all the data for the recognition thread to access. The ML
            // thread will copy out of this buffer into its own, while holding the
            // lock, so this should be thread safe.
            recordingBufferLock.lock();
            try {
                System.arraycopy(audioBuffer, 0, recordingBuffer, recordingOffset, firstCopyLength);
                System.arraycopy(audioBuffer, firstCopyLength, recordingBuffer, 0, secondCopyLength);
                recordingOffset = newRecordingOffset % maxLength;
            } finally {
                recordingBufferLock.unlock();
            }
        }

        record.stop();
        record.release();
    }

    public synchronized void startRecognition() {
        runOnUiThread(
            new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Recognition Started", Toast.LENGTH_SHORT).show();
                }
            }
        );

        if (recognitionThread != null) {
            return;
        }
        shouldContinueRecognition = true;
        recognitionThread =
                new Thread(
                        new Runnable() {
                            @Override
                            public void run() {
                                recognize();
                            }
                        });
        recognitionThread.start();
    }

    public synchronized void stopRecognition() {
        if (recognitionThread == null) {
            return;
        }
        shouldContinueRecognition = false;
        recognitionThread = null;
    }

    private void recognize() {

        Log.v(LOG_TAG, "Start recognition");

        short[] inputBuffer = new short[RECORDING_LENGTH];
        float[][] floatInputBuffer = new float[RECORDING_LENGTH][1];
        float[][] outputScores = new float[1][labels.size()];
        int[] sampleRateList = new int[] {SAMPLE_RATE};

        // Loop, grabbing recorded data and running the recognition model on it.
        while (shouldContinueRecognition) {
            long startTime = new Date().getTime();
            // The recording thread places data in this round-robin buffer, so lock to
            // make sure there's no writing happening and then copy it to our own
            // local version.
            recordingBufferLock.lock();
            try {
                int maxLength = recordingBuffer.length;
                int firstCopyLength = maxLength - recordingOffset;
                int secondCopyLength = recordingOffset;
                System.arraycopy(recordingBuffer, recordingOffset, inputBuffer, 0, firstCopyLength);
                System.arraycopy(recordingBuffer, 0, inputBuffer, firstCopyLength, secondCopyLength);
            } finally {
                recordingBufferLock.unlock();
            }

            // We need to feed in float values between -1.0f and 1.0f, so divide the
            // signed 16-bit inputs.
            for (int i = 0; i < RECORDING_LENGTH; ++i) {
                floatInputBuffer[i][0] = inputBuffer[i] / 32767.0f;
            }

            Object[] inputArray = {floatInputBuffer, sampleRateList};
            Map<Integer, Object> outputMap = new HashMap<>();
            outputMap.put(0, outputScores);

            // Run the model.
            tfLite.runForMultipleInputsOutputs(inputArray, outputMap);

            // Use the smoother to figure out if we've had a real recognition event.
            long currentTime = System.currentTimeMillis();
            final RecognizeCommands.RecognitionResult result =
                    recognizeCommands.processLatestResults(outputScores[0], currentTime);
            lastProcessingTimeMs = new Date().getTime() - startTime;
            runOnUiThread(
                    new Runnable() {
                        @Override
                        public void run() {
                            // If we do have a new command, highlight the right list entry.
                            if (!result.foundCommand.startsWith("_") && result.isNewCommand) {
                                Toast.makeText(getApplicationContext(), "Recognized: " + result.foundCommand, Toast.LENGTH_SHORT).show();

                                int labelIndex = -1;
                                for (int i = 0; i < labels.size(); ++i) {
                                    if (labels.get(i).equals(result.foundCommand)) {
                                        labelIndex = i;
                                    }
                                }

                                String[] labelName = {
                                        "Yes", "No", "Up", "Down", "Left", "Right", "On", "Off", "Stop", "Go"
                                };

                                if (labelIndex - 2 >= 0) {
                                    Toast.makeText(getApplicationContext(), "Detected: " + labelName[labelIndex - 2], Toast.LENGTH_LONG).show();

                                    switch (labelName[labelIndex - 2]) {
                                        case "Up":
                                            Toast.makeText(getApplicationContext(), "Publishing hotword Up", Toast.LENGTH_SHORT).show();
                                            hotwordDetectionEvent.PublishHotword("Up");
                                    }
                                }
                            }
                        }
                    });
            try {
                // We don't need to run too frequently, so snooze for a bit.
                Thread.sleep(MINIMUM_TIME_BETWEEN_SAMPLES_MS);
            } catch (InterruptedException e) {
                // Ignore
            }
        }

        Log.v(LOG_TAG, "End recognition");
    }

    private static final String HANDLE_THREAD_NAME = "CameraBackground";

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread(HANDLE_THREAD_NAME);
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    @TargetApi(Build.VERSION_CODES.ECLAIR)
    private void stopBackgroundThread() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
            backgroundThread.quitSafely();
        }
        else {
            backgroundThread.quit();
        }
        try {
            backgroundThread.join();
            backgroundThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            Log.e("amlan", "Interrupted when stopping background thread", e);
        }
    }

    private List<QaAnswer> answerQuestion(String question, String content) {
        question = question.trim();
        if (question.isEmpty()) {
            return new ArrayList<>();
        }

        // Append question mark '?' if not ended with '?'.
        // This aligns with question format that trains the model.
        if (!question.endsWith("?")) {
            question += '?';
        }
        final String questionToAsk = question;
        Boolean questionAnswered = false;

        // Run TF Lite model to get the answer.
        long beforeTime = System.currentTimeMillis();
        Toast.makeText(getApplicationContext(), "going to predict", Toast.LENGTH_LONG).show();
        final List<QaAnswer> answers = qaClient.predict(questionToAsk, content);
        long afterTime = System.currentTimeMillis();
        double totalSeconds = (afterTime - beforeTime) / 1000.0;

        if (!answers.isEmpty()) {
            Toast.makeText(getApplicationContext(), "answers is not empty", Toast.LENGTH_LONG).show();
            return answers;
        }

        Toast.makeText(getApplicationContext(), "answers is empty", Toast.LENGTH_LONG).show();
        return new ArrayList<>();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void initQaClient() {
        qaClient = new QaClient(getApplicationContext());
        qaClient.loadModel();
        qaClient.loadDictionary();
    }

    @RequiresApi(api = Build.VERSION_CODES.KITKAT)
    private void unloadQaClient() {
        qaClient.unload();
        qaClient = null;
    }

    @Override
    protected void onResume() {
        super.onResume();

        startBackgroundThread();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopBackgroundThread();
    }
}
