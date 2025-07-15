package com.example.simpletool.reader.core;

import android.content.Context;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import java.util.LinkedList;
import java.util.Locale;
import java.util.Queue;

public class TTSManager implements TextToSpeech.OnInitListener {
    public interface TTSListener {
        void onStart(String utteranceId);

        void onDone(String utteranceId);

        void onError(String utteranceId);
    }

    private TextToSpeech tts;
    private TTSListener listener;
    private final Queue<String> speechQueue = new LinkedList<>();
    private boolean isReady = false;

    public void initialize(Context context) {
        if (tts == null) {
            tts = new TextToSpeech(context, this);
            tts.setOnUtteranceProgressListener(new UtteranceListener());
        }
    }

    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            int result = tts.setLanguage(Locale.CHINA);
            isReady = (result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED);
        }
    }

    public void speak(String text, String utteranceId) {
        if (!isReady) return;

        if (speechQueue.isEmpty()) {
            speechQueue.offer(utteranceId);
            internalSpeak(text, utteranceId);
        } else {
            speechQueue.offer(utteranceId);
        }
    }

    private void internalSpeak(String text, String utteranceId) {
        Bundle params = new Bundle();
        params.putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId);
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId);
    }

    private class UtteranceListener extends UtteranceProgressListener {
        @Override
        public void onStart(String utteranceId) {
            if (listener != null) listener.onStart(utteranceId);
        }

        @Override
        public void onDone(String utteranceId) {
            speechQueue.remove(utteranceId);
            if (!speechQueue.isEmpty()) {
                String nextId = speechQueue.peek();
                // 触发下一段朗读
            }
            if (listener != null) listener.onDone(utteranceId);
        }

        @Override
        public void onError(String utteranceId) {
            speechQueue.remove(utteranceId);
            if (listener != null) listener.onError(utteranceId);
        }
    }

    public void setListener(TTSListener listener) {
        this.listener = listener;
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
