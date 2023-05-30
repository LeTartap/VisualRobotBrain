package nl.bliss.nvlg;

import com.fasterxml.jackson.databind.JsonNode;
import nl.bliss.util.APIHelper;
import nl.bliss.util.TimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;

/**
 * Class that determines which topics are the ones that should be used for memory questions
 */
public class ContentPlanner {

    private final String language;
    private static Logger logger = LoggerFactory.getLogger(ContentPlanner.class.getName());

    public ContentPlanner(String language){
        this.language = language;
        if(!APIHelper.initialized()){
            logger.error("The system does not have a set URL for APIHelper: {}",APIHelper.getURL());
            System.exit(1);
        }
    }

    public ContentPlanner(String language, String url){
        this.language = language;
        if(!APIHelper.initialized()){
            logger.warn("The system does not have a set APIHelper yet, will intialize now");
            APIHelper.init(url);
        }
        else{
            logger.warn("The APIHelper has been initialized already with URL {} ",APIHelper.getURL());
        }
    }

    /**
     * Method to select a topic for a template
     * @param topics, a JSONnode containing topics and their properties
     * @param userText, the history of the interactions
     * @return the topics to ask a question about or disclose about.
     */
    public ArrayList<JsonNode> selectTopics(ArrayList<JsonNode> topics, String userText){
        ArrayList<JsonNode> candidateTopics = new ArrayList<>();
        ZonedDateTime currentTime = ZonedDateTime.now();
        for(JsonNode topic : topics){
            // If the user is midly negative to extremely positive about the topic
            JsonNode node = topic.get(topic.fieldNames().next());
            if(node.get("sentiment").get("polarity").asDouble() >= -0.5 && node.get("frequency").get("user").asInt() > 2){
                // The agent hasn't asked many questions about it.
                ZonedDateTime nodeTime = ZonedDateTime.parse(node.get("lastTime").asText(), TimeHelper.formatter);
                if(node.get("frequency").get("agent").asInt() < 5 && nodeTime.isBefore(currentTime.minusHours(1))){
                    candidateTopics.add(topic);
                    continue;
                }
                // We don't want to discuss topics older than a month or younger than 1 hour.
                if(nodeTime.isAfter(currentTime.minusMonths(1)) &&
                        nodeTime.isBefore(currentTime.minusHours(1))){
                    // We want the user to have mentioned the topic at least 3 times.
                    if(node.get("frequency").get("user").asInt() > 2 && node.get("frequency").get("agent").asInt() < 5){
                        candidateTopics.add(topic);
                    }
                }
            }
        }
        // Sort the array based on similarity if there is a user text.
        sortMemory(userText, candidateTopics);
        return candidateTopics;
    }

    private void sortMemory(String userText, ArrayList<JsonNode> candidateTopics) {
        if(!userText.isEmpty()){
            ArrayList<MemorySimilarity> sortIt = new ArrayList<>();
            for(JsonNode candidate: candidateTopics){
                double similarity = APIHelper.getSimilarity(candidate.fieldNames().next(), userText,this.language);
                sortIt.add(new MemorySimilarity(candidate, similarity));
            }
            candidateTopics.clear();
            Collections.sort(sortIt);
            for(MemorySimilarity node : sortIt){
                candidateTopics.add(node.getNode());
            }
        }
    }


    private void sortMemory(String userNode, ArrayList<JsonNode> events, ArrayList<Integer> candidateEvents) {
        if(!userNode.isEmpty()){
            ArrayList<MemorySimilarity> sortIt = new ArrayList<>();
            for(int i : candidateEvents){
                double similarity = APIHelper.getSimilarity(events.get(i).fieldNames().next(), userNode, this.language);
                sortIt.add(new MemorySimilarity(i, similarity));
            }
            candidateEvents.clear();
            Collections.sort(sortIt);
            for(MemorySimilarity index : sortIt){
                candidateEvents.add(index.getNodeIndex());
            }
        }
    }

    /**
     * Method to select an event for a template
     * @param events, a JSONnode containing events and their properties
     * @param userText, the last thing the user said
     * @return a list of indices of the possible events
     */
    //Search for event topics that happened last week or next week. Select an event with high sentiment not mentioned before.
    public ArrayList<Integer> selectEvents(ArrayList<JsonNode> events, String userText){
        ArrayList<Integer> candidateEvents = new ArrayList<>();
        ZonedDateTime currentTime = ZonedDateTime.now();
        //Iterate through all events known
        for(int i =0; i< events.size(); i++){
            JsonNode node = events.get(i);
            // If the user is midly negative to extremely positive about the topic
            if(node.get("sentiment").get("polarity").asInt() >= -0.5 && node.get("frequency").get("user").asInt() > 2){
                // The agent hasn't asked many questions about it.
                ZonedDateTime nodeTime = ZonedDateTime.parse(node.get("lastTime").asText(), TimeHelper.formatter);
                if(node.get("frequency").get("agent").asInt() < 5 && nodeTime.isBefore(currentTime.minusHours(1))){
                    candidateEvents.add(i);
                }
                // We don't want to discuss events that happened longer than two months ago (60 days).
                else if(nodeTime.isAfter(currentTime.minusMonths(1)) &&
                        nodeTime.isBefore(currentTime.minusHours(1))){
                    // We want the user to have mentioned the topic at least 5 times.
                    if(node.get("frequency").get("user").asInt() > 2 && node.get("frequency").get("agent").asInt() < 5){
                        candidateEvents.add(i);
                    }
                }
            }
        }
        sortMemory(userText, events, candidateEvents);
        return candidateEvents;
    }


    class MemorySimilarity implements Comparable<MemorySimilarity> {
        private double salience;
        private JsonNode node;
        private int nodeIndex;

        public MemorySimilarity(JsonNode topic, double salience){
            this.salience = salience;
            this.node = topic;
        }

        public MemorySimilarity(int eventIndex, double salience){
            this.salience = salience;
            this.nodeIndex = eventIndex;
        }


        @Override
        public int compareTo(MemorySimilarity o) {
            if(o.salience < this.salience){
                return -1;
            }
            else{
                return 1;
            }
        }

        public JsonNode getNode(){
            return this.node;
        }

        public int getNodeIndex(){
            return this.nodeIndex;
        }
    }

}
