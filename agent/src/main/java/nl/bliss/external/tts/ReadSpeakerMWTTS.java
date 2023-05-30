package nl.bliss.external.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;
import org.apache.commons.io.IOUtils;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReadSpeakerMWTTS extends SimpleMWTTS {

    private String voice;
    private String language;
    private String streaming;
    private String audioformat;

    public ReadSpeakerMWTTS(JsonNode props){
        super(props);
        try {
            if(props.get("realizer").has("properties")){
                this.initParams(props.get("realizer").get("properties"));
            }
        } catch (IOException e) {
            logger.error("Could not load other properties {}",props.toString());
            e.printStackTrace();
        }
    }

    /**
     * Initializing the settings for the TTS of ReadSpeaker
     * @param p, the four required parameters
     * @throws IOException
     */
    private void initParams(JsonNode p) throws IOException {
        this.voice = p.get("voice").asText();
        this.language = p.get("language").asText();
        this.streaming = p.get("streaming").asText();
        this.audioformat = p.get("audioformat").asText();
        logger.info("Parameters ReadSpeaker: voice-{}, language-{}. streaming-{}, audioformat-{}",this.voice,this.language,this.streaming,this.audioformat);
    }

    /**
     * Initializing the TTS settings with a given voice for streaming
     * @param voice, Guus, James, Alice or Ilse
     * @throws IOException
     */
    private void initParams(String voice) throws IOException {
        switch(voice){
            case "Guus":
            case "Ilse":
                this.language = "nl_nl";
                break;
            case "Alice":
                this.language = "en_uk";
                break;
            case "James":
                this.language = "en_us";
                break;
            default:
                logger.error("Voice {} does not exist, exiting now.", voice);
                break;
        }
        this.voice = voice;
        this.streaming = "0";
        this.audioformat = "pcm";
    }

    /**
     * Method for setting the required parameters for ReadSpeakers SCAPI
     * @param params, the required parameters
     * @return a JsonObject with at least four parameters
     */
    private JsonNode checkDefault(ObjectNode params){
        ObjectNode ttsParams = mapper.createObjectNode();
        if(!params.has("text") && !params.has("ssml")){
            logger.error("No text or SSML provided!");
        }
        else{
            if(params.has("text")){
                ttsParams.put("text",params.get("text").asText());
            }
            else{
                ttsParams.put("ssml",params.get("ssml").asText());
            }
        }
        if(!params.has("voice")){
            ttsParams.put("voice",this.voice);
        }
        else{
            ttsParams.put("voice",params.get("voice").asText());
        }
        if(!params.has("lang")){
            ttsParams.put("lang",this.language);
        }
        else{
            ttsParams.put("lang",params.get("lang").asText());
        }
        if(!params.has("streaming")){
            ttsParams.put("streaming",this.streaming);
        }
        else{
            ttsParams.put("streaming",params.get("streaming").asText());
        }
        if(!params.has("audioformat")){
            ttsParams.put("audioformat",this.audioformat);
        }
        else{
            ttsParams.put("audioformat",params.get("audioformat").asText());
        }
        if(!params.has("key")){
            ttsParams.put("key",API_KEY);
        }
        else{
            ttsParams.put("key",params.get("key").asText());
        }
        return ttsParams;
    }

    /**
     * ReadSpeaker does a HTTPRequest to retrieve a byteStream
     * @param params, contains the text to parse the .wav for ({"content":{ .... }}
     * @return, the byteStream returned from ReadSpeakers SCAPI
     */
    @Override
    public InputStream retrieveAudioStream(JsonNode params) {
        InputStream stream = null;
        try {
            JsonNode ttsParams = checkDefault(params.deepCopy());
            Iterator<String> keys = ttsParams.fieldNames();
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.urlAct).newBuilder();
            FormBody.Builder builder = new FormBody.Builder();
            while (keys.hasNext()) {
                String key = keys.next();
                builder.add(key,ttsParams.get(key).textValue());
            }
            RequestBody body = builder.build();
            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .post(body)
                    .build();
            Response response = client.newCall(request).execute();
            if(response.isSuccessful()){
                stream = response.body().byteStream();
            }
        } catch (IOException ex) {
            Logger.getLogger(ReadSpeakerMWTTS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return stream;
    }



    /**
     * Implementation for playing the sound for ReadSpeaker, decoding the "audio/wav" file.
     * The BufferedInputStream is necessary for making sure it's read correctly
     * Also listens to the recording parameter to record the audio if need be
     * @param wavInput, the .wav file stream
     */
    @Override
    public void play(InputStream wavInput){
        //AudioStream to play
        AudioInputStream din = null;
        try{
            //OLD, does not work: We make a copy of the stream, so we can both record it and play it.
            //ByteArrayOutputStream baos = new ByteArrayOutputStream();
            //IOUtils.copy(wavInput,baos);
            //InputStream inputClone = new ByteArrayInputStream(baos.toByteArray());
            BufferedInputStream bufferedInput = new BufferedInputStream(wavInput);
            AudioInputStream in = AudioSystem.getAudioInputStream(bufferedInput);
            AudioFormat baseFormat = in.getFormat();
            AudioFormat decodedFormat = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,
                    baseFormat.getSampleRate(), 16, baseFormat.getChannels(),
                    baseFormat.getChannels() * 2, baseFormat.getSampleRate(),
                    false);
            din = AudioSystem.getAudioInputStream(decodedFormat, in);
//            if(recording){
//                this.recordAudio(baos, decodedFormat);
//            }
            //Playing the audio
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, decodedFormat);
            line = (SourceDataLine) AudioSystem.getLine(info);
            if(line != null) {
                line.open(decodedFormat);
                byte[] data = new byte[4096];
                // Start
                line.start();
                int nBytesRead;
                while ((nBytesRead = din.read(data, 0, data.length)) != -1) {
                    line.write(data, 0, nBytesRead);
                }
                // Stop and update lastTime when
                this.lastTime = System.currentTimeMillis();
                line.drain();
                line.stop();
                line.close();
                din.close();
            }
        }
        catch(IOException | LineUnavailableException | UnsupportedAudioFileException e) {
            e.printStackTrace();
        }
        finally {
            if(din != null) {
                try { din.close(); } catch(IOException e) { }
            }
        }
    }

    /**
     * Method for getting the duration of an AudioInputStream
     * @param file, the audioinputstream
     * @return, the duration (which could be used as milliseconds)
     */
    public static Duration getDuration(AudioInputStream file) {
        AudioFormat format = file.getFormat();
        long frames = file.getFrameLength();
        double durationInSeconds = (frames+0.0) / format.getFrameRate();
        double durationInMillis = durationInSeconds * 1000;
        Duration d = Duration.ofMillis((long) durationInMillis);
        return d;
    }
}
