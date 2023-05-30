package nl.bliss.nvlg;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import nl.bliss.util.Converter;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * This class connects to Mikael Lundell's memoryDialogueBot system
 * Original: https://github.com/lcebear/memoryDialogueBot
 */
public class MemoryComponent {

    private final OkHttpClient client;
    private String url;
    private String language;

    private final static Logger logger = LoggerFactory.getLogger(MemoryComponent.class.getName());

    public MemoryComponent(String url, OkHttpClient client, String language){
        this.url = url;
        this.client = client;
        this.language = language;
    }

    /**
     * Method for getting a personal preference of the bot
     * @param userID, the specific userID for the bot
     * @param data, the question, statement or past conversation lines.
     * @return a node with a possible answer.
     */
    public JsonNode getAnswer(String userID, String data){
        JsonNode jsonBody = Converter.mapper.createObjectNode()
                .put("userID",userID)
                .put("data",data);
        try {
            RequestBody body = RequestBody.create(
                    Converter.mapper.writeValueAsString(jsonBody),MediaType.get("application/json")
            );
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            urlBuilder.addPathSegment("/get_answer");
            return getRequest(body,urlBuilder);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Converter.mapper.createObjectNode();
    }

    /**
     * Method for getting a question based on the user response. It will most of the times spit out nonsense
     * @param userID, the specific userID for the bot
     * @param data, the question, statement or past conversation lines.
     * @return a node with best question and other possible questions.
     */
    public JsonNode getQuestion(String userID, String data){
        JsonNode jsonBody = Converter.mapper.createObjectNode()
                .put("userID",userID)
                .put("data",data);
        try {
            RequestBody body = RequestBody.create(
                    Converter.mapper.writeValueAsString(jsonBody),MediaType.get("application/json")
            );
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            urlBuilder.addPathSegment("/get_question");
            return getRequest(body,urlBuilder);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Converter.mapper.createObjectNode();
    }

    /**
     * Method for getting a personal preference of the bot
     * @param userID, the specific userID for the bot
     * @param data, the question, statement or past conversation lines.
     * @param topic, the topic to self disclose about
     * @return a node with a possible answer.
     */
    public JsonNode getSelfDisclosure(String userID, String data, String topic){
        JsonNode jsonBody = Converter.mapper.createObjectNode()
                .put("userID",userID)
                .put("topic",topic)
                .put("data",data);
        try {
            RequestBody body = RequestBody.create(
                    Converter.mapper.writeValueAsString(jsonBody),MediaType.get("application/json")
            );
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            urlBuilder.addPathSegment("/get_disclosure");
            return getRequest(body,urlBuilder);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Converter.mapper.createObjectNode();
    }

    /**
     * Method for getting a selfdisclosure and question of the bot
     * @param userID, the specific userID for the bot
     * @param data, the question, statement or past conversation lines.
     * @param topic, the topic to self disclose about
     * @return a node with a possible self disclosure and reflect
     */
    public JsonNode getSelfDisclosureAndReflect(String userID, String data, String topic){
        JsonNode jsonBody = Converter.mapper.createObjectNode()
                .put("userID",userID)
                .put("topic",topic)
                .put("data",data);
        try {
            RequestBody body = RequestBody.create(
                    Converter.mapper.writeValueAsString(jsonBody),MediaType.get("application/json")
            );
            HttpUrl.Builder urlBuilder = HttpUrl.parse(this.url).newBuilder();
            urlBuilder.addPathSegment("/get_disclosure_and_reflect");
            return getRequest(body,urlBuilder);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return Converter.mapper.createObjectNode();
    }


    private JsonNode getRequest(RequestBody body, HttpUrl.Builder urlBuilder) {
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            if(response.isSuccessful()){
                ObjectNode result = Converter.mapper.createObjectNode();
                JsonNode node = Converter.mapper.readTree(response.body().string());
                result.set("result",node);
                result.put("time",response.receivedResponseAtMillis()-response.sentRequestAtMillis());
                return result;
            }
        } catch (IOException ex) {
            logger.error("Cannot connect to the memory generation server.");

        }
        return Converter.mapper.createObjectNode();
    }


}
