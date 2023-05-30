package nl.bliss.external.tts;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.cloud.texttospeech.v1.*;
import com.google.protobuf.ByteString;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class GoogleMWTTS extends SimpleMWTTS {

    private String languageCode;
    private AudioEncoding audioEncoding;
    private String name;
    private SsmlVoiceGender gender;
    private Number speakingRate;
    private Number pitch;
    private TextToSpeechClient ttsClient;

    public GoogleMWTTS(JsonNode props){
        super(props);
        try {
            if(props.get("realizer").has("properties")){
                JsonNode field = props.get("realizer").get("properties");
                if(field.has("languageCode")){
                    this.languageCode = field.get("languageCode").asText();
                }
                else{
                    logger.warn("Language code undefined, choosing 'en-US' as default");
                    this.languageCode = "en-US";
                }
                if(field.has("audioEncoding")){
                    this.audioEncoding = AudioEncoding.valueOf(field.get("audioEncoding").asText());
                }
                else{
                    logger.warn("Audio encoding undefined, choosing LINEAR16 as default");
                    this.audioEncoding = AudioEncoding.LINEAR16;
                }
                if(field.has("ssmlGender")){
                    this.gender = SsmlVoiceGender.valueOf(field.get("ssmlGender").asText());
                }
                else{
                    logger.warn("SSML Gender undefined, choosing NEUTRAL as default");
                    this.gender = SsmlVoiceGender.NEUTRAL;
                }
                if(field.has("name")){
                    this.name = field.get("name").asText();
                }
                else{
                    logger.warn("Voice name undefined, choosing nl-NL-Wavenet-C as default");
                    this.name = "nl-NL-Wavenet-C";
                }
                this.ttsClient = TextToSpeechClient.create();
                logger.info("GoogleTTS parameters: languageCode-{}, gender-{}, name-{}, audioEncoding-{}",this.languageCode,this.gender,this.name, this.audioEncoding);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Retrieve the audio file from Google's TTS
     *
     * @param nodeToSay, the text to say
     * @return the speech string
     */
    public ByteString retrieveAudio(JsonNode nodeToSay) throws IOException {
        // Set the text input to be synthesized
        SynthesisInput input = SynthesisInput.newBuilder().setText(nodeToSay.get("text").asText()).build();

        // Build the voice request, select the language code ("en-US") and the ssml voice gender
        // ("neutral")
        VoiceSelectionParams voice =
                VoiceSelectionParams.newBuilder()
                        .setLanguageCode(this.languageCode)
                        .setSsmlGender(this.gender)
                        .setName(this.name)
                        .build();

        // Select the type of audio file you want returned
        AudioConfig audioConfig =
                AudioConfig.newBuilder().setAudioEncoding(this.audioEncoding).build();

        // Perform the text-to-speech request on the text input with the selected voice parameters and
        // audio file type
        SynthesizeSpeechResponse response = ttsClient.synthesizeSpeech(input, voice, audioConfig);

        // Get the audio contents from the response
        return response.getAudioContent();

    }

    /**
     * Specific extraction for GoogleTTS that converts a Base64 String to a byte inputstream for audio
     * @param jsonText, contains the 'audioContent' parameter
     * @return the bytearray inputstream
     */
    @Override
    public InputStream retrieveAudioStream(JsonNode jsonText){
        try {
            ByteString result = this.retrieveAudio(jsonText);
            return new ByteArrayInputStream(result.toByteArray());

        } catch (IOException e) {
        }
        return null;
    }

    /**
     * For the GoogleTTS, a specific implementation requires the translation from Base64 to inputstream
     * @param input, stream to play
     */
    @Override
    public void play(InputStream input){
        AudioInputStream stream;
        try {
            stream = AudioSystem.getAudioInputStream(input);
            AudioFormat format = stream.getFormat();
            SourceDataLine.Info info = new DataLine.Info(SourceDataLine.class, stream
                    .getFormat(), ((int) stream.getFrameLength() * format.getFrameSize()));
            line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(stream.getFormat());
            line.start();
            isTalking = true;

            int numRead = 0;
            byte[] buf = new byte[line.getBufferSize()];
            while ((numRead = stream.read(buf, 0, buf.length)) >= 0) {
                int offset = 0;
                while (offset < numRead) {
                    offset += line.write(buf, offset, numRead - offset);
                }
            }
            line.drain();
            line.stop();
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
        }

    }
}
