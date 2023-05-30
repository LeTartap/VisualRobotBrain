/*
 * Copyright 2018 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package nl.bliss.external.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.api.gax.rpc.ClientStream;
import com.google.api.gax.rpc.ResponseObserver;
import com.google.api.gax.rpc.StreamController;
import com.google.cloud.speech.v1.*;
import com.google.protobuf.ByteString;
import com.google.protobuf.Duration;
import nl.bliss.util.TimeHelper;
import nl.utwente.hmi.middleware.Middleware;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine.Info;
import javax.sound.sampled.TargetDataLine;
import java.text.DecimalFormat;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class GoogleMWASR extends SimpleMWASR {

    private static final int STREAMING_LIMIT = 720000; // ~12 minutes

    private static final Logger logger = LoggerFactory.getLogger(GoogleMWASR.class.getName());

    public static final String RED = "\033[0;31m";
    public static final String GREEN = "\033[0;32m";
    public static final String YELLOW = "\033[0;33m";

    // Creating shared object
    private static final BlockingQueue<byte[]> sharedQueue = new LinkedBlockingQueue<>();
    private static TargetDataLine targetDataLine;
    private static final int BYTES_PER_BUFFER = 6400; // buffer size in bytes

    private static int restartCounter = 0;
    private static ArrayList<ByteString> audioInput = new ArrayList<>();
    private static ArrayList<ByteString> lastAudioInput = new ArrayList<>();
    private static int resultEndTimeInMS = 0;
    private static int isFinalEndTime = 0;
    private static int finalRequestEndTime = 0;
    private static boolean newStream = true;
    private static double bridgingOffset = 0;
    private static boolean lastTranscriptWasFinal = false;
    private static StreamController referenceToStreamController;
    private static ByteString tempByteString;

    public GoogleMWASR(JsonNode props) {
        super(props);
    }

    @Override
    protected void start() {
        try {
            infiniteStreamingRecognize(this.mapper, this.middleware, this.languageCode);
        } catch (Exception e) {
            logger.error("Exception caught: {}",e);
        }
    }

    protected void init(JsonNode params){
        super.init(params);
        if(params.has("interpreter") && params.get("interpreter").has("properties")){
            if(params.get("interpreter").get("properties").has("languageCode")){
                this.languageCode = params.get("interpreter").get("properties").get("languageCode").asText();
                logger.info("Intializing Google Speech with language code: '{}'",this.languageCode);
            }
            else{
                this.languageCode = "en-US";
                logger.warn("No language code defined in the properties, will use '{}'",this.languageCode);
            }
        }
        this.start();
    }

    public static String convertMillisToDate(double milliSeconds) {
        long millis = (long) milliSeconds;
        DecimalFormat format = new DecimalFormat();
        format.setMinimumIntegerDigits(2);
        return String.format(
                "%s:%s /",
                format.format(TimeUnit.MILLISECONDS.toMinutes(millis)),
                format.format(
                        TimeUnit.MILLISECONDS.toSeconds(millis)
                                - TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))));
    }

    /** Performs infinite streaming speech recognition */
    public void infiniteStreamingRecognize(ObjectMapper mapper, Middleware middleware, String languageCode) throws Exception {

        // Microphone Input buffering
        class MicBuffer implements Runnable {

            @Override
            public void run() {
                System.out.println(YELLOW);
                logger.info("Start speaking...Press Ctrl-C to stop");
                targetDataLine.start();
                byte[] data = new byte[BYTES_PER_BUFFER];
                while (targetDataLine.isOpen()) {                    
                    try {
                        int numBytesRead = targetDataLine.read(data, 0, data.length);
                        if ((numBytesRead <= 0) && (targetDataLine.isOpen())) {
                            continue;
                        }
                        sharedQueue.put(data.clone());
                    } catch (InterruptedException e) {
                        logger.error("Microphone input buffering interrupted : " + e.getMessage());
                    }
                    if(isPaused()){
                        while(isPaused()) {
                            try {
                                Thread.sleep(100);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }                        
                    }
                }
            }
        }

        // Creating microphone input buffer thread
        MicBuffer micrunnable = new MicBuffer();
        Thread micThread = new Thread(micrunnable);
        ResponseObserver<StreamingRecognizeResponse> responseObserver;
        try (SpeechClient client = SpeechClient.create()) {
            ClientStream<StreamingRecognizeRequest> clientStream;
            responseObserver =
                    new ResponseObserver<StreamingRecognizeResponse>() {

                        final ArrayList<StreamingRecognizeResponse> responses = new ArrayList<>();

                        public void onStart(StreamController controller) {
                            referenceToStreamController = controller;
                        }

                        public void onResponse(StreamingRecognizeResponse response) {
                            responses.add(response);
                            StreamingRecognitionResult result = response.getResultsList().get(0);
                            Duration resultEndTime = result.getResultEndTime();
                            resultEndTimeInMS =
                                    (int)
                                            ((resultEndTime.getSeconds() * 1000) + (resultEndTime.getNanos() / 1000000));
                            double correctedTime =
                                    resultEndTimeInMS - bridgingOffset + (STREAMING_LIMIT * restartCounter);

                            SpeechRecognitionAlternative alternative = result.getAlternativesList().get(0);
                            if (result.getIsFinal()) {
                                System.out.print(GREEN);
                                System.out.print("\033[2K\r");
                                System.out.printf(
                                        "%s: %s [confidence: %.2f]\n",
                                        convertMillisToDate(correctedTime),
                                        alternative.getTranscript(),
                                        alternative.getConfidence());
                                isFinalEndTime = resultEndTimeInMS;
                                lastTranscriptWasFinal = true;
                                middleware.sendData(constructJson(mapper, alternative));
                            } else {
                                System.out.print(RED);
                                System.out.print("\033[2K\r");
                                System.out.printf(
                                        "%s: %s", convertMillisToDate(correctedTime), alternative.getTranscript());
                                lastTranscriptWasFinal = false;
                                middleware.sendData(constructJson(mapper, alternative));
                            }
                        }

                        public void onComplete() {}

                        public void onError(Throwable t) {}
                    };
            clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

            RecognitionConfig recognitionConfig =
                    RecognitionConfig.newBuilder()
                            .setEncoding(RecognitionConfig.AudioEncoding.LINEAR16)
                            .setLanguageCode(languageCode)
                            .setSampleRateHertz(16000)
                            .build();

            StreamingRecognitionConfig streamingRecognitionConfig =
                    StreamingRecognitionConfig.newBuilder()
                            .setConfig(recognitionConfig)
                            .setInterimResults(true)
                            .build();

            StreamingRecognizeRequest request =
                    StreamingRecognizeRequest.newBuilder()
                            .setStreamingConfig(streamingRecognitionConfig)
                            .build(); // The first request in a streaming call has to be a config

            clientStream.send(request);

            try {
                // SampleRate:16000Hz, SampleSizeInBits: 16, Number of channels: 1, Signed: true,
                // bigEndian: false
                AudioFormat audioFormat = new AudioFormat(16000, 16, 1, true, false);
                Info targetInfo =
                        new Info(
                                TargetDataLine.class,
                                audioFormat); // Set the system information to read from the microphone audio
                // stream

                if (!AudioSystem.isLineSupported(targetInfo)) {
                    logger.error("Microphone not supported");
                    System.exit(0);
                }
                // Target data line captures the audio stream the microphone produces.
                targetDataLine = (TargetDataLine) AudioSystem.getLine(targetInfo);
                targetDataLine.open(audioFormat);
                micThread.start();

                long startTime = System.currentTimeMillis();

                while (true) {

                    long estimatedTime = System.currentTimeMillis() - startTime;

                    if (estimatedTime >= STREAMING_LIMIT) {

                        clientStream.closeSend();
                        referenceToStreamController.cancel(); // remove Observer

                        if (resultEndTimeInMS > 0) {
                            finalRequestEndTime = isFinalEndTime;
                        }
                        resultEndTimeInMS = 0;

                        lastAudioInput = null;
                        lastAudioInput = audioInput;
                        audioInput = new ArrayList<>();

                        restartCounter++;

                        if (!lastTranscriptWasFinal) {
                            System.out.print('\n');
                        }

                        newStream = true;

                        clientStream = client.streamingRecognizeCallable().splitCall(responseObserver);

                        request =
                                StreamingRecognizeRequest.newBuilder()
                                        .setStreamingConfig(streamingRecognitionConfig)
                                        .build();

                        System.out.println(YELLOW);
                        logger.warn("{}: RESTARTING REQUEST\n", restartCounter * STREAMING_LIMIT);

                        startTime = System.currentTimeMillis();

                    } else {

                        if ((newStream) && (lastAudioInput.size() > 0)) {
                            // if this is the first audio from a new request
                            // calculate amount of unfinalized audio from last request
                            // resend the audio to the speech client before incoming audio
                            double chunkTime = STREAMING_LIMIT / lastAudioInput.size();
                            // ms length of each chunk in previous request audio arrayList
                            if (chunkTime != 0) {
                                if (bridgingOffset < 0) {
                                    // bridging Offset accounts for time of resent audio
                                    // calculated from last request
                                    bridgingOffset = 0;
                                }
                                if (bridgingOffset > finalRequestEndTime) {
                                    bridgingOffset = finalRequestEndTime;
                                }
                                int chunksFromMS =
                                        (int) Math.floor((finalRequestEndTime - bridgingOffset) / chunkTime);
                                // chunks from MS is number of chunks to resend
                                bridgingOffset =
                                        (int) Math.floor((lastAudioInput.size() - chunksFromMS) * chunkTime);
                                // set bridging offset for next request
                                for (int i = chunksFromMS; i < lastAudioInput.size(); i++) {
                                    request =
                                            StreamingRecognizeRequest.newBuilder()
                                                    .setAudioContent(lastAudioInput.get(i))
                                                    .build();
                                    clientStream.send(request);
                                }
                            }
                            newStream = false;
                        }

                        tempByteString = ByteString.copyFrom(sharedQueue.take());

                        request =
                                StreamingRecognizeRequest.newBuilder().setAudioContent(tempByteString).build();

                        audioInput.add(tempByteString);
                    }

                    clientStream.send(request);
                }
            } catch (Exception e) {
                logger.error(e.toString());
            }
        }
    }

    private static JsonNode constructJson(ObjectMapper mapper, SpeechRecognitionAlternative alternative) {
        ObjectNode node = mapper.createObjectNode();
        if(lastTranscriptWasFinal){
            node.put("type","final");
        }
        else{
            node.put("type","inc");
        }
        node.put("text",alternative.getTranscript().trim());
        node.put("confidence",alternative.getConfidence());
        node.put("ctml","");
        node.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
        return node;
    }
}
// [END speech_transcribe_infinite_streaming]
