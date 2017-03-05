package ge.ioane.visionware;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.ArrayList;

/**
 * Created by ioane5 on 3/4/17.
 */
public class VoiceCommandDetector {

    private static final String TAG = VoiceCommandDetector.class.getSimpleName();

    private Context mContext;
    private CommandCallbacks mCallback;
    private SpeechRecognizer mSpeechRecognizer;

    public VoiceCommandDetector(Context context, CommandCallbacks callbacks) {
        mContext = context;
        mCallback = callbacks;

        mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        mSpeechRecognizer.setRecognitionListener(new Listener());
    }

    public void activateCommandRecognition() {
        Log.d(TAG, "activateCommandRecognition() called");
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, mContext.getPackageName());
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        mSpeechRecognizer.startListening(intent);
    }

    public void onDestroy() {
        mSpeechRecognizer.destroy();
    }

    private class Listener implements RecognitionListener {


        @Override
        public void onReadyForSpeech(Bundle params) {
            Log.d(TAG, "onReadyForSpeech() called with: params = [" + params + "]");
        }

        @Override
        public void onBeginningOfSpeech() {
            System.out.println("Speech beginning");
        }

        /**
         * The sound level in the audio stream has changed. There is no guarantee that this method will
         * be called.
         *
         * @param rmsdB the new RMS dB value
         */
        @Override
        public void onRmsChanged(float rmsdB) {
            Log.d(TAG, "onRmsChanged() called with: rmsdB = [" + rmsdB + "]");
        }

        /**
         * More sound has been received. The purpose of this function is to allow giving feedback to the
         * user regarding the captured audio. There is no guarantee that this method will be called.
         *
         * @param buffer a buffer containing a sequence of big-endian 16-bit integers representing a
         *               single channel audio stream. The sample rate is implementation dependent.
         */
        @Override
        public void onBufferReceived(byte[] buffer) {
            Log.d(TAG, "onBufferReceived() called with: buffer = [" + buffer + "]");
        }

        /**
         * Called after the user stops speaking.
         */
        @Override
        public void onEndOfSpeech() {
            Log.d(TAG, "onEndOfSpeech() called");
        }

        /**
         * A network or recognition error occurred.
         *
         * @param error code is defined in {@link SpeechRecognizer}
         */
        @Override
        public void onError(int error) {
            Log.d(TAG, "onError() called with: error = [" + error + "]");
            mCallback.onNoCommand();
        }


        @Override
        public void onResults(@NonNull Bundle results) {
            StringBuilder sb = new StringBuilder();
            ArrayList data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
            if (data != null && data.size() > 0) {
                sb.append(data.get(0));
            }
            String command = sb.toString().toLowerCase();
            // Remove
            command = command.replace(" a ", " ");
            command = command.replace(" an ", " ");
            command = command.replace(" me ", " ");
            command = command.replace(" the ", " ");
            command = command.replace("please", "");

            if (command.contains("nearby items")) {
                mCallback.itemsNearbyCommand();
                return;
            }
            command = command.replace("find", "");
            command = command.trim();
            mCallback.findItemWithNameCommand(command);
        }

        @Override
        public void onPartialResults(Bundle partialResults) {
            Log.d(TAG, "onPartialResults() called with: partialResults = [" + partialResults + "]");
        }

        /**
         * Reserved for adding future events.
         *
         * @param eventType the type of the occurred event
         * @param params    a Bundle containing the passed parameters
         */
        @Override
        public void onEvent(int eventType, Bundle params) {
            Log.d(TAG, "onEvent() called with: eventType = [" + eventType + "], params = [" + params + "]");
        }
    }

    public interface CommandCallbacks {
        void findItemWithNameCommand(String result);

        void itemsNearbyCommand();

        void onNoCommand();
    }
}