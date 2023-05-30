package nl.bliss.external.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;

import static hmi.flipper2.environment.BaseFlipperEnvironment.getGMLClass;
import static hmi.flipper2.environment.BaseFlipperEnvironment.getGMLProperties;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.SequenceInputStream;
import java.util.logging.Level;
import java.util.concurrent.TimeUnit;

public abstract class SimpleMWTTS implements MiddlewareListener {

    protected OkHttpClient client;
    protected String urlAct;
    protected ObjectMapper mapper;
    protected static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    protected static String API_KEY;
    protected volatile boolean running;

    //Parameters for recording
    protected boolean recording;
    protected AudioInputStream current;
    protected String recordID;
    protected long lastTime;

    protected static final DateTimeFormatter formatter= DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-z");

    /**
     * Listens to middleware topic for messages in the form of:
     * {
     *     "content":
     *     {
     *          "type":"bml|ssml|text",
     *          "bml|ssml|text": "<bml></bml>" | "<speak></speak>" | "[text]"
     *     }
     * }
     */
    protected Middleware middleware;
    protected static Logger logger = LoggerFactory.getLogger(SimpleMWTTS.class.getName());
    protected boolean isTalking;
    protected SourceDataLine line;

    /**
     * Constructor for creating a middleware-based TTS service
     * @param properties, the properties for the specific TTS service
     */
    public SimpleMWTTS(JsonNode properties){
        this.mapper = new ObjectMapper();
        this.lastTime = System.currentTimeMillis();
        this.running = false;
        this.isTalking = false;
        if(properties.get("realizer").has("api")){
            API_KEY = properties.get("realizer").get("api").asText();
        }
        if(properties.get("realizer").has("url")){
            this.urlAct = properties.get("realizer").get("url").asText();
        }
        try {
            this.init(properties);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * There's two types of messages supported:
     * "TTS messages", format:
     * {
     *      "tts":{
     *          "type":"text",
     *          "text":[SOME PLAIN TEXT]
     *      }
     * }
     * OR
     * {
     *     "tts":{
     *         "type":"ssml",
     *         "ssml":[SSML]
     *     }
     * }
     * and "Recording messages", format:
     * {
     *      "recording":{
     *          "id":"[ID of the dialogue],
     *          "type":[start|stop]
     *      }
     * }
     * @param jsonNode
     */
    @Override
    public void receiveData(JsonNode jsonNode) {
        if (jsonNode.has("tts") && (jsonNode.get("tts").has("text") || jsonNode.get("tts").has("ssml"))) {
            logger.debug("Speaking: {}", jsonNode.toString());
            this.speak(jsonNode.get("tts"));
        }
//        else if(jsonNode.has("recording")){
//            logger.debug("Recording update: {}",jsonNode.toString());
//            this.recordProcess(jsonNode.get("recording"));
//        }
        else{
            logger.debug("Received other data: {}",jsonNode.toString());
        }
    }

    /**
     * Intialize it with middleware properties (see Environment.xml)
     * This class also includes starting a thread for sending feedback on the TTS if it's talking.
     * @param params, look at Environment.xml for the specifics.
     * @throws Exception
     */
    protected void init(JsonNode params) throws Exception {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.isTalking = false;
        if (!params.has("middleware")) throw new Exception("middleware object required in params");
        Properties mwProperties = getGMLProperties(params.get("middleware"));
        String loaderClass = getGMLClass(params.get("middleware"));
        if (loaderClass == null || mwProperties == null) throw new Exception("Invalid middleware spec in params");
        GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwProperties);
        init(gml.load());
    }

    /**
     * Initializing the middleware and adding a listener for feedback
     * @param m, the middleware to initialize and add a listener to
     */
    protected void init(Middleware m) {
        this.middleware = m;
        this.middleware.addListener(this);
    }

    /**
     * Class that converts the text to an audiostream and plays it.
     * Example input:
     * {
     *     "type" : "ssml",
     *     "ssml": "<speak></speak>"
     * }
     * or
     * {
     *     "type" : "text",
     *     "text" : [String]
     * }
     * @param input, json containing text, bml or ssml to say
     * @return true if we succesfully played the speech
     */
    protected boolean speak(JsonNode input){
        InputStream result;
        result = this.retrieveAudioStream(input);
        this.isTalking = true;
        Thread t = this.feedbackThread();
        t.start();
        this.play(result);
        this.isTalking = false;
        return true;
    }

    /**
     * Feedback thread created as the TTS is playing audio
     * @return the thread to start.
     */
    private Thread feedbackThread(){
        return new Thread(() -> {
            sendFeedback(true);
            while(this.isTalking){
                try {
                    Thread.sleep(20);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }            
            sendFeedback(false);
        });
    }

    /**
     * Sends feedback to the middleware
     * @param speaking, if audio is playing or not
     */
    private void sendFeedback(boolean speaking){
        ObjectNode root = mapper.createObjectNode();
        ObjectNode feedback = mapper.createObjectNode();
        feedback.put("isTalking",speaking);
        feedback.put("type","feedback");
        feedback.put("timestamp", formatter.format(ZonedDateTime.now()));
        root.set("tts",feedback);
        middleware.sendData(root);
    }

    /**
     * Any Act class should implement the class to convert text to inputstream
     * @param params, the parameters, including the text to say or the ssml
     * @return the inputstream of the wav
     */
    protected abstract InputStream retrieveAudioStream(JsonNode params);

    /**
     * Play the inputstream
     * @param input, the inputstream to play
     */
    protected abstract void play(InputStream input);

     /**
     * Record messages should look like this:
     * {
     *    "recording":
     *      {
     *          "id":[timestamp],
     *          "type":[start/stop]
     *      }
     * }
     * @param jsonNode
     */
    private void recordProcess(JsonNode jsonNode) {
        if(jsonNode.has("type")){
            if(jsonNode.get("type").asText().equals("start") && !this.recording){
                logger.debug("Start recording agent audio...");
                this.recording = true;
                this.recordID = jsonNode.get("id").asText(String.valueOf(System.currentTimeMillis()));
            }
            if(jsonNode.get("type").asText().equals("stop")){
                if(this.recording){
                    this.recordID = jsonNode.get("id").asText(String.valueOf(System.currentTimeMillis()));
                    saveRecording();
                    logger.debug("Stop and saving recording agent audio.");
                    this.current = null;
                }
                this.recording = false;
            }
        }
    }

    /**
     * Saves the agent speech in the log folder.
     */
    private void saveRecording(){
        try {
            AudioInputStream silence = getSilence();
            AudioInputStream addAudio = new AudioInputStream(
                    new SequenceInputStream(this.current, silence),
                    this.current.getFormat(),
                    this.current.getFrameLength() + silence.getFrameLength());
            current = addAudio;
            String filename = System.getProperty("user.dir") + File.separatorChar + "log" + File.separatorChar + this.recordID + ".wav";
            AudioSystem.write(this.current,
                    AudioFileFormat.Type.WAVE,
                    new File(filename));
            logger.info("Audio file saved as {}",filename);
            this.current.close();
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(SimpleMWTTS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Method that generates an AudioInputStream of silence between the lastTime and current time.
     * @return an AudioInputStream of silence
     */
    protected AudioInputStream getSilence(){
        long currentTime = System.currentTimeMillis();
        long difference = currentTime - this.lastTime;
        AudioInputStream appendedSilence = null;
        logger.debug("Adding silence: {} seconds", difference/1000);
        while(difference > 0){
            AudioInputStream silence;
            try {
                silence = AudioSystem.getAudioInputStream(SimpleMWTTS.class.getClassLoader().getResourceAsStream("external/silence.wav"));
                difference -= 100;
                if(appendedSilence == null){
                    appendedSilence = silence;
                }
                else{
                    appendedSilence = new AudioInputStream(
                            new SequenceInputStream(appendedSilence, silence),
                            appendedSilence.getFormat(),
                            appendedSilence.getFrameLength() + silence.getFrameLength()
                    );
                }
            } catch (UnsupportedAudioFileException | IOException ex) {
                java.util.logging.Logger.getLogger(SimpleMWTTS.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
            }
        }
        return appendedSilence;
    }

    /**
     * Records the audio with the specified decoding format
     * @param audio, the audio to record
     * @param decodedFormat, the format of the audiostream
     */
    protected void recordAudio(ByteArrayOutputStream audio, AudioFormat decodedFormat) {
        AudioInputStream inRecord = null;
        //AudioStream to record
        AudioInputStream dinRecord;
        BufferedInputStream bufferedRecord = new BufferedInputStream(new ByteArrayInputStream(audio.toByteArray()));
        try {
            inRecord = AudioSystem.getAudioInputStream(bufferedRecord);
        } catch (UnsupportedAudioFileException | IOException e) {
            e.printStackTrace();
        }
        dinRecord = AudioSystem.getAudioInputStream(decodedFormat, inRecord);
        AudioInputStream addAudio;
        AudioInputStream silence = getSilence();
        addAudio = new AudioInputStream(
                new SequenceInputStream(silence, dinRecord),
                dinRecord.getFormat(),
                silence.getFrameLength() + dinRecord.getFrameLength());
        //Make sure to close the silence stream;
        logger.debug("Appending silence");
        //If first audio by agent present, just take that as the current stream
        if(current == null){
            current = addAudio;
            logger.debug("Start audio.");
        }
        //Else append the new audio to the current stream
        else{
            AudioInputStream total = new AudioInputStream(
                    new SequenceInputStream(this.current, addAudio),
                    addAudio.getFormat(),
                    this.current.getFrameLength() + addAudio.getFrameLength());
            current = total;
            logger.debug("Adding audio.");
        }

    }

    /**
     * Currently supports three different TTS systems: Google, MaryTTS and ReadSpeaker
     * @param args
     */
    public static void main(String[] args){
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: -file";
        String propertiesFile = "external/TTS.json";
        boolean record = false;
        SimpleMWTTS act = null;
        if (args.length == 0 || args.length % 2 != 0) {
            logger.error(help);
            System.exit(0);
        }
        for (int i = 0; i < args.length; i = i + 2) {
            if (args[i].equals("-record")) {
                record = Boolean.parseBoolean(args[i+1]);
            }
            else if (args[i].equals("-file")) {
                propertiesFile = args[i+1];
                ObjectMapper mapper = new ObjectMapper();
                JsonNode props;
                try {
                    props = mapper.readTree(SimpleMWTTS.class.getClassLoader().getResourceAsStream(propertiesFile));
                    if(props.has("realizer") && props.get("realizer").has("type")){
                        String type = props.get("realizer").get("type").asText();
                        switch (type){
                            case "ReadSpeaker":
                                logger.info("Intializing TTS with ReadSpeaker");
                                act = new ReadSpeakerMWTTS(props);
                                break;
                            case "Google":
                                logger.info("Intializing TTS with Google Speech");
                                act = new GoogleMWTTS(props);
                                break;
                            // case "MaryTTS": NOT SUPPORTED anymore
                            //     logger.info("Intializing TTS with MaryTTS");
                            //     act = new MaryMWTTS(props);
                            //     break;
                            default:
                                logger.error("No correct realizer for Act provided: {}, exit now.",type);
                                System.exit(1);
                        }
                    }
                    else{
                        logger.error("No realizer for Act provided in {}, exit now.",propertiesFile);
                        System.exit(1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                logger.error("Unknown commandline argument: \"{}\" {} \".\n {}", args[i], args[i + 1], help);
                System.exit(1);
            }
        }
        if(act != null){
            if(record){
                act.recording = true;
            }
//            Thread actThread = new Thread(act);
//            actThread.start();
        }
    }

}
