package nl.bliss.external.asr;

import SpeechAPIDemo.DuplexRecognitionSession;
import SpeechAPIDemo.RecognitionEvent;
import SpeechAPIDemo.RecognitionEventListener;
import SpeechAPIDemo.WsDuplexRecognitionSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.bliss.util.TimeHelper;
import org.concentus.OpusApplication;
import org.concentus.OpusEncoder;
import org.concentus.OpusSignal;
import org.gagravarr.ogg.OggPacketWriter;
import org.gagravarr.opus.OpusAudioData;
import org.gagravarr.opus.OpusFile;
import org.gagravarr.opus.OpusInfo;
import org.gagravarr.opus.OpusTags;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.TargetDataLine;
import java.io.ByteArrayOutputStream;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class SpraakMWASR extends SimpleMWASR {

    boolean stopCapture = false;
    static boolean shouldRestartListener = false; //used to restart the listener when the server kicks you out
    AudioFormat audioFormat;
    TargetDataLine targetDataLine;

    private static String DEFAULT_WS_URL = "ws://this.is.my.server:port/client/ws/speech";
    private static String UserID = "unknown";
    private static String ContentID = "unknown";
    static boolean sendPong = false;

    private static BlockingQueue<JsonNode> speech;

    public SpraakMWASR(JsonNode props) {
        super(props);
    }

    @Override
    protected void init(JsonNode props) {
        super.init(props);
        if(props.get("interpreter").get("properties").has("ws")){
            DEFAULT_WS_URL = props.get("interpreter").get("properties").get("ws").asText();
        }
        else{
            logger.error("No websocket found in properties {}, will exit now.",props.toString());
            System.exit(1);
        }
        if(props.get("interpreter").get("properties").has("userID")){
            UserID = props.get("interpreter").get("properties").get("userID").asText();
        }
        if(props.get("interpreter").get("properties").has("contentID")){
            UserID = props.get("interpreter").get("properties").get("contentID").asText();
        }
        this.startCapturingAudio();
        logger.info("Starting SpraakASR with properties: ws-{}",DEFAULT_WS_URL);
    }

    /**
     * Method for gracefully stopping audio capture
     */
    public void stopCapturingAudio() {
        stopCapture = true;
        targetDataLine.close();
    }

    /**
     * Method that should be called whenever the ASR is not running anymore and we need to start it
     */
    public void startCapturingAudio() {
        WsDuplexRecognitionSession session = null;
        try {
            RecognitionEventAccumulator eventAccumulator = new RecognitionEventAccumulator(this);
            session = new WsDuplexRecognitionSession(DEFAULT_WS_URL);
            session.addRecognitionEventListener(eventAccumulator);
            session.setUserId(UserID);
            session.setContentId(ContentID);
            session.connect();
        } catch (Exception e2) {
            logger.error("Caught Exception: {}",e2.getMessage());
        }
        captureAudio(session);
    }

    //This method captures audio input from a microphone and saves it in a ByteArrayOutputStream object.
    private void captureAudio(DuplexRecognitionSession session){
        try{
            //Get everything set up for capture
            audioFormat = getAudioFormat();
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            // Create a thread to capture the microphone data and start it running.
            // It will run until the Stop button is clicked.
            Thread captureThread = new Thread(new CaptureThread(session));
            captureThread.start();
        } catch (Exception e) {
            logger.error("Exception in captureAudio: {}",e);
            System.exit(0);
        }
    }

    /**
     * Check if the SpraakASR is running
     * @return true if it's running
     */
    public boolean isStarted() {
        return !stopCapture;
    }


    private static AudioFormat getAudioFormat(){
        float sampleRate = 16000.0F;	//8000,11025,16000,22050,44100
        int sampleSizeInBits = 16;		//8,16
        int channels = 1;				//1,2
        boolean signed = true;			//true,false
        boolean bigEndian = false;		//true,false
        return new AudioFormat(
                sampleRate,
                sampleSizeInBits,
                channels,
                signed,
                bigEndian);
    }

    float getLevel(byte[] buffer) {
        int max=0;
        for (int i=0; i<buffer.length; i+=16) {
            short shortVal = (short) buffer[i+1];
            shortVal = (short) ((shortVal << 8) | buffer [i]);
            max=Math.max(max, (int) shortVal);
        }
        return (float) max / Short.MAX_VALUE;
    }

    @Override
    protected void start() {

    }



    //This thread puts the captured audio in the ByteArrayOutputStream object, and optionally sends it
    //to the speech server for live recognition.
    class CaptureThread extends Thread{
        private DuplexRecognitionSession session;
        //An arbitrary-size temporary holding buffer
        CaptureThread (DuplexRecognitionSession session) {
            this.session=session;
        }

        byte tempBuffer[] = new byte[1920];
        byte data_packet[] = new byte[1275];
        int bitrate=0;
        int packetSamples=960;
        OpusEncoder encoder;
        OpusFile file;
        OpusInfo info;
        OggPacketWriter OPwriter;
        ;
        ByteArrayOutputStream BAOut;



        public void run(){
            stopCapture = false;

            //set compression to 128kb/s. Bitrate, e.g. 128kb/s, must be multiplied * 1000
            bitrate=128000;
            logger.info("Spraak ASR started, Bitrate: {} kbit/second",bitrate);

            try {
                if (bitrate==0) {
                    //With no compression we send pcm data. To instruct our decoder, we make a wav header first.
                    session.sendChunk(CreateWAVHeader((int) audioFormat.getSampleRate(), audioFormat.getChannels(), audioFormat.getSampleSizeInBits()), false);
                } else {
                    // for an opus encoded stream we need to create the opusencoder, and also setup the stream writer
                    encoder = new OpusEncoder(16000, 1, OpusApplication.OPUS_APPLICATION_AUDIO);
                    encoder.setBitrate(bitrate);
                    encoder.setSignalType(OpusSignal.OPUS_SIGNAL_MUSIC);
                    encoder.setComplexity(10);
                    encoder.setUseVBR(true);
                    BAOut = new ByteArrayOutputStream();

                    info = new OpusInfo();
                    info.setNumChannels(1);
                    info.setSampleRate(16000);
                    OpusTags tags = new OpusTags();
                    file = new OpusFile(BAOut, info, tags);
                    OPwriter = file.getOggFile().getPacketWriter();
                    OPwriter.bufferPacket(info.write(), false);
                    OPwriter.bufferPacket(tags.write(), false);
                }

                //Loop until stopCapture is set by another thread that services the Stop button.
                while(!stopCapture){
                    if(pauseCapture){
                        while(pauseCapture) {
                            Thread.sleep(100);
                        }                        
                    }
                    //Read data from the internal buffer of the data line.
                    int cnt = targetDataLine.read(tempBuffer, 0, tempBuffer.length);
                    if(cnt > 0) {
                        if (bitrate>0) {
                            // let's use Opus compression
                            short[] pcm = BytesToShorts(tempBuffer, 0, tempBuffer.length);
                            int bytesEncoded = encoder.encode(pcm, 0, packetSamples, data_packet, 0, 1275);
                            byte[] packet = new byte[bytesEncoded];
                            System.arraycopy(data_packet, 0, packet, 0, bytesEncoded);
                            OpusAudioData data = new OpusAudioData(packet);

                            OPwriter.bufferPacket(data.write(), true);
                            // let's send it out in chunks of ~0.25sec. VBR will cause the chunks to typically be a bit larger.
                            if (BAOut.size()>(bitrate/32)) {
                                session.sendChunk(BAOut.toByteArray(), false);
                                BAOut.reset();
                            }

                            // System.out.println("bytesEncoded: " +bytesEncoded);
                        } else {
                            session.sendChunk(tempBuffer, false);
                        }

                        //double level=Math.log10(getLevel(tempBuffer));
                        //System.out.println("VU level: " + 20* level + " dB");
                    }
                    if (sendPong) {
                        sendPong = false;
                        session.sendChunk("PONG".getBytes(), false);
                    }

                }

                byte tmp[] = new byte[0];
                session.sendChunk(tmp,  true);
            } catch (Exception e) {
                logger.error("Exception in run(), stopping audio capture: {}",e);
                stopCapturingAudio();
                if (shouldRestartListener) {
                    logger.error("Server closed connected, will restart to connect!");
                    startCapturingAudio();
                }
            }
        }
    }




    class RecognitionEventAccumulator implements RecognitionEventListener {

        private List<RecognitionEvent> events = new ArrayList<RecognitionEvent>();
        private boolean closed = false;
        private String finalText = "";
        private String incText = "";
        private float confidence = 0;
        private String ctml = "";

        private SpraakMWASR spraakMWASR;

        private List<String> alternatives;

        public void notifyWorkerCount(int count) {
            logger.error("****** N_WORKERS = {}",count);
        }

        public void notifyRequests(int count) {
            logger.error("****** N_REQUESTS = ",count);
        }

        public void notifyDescription(String description) {
            Object obj = JSONValue.parse(description);
            if ( obj != null ) {
                String lang="";
                String modtype="";
                JSONObject jsonObj = (JSONObject) obj;
                if (jsonObj.containsKey("language")) {
                    lang=(String) jsonObj.get("language");
                }
                if (jsonObj.containsKey("modeltype")) {
                    modtype=(String) jsonObj.get("modeltype");
                }
                logger.error("Language / Modeltype: {} / {}",lang,modtype);
                logger.error("****** DESCRIPTION = {}",jsonObj.get("identifier"));
                // System.err.println("****** DESCRIPTION = "+jsonObj);
            }
        }

        public RecognitionEventAccumulator(SpraakMWASR spraakMWASR){
            this.spraakMWASR = spraakMWASR;
        }

        public void onClose() {
            closed = true;
            this.notifyAll();
        }

        public void onRecognitionEvent(RecognitionEvent event) {
            events.add(event);
            logger.debug("Got event: {}");
            if (event.getResult() != null) {
                incText = event.getResult().getHypotheses().get(0).getTranscript();
                confidence = event.getResult().getHypotheses().get(0).getConfidence();
                ctml = ((RecognitionEvent.Hypothesis)event.getResult().getHypotheses().get(0)).getCtmline();
                ObjectNode spraak;
                if(!event.getResult().getHypotheses().get(0).getTranscript().equals("<unk>.")){
                    if (event.getResult().isFinal()) {
                        finalText = event.getResult().getHypotheses().get(0).getTranscript();
                        logger.info("Final hypothesis: {} \n", finalText);
                        spraak = mapper.createObjectNode();
                        spraak.put("type", "final");
                        spraak.put("text", finalText);

                    }
                    else{
                        logger.info("Transcript hypothesis: {}",incText);
                        spraak = mapper.createObjectNode();
                        spraak.put("type", "inc");
                        spraak.put("text", incText);

                    }
                    spraak.put("confidence", confidence);
                    spraak.put("ctml", ctml);
                    spraak.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
                    spraakMWASR.middleware.sendData(spraak);
                }
            }

            if (event.getStatus() == RecognitionEvent.STATUS_PING) {
                logger.warn("Got Ping Request");
            }

            if (event.getResult() == null && event.getStatus()==0) {
                logger.error("Server sent message to disconnect");
                logger.error("Try to reconnect...");
                shouldRestartListener = true;
            }
        }

        public List<RecognitionEvent> getEvents() {
            return events;
        }

        public boolean isClosed() {
            return closed;
        }
    }


    public static short[] BytesToShorts(byte[] input) {
        return BytesToShorts(input, 0, input.length);
    }

    public static short[] BytesToShorts(byte[] input, int offset, int length) {
        short[] processedValues = new short[length / 2];
        for (int c = 0; c < processedValues.length; c++) {
            short a = (short) (((int) input[(c * 2) + offset]) & 0xFF);
            short b = (short) (((int) input[(c * 2) + 1 + offset]) << 8);
            processedValues[c] = (short) (a | b);
        }

        return processedValues;
    }

    public static byte[] ShortsToBytes(short[] input) {
        return ShortsToBytes(input, 0, input.length);
    }

    public static byte[] ShortsToBytes(short[] input, int offset, int length) {
        byte[] processedValues = new byte[length * 2];
        for (int c = 0; c < length; c++) {
            processedValues[c * 2] = (byte) (input[c + offset] & 0xFF);
            processedValues[c * 2 + 1] = (byte) ((input[c + offset] >> 8) & 0xFF);
        }

        return processedValues;
    }

    public static byte[] CreateWAVHeader(int samplerate, int channels, int format) {
        byte[] header = new byte[44];
        long totalDataLen = 36;
        long bitrate = samplerate*channels*format;
        header[0] = 'R';
        header[1] = 'I';
        header[2] = 'F';
        header[3] = 'F';
        header[4] = (byte) (totalDataLen & 0xff);
        header[5] = (byte) ((totalDataLen >> 8) & 0xff);
        header[6] = (byte) ((totalDataLen >> 16) & 0xff);
        header[7] = (byte) ((totalDataLen >> 24) & 0xff);
        header[8] = 'W';
        header[9] = 'A';
        header[10] = 'V';
        header[11] = 'E';
        header[12] = 'f';
        header[13] = 'm';
        header[14] = 't';
        header[15] = ' ';
        header[16] = (byte) format;
        header[17] = 0;
        header[18] = 0;
        header[19] = 0;
        header[20] = 1;
        header[21] = 0;
        header[22] = (byte) channels;
        header[23] = 0;
        header[24] = (byte) (samplerate & 0xff);
        header[25] = (byte) ((samplerate >> 8) & 0xff);
        header[26] = (byte) ((samplerate >> 16) & 0xff);
        header[27] = (byte) ((samplerate >> 24) & 0xff);
        header[28] = (byte) ((bitrate / 8) & 0xff);
        header[29] = (byte) (((bitrate / 8) >> 8) & 0xff);
        header[30] = (byte) (((bitrate / 8) >> 16) & 0xff);
        header[31] = (byte) (((bitrate / 8) >> 24) & 0xff);
        header[32] = (byte) ((channels* format) / 8);
        header[33] = 0;
        header[34] = 16;
        header[35] = 0;
        header[36] = 'd';
        header[37] = 'a';
        header[38] = 't';
        header[39] = 'a';
        header[40] = (byte) (0  & 0xff);
        header[41] = (byte) ((0 >> 8) & 0xff);
        header[42] = (byte) ((0 >> 16) & 0xff);
        header[43] = (byte) ((0 >> 24) & 0xff);

        return header;
    }
}
