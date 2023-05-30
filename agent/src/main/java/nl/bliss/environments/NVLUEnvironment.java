package nl.bliss.environments;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import nl.bliss.util.TimeHelper;
import okhttp3.*;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;

public class NVLUEnvironment extends BaseFlipperEnvironment {

    private ObjectMapper mapper;
    private OkHttpClient client;
    private String url;
    private JsonNode intents;
    private String language;

    private UMEnvironment umEnvironment;

    @Override
    public void init(JsonNode params) throws Exception {
        mapper = new ObjectMapper();
        client = new OkHttpClient();
        url = params.get("url").asText();
        try {
            HttpUrl.parse(this.url).newBuilder();
        } catch (Exception e) {
            logger.error("Do you have the python_server.py running on {}?", url);
        }
        String file = params.get("intents").asText();
        InputStream intentFile = NVLUEnvironment.class.getClassLoader().getResourceAsStream(file);
        intents = mapper.readTree(intentFile);
        this.language = params.get("language").asText();
    }

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {

        switch (fenvmsg.cmd) {
            case "nvlu":
                if (fenvmsg.params.has("content")) {
                    logger.debug("Processing user behaviour");
                    Thread t = new NVLUEnvironment.UserMoveCreator("UserMoveCreator",fenvmsg,this);
                    t.start();
                }
                break;
            default:
                logger.warn("Unhandled message in {}: {}, content: {}", fenvmsg.environment,fenvmsg.cmd,fenvmsg.params.asText());
        }
        return null;
    }




    /**
     * The method that creates the user move node with the relevant information
     *
     * @param content, semantic content of the sentence of the user
     * @return the user move
     */
    private ObjectNode performNVLU(JsonNode content){
        ObjectNode node = mapper.createObjectNode();
        if(content.has("text")){
            node.put("text", content.get("text").asText());
            //node.set("content", determineContent(content));
            node.put("intent", determineIntent(content));
            if(node.get("intent").asText().equals("inform")){
                node.set("topics", determineTopics(content));
            }
            else{
                node.set("topics", mapper.createObjectNode());
            }
            node.set("emotion", determineEmotion(content));
            node.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
        }
        else if(content.has("interaction")){
            node.put("intent", determineIntent(content));
            node.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
        }
        else{
            logger.error("No text or interaction in node to perform NVLU on: {}.",content.toString());
        }
        return node;
    }

    /**
     * Determines the emotion of the sentence, with the Pattern library.
     * @param text the node that contains the user text and other information about the interaction
     */
    public JsonNode determineEmotion(JsonNode text) {
        ObjectNode sentiment = mapper.createObjectNode();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/sentiment");
        urlBuilder.addQueryParameter("text", text.get("text").asText());
        urlBuilder.addQueryParameter("lang", this.language);
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                JsonNode node = mapper.readTree(response.body().string());
                sentiment.set("sentiment", node.get("sentiment"));
                return sentiment;
            }
        } catch (IOException ex) {
            Logger.getLogger(NVLUEnvironment.class.getName()).log(Level.SEVERE, null, ex);
            logger.error("Cannot connect to the NVLU server, will find no sentiment.");
        }
        ArrayNode array = mapper.createArrayNode();
        array.add(0);
        array.add(0);
        sentiment.set("sentiment", array);
        return sentiment;
    }

    /**
     * Extracts the entities from the user sentence. Preferably we use another process for this, such as
     * the CoreNLP package via a server or a request via spaCy.
     * Could also use ConceptNet for that, has already a HTTP API
     * Currently returns the NPs and VPs of user phrases
     */
    public JsonNode determineContent(JsonNode node) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/content");
        urlBuilder.addQueryParameter("text", node.get("text").asText());
        urlBuilder.addQueryParameter("lang", this.language);
        return requestJsonObjectBuilder(urlBuilder);
    }

    /**
     * Collective function for getting topics and events from the text
     * @param node, the node containing the text to extract from
     * @return a node filled with the topics and events from the last user utterance.
     */
    public JsonNode determineTopics(JsonNode node){
        ObjectNode phrasesAndEvents = mapper.createObjectNode();
        phrasesAndEvents.set("phrases", determinePhrases(node));
        phrasesAndEvents.set("events",determineEvent(node));
        return phrasesAndEvents;
    }

    /**
     * Method for extracting the topics from a text (with or without context)
     * @param node, a node that contains text, a language and optionally context for resolving (only EN)
     * @return a node with the NPs and VPs.
     */
    public JsonNode determinePhrases(JsonNode node){
        ObjectNode content = mapper.createObjectNode();
        content.set("NPS", getNPS(node).get("chunks"));
        content.set("VPS", getVPS(node).get("chunks"));
        return content;
    }

    /**
     * Extracts the VPs from a sentence
     * @param node, contains the text and language, optionally the context
     * @return the vps in a JsonNode
     */
    private JsonNode getVPS(JsonNode node) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/vps");
        urlBuilder.addQueryParameter("text", node.get("text").asText());
        urlBuilder.addQueryParameter("lang", this.language);
        if(node.has("context")){
            urlBuilder.addQueryParameter("context", node.get("context").asText());
        }
        return requestJsonObjectBuilder(urlBuilder);
    }

    /**
     * Extracts the NPs from a sentence
     * @param node, contains the text and language, optionally the context
     * @return the nps in a JsonNode
     */
    private JsonNode getNPS(JsonNode node) {
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/nps");
        urlBuilder.addQueryParameter("text", node.get("text").asText());
        urlBuilder.addQueryParameter("lang", this.language);
        if(node.has("context")){
            urlBuilder.addQueryParameter("context", node.get("context").asText());
        }
        return requestJsonObjectBuilder(urlBuilder);
    }

    /**
     * Builds requests to the NLG/NLU server
     * @param urlBuilder, the builder for the connection
     * @return a JsonNode with the result of the request.
     */
    private JsonNode requestJsonObjectBuilder(HttpUrl.Builder urlBuilder) {
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                return mapper.readTree(response.body().string());
            }
        } catch (IOException ex) {
            Logger.getLogger(NVLUEnvironment.class.getName()).log(Level.SEVERE, null, ex);
            logger.error("Cannot connect to the NVLU server, will not execute request.");
        }
        return mapper.createObjectNode();
    }

    /**
     * Calling the method for extracting 'events', based on Semantic Role Labelling (only EN)
     * @param node, the node contains text, language and possible context (only EN)
     * @return a list of events in a JsonNode
     */
    public JsonNode determineEvent(JsonNode node){
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/srl");
        urlBuilder.addQueryParameter("text", node.get("text").asText());
        urlBuilder.addQueryParameter("lang", this.language);
        if(node.has("context")){
            urlBuilder.addQueryParameter("context", node.get("context").asText());
        }
        return requestJsonObjectBuilder(urlBuilder).get("result");
    }

    /**
     * Extracts the dialog act from the user sentence, based on the usermoves file
     * The order is that it first checks if
     * 1) the exact keywords occur in the string
     * 2) the string starts with a specific keyword
     * 3) if any of the keywords occur in the string
     */

    public String determineIntent(JsonNode node) {
        ArrayNode userIntents = (ArrayNode) this.intents;
        Iterator<JsonNode> intentIt = userIntents.elements();
        while (intentIt.hasNext()) {
            JsonNode intent = intentIt.next();
            if(node.has("text") && intent.has("keywords") && intent.get("keywords").size() > 0){
                String text = node.get("text").asText().toLowerCase().replace(".","");
                JsonNode keywords = intent.get("keywords");
                if (keywords.has("exactly")) {
                    ArrayNode words = keywords.withArray("exactly");
                    for (JsonNode keyword : words) {
                        if (text.equals(keyword.asText())) {
                            return intent.get("intent").asText();
                        }
                    }
                }
                if (keywords.has("starts")) {
                    ArrayNode words = keywords.withArray("starts");
                    for (JsonNode keyword : words) {
                        //Look if the first word of the sentence matches
                        if (text.equals(keyword.asText()+" ")) {
                            return intent.get("intent").asText();
                        }
                    }
                }
                if (keywords.has("ends")) {
                    ArrayNode words = keywords.withArray("ends");
                    for (JsonNode keyword : words) {
                        //Look if the last word of the sentence matches
                        if (text.endsWith(" "+keyword.asText())) {
                            return intent.get("intent").asText();
                        }
                    }
                }
                if (keywords.has("exists")) {
                    ArrayNode words = keywords.withArray("exists");
                    for (JsonNode keyword : words) {
                        if (text.contains(keyword.asText())) {
                            return intent.get("intent").asText();
                        }
                    }
                }
            }
            if(node.has("interaction")){
                if(node.get("interaction").has("timeoutFrequency")){
                    if(intent.has("interaction") && intent.get("interaction").has("timeoutFrequency")){
                        if(intent.get("interaction").get("timeoutFrequency").asInt() <= node.get("interaction").get("timeoutFrequency").asInt()){
                            return intent.get("intent").asText();
                        }
                    }
                }
            }
        }
        return "inform";
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        for(IFlipperEnvironment env : envs){
            if(env instanceof UMEnvironment){
                this.umEnvironment = (UMEnvironment) env;
            }
        }
        if(this.umEnvironment == null) throw new Exception("Required loader of type UMEnvironment not found!");
    }

    /**
     * Method for getting only the dialogue text over all sessions.
     *
     * @param context, the context of the information state to extract the text from
     * @param local,   only the local context or the full context.
     * @return the history requested as a list of sentences
     */
    private String getConversationHistory(JsonNode context, boolean local) {
        ArrayNode conversation = mapper.createArrayNode();
        if (context.has("history")) {
            JsonNode history = context.get("history");
            // If just last session
            if (local) {
                ArrayNode localHistory = (ArrayNode) history.get(0);
                conversation = convertHistoryToPlain(localHistory, conversation);
            }
            // Otherwise, all sessions
            else {
                for (int i = 0; i < history.size(); i++) {
                    ArrayNode localHistory = (ArrayNode) history.get(i);
                    conversation = convertHistoryToPlain(localHistory, conversation);
                }
            }
        }
        return conversation.toString();
    }

    /**
     * Method to convert the complex history to simple text
     *
     * @param session,      the session to convert
     * @param conversation, the simplified conversation
     * @return to convert the history in the database to plain text.
     */
    private ArrayNode convertHistoryToPlain(ArrayNode session, ArrayNode conversation) {
        Iterator<String> it = session.fieldNames();
        int turnCounter = 0;
        while (it.hasNext()) {
            String type = it.next();
            if (type.equals("tts")) {
                ObjectNode node = mapper.createObjectNode()
                        .put("agent", session.get(turnCounter).get("tts").get("text").asText());
                conversation.add(node);
            }
            if (type.equals("asr")) {
                ObjectNode node = mapper.createObjectNode()
                        .put("user", session.get(turnCounter).get("asr").get("text").asText());
                conversation.add(node);
            }
            turnCounter++;
        }
        return conversation;
    }

    private class UserMoveCreator extends Thread {

        private final FlipperEnvironmentMessageJSON fenvmsg;
        private final NVLUEnvironment nvluEnvironment;

        public UserMoveCreator(String userMoveCreator, FlipperEnvironmentMessageJSON fenvmsg, NVLUEnvironment nvluEnvironment) {
            super(userMoveCreator);
            this.fenvmsg = fenvmsg;
            this.nvluEnvironment = nvluEnvironment;

        }
        public void run() {
            ObjectNode result;
            result = this.nvluEnvironment.performNVLU(fenvmsg.params.get("content"));
            this.nvluEnvironment.enqueueMessage(result, "nvlu",this.fenvmsg.msgId);
        }
    }
}
