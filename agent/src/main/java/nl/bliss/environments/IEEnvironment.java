package nl.bliss.environments;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import hmi.qam.QA;
import nl.bliss.question.QAsker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In this environment we would like to connect Flipper to the information extraction component. Options for that would be
 * - Using a knowledge base to answer a user's question
 * - Telling a relevant story to cheer up the user
 * - Derive from the dialogue history the most important information
 * ...
 * Current implementation is a QA matcher
 */
public class IEEnvironment extends BaseFlipperEnvironment {

    private static Logger logger = LoggerFactory.getLogger(IEEnvironment.class.getName());
    private QA qaMatcher;
    private QAsker qAsker;
    private ObjectMapper mapper;

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
        switch(fenvmsg.cmd){
            case "qa":
                if(fenvmsg.params.has("content")){
                    String answer = this.getBestAnswer(fenvmsg.params.get("content").asText());
                    logger.debug("Answer: {}",answer);
                    JsonNode res = mapper.createObjectNode().put("bestAnswer", answer);
                    enqueueMessage(res, "bestAnswer",fenvmsg.msgId);
                }
                break;
            case "question":
                if(fenvmsg.params.has("content")){
                    String question = qAsker.getValidQuestion();
                    logger.debug("Question: {}", question);
                    JsonNode res = mapper.createObjectNode().put("bestQuestion",question);
                    enqueueMessage(res,"question",fenvmsg.msgId);
                }
                break;
            case "answer":
                if(fenvmsg.params.has("content")){
                    String answer = fenvmsg.params.get("content").asText();
                    logger.debug("User answer: {}", answer);
                    this.qAsker.addAnswer(answer);
                    JsonNode res = mapper.createObjectNode().put("answer",answer);
                    enqueueMessage(res,"answer",fenvmsg.msgId);
                }
                break;
            default:
                logger.warn("Unhandled message: {}",fenvmsg.cmd);
        }
        return null;
    }

    public void responseCallback(FlipperEnvironmentMessageJSON fenvmsg, JsonNode params) {
        enqueueMessage(buildResponse(fenvmsg, params));
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        for (IFlipperEnvironment env : envs) {
            logger.warn("IEEnvironment doesn't need environment: "+env.getId());
        }
    }

    /**
     * We initialize the Information Extraction component
     * - In this prototype we have two components:
     * - Question-Answer matcher, to give answers to a limited set of user questions
     * - Question asker, to elicitate user responses and give more information
     * @param params, settings for the QA-database and the Question Asker
     * @throws Exception
     */
    @Override
    public void init(JsonNode params) throws Exception {
        if(params.has("qaDatabase")){
            if(params.has("defaultAnswers")){
                this.qaMatcher = new QA(params.get("qaDatabase").asText(),params.get("defaultAnswers").asText());
            }
            else{
                this.qaMatcher = new QA(params.get("qaDatabase").asText());
            }
        }
        if(params.has("questions")){
            this.qAsker = new QAsker(params.get("questions").asText());
        }
        mapper = new ObjectMapper();
    }

    public String getBestAnswer(String query){
        return getBestAnswer(query, null,null);
    }

    public String getBestAnswer(String query, String type, String value){
        return this.qaMatcher.findAndReturn(query,type,value);
    }

    public String getQuestion(){
        return this.qAsker.getValidQuestion();
    }

    public void setAnswer(String answer){
        this.qAsker.addAnswer(answer);
    }

//    class QuestionMatcher extends Thread {
//
//        private FlipperEnvironmentMessageJSON fenvmsg;
//        private String content;
//        private IEEnvironment ie;
//        private ObjectMapper om;
//
//        public QuestionMatcher(String name, FlipperEnvironmentMessageJSON fenvmsg, IEEnvironment ie) {
//            super(name);
//            this.om = new ObjectMapper();
//            this.fenvmsg = fenvmsg;
//            if (fenvmsg.params.has("content")) {
//                if (fenvmsg.params.get("content").isTextual()) {
//                    this.content = fenvmsg.params.get("content").asText();
//                }
//            }
//            this.ie = ie;
//        }
//
//        public void run() {
//            // PROCESSING HERE
//            JsonNode res = om.convertValue(new Match(this.content,this.ie), JsonNode.class);
//            ie.responseCallback(fenvmsg, res);
//        }
//    }
//
//    class Match {
//        public String answer;
//
//        public Match(String query, IEEnvironment ie){
//            this.answer = ie.getBestAnswer(query);
//            logger.debug(this.answer);
//        }
//    }
}
