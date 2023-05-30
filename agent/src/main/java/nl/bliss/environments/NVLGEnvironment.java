package nl.bliss.environments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import nl.bliss.nvlg.ContentPlanner;
import nl.bliss.nvlg.MemoryComponent;
import nl.bliss.nvlg.SentencePlanner;
import nl.bliss.util.APIHelper;
import nl.bliss.util.Converter;
import nl.bliss.nvlg.ReadSpeakerSSML;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import nl.bliss.util.TimeHelper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * In this class we want to interface between Flipper and an agent/TTS engine. Could be with emoji/plaintext/text
 * Input:
 *  - What to say
 *  - How to say it
 *  -
 * Output:
 *  - JSON:
 *      {
 *          "content" : "bml",
 *          "bml" : "<bml> </bml>" //OR ["<bml></bml>","<bml></bml>"]
 *      }
 *   - OR:
 *      {
 *          "content" : "text",
 *          "text": [text],
 *          "plaintext" : "text"
 *      }
 *   - OR:
 *      {
 *          "content" : "ssml",
 *          "ssml": [ssml]
 *      }
 */
public class NVLGEnvironment extends BaseFlipperEnvironment {

    //Helpers
    private ObjectMapper mapper;
    private ReadSpeakerSSML parser;
    private OkHttpClient client;
    private String url;
    private MemoryComponent mc;
    private UMEnvironment umEnvironment;

    //Intents
    private ArrayNode intents;
    private ArrayList<Integer> validIntents;
    private JsonNode move;

    //Activity extraction
    private Pattern ssmlEmotion;
    private Pattern activity;

    //Language parameters
    private TTSEnvironment ttsEnvironment;
    private String voice;
    private String language;
    private String streaming;
    private String audioformat;

    //NLG components
    private SentencePlanner sentencePlanner;
    private ContentPlanner contentPlanner;
    private QUESTION_TYPE lastQuestionType;
    private int countFollowup = 0;
    private int maxFollowup = 2;
    private ArrayList<String> backChannels;

    public enum QUESTION_TYPE {OPEN,MEMORY,FOLLOWUP,NONE}

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {
        switch (fenvmsg.cmd) {
            case "send_ic":
                logger.debug("Got send_ic request. Retrieving agent ic..");
                if(fenvmsg.params.has("content")){
                    logger.debug("Generating agent intent/content: {}",fenvmsg.params.toString());
                    Thread t = new MoveCreator("MoveCreator",fenvmsg,this);
                    t.start();
                }
                break;
            case "erasePastIntents":
                if(fenvmsg.params.has("content")){
                    logger.debug("Removing past intents");
                    this.clearIntents();
                    this.enqueueMessage(mapper.createObjectNode().put("erase",true),"reset",fenvmsg.msgId);
                }
                break;
            default:
                logger.warn("Unhandled message: "+fenvmsg.toString());
                break;
        }
        return null;
    }

    private String parseContent(JsonNode content) {
        try {
            return this.parser.parseSSML(content.toString());
        } catch (XMLStreamException e) {
            e.printStackTrace();
        }
        return "";
    }

    private JsonNode getMove(int moveID){
        Iterator<JsonNode> it = this.intents.elements();
        while(it.hasNext()){
            JsonNode agentMove = it.next();
            if(agentMove.has("id") && agentMove.get("id").asInt() == moveID){
                return agentMove;
            }
        }
        logger.warn("Could not find agentMove with ID {}",moveID);
        return mapper.createObjectNode();
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        for(IFlipperEnvironment env : envs){
            if(env instanceof TTSEnvironment){
                this.ttsEnvironment = (TTSEnvironment) env;
            }
            if(env instanceof UMEnvironment){
                this.umEnvironment = (UMEnvironment) env;
            }
        }
        if(this.ttsEnvironment == null) throw new Exception ("Required loader of type TTSEnvironment not found!");
        if(this.umEnvironment == null) throw new Exception("Required loader of type UMEnvironment not found!");
    }

    @Override
    public void init(JsonNode params) {
        this.mapper = new ObjectMapper();
        this.parser = new ReadSpeakerSSML();
        this.client = new OkHttpClient();
        this.url = params.get("url").asText();
        APIHelper.init(this.url,this.client);
        this.lastQuestionType = QUESTION_TYPE.NONE;
        if(params.has("intents")){
            try{
                InputStream intentFile = NVLGEnvironment.class.getClassLoader().getResourceAsStream(params.get("intents").asText());
                intents = (ArrayNode) mapper.readTree(intentFile);
            }
            catch(Exception e){
                logger.error("Could not read intents folder!");
                e.printStackTrace();
                System.exit(1);
            }
        }
        else{
            logger.error("Missing agent intents, no field 'intents'!");
        }
        JsonNode ttsParams = this.ttsEnvironment.getRealizer();
        if(ttsParams.has("type")){
            String type = ttsParams.get("type").asText();
            switch(type){
                case "ReadSpeaker":
                    this.language = ttsParams.get("properties").get("language").asText();
                    this.audioformat = ttsParams.get("properties").get("audioformat").asText();
                    this.voice = ttsParams.get("properties").get("voice").asText();
                    this.streaming = ttsParams.get("properties").get("streaming").asText();
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
        this.ssmlEmotion = Pattern.compile("\\{\\{(emotion)=(\\S+)}}");
        this.activity = Pattern.compile("\\{\\{(activity)=(\\S+)}}");
        if(params.has("urlMemory")){
            this.mc = new MemoryComponent(params.get("urlMemory").asText(), this.client, this.language);
        }
        this.contentPlanner = new ContentPlanner(this.language.substring(0,2));
        this.sentencePlanner = new SentencePlanner(this.language.substring(0,2));
        if(this.language.startsWith("en")){
            this.backChannels = new ArrayList<>();
            this.backChannels.add("Okay.");
            this.backChannels.add("I see.");
            this.backChannels.add("Alright.");
            this.backChannels.add("Interesting!");
        }
        if(this.language.startsWith("nl")){
            this.backChannels = new ArrayList<>();
            this.backChannels.add("Oké.");
            this.backChannels.add("Uhu.");
            this.backChannels.add("Interessant!");
        }
    }

    private String sendBackchannel(String voice, String language){
        return parser.getPositiveBackchannel(language,voice);
    }

    private JsonNode setParameters(){
        return mapper.createObjectNode()
                .put("voice",this.voice)
                .put("audioformat",this.audioformat)
                .put("streaming",this.streaming)
                .put("lang",this.language);
    }

    /**
     * This method looks for the most appropriate move the agent must do.
     * @param content, the content of the last user move
     * example text: "Ik wil op vakantie in Spanje".
     * Example: {
     *              "type":"intent",
     *              "content": {
     *                 "intentContent":{
     *                      "text": "Ik wil op vakantie in Spanje."
     *                      "content":{
     *                           "VP": [],
     *                           "NP": ["vakantie","Spanje"],
     *                      "intent":"inform",
     *                      "emotion":"{
     *                           "sentiment": [0.0, 0.0]
     *                      }
     *                 }
     *              }
     *          }
     * @return the move for the agent containing intent, content, emotion and whether he wants to keep the turn
     */
    public ObjectNode determineMove(JsonNode content){
        ObjectNode node = mapper.createObjectNode();
        if(this.validIntents == null){
            this.validIntents = new ArrayList<>();
            setValidIntents();
        }
        if(move != null){
            this.updateValidIntents(move.get("id").asInt());
        }
        ArrayNode possibleIntents = Converter.convertToArrayNode(this.validIntents);
        ArrayNode validIntents = checkConditionIntents(content, possibleIntents);
        if(validIntents.size() == 0){
            return node;
        }
        // With multiple intents, it was first implemented as taking a random possible intent
        if(validIntents.size() > 1){
            int maxRank = Integer.MAX_VALUE;
            //By default, take the first move if no better rank exists
            move = validIntents.get(0);
            for(int i = 0; i < validIntents.size(); i++){
                int currentRank = this.intentRank(validIntents.get(i));
                if(currentRank < maxRank){
                    move = validIntents.get(i);
                    maxRank = currentRank;
                }
            }
        }
        else{
            move = validIntents.get(0);
        }
        node.put("turn",determineTurn(move, content));
        node.put("emotion",determineEmotion(move));
        node.put("intent",determineIntent(move));
        node.put("text",determineContent(move, content));
        node.put("timestamp",TimeHelper.formatter.format(ZonedDateTime.now()));
        if(!move.has("id")){
            logger.warn("This move does not have an ID in the agent behaviours file!");
        }
        else{
            node.put("id",move.get("id").asInt());
        }
        logger.debug("Sending move: {}",move.get("id").asInt());
        return node;
    }

    /**
     * Remove the move from the valid intents list
     * @param id, the id of the particular intent
     */
    public void updateValidIntents(int id) {
        JsonNode localHistory = Converter.convertToJsonNode(this.umEnvironment.getLocalConversationHistory());
        if(this.hasIDInHistory(localHistory,id)){
            JsonNode agentMove = this.getMove(id);
            if(agentMove.size() > 0){
                if(this.validIntents != null && agentMove.has("unique") && agentMove.get("unique").asBoolean()){
                    logger.debug("Removing intent: {}",id);
                    this.validIntents.remove(Integer.valueOf(id));
                }
            }
        }
    }

    /**
     * Set the intents based on the history with the user
     */
    private void setValidIntents() {
        JsonNode localHistory = Converter.convertToJsonNode(this.umEnvironment.getLocalConversationHistory());
        for(JsonNode intent : intents){
            if(intent.has("id")){
                int idMove = intent.get("id").asInt();
                if(intent.has("unique") && intent.get("unique").asBoolean()){
                    if(this.hasIDInHistory(localHistory,idMove)){
                        continue;
                    }
                }
                if(!this.validIntents.contains(idMove)){
                    this.validIntents.add(idMove);
                }
                else{
                    logger.warn("Move with id {} has duplicate.",idMove);
                }
            }
            else{
                logger.warn("No ID defined for intent {}",intent.toString());
            }
        }
    }

    /**
     * Checks if an ID already exists in the history. The most recent entries are in the back of the array.
     * @param history, the ArrayNode = [{},{}}] containing the history of the conversation
     * @param id, the id to check for
     * @return true if the id is found
     */
    private boolean hasIDInHistory(JsonNode history, int id){
        if(history != null){
            for(int i = history.size()-1; i > 0; i--){
                JsonNode next = history.get(i);
                if(next.has("tts") && next.get("tts").has("moveID")){
                    if(id == next.get("tts").get("moveID").asInt()){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Method to select the most likely intent (is arbitrary now)
     * @param intent, the intent to calculate the rank for
     * @return the rank, the lower, the more likely
     */
    private int intentRank(JsonNode intent) {
        switch(intent.get("intent").asText()){
            case "greeting":
            case "valediction":
            case "contact":
                return 1;
            case "repeat":
                return 2;
            case "confirm":
            case "disconfirm":
                return 3;
            case "pausing":
            case "stalling":
                return 4;
            case "backchannel":
                return 5;
            case "inform":
                return 6;
            case "question":
            default:
                return 7;
        }
    }

    /**
     * This method adds emotion to the possible move (for example parsing the SSML emotions)
     * @param move, the move to parse
     * @return the neutral emotion by default if the move doesn't contain an emotion.
     */
    private String determineEmotion(JsonNode move) {
        if(move.has("emotion")){
            return move.get("emotion").asText();
        }
        return "neutral";
    }

    private String determineIntent(JsonNode intent) {
        if(intent.has("intent")){
            return intent.get("intent").asText();
        }
        return "inform";
    }

    /**
     * This method determines what the agent has to say
     * @param intent, the intent of the agent
     * @param userContent, the usercontent to base the answer on, containing text, but also other non-verbal things
     * @return a String representation of what the agent needs to say.
     */
    private String determineContent(JsonNode intent, JsonNode userContent) {
        String content = "";
        if(intent.has("content")){            
            if(intent.get("content").isArray()){
                int index = ThreadLocalRandom.current().nextInt(0,intent.get("content").size());
                content = intent.get("content").get(index).asText();
            }
            else if(intent.get("content").asText().equals("QUESTION")){
                if(userContent.has("content") && !userContent.get("content").isEmpty()){
                    if(!content.isEmpty() && APIHelper.getSimilarity(content,userContent.get("content").get("text").asText(),this.language) > 0.9){
                        //Remove previously asked disclosures
                        content = this.determineSelfDisclosureAndReflect(userContent);
                        ArrayList<String> previousAgentUtterances = this.previousAgentUtterances(false);
                        if(previousAgentUtterances.contains(content)){
                            previousAgentUtterances = this.previousAgentUtterances(true);
                            if(previousAgentUtterances.contains(content)){
                                content = "";
                            }
                        }
                    }
                    else if(content.isEmpty()){
                        content = this.determineQuestion(userContent);
                    }
                }
                else{
                    content = this.determineQuestion(userContent);
                }
            }
            else if(intent.get("content").asText().equals("SELFDISCLOSURE")){
                content = this.determineSelfDisclosureAndReflect(userContent);
            }
            else if(intent.get("content").asText().equals("REPEAT")){

                if(this.language.startsWith("nl")){
                    content = "Ik weet niet precies wat ik u net vroeg.";
                }
                else if(this.language.startsWith("en")){
                    content = "I can't seem to recall what I said";
                }
                else{
                    content = "I can't seem to recall what I said";
                }
                if(this.umEnvironment.getLocalConversationHistory().size() > 0){
                    JsonNode localHistory = Converter.convertToJsonNode(this.umEnvironment.getLocalConversationHistory());
                    //Iterator<JsonNode> it = localHistory.iterator();
                    for(int i = localHistory.size()-1; i > 0; i--){
                        JsonNode entry = localHistory.get(i);
                        if(entry.has("nvlg") &&
                                entry.get("nvlg").has("text") &&
                                entry.get("nvlg").has("intent") &&
                                !entry.get("nvlg").get("intent").asText().equals("contact")
                        ){
                            content = entry.get("nvlg").get("text").asText();
                            break;
                        }
                    }
                }
            }
            else if(intent.get("content").asText().equals("EXPLAIN")){
                //TODO: Explanations for certain questions?
                if(this.language.startsWith("nl")){
                    content = "Ik wil graag meer over u leren.";
                }
                else if(this.language.startsWith("en")){
                    content = "I would like to get to know you.";
                }
                else{
                    content = "I can't seem to recall what I said";
                }
            }
            else{
                content = intent.get("content").asText();
            }
            Matcher mArgument = this.activity.matcher(content);
            while(mArgument.find()){
                String key = mArgument.group(1);
                String value = mArgument.group(2);
                if(key.equals("activity")){
                    JsonNode verbs = userContent.get("content").get("content").get("VP");
                    JsonNode nouns = userContent.get("content").get("content").get("NP");
                    JsonNode prepositions = userContent.get("content").get("content").get("PP");
                    switch (verbs.size()) {
                        case 0:
                            content = mArgument.replaceAll(value);
                            break;
                        case 1:
                            content = mArgument.replaceAll(verbs.get(0).asText());
                            break;
                        default:
                            int index = ThreadLocalRandom.current().nextInt(0,verbs.size());
                            content = mArgument.replaceAll(verbs.get(index).asText());
                            break;
                    }
                }
            }


//            Matcher mEmotion = this.ssmlEmotion.matcher(content);
//            while(mEmotion.find()){
//                String key = mEmotion.group(1);
//                String value = mEmotion.group(2);
//
//                if(key.equals("emotion")){
//                    speak.put("src","file:"+value);
//                    content = mEmotion.replaceAll("");
//                }
//
//                speak.put("text",content);
//                ssmlNode.set("speak",speak);
//                String ssml = null;
//                try {
//                    ssml = parser.parseSSML(ssmlNode.toString());
//                } catch (XMLStreamException e) {
//                    e.printStackTrace();
//                }
//                return ssml;
            if(!content.isEmpty()){
                return content;
            }
        }
        if(this.language.startsWith("nl")){
            return "Sorry, ik ben het gesprek even kwijt. Waar hadden we het over?";
        }
        else if(this.language.startsWith("en")){
            return "I lost my train of thought, where were we again?";
        }
        else{
            return "I can't seem to recall what I said";
        }        
    }

    /**
     * Retrieve a list of possible opening questions to ask
     * @return the list of questions
     * REST-API: [q_1, q_2, q_n]
     */
    public JsonNode openQuestions(){
        ObjectNode questions = mapper.createObjectNode();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/openquestions");
        urlBuilder.addQueryParameter("lang",this.language.substring(0,2));
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()){
                JsonNode node = mapper.readTree(response.body().string());
                ArrayNode array = mapper.readValue( node.get("openquestions").asText(),ArrayNode.class);
                questions.set("openquestions",array);
            }
        } catch (IOException ex) {
            logger.error("Cannot connect to the Question generation server, will find no open questions. Will use default question.");
            ArrayNode array = mapper.createArrayNode();
            array.add("What are you planning to do next week?");
            questions.set("openquestions",array);
        }
        return questions;
    }

    /**
     * Retrieve a list of follow-up questions based on an answer by the user
     * @param answer that the user gave
     * @return a list of follow-up questions based on SRL and the answer
     * REST-API: [[[q_1, TYPE],[q_2, TYPE],[q_n, TYPE]]]
     */
    public JsonNode followUpQuestions(String answer){
        ObjectNode questions = mapper.createObjectNode();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/followupquestions");
        urlBuilder.addQueryParameter("text",answer);
        urlBuilder.addQueryParameter("lang",this.language.substring(0,2));
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()){
                JsonNode node = mapper.readTree(response.body().string());
                ArrayNode array = mapper.readValue( node.get("followupquestions").asText(),ArrayNode.class);
                questions.set("followupquestions",array);
                return questions;
            }
        } catch (IOException ex) {
            // When creating a default question, we add the question + type.
            logger.error("Cannot connect to the Question generation server, will find no follow-up questions. Will return default question.");
            ArrayNode def = mapper.createArrayNode();
            def.add("Tell me more.");
            def.add("DEFX");
            ArrayNode array2 = mapper.createArrayNode();
            array2.add(def);
            ArrayNode array = mapper.createArrayNode();
            array.add(array2);
            questions.set("followupquestions",array);
        }
        return questions;
    }

    /**
     * Retrieve a list of topical questions based on the history of the user
     * @param topic of the user
     * @return a list of follow-up questions based on SRL and the answer
     */
    private JsonNode topicalQuestions(String topic){
        ObjectNode questions = mapper.createObjectNode();
        HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
        urlBuilder.addPathSegment("/topicalquestions");
        urlBuilder.addQueryParameter("topic",topic);
        urlBuilder.addQueryParameter("lang",this.language.substring(0,2));
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()){
                JsonNode node = mapper.readTree(response.body().string());
                ArrayNode array = mapper.readValue( node.get("topicalquestions").asText(),ArrayNode.class);
                questions.set("topicalquestions",array);
                return questions;
            }
        } catch (IOException ex) {
            logger.error("Cannot connect to the Question generation server, will find no topical questions. Will return default question");
            ArrayNode array = mapper.createArrayNode();
            array.add("What was something nice that happened to you last week?");
            questions.set("topicalquestions",array);
        }
        return questions;
    }

    /**
     * Method for retrieving a self-disclosure
     * @param topic , the topic to relate the self-disclosure to
     * @return, the node with self-disclosure
     */
    private JsonNode selfDisclosure(String topic){
        ObjectNode selfDisclosure = mapper.createObjectNode();
        //Do some API call and retrieve the String and possible meta information.
        return selfDisclosure;
    }


    private String determineTurn(JsonNode move, JsonNode content) {
        if(move.has("turn") && !move.get("intent").asText().equals("repeat")){
            return move.get("turn").asText();
        }
        else{
            String turn = lastNonRepeatTurn();
            if(!turn.equals("")){
                return turn;
            }
            else{
                return "assign";
            }
        }
    }

    private String lastNonRepeatTurn() {
        ArrayList<JsonNode> localHistory = this.umEnvironment.getLocalConversationHistory();
        for(int i=localHistory.size()-1;i>0;i--){
            JsonNode entry = localHistory.get(i);
            if(entry.has("nvlg") && entry.get("nvlg").has("intent")){
                if(!entry.get("nvlg").get("intent").asText().equals("repeat")){
                    return entry.get("nvlg").get("turn").asText();
                }
            }
        }
        return "assign";
    }

    /**
     * This method is for clearing all past intents for a local dialogue
     *
     */
    public void clearIntents(){
        this.validIntents = null;
    }

    /**
     * Check if an agent move is possible given the dialogue history
     * Condition types: User Move, Agent Move, Not Agent Move
     * @param content, the jsonnode containing the last user info
     * @param possibleIntents, the filtered intents thus far
     * @return ArrayNode, the remaining moves for the agent
     */
    private ArrayNode checkConditionIntents(JsonNode content, ArrayNode possibleIntents) {

        ArrayNode validIntents = mapper.createArrayNode();
        for(JsonNode id : possibleIntents){
            JsonNode intent = this.getMove(id.asInt());
            boolean add = false;
            if(intent.has("conditions")){
                Iterator<JsonNode> it = intent.get("conditions").elements();
                while(it.hasNext()){
                    JsonNode condition = it.next();
                    if(conditionMet(condition, content)){
                        add = true;
                    }
                }
            }
            else{
                add = true;
            }
            if(add){
                validIntents.add(intent);
            }
        }
        return validIntents;
    }

    /**
     * Condition checker. Current supported conditions:
     * - first: if first contact
     * - move: ID, empty, timeout, intent, userMove
     * @param condition, the condition of a possible move
     * @param context, userMove context of the last user utterance
     * @return true if the condition is met under the context
     */
    private boolean conditionMet(JsonNode condition, JsonNode context) {
        //If the conversation is the first one with the participant, this is true
        JsonNode userMove = context.get("content");
        ArrayNode history = Converter.convertToArrayNode(this.umEnvironment.getLocalConversationHistory());
        if(condition.has("first")){
            if(!userMove.has("first")){
                return false;
            }
            if(condition.get("first").asBoolean()){
                return userMove.get("first").asBoolean();
            }
            else{
                return !userMove.get("first").asBoolean();
            }
        }
        if(condition.has("wrapUp")){
            return this.umEnvironment.timeUp(condition.get("wrapUp").asInt());

        }
        if(condition.has("move")) {
            if(condition.get("move").has("agent")){
                JsonNode ids = condition.get("move").get("agent").get("id");
                if(ids.size() > 1){
                    if(!hasIDSInHistory(history,ids)){
                        return false;
                    }
                }
                else{
                    if(!hasIDInHistory(history,ids.asInt())){
                        return false;
                    }
                }
            }
            if(condition.get("move").has("user")){
                if(condition.get("move").get("user").has("intent")){
                    if(!userMove.has("intent")){
                        return false;
                    }
                    String userIntent = userMove.get("intent").asText();
                    JsonNode conditionUserIntent = condition.get("move").get("user").get("intent");
                    if(conditionUserIntent.isTextual()){
                        String conditionUserIntentName = condition.get("move").get("user").get("intent").asText();
                        if(!userIntent.toLowerCase().equals(conditionUserIntentName)){
                            return false;
                        }
                    }
                    if(conditionUserIntent.isArray()){
                        boolean matchingUserIntent = false;
                        for(JsonNode intent : conditionUserIntent){
                            String conditionUserIntentName = intent.asText();
                            if(userIntent.toLowerCase().equals(conditionUserIntentName)){
                                matchingUserIntent = true;
                                break;
                            }
                        }
                        if(!matchingUserIntent){
                            return false;
                        }
                    }

                }
                if(condition.get("move").get("user").has("timeoutFrequency")){
                    int timeOuts = condition.get("move").get("user").get("timeoutFrequency").asInt();
                    if(context.has("interaction") &&
                            context.get("interaction").has("timeoutFrequency") &&
                            timeOuts != context.get("interaction").get("timeoutFrequency").asInt()){
                        return false;
                    }
                }
                if(condition.get("move").get("user").has("emotion")){
                    double polarity = userMove.get("emotion").get("sentiment").get(0).asDouble();
                    double lowerBound = condition.get("move").get("user").get("emotion").get(0).asDouble();
                    double upperBound = condition.get("move").get("user").get("emotion").get(1).asDouble();
                    if(!(polarity >= lowerBound && polarity < upperBound)){
                        return false;
                    }

                }
                if(condition.get("move").get("user").has("keywords")){
                    if(!userMove.has("text")){
                        return false;
                    }
                    String userInput = userMove.get("text").asText();
                    JsonNode keywords = condition.get("move").get("user").get("keywords");
                    if(keywords.has("exactly")){
                        ArrayNode words = (ArrayNode) keywords.get("exactly");
                        for(JsonNode keyword : words){
                            if(userInput.equals(keyword.asText())){
                                return true;
                            }
                        }
                    }
                    if(keywords.has("starts")){
                        ArrayNode words = (ArrayNode) keywords.get("starts");
                        for(JsonNode keyword : words){
                            if(userInput.startsWith(keyword.asText())){
                                return true;
                            }
                        }
                    }
                    if(keywords.has("exists")){
                        ArrayNode words = (ArrayNode) keywords.get("exists");
                        for(JsonNode keyword : words){
                            if(userInput.contains(keyword.asText())){
                                return true;
                            }
                        }
                    }
                    return false;
                }
            }
        }
        if(condition.has("notmove")) {
            if(condition.get("notmove").has("agent")){
                JsonNode ids = condition.get("notmove").get("agent").get("id");
                if(ids.size() > 1){
                    Iterator<JsonNode> values = ids.elements();
                    while(values.hasNext()){
                        if(hasIDInHistory(history, values.next().asInt())){
                            return false;
                        }
                    }
                }
                else{
                    return !hasIDInHistory(history, ids.asInt());
                }
            }
        }
        return true;
    }

    /**
     * Checks if all IDs exist in the history
     * @param ids, the node containing the IDs of previous moves
     * @param history, the history of the conversation
     * @return true only if all IDs exist
     */
     private boolean hasIDSInHistory(JsonNode history, JsonNode ids){
         for(JsonNode id : ids){
             if(!this.hasIDInHistory(history,id.asInt())){
                 return false;
             }
         }
        return true;
     }

     private boolean hasAgentIntent(int id){
        return hasPreviousAgentIntent(-1, id);
    }

    private boolean hasUserIntent(String intent){
        return hasPreviousUserIntent(-1, intent);
    }

    private boolean hasPreviousAgentIntent(int distance, int id){
       return this.hasPreviousAgentIntent(this.umEnvironment.getLocalConversationHistory(),distance,id);
    }

    private boolean hasPreviousUserIntent(int distance, String intent){
        return this.hasPreviousUserIntent(this.umEnvironment.getLocalConversationHistory(),distance,intent);
    }

    private boolean hasPreviousGlobalAgentIntent(int id){
        ArrayList<ArrayList<JsonNode>> globalHistory = this.umEnvironment.getGlobalConverationHistory();
        ListIterator<ArrayList<JsonNode>> listIterator = globalHistory.listIterator(globalHistory.size()-1);
        while(listIterator.hasPrevious()){
            ArrayList<JsonNode> localHistory = listIterator.previous();
            if(this.hasPreviousAgentIntent(localHistory,-1,id)){
                return true;
            }
        }
        return false;
    }

    private boolean hasPreviousGlobalUserIntent(String intent){
        ArrayList<ArrayList<JsonNode>> globalHistory = this.umEnvironment.getGlobalConverationHistory();
        ListIterator<ArrayList<JsonNode>> listIterator = globalHistory.listIterator(globalHistory.size()-1);
        while(listIterator.hasPrevious()){
            ArrayList<JsonNode> localHistory = listIterator.previous();
            if(this.hasPreviousUserIntent(localHistory,-1,intent)){
                return true;
            }
        }
        return false;
    }

    private boolean hasPreviousAgentIntent(List<JsonNode> conversation, int distance, int id){
        if(conversation.isEmpty()){
            return false;
        }
        if(distance == -1){
            ListIterator<JsonNode> iterator = conversation.listIterator(conversation.size()-1);
            while(iterator.hasPrevious()){
                JsonNode entry = iterator.previous();
                if(entry.get("tts").get("moveID").asInt() == id){
                    return true;
                }
            }
        }
        else if (distance > 0){
            for(int i = conversation.size()-1; i > distance;i--){
                JsonNode entry = conversation.get(i);
                if(entry.has("tts") && entry.get("tts").has("moveID")){
                    if(entry.get("tts").get("moveID").asInt() == id){
                        return true;
                    }
                }
            }
        }
        else{
            logger.error("Distance {} has to be greater than 0",distance);
        }
        return false;
    }

    private boolean hasPreviousUserIntent(List<JsonNode> conversation, int distance, String intent){
        if(conversation.isEmpty()){
            return false;
        }
        if(distance == -1){
            ListIterator<JsonNode> iterator = conversation.listIterator(conversation.size()-1);
            while(iterator.hasPrevious()){
                JsonNode entry = iterator.previous();
                if(entry.has("nvlu") && entry.get("nvlu").has("intent")){
                    if(entry.get("nvlu").get("intent").asText().equals(intent)){
                        return true;
                    }
                }
            }
        }
        else if (distance > 0){
            for(int i = conversation.size()-1; i > distance;i--){
                JsonNode entry = conversation.get(i);
                if(entry.has("nvlu") && entry.get("nvlu").has("intent")){
                    if(entry.get("nvlu").get("nvlu").asText().equals(intent)){
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * This method retrieves either all local or global agent utterances for a particular user
     * @param local, to only search the local history for the previous utterances
     * @return the list of all previous agent utterances in an ArrayList
     */
    private ArrayList<String> previousAgentUtterances(boolean local){
        ArrayList<String> questions = new ArrayList<>();
        if(local){
            ArrayNode history = Converter.convertToArrayNode(this.umEnvironment.getLocalConversationHistory());
            this.addEntries(questions, history);
        }
        else{
            ArrayNode history = Converter.convertToArrayNode(this.umEnvironment.getGlobalConverationHistory());
            for(JsonNode session : history)
            {
                this.addEntries(questions, session);
            }
        }
        return questions;
    }

    /**
     * Checks the history for all TTS text messages (skips feedback)
     * @param ttsList, the list of agent utterances
     * @param history, the history containing possible TTS
     * @return ArrayList<String>, a list of all agent utterances
     */
    private ArrayList<String> addEntries(ArrayList<String> ttsList, JsonNode history){
        for(int i = history.size()-1; i > 0; i--){
            JsonNode entry = history.get(i);
            if(entry.has("tts") && entry.get("tts").has("text")){
                ttsList.add(entry.get("tts").get("text").asText());
            }
        }
        return ttsList;
    }

    /**
     * Check if a text exists in the history
     * @param sentence, the sentence to check
     * @return true if the sentence exists
     */
    private boolean checkIfTextExists(String sentence, String field){
        ArrayList<JsonNode> history = this.umEnvironment.getLocalConversationHistory();
        ListIterator<JsonNode> it = history.listIterator(history.size()-1);
        while(it.hasPrevious()){
            JsonNode turn = it.previous();
            if(turn.has(field)){
                if(turn.get(field).get("text").asText().equals(sentence)){
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Method to determine possible follow up questions
     * @param q, candidate follow-up questions
     * @return a list of possible follow-up questions or empty list if none could be found
     */
    private JsonNode determineFollowUpQuestion(JsonNode q, JsonNode context){
        boolean onlyDefault = true;
        //If only default follow-up, ask open question
        if(q == null){
            return mapper.createArrayNode();
        }
        if(q.size() > 0  && !q.get(0).get(1).asText().startsWith("DEF")){
            onlyDefault = false;
        }
        if(onlyDefault){
            return mapper.createArrayNode();
        }
        //Remove previously asked questions
        List<String> possibleQuestions = Converter.takeFirstElements(q.deepCopy());
        ArrayList<String> previousAgentUtterances = this.previousAgentUtterances(false);
        possibleQuestions.removeAll(previousAgentUtterances);
        if(possibleQuestions.size() == 0){
            //Try again but only don't repeat questions only from current conversation
            possibleQuestions = Converter.convertToList(q.deepCopy());
            previousAgentUtterances = this.previousAgentUtterances(true);
            possibleQuestions.removeAll(previousAgentUtterances);
            if(possibleQuestions.size() == 0) {
                return mapper.createArrayNode();
            }
        }
        return Converter.convertToArrayNode(possibleQuestions);
    }


    private JsonNode determineOpenQuestion(JsonNode q, JsonNode context) {
        List<String> possibleOpenQuestions = Converter.convertToList(q.deepCopy());
        ArrayList<String>  previousAgentUtterances = this.previousAgentUtterances(false);
        possibleOpenQuestions.removeAll(previousAgentUtterances);
        //If all open questions have been asked, ask a question from earlier conversation
        if(possibleOpenQuestions.size() == 0) {
            possibleOpenQuestions = Converter.convertToList(q.deepCopy());
            previousAgentUtterances = this.previousAgentUtterances(true);
            possibleOpenQuestions.removeAll(previousAgentUtterances);
            //If still no question can be found! Ask a default question
            if(possibleOpenQuestions.size() == 0){
                q = mapper.createArrayNode()
                        .add("Tell me more about yourself")
                        .add("What have you been up to lately?")
                        .add("What did you do last week?");
            }
        }
        return q;
    }

    /**
     * Method that determines a list of possible memory questions
     * @param userText , node that contains the user text
     * @return a list of possible memory questions
     */
    private JsonNode memoryQuestions(String userText){
        ObjectNode questions = mapper.createObjectNode();
        ArrayList<JsonNode> candidateTopics = this.umEnvironment.getTopics();
        ArrayList<JsonNode> sortedTopics = this.contentPlanner.selectTopics(candidateTopics,userText);

        ArrayList<JsonNode> candidateEvents = this.umEnvironment.getEvents();
        ArrayList<Integer> sortedEventIndices = this.contentPlanner.selectEvents(candidateEvents,userText);

        //if empty, just return
        if(sortedTopics.isEmpty() && sortedEventIndices.isEmpty()){
            return questions;
        }
        ArrayNode topicQuestions = mapper.createArrayNode();
        for(JsonNode node : sortedTopics){
            String question = this.sentencePlanner.generateTopicQuestion(node);
            String topicName = node.fieldNames().next();
            JsonNode topic = node.get(topicName);
            topicQuestions.add(mapper.createObjectNode()
                    .put("name",topicName)
                    .put("type",topic.get("type").asText())
                    .put("question",question));
        }
        questions.set("topics",topicQuestions);
        ArrayNode eventQuestions = mapper.createArrayNode();
        for(int eventIndex : sortedEventIndices){
            String question = this.sentencePlanner.generateEventQuestion(candidateEvents.get(eventIndex));
            eventQuestions.add(mapper.createObjectNode().put("index",eventIndex)
                    .put("question",question));
        }
        questions.set("events",eventQuestions);
        return questions;
    }

    /**
     * Determines the self disclosure statement
     * @param userNode, the NVLU node with user information
     * @return the self-disclosure of the agent
     */
    private String determineSelfDisclosureAndReflect(JsonNode userNode){
        if(!userNode.get("content").get("topics").has("phrases")){
            return "";
        }
        else{
            JsonNode selfDisclosure = this.mc.getSelfDisclosureAndReflect(this.umEnvironment.getCurrentPersonID(),
                    userNode.get("content").get("text").asText(),
                    userNode.get("content").get("topics").get("phrases").get("NPS").get(ThreadLocalRandom.current().nextInt(userNode.get("content").get("topics").get("phrases").get("NPS").size())).asText());
            if(selfDisclosure.isEmpty()){
                return "";
            }
            else{
                return selfDisclosure.get("result").get("answer").asText();
            }
        }
    }

    /**
     * Determines the self disclosure statement
     * @param userNode, the NVLU node with user information
     * @return the self-disclosure of the agent
     */
    private String determineSelfDisclosure(JsonNode userNode){
        if(!userNode.get("content").get("topics").has("phrases")){
            return "";
        }
        else{
            JsonNode selfDisclosure = this.mc.getSelfDisclosure(this.umEnvironment.getCurrentPersonID(),
                    userNode.get("content").get("text").asText(),
                    userNode.get("content").get("topics").get("phrases").get("NPS").get(ThreadLocalRandom.current().nextInt(userNode.get("content").get("topics").get("phrases").get("NPS").size())).asText());
            if(selfDisclosure.isEmpty()){
                return "";
            }
            else{
                return selfDisclosure.get("result").get("answer").asText();
            }
        }
    }

    /**
     * Method for determining the type of question to ask
     * - Open, follow-up or memory (not implemented yet)
     * Ranking: First try memory, then follow-up, then open question
     * @param context, the user interpretation to generate a question after
     * @return the question to ask the user
     */
    private String determineQuestion(JsonNode context) {

        //Index for selecting a question
        int selection;
        //Question list
        JsonNode q = mapper.createArrayNode();

        if(context.has("content") && context.get("content").size() > 0){
            // getFollowUpQuestion if there is user text
            // Counters for asking about 3 to 5 follow up questions in a row.
            if(this.countFollowup < this.maxFollowup && q.isEmpty() && context.get("content").has("text")){
                if(this.lastQuestionType.equals(QUESTION_TYPE.FOLLOWUP)){
                    this.countFollowup++;
                }
                q = this.followUpQuestions(context.get("content").get("text").asText()).get("followupquestions").get(0);
                q = determineFollowUpQuestion(q, context);
                this.lastQuestionType = QUESTION_TYPE.FOLLOWUP;
            }
            // retrieve a memory question
            if(!this.lastQuestionType.equals(QUESTION_TYPE.NONE) && q.isEmpty() || q.isEmpty() && (!this.lastQuestionType.equals(QUESTION_TYPE.MEMORY) || context.get("content").has("type") && context.get("content").get("type").asInt() == 1)){
                if(context.get("content").has("text")){
                    q = this.memoryQuestions(context.get("content").get("text").asText());
                }
                else{
                    q = this.memoryQuestions("");
                }
                if(q.size() != 0){
                    q = this.determineMemoryQuestion(q, context);
                    this.lastQuestionType = QUESTION_TYPE.MEMORY;
                    this.countFollowup = 0;
                    this.maxFollowup = ThreadLocalRandom.current().nextInt(1,3);
                }
            }
        }
        //if empty or no user text present
        if(q.size() == 0 || !context.get("content").has("text")){
            // If no text in user text for follow-up and no memory question present
            q = this.openQuestions().get("openquestions");
            q = determineOpenQuestion(q, context);
            this.lastQuestionType = QUESTION_TYPE.OPEN;
            this.countFollowup = 0;
            this.maxFollowup = ThreadLocalRandom.current().nextInt(1,3);
        }
        if(q.size() > 1){
            selection = ThreadLocalRandom.current().nextInt(0,q.size());
        }
        else{
            selection = 0;
        }
        // If there is a follow up question, add a probable backchannel.
        if(this.lastQuestionType.equals(QUESTION_TYPE.FOLLOWUP) || this.lastQuestionType.equals(QUESTION_TYPE.MEMORY)){
            boolean addBackchannel;
            if(ThreadLocalRandom.current().nextInt(0,5) > -1){
                String backchannel = this.backChannels.get(ThreadLocalRandom.current().nextInt(this.backChannels.size()));
                return backchannel + " " + q.get(selection).asText();
            }
        }
        return q.get(selection).asText();
    }

    /**
     * Method for determining from a list of questions (q) and the context
     * @param q, the node containing two arraynodes of possible memory questions
     * @param context, the user context
     * @return the remaining list of memoryQuestions
     */
    private JsonNode determineMemoryQuestion(JsonNode q, JsonNode context) {
        //If the list is empty, return an empty list
        ArrayNode questions = mapper.createArrayNode();
        if(q.has("topics") && q.get("topics").isEmpty() && q.has("events") && q.get("events").isEmpty() ){
            return questions;
        }
        else{
            int memoryType  = ThreadLocalRandom.current().nextInt(2);
            if(q.has("topics") && q.get("topics").isEmpty() || memoryType==0 && !q.get("events").isEmpty()){
                JsonNode events = q.get("events");
                //Go through the events from best pick to worst until you find a sentence you haven't asked before
                for(JsonNode event : events){
                    int index = event.get("index").asInt();
                    //Remove previously asked questions
                    ArrayList<String> previousAgentUtterances = this.previousAgentUtterances(false);
                    if(!previousAgentUtterances.contains(event.get("question").asText())) {
                        questions.add(event.get("question").asText());
                        this.umEnvironment.updateEventsGlobal(index);
                        return questions;
                    }
                    previousAgentUtterances = this.previousAgentUtterances(true);
                    if(!previousAgentUtterances.contains(event.get("question").asText())){
                        questions.add(event.get("question").asText());
                        this.umEnvironment.updateEventsGlobal(index);
                        return questions;
                    }
                }
            }
            else if(q.has("events") && q.get("events").isEmpty() || memoryType==1 && !q.get("topics").isEmpty()){
                JsonNode topics = q.get("topics");
                for(JsonNode topic : topics){
                    String name = topic.get("name").asText();
                    String phraseType = topic.get("type").asText();
                    ArrayList<String> previousAgentUtterances = this.previousAgentUtterances(false);
                    if(!previousAgentUtterances.contains(topic.get("question").asText())) {
                        questions.add(topic.get("question").asText());
                        this.umEnvironment.updateTopicsGlobal(name, phraseType);
                        return questions;
                    }
                    previousAgentUtterances = this.previousAgentUtterances(true);
                    if(!previousAgentUtterances.contains(topic.get("question").asText())){
                        questions.add(topic.get("question").asText());
                        this.umEnvironment.updateTopicsGlobal(name, phraseType);
                        return questions;
                    }
                }
            }
        }
        return questions;
    }

    class MoveCreator extends Thread {

        private FlipperEnvironmentMessageJSON fenvmsg;
        private NVLGEnvironment nvlgEnvironment;

        public MoveCreator(String name, FlipperEnvironmentMessageJSON fenvmsg, NVLGEnvironment nvlg) {
            super(name);
            this.fenvmsg = fenvmsg;
            this.nvlgEnvironment = nvlg;
        }

        public void run() {
            ObjectNode result = this.nvlgEnvironment.determineMove(fenvmsg.params.get("content"));
            this.nvlgEnvironment.enqueueMessage(result,"nvlg",fenvmsg.msgId);


        }
    }
}
