package nl.bliss.environments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import nl.bliss.util.TimeHelper;

import java.time.ZonedDateTime;
import java.util.regex.Pattern;

/**
 * In this class we want to interface between Flipper and an agent/TTS engine. Could be with emoji/plaintext/text
 * Input:
 **/

public class TTSEnvironment extends BaseFlipperEnvironment {

    private GenericMiddlewareEnvironment mEnv;

    private final ObjectMapper mapper;
    private boolean isTalking;
    private volatile boolean intentUpdate;
    private JsonNode realizer;

    public String getVoice() {
        return voice;
    }

    public String getLanguage() {
        return language;
    }

    public String getStreaming() {
        return streaming;
    }

    public String getAudioformat() {
        return audioformat;
    }

    private String voice;
    private String language;
    private String streaming;
    private String audioformat;

    private Pattern ssmlEmotion;
    
    public TTSEnvironment(){
        mapper = new ObjectMapper();
    }

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
        switch (fenvmsg.cmd) {
            case "send_tts":
                if (!fenvmsg.params.has("content")) {
                    logger.info("{}: Got send_tts request, but {} don't contain a tts property...",fenvmsg.environment,fenvmsg.params);
                    break;
                }
                if (fenvmsg.params.get("content").isArray()) {
                    String[] ttss = mapper.convertValue(fenvmsg.params.get("content"), String[].class);
                    for (String tts : ttss) {
                        logger.debug("Performing tts: \n\n"+tts+"\n");
                        this.mEnv.sendData(mapper.readTree(tts));
                    }
                }
                else if(fenvmsg.params.get("content").isObject()){
                    JsonNode content = fenvmsg.params.get("content");
                    String tts = fenvmsg.params.get("content").asText();
                    logger.debug("Performing tts: \n\n"+tts+"\n");
                    ObjectNode ttsNode = mapper.createObjectNode();
                    if(fenvmsg.params.get("content").has("type")){
                        switch(content.get("type").asText()){
                            case "ssml":
                                tts = content.get("parameters").get("ssml").asText();
//                                tts = parseSSML(tts);
                                ttsNode.put("ssml",tts);
                                break;
                            case "text":
                                tts = content.get("parameters").get("text").asText();
                                //tts = content.get("text").asText();
                                ttsNode.put("text",tts);
                                break;
                            case "bml":
                                tts = content.get("parameters").get("bml").asText();
                                ttsNode.put("bml",tts);
                                break;
                            default:
                                logger.warn("Unknown type defined for content, will try using default text for {}",tts);
                                tts = tts = content.get("parameters").get("text").asText();
                                ttsNode.put("text",tts);
                                break;
                        }
                    }
                    else{
                        logger.warn("No type defined for content, using default text for {} \n",tts);
                        ttsNode.put("text",tts);
                    }
                    ttsNode.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
                    ttsNode.put("moveID",content.get("parameters").get("id").asInt());
                    enqueueMessage(ttsNode,"tts",fenvmsg.msgId);
                    this.mEnv.sendData(mapper.createObjectNode().set("tts",ttsNode));
                }
                else if(fenvmsg.params.get("content").isTextual()){
                    String tts = fenvmsg.params.get("content").asText();
                    logger.debug("Performing tts: \n\n"+tts+"\n");
                    JsonNode text = mapper.createObjectNode().put("text",tts);
                    JsonNode node = mapper.createObjectNode().set("content",text);
                    this.mEnv.sendData(node);
                }
                else {
                    logger.info("Got send_tts request, but tts is neither array nor object...\n");
                }
                break;
            default:
                logger.warn("Unhandled message: "+fenvmsg.cmd);
                break;
        }
        return null;
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        this.mEnv = GenericMiddlewareEnvironment.loadMiddlewareEnvironment(this.mEnv,envs);
    }

    @Override
    public void init(JsonNode params) throws Exception {
        this.realizer = params.get("realizer");
        if(this.realizer.has("type")){
            String type = this.realizer.get("type").asText();
            switch(type){
                case "ReadSpeaker":
                    this.language = this.realizer.get("properties").get("language").asText();
                    this.audioformat = this.realizer.get("properties").get("audioformat").asText();
                    this.voice = this.realizer.get("properties").get("voice").asText();
                    this.streaming = this.realizer.get("properties").get("streaming").asText();
                    break;
                case "ASAP":
                    break;
                case "Google":
                    break;
                default:
                    logger.error("No appropriate TTS engine selected");
                    break;
            }
        }

    }

    public JsonNode getRealizer(){
        return this.realizer;
    }
}
