package nl.bliss.environments;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import hmi.flipper2.FlipperException;
import nl.bliss.util.Converter;
import nl.bliss.util.TimeHelper;

import java.sql.*;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.logging.Level;

/**
 * The UMEnvironment is responsible for database handling within Flipper. This specific environment is set up for adding users in the database and retrieving
 * information from previous sessions.
 * @author WaterschootJB
 */
public class UMEnvironment extends BaseFlipperEnvironment {

    //Variables for the database connection
    private Connection connection;
    private String tableName;
    private String event;
    private String currentPersonID;

    //Helpers
    private ObjectMapper mapper;
    private GenericMiddlewareEnvironment mEnv;

    //History
    private ArrayList<JsonNode> localHistory;
    private ArrayList<JsonNode> topics;
    private ArrayList<JsonNode> events;
    private ZonedDateTime startConversation;

    /**
     * If no database is defined, the environment will use the null constructor.
     */
    public UMEnvironment(){
        this(null);
    }

    /**
     * If a database is defined and the parameters are filled in in the Environment.xml file, this will establish a connection with the database.
     * @param connection, the database connection
     */
    public UMEnvironment(Connection connection){
        this.connection = connection;
    }

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
        JsonNode res;
        switch(fenvmsg.cmd){
            case "add":
                logger.info("Adding new person to the database: {}",fenvmsg.params.get("content").get("id").asText());
                this.setCurrentPersonID(fenvmsg.params.get("content").get("id").asText());
                res = mapper.createObjectNode().put("id",this.addPerson(fenvmsg.params.get("content").get("id").asText()));
                this.topics = new ArrayList<>();
                this.events = new ArrayList<>();
                this.localHistory = new ArrayList<>();
                this.enqueueMessage(res,"update",fenvmsg.msgId);
                this.startConversation = ZonedDateTime.now();
                break;
            case "update":
                if(fenvmsg.params.get("content").has("type") && fenvmsg.params.get("content").get("type").asText().equals("state")){
                    this.localHistory.add(fenvmsg.params.get("content").get("value"));
                    this.addHistoryGlobal(false);
                }
                else{
                    JsonNode update = fenvmsg.params.get("content");
                    this.updatePerson(update.get("id").asText(),update.get("type").asText(),update.get("value").asText());
                }
                res = mapper.createObjectNode().put("updated",true);
                this.enqueueMessage(res,"update",fenvmsg.msgId);
                break;
            case "get":
                logger.info("Retrieving person from the database: {}",fenvmsg.params.get("content").get("id").asText());
                this.setCurrentPersonID(fenvmsg.params.get("content").get("id").asText());
                this.localHistory = new ArrayList<>();
                this.topics = this.getGlobalTopics();
                this.events = this.getGlobalEvents();
                JsonNode person = this.getPerson(fenvmsg.params.get("content").get("id").asText());
                JsonNode personInfo = mapper.createObjectNode();
                if(person.has("state") && person.get("state").has("user")){
                    personInfo = this.getPersonInformation();
                }
                res = mapper.createObjectNode().set("person",personInfo);
                this.addHistoryGlobal(true);
                this.enqueueMessage(res,fenvmsg.cmd,fenvmsg.msgId);
                this.startConversation = ZonedDateTime.now();
                break;
            case "has":
                logger.info("ID presented: {}",fenvmsg.params.get("content").get("id").asText());
                boolean exists = this.existingPerson(fenvmsg.params.get("content").get("id").asText());
                res = mapper.createObjectNode().put("exists",exists);
                if(this.currentPersonID != null){
                    this.addTopicsGlobal();
                    this.addEventsGlobal();
                    this.addHistoryGlobal(false);
                }
                this.enqueueMessage(res,fenvmsg.cmd,fenvmsg.msgId);
                break;
            case "addTopics":
                this.addPhrases(fenvmsg.params.get("nvlu"));
                this.addEvents(fenvmsg.params.get("nvlu"));
                res = mapper.createObjectNode().put("added",true);
                this.addTopicsGlobal();
                this.addEventsGlobal();
                this.enqueueMessage(res,fenvmsg.cmd,fenvmsg.msgId);
                break;
            case "getTopics":
                if(this.currentPersonID != null){
                    res = Converter.convertToJsonNode(this.getTopics());
                    this.enqueueMessage(res,fenvmsg.cmd,fenvmsg.msgId);
                }
                else{
                    logger.error("Could not retrieve topics. Current person undefined");
                }
                break;
            case "updateUser":
                boolean success = this.updatePersonInformation(fenvmsg.params.get("content"));
                res = mapper.createObjectNode().put("updated",success);
                this.enqueueMessage(res,fenvmsg.cmd,fenvmsg.msgId);
                break;
            default:
                logger.error("{}: No content in message: {}",fenvmsg.environment,fenvmsg.params.toString());
        }
        this.connection.commit();
        return null;
    }

    public ArrayList<JsonNode> getEvents() {
        return this.events;
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        this.mEnv = GenericMiddlewareEnvironment.loadMiddlewareEnvironment(this.mEnv,envs);
    }

    @Override
    public void init(JsonNode params, Connection conn) {
        try {
            this.connection = conn;
            this.mapper = new ObjectMapper();
            logger.info("Initializing database");
            if(params.has("name")){
                this.tableName = params.get("name").asText();
            }
            else{
                this.tableName = "persons";
            }
            if(params.has("event")){
                this.event = params.get("event").asText();
            }
            else{
                this.event = "default";
            }
            if(params.has("reset")){
                if(params.get("reset").asBoolean()){
                    try {
                        String deleteTable = String.format("DROP TABLE IF EXISTS %1$s", tableName);
                        logger.info("Deleting table: {}",tableName);
                        Statement deleteStatement = this.connection.createStatement();
                        deleteStatement.execute(deleteTable);
                    } catch (SQLException ex) {
                        java.util.logging.Logger.getLogger(UMEnvironment.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
            String createTable = String.format("CREATE TABLE IF NOT EXISTS %1$s ("
                    + "id text PRIMARY KEY not NULL,"
                    + "age text,"
                    + "gender text,"
                    + "region text,"
                    + "numberofsessions int,"
                    + "event text,"
                    + "state text,"
                    + "created TIMESTAMPTZ,"
                    + "modified TIMESTAMPTZ"
                    +  ")",this.tableName);
            Statement statement = this.connection.createStatement();
            logger.info("Create table if exists: {}",tableName);
            DatabaseMetaData md = connection.getMetaData();
            ResultSet rs = md.getColumns(null, null, this.tableName,null);
            if(rs.next()){
                rs = md.getColumns(null, null, this.tableName, "numberofsessions");
                if (rs.next()) {
                }
                else{
                    logger.info("Columns incorrect! Deleting table: {}",tableName);
                    String deleteTable = String.format("DROP TABLE %1$s",tableName);
                    Statement deleteStatement = this.connection.createStatement();
                    deleteStatement.execute(deleteTable);
                }
            }

            statement.execute(createTable);
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(UMEnvironment.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * We add a current Date in String format     
     * @return the Date in a certain format
     */
    public String getDate() {
        ZonedDateTime date = ZonedDateTime.now();
        return TimeHelper.formatter.format(date);
    }


    /**
     * We want to add another user to our database
     * @param id, the identifier of the person     
     * @return the ID of the person.
     * @throws FlipperException if there is no database connection to add the person
     */
    public String addPerson(String id) throws FlipperException {
        logger.debug("Adding {} to {}",id,this.tableName);
        ObjectNode initialState = mapper.createObjectNode();
        initialState.set("history",mapper.createArrayNode());
        initialState.set("topics",mapper.createObjectNode());
        initialState.set("events",mapper.createArrayNode());
        initialState.set("user",mapper.createObjectNode());
        String is = initialState.toString();
        try {
            String insertTableSQL = String.format("INSERT INTO %1$s (id, event, numberofsessions, state, created, modified) VALUES(?,?,?,?,?,?) RETURNING id",this.tableName);
            PreparedStatement preparedStatement = this.connection.prepareStatement(insertTableSQL);
            preparedStatement.setString(1, id);
            preparedStatement.setString(2, this.event);
            preparedStatement.setInt(3, 1);
            preparedStatement.setString(4, is);
            preparedStatement.setTimestamp(5, UMEnvironment.getCurrentTimeStamp());
            preparedStatement.setTimestamp(6, UMEnvironment.getCurrentTimeStamp());
            ResultSet rs = preparedStatement.executeQuery();
            rs.next();
            return rs.getString("id");
        } catch (SQLException e) {
            logger.error("CAUGHT in adding {}" + e,id);
            throw new FlipperException(e);
        }
    }

    /**
     * Method for adding information to the user profile
     * @param id, the id of the user
     * @param type, the type of column to update
     * @param content, the content to put in the column
     * @return if the update is successful
     * @throws hmi.flipper2.FlipperException if the update goes wrong
     */
    public int updatePerson(String id, String type, String content) throws FlipperException {
        if (type.equals("id")) {
                logger.debug("Updating ID. Old: {}, New: {}", id, content);
                return this.updatePersonID(id, content);
            } else {
                logger.debug("Updating in table '{}' for '{}', '{}' to '{}'", this.tableName, id, type, content);
                try {
                    String modifyTableSQL = String.format("UPDATE %1$s SET %2$s = ?, modified = ? WHERE id = ?", this.tableName, type);
                    PreparedStatement preparedStatement = this.connection.prepareStatement(modifyTableSQL);
                    preparedStatement.setString(3, id);
                    preparedStatement.setTimestamp(2, UMEnvironment.getCurrentTimeStamp());
                    if (type.equals("numberofsessions")) {
                        JsonNode person = this.getPerson(id);
                        int sessions = person.get("numberofsessions").asInt();
                        preparedStatement.setInt(1, sessions + Integer.parseInt(content));
                    } else {
                        preparedStatement.setString(1, content);
                    }
                    return preparedStatement.executeUpdate();
                } catch (SQLException e) {
                    logger.error("CAUGHT in modifying " + id + ": " + e);
                    throw new FlipperException(e);
                }
            }
    }

    private JsonNode getPerson(ResultSet rs){
        try {
            if(rs.next()){
                JsonNode resultPerson = mapper.createObjectNode()
                        .put("id",rs.getString("id"))
                        .put("age", rs.getString("age"))
                        .put("gender",rs.getString("gender"))
                        .put("region", rs.getString("region"))
                        .put("numberofsessions", rs.getInt("numberofsessions"))
                        .put("created",rs.getTimestamp("created").getTime())
                        .put("modified",rs.getTimestamp("modified").getTime())
                        .set("state",mapper.readTree(rs.getString("state")));
                logger.debug("Retrieving person {}",resultPerson.toString());
                return resultPerson;
            }
        } catch (SQLException ex) {
            java.util.logging.Logger.getLogger(UMEnvironment.class.getName()).log(Level.SEVERE, null, ex);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return mapper.createObjectNode();
    }

    /**
     * Retrieves the person with a specific ID from the person database. Also updates the number of sessions.
     * @param id, the id of the person you want to retrieve
     * @return a JSON string representation of the person.
     * @throws FlipperException, if there is no database connnection
     */
    public JsonNode getPerson(String id) throws FlipperException {
        logger.debug("Retrieving {} from database",id);
        try {
            String selectSQL = String.format("SELECT * FROM %1$s WHERE id = ?",this.tableName);
            PreparedStatement preparedStatement = this.connection.prepareStatement(selectSQL);
            preparedStatement.setString(1, id);
            ResultSet rs = preparedStatement.executeQuery();
            return this.getPerson(rs);
        } catch (SQLException e) {
            logger.error("CAUGHT in getting person: {}" + e,id);
            throw new FlipperException(e);
        }
    }

    /**
     * Retrieve all the people in the table. Could be useful if you want to find semantically similar people.
     * @return the set of all people.
     * @throws FlipperException, if there is no database connection
     */
    public JsonNode getAllPeople() throws FlipperException {
        logger.debug("Retrieving all people from the database");
        try {
            String selectSQL = String.format("SELECT * FROM %1$s",this.tableName);
            PreparedStatement preparedStatement = this.connection.prepareStatement(selectSQL);
            ResultSet rs = preparedStatement.executeQuery();
            ArrayNode people = mapper.createArrayNode();
            while (rs.next()) {
                JsonNode resultPerson = this.getPerson(rs);
                people.add(resultPerson);
            }
            return people;

        } catch (SQLException e) {
            logger.error("CAUGHT in getting all IDs: "+ e);
            throw new FlipperException(e);
        }
    }


    /**
     * Update a persons ID in the database.
     * @param oldID, the old ID of the person
     * @param newID, the new ID of the person
     * @return if successful
     * @throws FlipperException if the update fails
     */
    public int updatePersonID(String oldID, String newID) throws FlipperException {
        try{
            String modifyTableSQL = String.format("UPDATE %1$s SET id = ?, modified = ? WHERE id = ?",this.tableName);
            PreparedStatement preparedStatement = this.connection.prepareStatement(modifyTableSQL);
            preparedStatement.setString(1,newID);
            preparedStatement.setTimestamp(2, UMEnvironment.getCurrentTimeStamp());
            preparedStatement.setString(3,oldID);
            return preparedStatement.executeUpdate();
        }
        catch (SQLException e){
            logger.error("CAUGHT in modifying "+oldID+" to "+newID+": " + e);
            throw new FlipperException(e);
        }
    }

    /**
     * Check if a person exists in the person tables
     * @param id, the id of the person to check
     * @return true if the person exists, false if the person doesn't
     * @throws FlipperException if there is no database connection
     */
    public boolean existingPerson(String id) throws FlipperException{
        logger.debug("Checking if ID {} exists.",id);
        try {
            String checkForPerson = String.format("SELECT * FROM %1$s WHERE id = ?",this.tableName);
            PreparedStatement preparedStatement = this.connection.prepareStatement(checkForPerson);
            preparedStatement.setString(1, id);
            ResultSet rs = preparedStatement.executeQuery();
            return rs.next();

        }catch( SQLException e){
            logger.error("CAUGHT in existing {} "+e,id);
            throw new FlipperException(e);
        }
    }

    private static Timestamp getCurrentTimeStamp() {
        OffsetDateTime time = OffsetDateTime.now();
        return Timestamp.from(time.toInstant());
    }

    public Connection getConnection(){
        return this.connection;
    }

    /**
     * Return the local history
     * @return the history of the current conversation
     */
    public ArrayList<JsonNode> getLocalConversationHistory(){
        return this.localHistory;
    }


    /**
     * Returns the global history of the conversations
     * content :
     *                {
     *                  "state":{
     *                      "history":[[{},{},..],[..]],
     *                      "topics":{{},{},{}},
     *                      "user": ...
     *                  }
     *                }
     * @return a list of all conversation sessions in the past.
     */
    public ArrayList<ArrayList<JsonNode>> getGlobalConverationHistory(){
        ArrayList<ArrayList<JsonNode>> globalHistory = new ArrayList<>();
        try {
            JsonNode content = this.getPerson(this.currentPersonID);
            if(content.has("state") && content.get("state").has("history")){
                for (JsonNode conversation : content.get("state").get("history")) {
                    globalHistory.add((ArrayList<JsonNode>) Converter.convertToList(conversation));
                }
            }
            globalHistory.add(this.localHistory);
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return globalHistory;
    }

    /**
     * Get the global topics for the particular user from the database
     * content :
     *                {
     *                  "state":{
     *                      "history":[[{},{},..],[..]],
     *                      "topics":{{},{},{}},
     *                      "events":[{},{}]
     *                      "user":...
     *                  }
     *                }
     * @return the list of topics
     */
    public ArrayList<JsonNode> getGlobalTopics(){
        ArrayList<JsonNode> globalTopics = new ArrayList<>();
        try {
            JsonNode content = this.getPerson(this.currentPersonID);
            if(content.has("state") && content.get("state").has("topics")){
                for (JsonNode topic : content.get("state").get("topics")) {
                    globalTopics.add(topic);
                }
            }
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return globalTopics;
    }

    public ArrayList<JsonNode> getGlobalEvents(){
        ArrayList<JsonNode> globalEvents = new ArrayList<>();
        try {
            JsonNode content = this.getPerson(this.currentPersonID);
            if(content.has("state") && content.get("state").has("events")){
                for (JsonNode event : content.get("state").withArray("events")) {
                    globalEvents.add(event);
                }
            }
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return globalEvents;
    }

    /**
     * Update the database user model with topics
     * @return if it succeeded
     */
    public boolean addTopicsGlobal(){
        try {
            JsonNode topicsToAdd = Converter.convertToJsonNode(this.topics);
            JsonNode person = this.getPerson(this.currentPersonID);
            if(person.has("state")){
                ObjectNode state = person.get("state").deepCopy();
                state.set("topics",topicsToAdd);
                this.updatePerson(this.currentPersonID,"state",state.toString());
                return true;
            }
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateTopicsGlobal(String topicName, String phraseType){
        int topicIndex = this.topicExists(topicName,phraseType);
        if(topicIndex < 0){
            logger.error("Couldn't update the topic {} with type {}",topicName,phraseType);
        }
        else{
            ObjectNode topicNode = this.topics.get(topicIndex).deepCopy();
            ObjectNode topic = topicNode.get(topicName).deepCopy();
            topic.put("lastTime",TimeHelper.formatter.format(ZonedDateTime.now()));
            JsonNode frequency = mapper.createObjectNode()
                    .put("user",topic.get("frequency").get("user").asInt())
                    .put("agent",topic.get("frequency").get("agent").asInt()+1);
            topic.set("frequency",frequency);
            topicNode.set(topicName,topic);
            this.topics.set(topicIndex, topicNode);
        }
        this.addTopicsGlobal();
    }

    /**
     * Update the database user model with events
     * @return if it succeeded
     */
    public boolean addEventsGlobal(){
        try {
            ArrayNode eventsToAdd = Converter.convertToArrayNode(this.events);
            JsonNode person = this.getPerson(this.currentPersonID);
            if(person.has("state")){
                ObjectNode state = person.get("state").deepCopy();
                state.set("events",eventsToAdd);
                this.updatePerson(this.currentPersonID,"state",state.toString());
                return true;
            }
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return false;
    }

    public void updateEventsGlobal(int eventIndex){
        ObjectNode event = this.events.get(eventIndex).deepCopy();
        event.put("lastTime",TimeHelper.formatter.format(ZonedDateTime.now()));
        JsonNode frequency = mapper.createObjectNode()
                .put("user",event.get("frequency").get("user").asInt())
                .put("agent",event.get("frequency").get("agent").asInt()+1);
        event.set("frequency",frequency);
        this.events.set(eventIndex,event);
        this.addEventsGlobal();
    }

    /**
     * This method adds the localhistory at the start of the global history
     * @return if succeeded.
     */
    public boolean addHistoryGlobal(boolean initial){
        try {
            ArrayNode historyToAdd = Converter.convertToArrayNode(this.localHistory);
            JsonNode person = this.getPerson(this.currentPersonID).deepCopy();
            ObjectNode state;
            if(person.has("state") && !person.get("state").isMissingNode()){
                state = person.get("state").deepCopy();
            }
            else{
                state = mapper.createObjectNode();
            }
            ArrayNode globalHistory = state.withArray("history");
            if(!initial && globalHistory.size() > 0){
                globalHistory.set(0,historyToAdd);
            }
            else{
                globalHistory.insert(0,historyToAdd);
            }
            state.set("history",globalHistory);
            this.updatePerson(this.currentPersonID,"state",state.toString());
            return true;
        } catch (FlipperException e) {
            e.printStackTrace();

        }
        return false;
    }

    /**
     * Return all topics mentioned by the user
     * @return the topics mentioned by the user in all conversations
     */
    public ArrayList<JsonNode> getTopics(){
        return this.topics;
    }

    /**
     * Method for adding the topics to the current topics in the information state
     * @param topicNode, the node containing possible topics to add
     *                      {
     *                          "text":[user utterance],
     *                          "content":{VP:[vps],NP:[nps],PP:[pps]},
     *                          "topics":{
     *                              "phrases":{
     *                                  "VPS":{
     *                                    "chunks":[{"verb":[VERB],"complement":[COMPLEMENT]}]
     *                                  },
     *                                  "NPS":{
     *                                    "chunks":[]
     *                                  }},
     *                              "events":[
     *                                  {
     *                                     "V":[VERB],
     *                                      "A0":[SUBJECT]
     *                                  },
     *                                  {
     *                                      "V":[VERB],
     *                                  }
     *                                  ]
     *                          }
     *                          "intent":[user intent],
     *                          "emotion":{"sentiment:[polarity,intensity]},
     *                          "timestamp":[timestamp UTC]
     *                      }
     */
    private void addPhrases(JsonNode topicNode) {
        if(topicNode.has("topics") && topicNode.get("topics").has("phrases")){
            JsonNode newPhrases = topicNode.get("topics").get("phrases");
            for (JsonNode vpPhrase : newPhrases.withArray("VPS")) {
                addAndUpdatePhrases(vpPhrase, "VP",topicNode);
            }
            for (JsonNode npPhrase : newPhrases.withArray("NPS")) {
                addAndUpdatePhrases(npPhrase, "NP",topicNode);
            }
        }

    }

    /**
     * Method for adding events to the database.
     * events:[
     * {
     *     "V":[VERB],
     *     "A0":[SUBJECT]
     * },
     * {
     *     ...
     * }
     * ]
     * @param nluNode, the node containing the topics and events
     */
    private void addEvents(JsonNode nluNode) {
        if (nluNode.has("topics") && nluNode.get("topics").has("events")) {

            ArrayNode newEvents = nluNode.get("topics").withArray("events");
            if (newEvents.isEmpty())
                return;
            for (JsonNode newEvent : newEvents) {
                int index = eventExists(newEvent);
                if (index >= 0) {
                    JsonNode oldEvent = this.events.remove(index);
                    events.add(updateExistingEvent(newEvent, oldEvent, nluNode));
                } else {
                    addNewEvent(newEvent, nluNode);
                }
            }
        }

    }

    public void updateEvent(int index, JsonNode event){
        this.events.set(index,event);
    }

    /**
     * Method for adding new events to the databse
     * @param newEvent, the new event to add, which is a JsonNode of arguments
     * @param nluNode, the full node for processing.
     */
    private void addNewEvent(JsonNode newEvent, JsonNode nluNode) {
        ObjectNode eventNode = mapper.createObjectNode();
        eventNode.put("text",nluNode.get("text").asText());
        //Add frequency
        ObjectNode frequency = mapper.createObjectNode();
        frequency.put("user",1);
        frequency.put("agent",0);
        eventNode.set("frequency",frequency);
        //Add times
        eventNode.put("firstTime",TimeHelper.formatter.format(ZonedDateTime.now()));
        eventNode.put("lastTime",TimeHelper.formatter.format(ZonedDateTime.now()));
        if(newEvent.has("AM-TMP") && TimeHelper.getZonedDateTime(newEvent.get("AM-TMP").asText()) != null){
            eventNode.put("eventTime",TimeHelper.formatter.format(TimeHelper.getZonedDateTime(newEvent.get("AM-TMP").asText())));
        }
        //Add sentiment
        ObjectNode sentiment = mapper.createObjectNode();
        sentiment.put("polarity", nluNode.get("emotion").get("sentiment").get("polarity").asDouble());
        sentiment.put("intensity", nluNode.get("emotion").get("sentiment").get("intensity").asDouble());
        eventNode.set("sentiment", sentiment);
        //Add args
        eventNode.set("args",newEvent);
        events.add(eventNode);
    }

    private JsonNode updateExistingEvent(JsonNode newEvent, JsonNode oldEvent, JsonNode nluNode) {
        ObjectNode mergedEvent = oldEvent.deepCopy();
        //Set frequency
        ObjectNode frequency = mapper.createObjectNode();
        frequency.put("user",oldEvent.get("frequency").get("user").asInt());
        frequency.put("agent",oldEvent.get("frequency").get("agent").asInt()+1);
        mergedEvent.set("frequency",frequency);
        // Update sentiment
        JsonNode sentiment = updateSentiment(oldEvent.get("sentiment"),
                nluNode.get("emotion").get("sentiment"),
                oldEvent.get("frequency").get("user").asInt());
        mergedEvent.set("sentiment", sentiment);
        //Update time and optionally modify event time
        mergedEvent.put("lastTime",TimeHelper.formatter.format(ZonedDateTime.now()));
        if(!oldEvent.has("eventTime")){
            if(newEvent.has("AM-TMP") && TimeHelper.getZonedDateTime(newEvent.get("AM-TMP").asText()) != null){
                mergedEvent.put("eventTime",TimeHelper.formatter.format(TimeHelper.getZonedDateTime(newEvent.get("AM-TMP").asText())));
            }
        }
        return mergedEvent;
    }

    /**
     * Check if an event exists already in the database
     * @param newEvent, the new event to check
     * @return if the event already exists
     */
    private int eventExists(JsonNode newEvent){
        ArrayList<JsonNode> events = this.getEvents();
        if(events.isEmpty()){
            return -1;
        }
        for(int i=0; i<events.size(); i++){
            JsonNode oldEvent = events.get(i);
            //Check for event with same timestamp
            if(newEvent.has("eventTime")){
                if(oldEvent.has("eventTime")){
                    ZonedDateTime newEventTime = ZonedDateTime.parse(newEvent.get("eventTime").asText());
                    ZonedDateTime eventTime = ZonedDateTime.parse(oldEvent.get("eventTime").asText());
                    if(newEventTime.isAfter(eventTime.minusDays(1)) && newEventTime.isBefore(eventTime.plusDays(1))){
                        if(argumentsBaseIdentical(oldEvent.get("args"),newEvent.get("args"))){
                            return i;
                        }
                    }
                }
            }
            else{
                if(argumentsIdentical(oldEvent.get("args"),newEvent)){
                    return i;
                }
            }
        }
        return -1;
    }

    private boolean argumentsIdentical(JsonNode eventArgs, JsonNode newEventArgs){
        if(eventArgs.size() != newEventArgs.size()){
            return false;
        }
        else{
            Iterator<String> argumentNames = newEventArgs.fieldNames();
            while(argumentNames.hasNext()){
                String argName = argumentNames.next();
                if(!eventArgs.has(argName) || !eventArgs.get(argName).asText().equals(newEventArgs.get(argName).asText()))
                    return false;
            }
        }
        return true;
    }

    private boolean argumentsBaseIdentical(JsonNode eventArgs, JsonNode newEventArgs) {
        if(eventArgs.has("V") &&
                newEventArgs.has("V") &&
                !eventArgs.get("V").asText().equals(newEventArgs.get("V").asText())){
            return false;
        }
        if(eventArgs.has("A0") &&
                newEventArgs.has("A0") &&
                !eventArgs.get("A0").asText().equals(newEventArgs.get("A0").asText())){
            return false;
        }
        if(eventArgs.has("A1") &&
                newEventArgs.has("A1") &&
                !eventArgs.get("A1").asText().equals(newEventArgs.get("A1").asText())){
            return false;
        }
        return true;
    }

    public boolean updatePersonInformation(JsonNode content){
        try {
            JsonNode person = this.getPerson(this.currentPersonID).deepCopy();
            if(person.has("state")){
                ObjectNode state = person.get("state").deepCopy();
                state.set("user",content.get("value"));
                this.updatePerson(this.currentPersonID,"state",state.toString());
                return true;
            }
        } catch (FlipperException e) {
            e.printStackTrace();

        }
        return false;
    }

    public JsonNode getPersonInformation(){
        JsonNode user = mapper.createObjectNode();
        try {
            ObjectNode content = this.getPerson(this.currentPersonID).deepCopy();
            if(content.has("state")){
                if(content.get("state").has("user")){
                    user = content.get("state").get("user");
                }
                else{
                    user = content.set("user",mapper.createObjectNode());
                }
            }
        } catch (FlipperException e) {
            e.printStackTrace();
        }
        return user;
    }

    /**
     * Checks if a topic already exists in the database and add it, or adds to the frequency
     * @param newTopic,  the lists of new topics and their properties
     * @param phraseType, the type to compare with
     * @param nluNode, contains the nvlu information
     */
    private void addAndUpdatePhrases(JsonNode newTopic, String phraseType, JsonNode nluNode) {
        ObjectNode topicNode;
        ObjectNode topicParent = mapper.createObjectNode();
        //Check if topic exists already
        int topicIndex = this.topicExists(newTopic, phraseType);
        String topicName = this.getTopicName(newTopic, phraseType);
        if(topicIndex >= 0){
            // Update existing topic
            topicNode = topics.remove(topicIndex).get(topicName).deepCopy();
            // Update user frequency
            ObjectNode frequency = mapper.createObjectNode();
            frequency.put("user",topicNode.get("frequency").get("user").asInt()+1);
            frequency.put("agent",topicNode.get("frequency").get("agent").asInt());
            topicNode.set("frequency",frequency);
            // Update time
            topicNode.put("lastTime",TimeHelper.formatter.format(ZonedDateTime.now()));
            // Update sentiment
            JsonNode sentiment = updateSentiment(topicNode.get("sentiment"),
                    nluNode.get("emotion").get("sentiment"),
                    topicNode.get("frequency").get("user").asInt());
            topicNode.set("sentiment", sentiment);
        }
        else{
            // Add new topic
            topicNode = mapper.createObjectNode();
            if(phraseType.equals("NP")){
                topicNode.put("name",topicName);
            }
            else if(phraseType.equals("VP")){
                topicNode.put("name",newTopic.get("verb").asText());
                if(newTopic.has("complement")){
                    topicNode.put("complement",newTopic.get("complement").asText());
                }
            }
            // Add frequencies
            ObjectNode frequency = mapper.createObjectNode();
            frequency.put("user",1);
            frequency.put("agent",0);
            topicNode.set("frequency",frequency);
            // Add times
            topicNode.put("firstTime",TimeHelper.formatter.format(ZonedDateTime.now()));
            topicNode.put("lastTime", TimeHelper.formatter.format(ZonedDateTime.now()));
            // Add type
            topicNode.put("type",phraseType);
            // Add sentiment
            ObjectNode sentiment = mapper.createObjectNode();
            sentiment.put("polarity", nluNode.get("emotion").get("sentiment").get("polarity").asDouble());
            sentiment.put("intensity", nluNode.get("emotion").get("sentiment").get("intensity").asDouble());
            topicNode.set("sentiment", sentiment);
        }
        topicParent.set(topicName,topicNode);
        topics.add(topicParent);
    }

    private int topicExists(JsonNode newTopic, String phraseType){
        String topicName = getTopicName(newTopic, phraseType);
        return topicExists(topicName, phraseType);
    }

    private int topicExists(String topicName, String phraseType){
        if(topicName.equals("")){
            return -1;
        }
        for(int i = 0; i < topics.size(); i++){
            if(topics.get(i).has(topicName) &&
                    topics.get(i).get(topicName).has("type") &&
                    topics.get(i).get(topicName).get("type").asText().equals(phraseType)){
                return i;
            }
        }
        return -1;
    }

    private String getTopicName(JsonNode newTopic, String phraseType){
        String topicName = "";
        if(phraseType.equals("VP")){
            topicName = newTopic.get("verb").asText();
            if(newTopic.has("complement")){
                topicName = topicName + " " + newTopic.get("complement").asText();
            }
        }
        else if(phraseType.equals("NP")){
            topicName = newTopic.asText();
        }
        return topicName;
    }

    /**
     * Calculates the average sentiment with the new sentiment values
     *
     * @param currentSentiment, the sentiment value currently for the topic
     * @param newSentiment,     the new sentiment value to update with
     * @param frequency,        the frequency of the topic
     * @return an array node of the average sentiment for this topic over `frequency` occurences
     */
    private JsonNode updateSentiment(JsonNode currentSentiment, JsonNode newSentiment, int frequency) {
        ObjectNode averageSentiment = mapper.createObjectNode();
        double polarity = (currentSentiment.get("polarity").asDouble() * (frequency - 1) + newSentiment.get("polarity").asDouble()) / frequency;
        double intensity = (currentSentiment.get("intensity").asDouble() * (frequency - 1) + newSentiment.get("intensity").asDouble()) / frequency;
        averageSentiment.put("polarity",polarity);
        averageSentiment.put("intensity",intensity);
        return averageSentiment;
    }

    public void setCurrentPersonID(String id){
        this.currentPersonID = id;
    }

    public String getCurrentPersonID(){
        return this.currentPersonID;
    }

    public boolean timeUp(int timeOut) {
        if(ZonedDateTime.now().isAfter(this.startConversation.plusMinutes(timeOut))){
            return true;
        }
        return false;
    }
}
