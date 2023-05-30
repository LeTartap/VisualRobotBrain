import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.IFlipperEnvironment;
import hmi.flipper2.postgres.Database;
import nl.bliss.environments.*;
import nl.bliss.nvlg.ReadSpeakerSSML;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.hamcrest.CoreMatchers.equalTo;

public class TestEnvironments {

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    public final ObjectMapper mapper = new ObjectMapper();

    /**
     * Helper method for creating a middleware environment on the spot
     * @param in, if true, itopic=in, otopic=out, otherwise reverse
     * @return the middleware environment
     */
    public GenericMiddlewareEnvironment mEnv(boolean in){
        GenericMiddlewareEnvironment mEnv = new GenericMiddlewareEnvironment();
        String mwParams;
        if(in){
            mwParams = " {\"params\" : {\n" +
                    "                            \"middleware\": {\n" +
                    "                                \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                    "                                \"properties\": {\n" +
                    "                                    \"iTopic\": \"BLISS/in\",\n" +
                    "                                    \"oTopic\": \"BLISS/out\",\n" +
                    "                                    \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                    "                                }\n" +
                    "                            }\n" +
                    "                        }}";
        }
        else{
            mwParams = " {\"params\" : {\n" +
                    "                            \"middleware\": {\n" +
                    "                                \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                    "                                \"properties\": {\n" +
                    "                                    \"iTopic\": \"BLISS/out\",\n" +
                    "                                    \"oTopic\": \"BLISS/in\",\n" +
                    "                                    \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                    "                                }\n" +
                    "                            }\n" +
                    "                        }}";
        }

        try {
            mEnv.init(this.mapper.readTree(mwParams).get("params"));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return mEnv;
    }

    public UMEnvironment getUMEnvironment(GenericMiddlewareEnvironment environment){
        String params = "{\"params\" : {\n" +
                "                                \"db\" : \"default\",\n" +
                "                                \"name\": \"test\",\n" +
                "                                \"reset\": false,\n" +
                "                                \"event\": \"testUM\"\n" +
                "                        }}";

        try {
            UMEnvironment um = new UMEnvironment();
            IFlipperEnvironment[] list = new IFlipperEnvironment[1];
            list[0] = environment;
            um.setRequiredEnvironments(list);
            JsonNode databaseParams = mapper.readTree(params).get("params");
            String host = "127.0.0.1";
            String database = "test";
            String role = "cb";
            String password = "coffeebot";
            Database db= new Database("jdbc:postgresql://"+host+"/"+database, role, password);
            um.init(databaseParams,db.getConnection());
            return um;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Test case for the user modelling in the database
     */
    @Test
    public void testUMEnvironment(){
        try {
            ObjectMapper mapper = new ObjectMapper();
            String dbParams = " {\"params\" : {\n" +
                    "                            \"db\" : \"default\",\n" +
                    "                            \"name\": \"testUM\",\n" +
                    "                            \"reset\": true,\n" +
                    "                            \"event\": \"test\"\n" +
                    "                        }}";
            String mwParams = " {\"params\" : {\n" +
                    "                            \"middleware\": {\n" +
                    "                                \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                    "                                \"properties\": {\n" +
                    "                                    \"iTopic\": \"BLISS/in\",\n" +
                    "                                    \"oTopic\": \"BLISS/out\",\n" +
                    "                                    \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                    "                                }\n" +
                    "                            }\n" +
                    "                        }}";
            //Database setup
            String host = "127.0.0.1";
            String database = "testUM";
            String role = "cb";
            String password = "test";
            GenericMiddlewareEnvironment mEnv = new GenericMiddlewareEnvironment();
            mEnv.init(mapper.readTree(mwParams).get("params"));
            IFlipperEnvironment[] list = new IFlipperEnvironment[1];
            list[0] = mEnv;
            UMEnvironment um = new UMEnvironment();
            um.setRequiredEnvironments(list);
            JsonNode databaseParams = mapper.readTree(dbParams).get("params");
            Database db= new Database("jdbc:postgresql://"+host+"/"+database, role, password);
            um.init(databaseParams,db.getConnection());

            //Testing 0
            collector.checkThat(um.existingPerson("407"), equalTo(false));

            //Create information to add to database
            ObjectNode person = mapper.createObjectNode()
                    .put("id", "407")
                    .put("cmd", "add");

            //Commit information to the database
            um.addPerson(person.get("id").asText());
            um.getConnection().commit();
            Thread.sleep(20);


            //Testing 1
            JsonNode personNode = um.getPerson("407");
            collector.checkThat(um.existingPerson("407"), equalTo(true));
            collector.checkThat(personNode.get("gender").asText(), equalTo("null"));

            ObjectNode updatePerson = mapper.createObjectNode()
                    .put("type","gender")
                    .put("value","sex0")
                    .put("id","407")
                    .put("cmd","update");

            um.updatePerson(updatePerson.get("id").asText(),updatePerson.get("type").asText(),updatePerson.get("value").asText());
            um.getConnection().commit();
            Thread.sleep(20);

            //Testing 2
            collector.checkThat(um.existingPerson("407"), equalTo(true));
            personNode = um.getPerson("407");
            collector.checkThat(personNode.get("gender").asText(), equalTo("sex0"));

        } catch (Exception e) {
            System.out.println("Something went wrong!: " + e);
        }
    }

    @Test
    public void testASREnvironment(){

    }

    @Test
    public void testGUIEnvironment(){

    }

    @Test
    public void testAuthenticationRFIDEnvironment(){
        boolean fail = true;
        String params = "{\n" +
                "                            \"identification\": {\n" +
                "                                \"type\": \"rfid\",\n" +
                "                                \"properties\":{\n" +
                "                                    \"port\":\"COM6\",\n" +
                "                                    \"delay\":3,\n" +
                "                                    \"timeunit\":\"SECONDS\"\n" +
                "                                }\n" +
                "                            }\n" +
                "                        }";
        try {
            ObjectMapper mapper = new ObjectMapper();
            AuthenticationEnvironment env = new AuthenticationEnvironment();
            JsonNode node = mapper.readTree(params);
            env.init(node);
            fail = false;
//            while(fail){
//                Thread.sleep(1000);
//                if(env.getIdentification().getID() != null){
//                    fail = false;
//                }
//            }
        } catch (Exception e) {

        }
        collector.checkThat(fail,equalTo(false));
    }

    @Test
    public void testNVLGEnvironment(){
        String params = "{\n" +
                "                            \"middleware\": {\n" +
                "                                \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                "                                \"properties\": {\n" +
                "                                    \"iTopic\": \"BLISS/TTSFeedback\",\n" +
                "                                    \"oTopic\": \"BLISS/TTS\",\n" +
                "                                    \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                "                                }\n" +
                "                            },\n" +
                "                            \"realizer\": {\n" +
                "                                \"type\":\"ReadSpeaker\",\n" +
                "                                \"properties\": {\n" +
                "                                    \"language\":\"en_us\",\n" +
                "                                    \"voice\":\"James\",\n" +
                "                                    \"streaming\":\"0\",\n" +
                "                                    \"audioformat\":\"pcm\"\n" +
                "                                }\n" +
                "                            },\n" +
                "                            \"intents\": \"./behaviour/agentmoves_EN.json\",\n" +
                "                            \"url\":\"http://127.0.0.1:8190\",\n" +
                "                            \"language\":\"en\"\n" +
                "                        }";
        ObjectMapper mapper = new ObjectMapper();
        TTSEnvironment tts = new TTSEnvironment();
        GenericMiddlewareEnvironment mEnv = new GenericMiddlewareEnvironment();
        IFlipperEnvironment[] list = new IFlipperEnvironment[1];
        list[0] = mEnv;
        UMEnvironment um = new UMEnvironment();
        try {
            um.setRequiredEnvironments(list);
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            tts.init(mapper.readTree(params));
        } catch (Exception e) {
            e.printStackTrace();
        }
        IFlipperEnvironment[] environments = new IFlipperEnvironment[2];
        environments[0] = tts;
        environments[1] = um;
        NVLGEnvironment nvlg = new NVLGEnvironment();
        try {
            nvlg.setRequiredEnvironments(environments);
        } catch (Exception e) {
            e.printStackTrace();
        }
        try {
            nvlg.init(mapper.readTree(params));
        } catch (Exception e) {
            e.printStackTrace();
        }
        //nvlg.testMoves();
        //nvlg.testRegex();
        testQuestions(nvlg);
    }

    private void testRegex() {
        String intent = "{\n" +
                "                   \"type\":\"intent\",\n" +
                "                   \"content\": {\n" +
                "                      \"intentContent\":{\n" +
                "                           \"content\":{\n" +
                "                                \"VP\": [],\n" +
                "                                \"NP\": [\"vakantie\",\"Spanje\"],\n" +
                "                           \"intent\":\"inform\",\n" +
                "                           \"emotion\":\"{\n" +
                "                            *                           \"sentiment\": [0.0, 0.0]\n" +
                "                           }\n" +
                "                      }\n" +
                "                   }\n" +
                "               }";
        String userContent = "";


        Pattern activity = Pattern.compile("\\{\\{(activity)=(\\S+)}}");


        String test = "{{activity=dat}} klinkt goed. Waarom vind je {{activity=dat}} leuk?";
        Matcher m = activity.matcher(test);
        while(m.find()){
            System.out.println("Start index: " + m.start());
            System.out.println("End index: " + m.end());
            String key = m.group(1);
            String value = m.group(2);
            if(key.equals("activity")){
                test = m.replaceAll(value);
            }
            else if(key.equals("emotion")){
                //Do some SSML making.
            }
            System.out.println(test);
        }
    }

    public void testNVLGMoves(){
        String userText = " {\n" +
                "                   \"type\":\"intent\",\n" +
                "                   \"content\": {\n" +
                "                      \"intentContent\":{\n" +
                "                           \"content\":{\n" +
                "                                \"VP\": [],\n" +
                "                                \"NP\": [\"vakantie\",\"Spanje\"],\n" +
                "                           \"intent\":\"inform\",\n" +
                "                           \"emotion\":\"{\n" +
                "                                \"sentiment\": [0.0, 0.0]\n" +
                "                           }\n" +
                "                      }\n" +
                "                   }\n" +
                "               }";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode content = mapper.readTree(userText);
        } catch (IOException e) {
        }
    }

    /**
     * Testing if we can get open and follow-up questions from the webservice.
     * Possible open question: What is your favorite TV show?
     * Possible follow-up question after user response "I live in the Netherlands." : Where in the Netherlands do you live?
     */
    public void testQuestions(NVLGEnvironment nvlg){
        String text = "i grew up in the netherlands and rode my bike all the time.";
        JsonNode questions = nvlg.openQuestions().get("openquestions");
        for(JsonNode question : questions){
            System.out.println("open question: " + question.toString());
        }
        JsonNode fuQuestions = nvlg.followUpQuestions(text).get("followupquestions");
        for(JsonNode question : fuQuestions){
            System.out.println("follow-up question: " + question.toString());
        }
    }

    @Test
    public void testNVLUEnvironment(){
        try {
            String params = "{\"intents\" : \"./behaviour/usermoves_EN.json\",\n" +
                    "                            \"url\": \"http://127.0.0.1:8190\",\n" +
                    "                            \"language\" : \"en\"}";

            GenericMiddlewareEnvironment middlewareEnvironment = mEnv(true);
            UMEnvironment umEnvironment = this.getUMEnvironment(middlewareEnvironment);
            NVLUEnvironment environment = new NVLUEnvironment();
            IFlipperEnvironment[] list = new IFlipperEnvironment[1];
            list[0] = umEnvironment;
            environment.setRequiredEnvironments(list);
            environment.init(mapper.readTree(params));

            //Sentiment test
            ObjectNode sentiment = mapper.createObjectNode();
            sentiment.put("text","i'm extremely happy today");
            sentiment.put("lang","en");
            JsonNode emotion = environment.determineEmotion(sentiment);
            collector.checkThat(emotion.get("sentiment").get("polarity").asDouble(),equalTo(0.8));

            //Content test
            ObjectNode content = mapper.createObjectNode();
            content.put("text","i would like to go meet friends today");
            content.put("lang","en");
            JsonNode topic = environment.determineContent(content);
            collector.checkThat(topic.get("NP").get(0).asText(),equalTo("i"));

            //Topic test
            ObjectNode topics = mapper.createObjectNode();
            topics.put("text","i would like to go meet friends today");
            topics.put("lang","en");
            JsonNode nvps = environment.determinePhrases(topics);
            collector.checkThat(nvps.get("NPS").get(0).asText(),equalTo("friend"));

            //Event test
            ObjectNode events = mapper.createObjectNode();
            events.put("text","i would like to go meet friends today");
            events.put("lang","en");
            JsonNode eventNode = environment.determineEvent(events);
            collector.checkThat(eventNode.get(0).get("A0").asText(),equalTo("i"));

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    @Test
    public void testTTSEnvironment() throws XMLStreamException {
        String params = "{\n" +
                " \"realizer\": {\n" +
                "    \"type\":\"ReadSpeaker\",\n" +
                "    \"properties\": {\n" +
                "       \"language\":\"nl_nl\",\n" +
                "       \"voice\":\"Guus\",\n" +
                "       \"streaming\":\"0\",\n" +
                "       \"audioformat\":\"pcm\"\n" +
                "    }\n" +
                " }\n" +
                "}";
        GenericMiddlewareEnvironment middlewareEnvironment = this.mEnv(true);
        TTSEnvironment tts = new TTSEnvironment();
        try {
            tts.init(mapper.readTree(params));
            IFlipperEnvironment[] environments = new IFlipperEnvironment[1];
            environments[0] = middlewareEnvironment;
            tts.setRequiredEnvironments(environments);
        } catch (Exception e) {
            e.printStackTrace();
        }
        String ssml = "" +
                "<speak version=\"1.1\" xml:lang=\"nl_nl\">\n" +
                "<voice name=\"Guus\">\n" +
                "<p>" +
                "           Hier is een lange pauze." +
                "       </p>" +
                "       <break time=\"800ms\" />\n" +
                "Ik wil het woord AMEP spellen als: " +
                "       <say-as interpret-as=\"characters\">" +
                "           AMEP" +
                "       </say-as>\n" +
                "       Ik kan ook een andere taal gebruiken zoals " +
                "       <lang xml:lang=\"de\">" +
                "           Ich kann Deutsch sprechen." +
                "       </lang> " +
                "       met een Duitse stem.\n" +
                "       <prosody volume=\"soft\" rate=\"slow\" pitch=\"low\">" +
                "           Dit zal gelezen worden op een zachter volume, minder snel en lagere toonhoogte" +
                "       </prosody>\n" +
                "</voice>\n" +
                "</speak> ";
        ObjectNode ttsNode = this.mapper.createObjectNode();
        ttsNode.put("ssml", ssml);
        ObjectNode toSend = this.mapper.createObjectNode().set("tts", ttsNode);
//        middlewareEnvironment.sendData(toSend);


        ReadSpeakerSSML parser = new ReadSpeakerSSML();
        ObjectNode backchannel = this.mapper.createObjectNode();
        backchannel.put("ssml", parser.getPositiveBackchannel(tts.getLanguage(), tts.getVoice()));
        ObjectNode toSend2 = this.mapper.createObjectNode().set("tts", backchannel);//
//        middlewareEnvironment.sendData(toSend2);


        try {
            List<String> emotions = Files.readAllLines(Paths.get(TestEnvironments.class.getResource("/data/tts/paraling_options_guus.txt").toURI()));                   ;
            int i = ThreadLocalRandom.current().nextInt(0,emotions.size()-1);
            String emotion = emotions.get(i);
            System.out.println("Emotion: " + emotion);
            ObjectNode content = mapper.createObjectNode()
                    .put("name",tts.getVoice())
                    .put("xml:lang",tts.getLanguage())
                    .put("src","file:"+emotion);
            ObjectNode speak = mapper.createObjectNode()
                    .set("speak",content);
            ObjectNode finalSSML = mapper.createObjectNode();
            finalSSML.put("ssml",parser.parseSSML(speak.toString()));
            ObjectNode toSend3 = this.mapper.createObjectNode().set("tts",finalSSML);
//            middlewareEnvironment.sendData(toSend3);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        String text = "Ik wil naar huis gaan.";
        ObjectNode content = mapper.createObjectNode()
                .put("name",tts.getVoice())
                .put("xml:lang",tts.getLanguage())
                .put("text",text);
        ObjectNode speak = mapper.createObjectNode()
                .set("speak",content);
        ObjectNode finalSSML = mapper.createObjectNode();
        finalSSML.put("ssml",parser.parseSSML(speak.toString()));
        ObjectNode toSend4 = this.mapper.createObjectNode().set("tts",finalSSML);
        middlewareEnvironment.sendData(toSend4);



    }

    public void testTTSMoves(){
        String userText = " {\n" +
                "          \"text\": \"My name is Jelte\",\n" +
                "          \"content\": {\n" +
                "            \"VP\": [],\n" +
                "            \"NP\": [\n" +
                "              \"My name\",\n" +
                "              \"My name\",\n" +
                "              \"Jelte\"\n" +
                "            ],\n" +
                "            \"PP\": []\n" +
                "          },\n" +
                "          \"intent\": \"inform\",\n" +
                "          \"emotion\": {\n" +
                "            \"sentiment\": [\n" +
                "              0,\n" +
                "              0\n" +
                "            ]\n" +
                "          },\n" +
                "          \"timestamp\": \"2020-07-12-12-41-12-CEST\"\n" +
                "        }";
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode content = mapper.readTree(userText);
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    @Test
    public void testSSML(){
        String params = "{\n" +
                "                            \"middleware\": {\n" +
                "                                \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                "                                \"properties\": {\n" +
                "                                    \"iTopic\": \"BLISS/in\",\n" +
                "                                    \"oTopic\": \"BLISS/out\",\n" +
                "                                    \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                "                                }\n" +
                "                            },\n" +
                "                            \"realizer\": {\n" +
                "                                \"type\":\"ReadSpeaker\",\n" +
                "                                \"properties\": {\n" +
                "                                    \"language\":\"nl_nl\",\n" +
                "                                    \"voice\":\"Guus\",\n" +
                "                                    \"streaming\":\"0\",\n" +
                "                                    \"audioformat\":\"pcm\"\n" +
                "                                }\n" +
                "                            }\n" +
                "                        }";
        ObjectMapper mapper = new ObjectMapper();
        TTSEnvironment tts = new TTSEnvironment();
    }

    @Test
    public void testTopics(){
        String dbParams = " {\"params\" : {\n" +
                "                            \"db\" : \"default\",\n" +
                "                            \"name\": \"users\",\n" +
                "                            \"reset\": false,\n" +
                "                            \"event\": \"test\"\n" +
                "                        }}";
        String host = "127.0.0.1";
        String database = "cb";
        String role = "cb";
        String password = "coffeebot";
        GenericMiddlewareEnvironment mEnv = this.mEnv(true);
        IFlipperEnvironment[] envs = new IFlipperEnvironment[1];
        envs[0] = mEnv;
        UMEnvironment um = new UMEnvironment();
        try {
            um.setRequiredEnvironments(envs);
            JsonNode databaseParams = mapper.readTree(dbParams).get("params");
            Database db= new Database("jdbc:postgresql://"+host+"/"+database, role, password);
            um.init(databaseParams,db.getConnection());

            String id = "testTopic";
            um.setCurrentPersonID(id);
            JsonNode person = um.getPerson(id);
            ArrayList<JsonNode> topics = um.getGlobalTopics();
            ArrayList<ArrayList<JsonNode>> global = um.getGlobalConverationHistory();
            //ArrayList<JsonNode> local = um.getLocalConversationHistory();
            JsonNode info = um.getPersonInformation();
            System.out.println("Done loading information");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
