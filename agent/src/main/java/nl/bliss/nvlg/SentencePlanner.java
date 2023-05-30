package nl.bliss.nvlg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import net.time4j.PrettyTime;
import nl.bliss.util.RandomHelper;
import nl.bliss.util.TimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import simplenlg.features.*;
import simplenlg.framework.NLGElement;
import simplenlg.framework.NLGFactory;
import simplenlg.lexicon.Lexicon;
import simplenlg.phrasespec.NPPhraseSpec;
import simplenlg.phrasespec.PPPhraseSpec;
import simplenlg.phrasespec.SPhraseSpec;
import simplenlg.phrasespec.VPPhraseSpec;
import simplenlg.realiser.Realiser;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 *
 * @author WaterschootJB
 */
public class SentencePlanner {

    private final Lexicon lexicon;
    private final NLGFactory factory;
    private final Realiser realiser;

    private final String language;
    private String preposition;

    private final static Logger logger = LoggerFactory.getLogger(SentencePlanner.class.getName());

    private final ObjectMapper mapper;

    private final ArrayList<String> talkVerbs;
    private final ArrayList<String> likeVerbs;
    EnumSet<InterrogativeType> topicInterrogatives = EnumSet.noneOf(InterrogativeType.class);

    public SentencePlanner(String lang){
        this.language = lang;
        this.talkVerbs = new ArrayList<>();
        this.likeVerbs = new ArrayList<>();
        switch(lang.substring(0,2)){
            case "nl":
                this.lexicon = new simplenlg.lexicon.dutch.XMLLexicon();
                this.preposition = "over";
                break;
            case "fr":
                this.lexicon = new simplenlg.lexicon.french.XMLLexicon();
                break;
            case "en":
            default:
                this.lexicon = new simplenlg.lexicon.english.XMLLexicon();
                this.preposition = "about";
                this.talkVerbs.add("talk");
                this.talkVerbs.add("speak");
                this.talkVerbs.add("chat");
                this.talkVerbs.add("discuss");
                this.talkVerbs.add("chatter");
                this.likeVerbs.add("like");
                this.likeVerbs.add("experience");
                break;
        }
        this.factory = new NLGFactory(this.lexicon);
        this.realiser = new Realiser();
        this.mapper = new ObjectMapper();
        this.topicInterrogatives.add(InterrogativeType.HOW);
        this.topicInterrogatives.add(InterrogativeType.WHY);
        this.topicInterrogatives.add(InterrogativeType.HOW_COME);
        this.topicInterrogatives.add(InterrogativeType.WHEN);
        this.topicInterrogatives.add(InterrogativeType.WHERE);
        this.topicInterrogatives.add(InterrogativeType.YES_NO);

    }
    
    /**
     * Use custom lexicons
     * @param lang, nl, fr or en.
     * @param filepath, the location of the XML lexicon files to load
     */
    public SentencePlanner(String lang, String filepath){
        this.language = lang;
        this.talkVerbs = new ArrayList<>();
        this.likeVerbs = new ArrayList<>();
        switch(lang.substring(0,2)){
            case "nl":                                
                this.lexicon = new simplenlg.lexicon.dutch.XMLLexicon(filepath + "/default-dutch-lexicon.xml");
                this.preposition = "over";
                break;
            case "fr":
                this.lexicon = new simplenlg.lexicon.french.XMLLexicon(filepath + "/default-french-lexicon.xml");
                break;
            case "en":                
            default:
                this.lexicon = new simplenlg.lexicon.english.XMLLexicon(filepath + "/default-lexicon.xml");
                this.preposition = "about";
                this.talkVerbs.add("talk");
                this.talkVerbs.add("speak");
                this.talkVerbs.add("chat");
                this.talkVerbs.add("discuss");
                this.talkVerbs.add("chatter");
                this.likeVerbs.add("like");
                this.likeVerbs.add("experience");
                break;
        }
        this.factory = new NLGFactory(this.lexicon);
        this.realiser = new Realiser();
        this.mapper = new ObjectMapper();
        this.topicInterrogatives.add(InterrogativeType.HOW);
        this.topicInterrogatives.add(InterrogativeType.WHY);
        this.topicInterrogatives.add(InterrogativeType.HOW_COME);
        this.topicInterrogatives.add(InterrogativeType.WHEN);
        this.topicInterrogatives.add(InterrogativeType.WHERE);
        this.topicInterrogatives.add(InterrogativeType.YES_NO);
    }

    /**
     * // Select the list of interrogative types.
     *         // Generate all sentences with the templates + timeslot marker + topic
     *         //Why do you like TOPIC
     *         //How come do you like TOPIC
     *         //Last week we talked about TOPIC. Any updates on that?
     *         //Yesterday we talked about TOPIC. Is this important to you?
     *         //Last night we spoke about TOPIC. Do you think a lot about TOPIC?
     *         //Last week I found your thoughts about TOPIC interesting. SELFDISCLOSURE. What do you think?
     *         //How do you like TOPIC?
     *         //When do you like TOPIC
     *         //Where do you like TOPIC
     *         //How much do you like TOPIC
     *         //Whom does also like TOPIC
     * Method to generate some memory questions based on a topic:
     * {
     *     "[topic]":{
     *         "firstTime":[DATE],
     *         "lastTime":[DATE],
     *         "sentiment":[POLARITY,INTENSITY],
     *         "type":[NPS/VPS],
     *         "frequency:{
     *             "user":[NUMBER],
     *             "agent":[NUMBER],
     *         },
     *         "questionsAsked":[
     *          {
     *              "question":"What do you think about [TOPIC]?"
     *              "answer": "I don't know",
     *              "time": [DATE]
     *          },
     *          {
     *              "question":"What is it that you like about [TOPIC]?",
     *              "answer": "It gives me lots of energy.",
     *              "time": [DATE]
     *          }
     *          ]
     *     }
     * }
     * @param topic , the jsonnode containing the topic information
     * @return a list of all possible questions to ask about the topic.
     */
    public String generateTopicQuestion(JsonNode topic){
        ObjectNode question = mapper.createObjectNode();
        String topicName = topic.fieldNames().next();
        // Construct memory sentence with topic
        String memoryStatement = getMemoryStatement(topic, topicName);
        // Construct question with topic
        String interrogativeQuestion = getTopicInterrogative(topic, topicName);
        return memoryStatement + " " + interrogativeQuestion;
    }

    /**
     * Construct the question based on a topic
     * @param node, the topic node that contains some meta information
     * @param topicName, the topic name
     * @return a question.
     */
    private String getTopicInterrogative(JsonNode node, String topicName) {
        JsonNode topic = node.get(node.fieldNames().next());
        // Construct sentence with interrogative type
        SPhraseSpec phraseInterrogative = this.factory.createClause();
        NPPhraseSpec subjectInterrogative = this.factory.createNounPhrase("YOU");
        subjectInterrogative.setFeature(Feature.PRONOMINAL, true);
        subjectInterrogative.setFeature(Feature.PERSON, Person.SECOND);
        subjectInterrogative.setPlural(false);
        phraseInterrogative.setSubject(subjectInterrogative);

        // Set the topic
        if(topic.has("type") && topic.get("type").asText().equals("NP")){
            VPPhraseSpec verbInterrogative = this.factory.createVerbPhrase();
            verbInterrogative.setVerb(this.likeVerbs.get(ThreadLocalRandom.current().nextInt(0,this.likeVerbs.size())));
            phraseInterrogative.setVerbPhrase(verbInterrogative);
            phraseInterrogative.setFeature(Feature.TENSE, Tense.PRESENT);

            NPPhraseSpec objectInterrogative = this.factory.createNounPhrase("TOPIC");
            objectInterrogative.setNoun(topicName);
            objectInterrogative.setPlural(false);
            phraseInterrogative.addComplement(objectInterrogative);
        }
        else if(topic.has("type") && topic.get("type").asText().equals("VP")){
            VPPhraseSpec verbInterrogative = this.factory.createVerbPhrase();
            verbInterrogative.setVerb(topic.get("name").asText());
            verbInterrogative.setFeature(Feature.FORM, Form.GERUND);
            if(topic.has("complement")){
                verbInterrogative.addPostModifier(topic.get("complement").asText());
            }
            phraseInterrogative.setVerb(verbInterrogative);

        }

        // Select the interrogative
        InterrogativeType interrogative = RandomHelper.getRandomSetElement(this.topicInterrogatives);
        phraseInterrogative.setFeature(Feature.INTERROGATIVE_TYPE, interrogative);

        return realiser.realiseSentence(phraseInterrogative);
    }

    /**
     * Get statement about topic with timemarker
     * @param node, topic node containing the meta data
     * @param topicName, the name of the topic
     * @return a statement about the topic with timemarker.
     */
    private String getMemoryStatement(JsonNode node, String topicName) {
        SPhraseSpec phraseMemory = this.factory.createClause();
        JsonNode topic = node.get(topicName);
        // Add the time marker
        ZonedDateTime timeTopic = ZonedDateTime.parse(topic.get("lastTime").asText(),TimeHelper.formatter);
        ArrayList<String> timeMarkers = TimeHelper.getAlternativePrettyTimeString(timeTopic);
        timeMarkers.add(PrettyTime.of(Locale.forLanguageTag(this.language)).printRelative(timeTopic));
        String timeMemory = timeMarkers.get(ThreadLocalRandom.current().nextInt(0,timeMarkers.size()));
        phraseMemory.addPostModifier(timeMemory);

        // Set the subject to 'we'
        NPPhraseSpec subjectMemory = this.factory.createNounPhrase("WE");
        subjectMemory.setFeature(Feature.PRONOMINAL, true);
        subjectMemory.setFeature(Feature.PERSON, Person.FIRST);
        subjectMemory.setPlural(true);
        phraseMemory.setSubject(subjectMemory);

        // Select the 'talk' synonym verb
        VPPhraseSpec verbMemory = this.factory.createVerbPhrase(this.talkVerbs.get(ThreadLocalRandom.current().nextInt(0,this.talkVerbs.size())));
        phraseMemory.setVerb(verbMemory);
        phraseMemory.setFeature(Feature.TENSE, Tense.PAST);

        // Set the topic in a prepositional phrase
        PPPhraseSpec phraseTopic = this.factory.createPrepositionPhrase();
        phraseTopic.setPreposition(this.preposition);
        if(topic.get("type").asText().equals("NP")){
            NPPhraseSpec objectTopic = this.factory.createNounPhrase("topic");
            objectTopic.setNoun(topicName);
            objectTopic.setPlural(false);
            phraseTopic.addComplement(objectTopic);
        }
        else{
            VPPhraseSpec objectTopic = this.factory.createVerbPhrase("topic");
            objectTopic.setVerb(topic.get("name").asText());
            objectTopic.setFeature(Feature.FORM, Form.GERUND);
            if(topic.has("complement")){
                objectTopic.addPostModifier(topic.get("complement").asText());
            }
            phraseTopic.addComplement(objectTopic);
        }
        phraseMemory.setComplement(phraseTopic);

        // Construct the memoryTopic statement
        return realiser.realiseSentence(phraseMemory);
    }

    /**
     * Method to generate event questions based on the event node:
     * {
     *     [event]:{
     *         "text":[ORIGINAL EVENT TEXT],
     *         "firstTime":[DATE],
     *         "eventTime":[DATE],
     *         "lastTime":[DATE],
     *         "sentiment":[POLARITY,INTENSITY],
     *         "args":[
     *              {
     *                     "A0":[SUBJECT]
     *              },
     *              {
     *                     "A1":[DIRECT_OBJECT]
     *              },
     *              {
     *                     "A2":[INDIRECT_OBJECT]
     *              }
     *         ]
     *         "questionsAsked:[
     *          {
     *              "question":[QUESTION],
     *              "lastTime":[DATE],
     *              "answered":[ANSWER]
     *          }
     *         ]
     *     }
     * }
     * @param event, the event containing information about the event
     * @return an event question
     */
    public String generateEventQuestion(JsonNode event){

        // Select a relevant timeslot maker, either eventTime or lastTime.
        ZonedDateTime eventTime;
        if(event.has("eventTime")){
            // Create eventTime question
            eventTime = ZonedDateTime.parse(event.get("eventTime").asText(),TimeHelper.formatter);
            // Create with and without statement
            if(ThreadLocalRandom.current().nextInt(2)==0){
                // Statement includes event time and event.
                String statement = this.getEventStatement(event,true,eventTime);
                // Event question is generic or specific.
                String question = this.getEventQuestion(event,true,eventTime, true);
                return statement+" "+question;
            }
            else{
                // Event question is specific.
                String question = this.getEventQuestion(event, true, eventTime, false);
                return question;
            }
        }
        // If the event doesn't have a time, we talk about the event in relation to the last time mentioned
        else{
            ZonedDateTime lastTime = ZonedDateTime.parse(event.get("lastTime").asText(), TimeHelper.formatter);
            if(ThreadLocalRandom.current().nextInt(2)==0){
                // Statement includes the last time and event
                String statement = this.getEventStatement(event, false, lastTime);
                // Event question
                String question = this.getEventQuestion(event, false, lastTime, true);
                return statement+" "+question;
            }
            else{
                // Ask question about the event.
                String question = this.getEventQuestion(event, false, lastTime, false);
                return question;
            }
        }
    }

    /**
     * Method for reconstructing the event with SimpleNLG from the event Node
     * Can be used to construct either a question or statement
     * @param event, the event node
     * @return a sentence specification of the event
     */
    private SPhraseSpec reconstructEvent(JsonNode event){
        // Construct the question
        JsonNode arguments = event.get("args");
        SPhraseSpec eventPhrase = this.factory.createClause();
        // Verb phrase
        VPPhraseSpec eventVerb = this.factory.createVerbPhrase();
        if(arguments.has("V")){
            eventVerb.setVerb(arguments.get("V").asText());
        }
        else{
            eventVerb.setVerb("be");
        }
        eventPhrase.setVerbPhrase(eventVerb);

        // Subject
        NPPhraseSpec eventSubject = this.factory.createNounPhrase("SUBJECT");
        if(arguments.has("A0") && !arguments.get("A0").asText().toLowerCase().equals(this.getStringifiedPronoun(DiscourseFunction.SUBJECT,Person.FIRST,false))){
            eventSubject.setNoun(arguments.get("A0").asText());
        }
        else{
            eventSubject.setFeature(Feature.PRONOMINAL,true);
            eventSubject.setFeature(Feature.PERSON,Person.SECOND);
            eventSubject.setPlural(false);
        }
        eventPhrase.setSubject(eventSubject);

        // Object
        if(arguments.has("A1")){
            NPPhraseSpec eventObject = this.factory.createNounPhrase("OBJECT");
            if(!arguments.get("A1").asText().toLowerCase().equals(this.getStringifiedPronoun(DiscourseFunction.OBJECT,Person.FIRST, false))){
                eventObject.setNoun(arguments.get("A1").asText());
            }
            else{
                eventObject.setFeature(Feature.PRONOMINAL,true);
                eventObject.setFeature(Feature.PERSON,Person.SECOND);
            }
            eventPhrase.setObject(eventObject);
        }

        // Indirect object
        if(arguments.has("A2")){
            NPPhraseSpec eventIdObject = this.factory.createNounPhrase("DOBJECT");
            if(!arguments.get("A2").asText().toLowerCase().equals(this.getStringifiedPronoun(DiscourseFunction.INDIRECT_OBJECT,Person.FIRST,false))){
                eventIdObject.setNoun(arguments.get("A2").asText());
            }
            else{
                eventIdObject.setFeature(Feature.PRONOMINAL,true);
                eventIdObject.setFeature(Feature.PERSON,Person.SECOND);
            }
            eventPhrase.setIndirectObject(eventIdObject);
        }
        // Location, 50% chance of adding this.
        if(arguments.has("AM-LOC")){
            if(ThreadLocalRandom.current().nextInt(0,2) == 1){
                eventPhrase.addComplement(arguments.get("AM-LOC").asText());
            }
        }
        return eventPhrase;
    }

    /**
     * Generates an event question
     * @param event, event node containing event information
     * @param eventTime, if event time (true) or last time (false) is used
     * @param time, to add the relative time to the component
     * @param statement, if a statement will be added before the question
     * @return an event question
     */
    private String getEventQuestion(JsonNode event, boolean eventTime, ZonedDateTime time, boolean statement) {

        ArrayList<String> questions = new ArrayList<>();
        SPhraseSpec question = this.reconstructEvent(event);
        if(statement){
            int type = ThreadLocalRandom.current().nextInt(2);
            if(!eventTime){
                if(type==0){
                    questions.add(this.getGenericQuestion(Tense.PRESENT));
                }
                else{
                    SPhraseSpec superClause = getTimelessQuestion(event, question);
                    questions.add(this.realiser.realiseSentence(superClause));
                }
            }
            else{
                if(type==0){
                    if(time.isBefore(ZonedDateTime.now())){
                        questions.add(this.getGenericQuestion(Tense.PAST));
                    }
                    else{
                        questions.add(this.getGenericQuestion(Tense.FUTURE));
                    }
                }
                else{
                    getTimeQuestion(event, time, question);
                    questions.add(this.realiser.realiseSentence(question));
                }
            }
        }
        else{
            if(!eventTime){
                SPhraseSpec superClause = getTimelessQuestion(event, question);
                questions.add(this.realiser.realiseSentence(superClause));
            }
            else{
                getTimeQuestion(event, time, question);
                questions.add(this.realiser.realiseSentence(question));
            }
        }
        return questions.get(ThreadLocalRandom.current().nextInt(questions.size()));
    }

    /**
     * Method for getting a question of a event with temporal. Modifies the question.
     * @param event, the event node
     * @param time, the time of the event
     * @param question, the reconstructed event
     */
    private void getTimeQuestion(JsonNode event, ZonedDateTime time, SPhraseSpec question) {
        InterrogativeType interrogative = RandomHelper.getRandomSetElement(this.selectCandidateInterrogatives(event));
        question.setFeature(Feature.INTERROGATIVE_TYPE, interrogative);
        //Set correct time
        if(time.isBefore(ZonedDateTime.now())){
            question.getVerb().setFeature(Feature.TENSE,Tense.PAST);
        }
        else{
            question.getVerb().setFeature(Feature.TENSE,Tense.FUTURE);
        }
        //Add relative time
        ArrayList<String> timeMarkers = TimeHelper.getAlternativePrettyTimeString(time);
        timeMarkers.add(PrettyTime.of(Locale.forLanguageTag(this.language)).printRelative(time));
        String timeMemory = timeMarkers.get(ThreadLocalRandom.current().nextInt(0,timeMarkers.size()));
        question.addComplement(timeMemory);
    }

    private SPhraseSpec getTimelessQuestion(JsonNode event, SPhraseSpec question) {
        //Reset verb of event and add it
        VPPhraseSpec verb = this.factory.createVerbPhrase(question.getVerb());
        verb.setFeature(Feature.FORM,Form.GERUND);
        question.setVerbPhrase(verb);
        //Realize the subclause.
        String complement = this.realiser.realiseSentence(question);

        //Construct the superclause
        SPhraseSpec superClause = this.factory.createClause();
        //Add subject
        NPPhraseSpec superClauseSubject = this.factory.createNounPhrase("YOU");
        superClauseSubject.setFeature(Feature.PRONOMINAL,true);
        superClauseSubject.setFeature(Feature.PERSON,Person.SECOND);
        superClauseSubject.setPlural(false);
        superClause.setSubject(superClauseSubject);
        //Add verb
        VPPhraseSpec superClauseVerb = this.factory.createVerbPhrase();
        superClauseVerb.setVerb(this.likeVerbs.get(ThreadLocalRandom.current().nextInt(0,this.likeVerbs.size())));
        superClause.setVerbPhrase(superClauseVerb);
        //Add the subclause
        superClause.addComplement(complement);
        //Add interrogative
        InterrogativeType interrogative = RandomHelper.getRandomSetElement(this.selectCandidateInterrogatives(event));
        superClause.setFeature(Feature.INTERROGATIVE_TYPE, interrogative);
        return superClause;
    }

    /**
     * Method for getting an event statement, such as "Last week you played football" or
     * "Last week we talked about you playing football"
     * @param event, the event node
     * @param eventTime, if a event time is provided
     * @param time, the time given for the statement.
     * @return a statement with time.
     */
    private String getEventStatement(JsonNode event, boolean eventTime, ZonedDateTime time) {
        ArrayList<String> statements = new ArrayList<>();
        //Reconstruct event
        SPhraseSpec eventPhrase = this.reconstructEvent(event);
        //Get relative time
        ArrayList<String> timeMarkers = TimeHelper.getAlternativePrettyTimeString(time);
        timeMarkers.add(PrettyTime.of(Locale.forLanguageTag(this.language)).printRelative(time));
        String timeMemory = timeMarkers.get(ThreadLocalRandom.current().nextInt(0,timeMarkers.size()));

        // Add time and possible superclause
        if(!eventTime){
            SPhraseSpec superClause = this.factory.createClause();

            NPPhraseSpec superClauseSubject = this.factory.createNounPhrase();
            superClauseSubject.setFeature(Feature.PRONOMINAL,true);
            superClauseSubject.setFeature(Feature.PERSON,Person.FIRST);
            superClauseSubject.setPlural(true);
            superClause.setSubject(superClauseSubject);

            VPPhraseSpec superClauseVerb = this.factory.createVerbPhrase();
            superClauseVerb.setVerb(this.talkVerbs.get(ThreadLocalRandom.current().nextInt(0,this.talkVerbs.size())));
            superClauseVerb.setFeature(Feature.TENSE,Tense.PAST);
            superClause.setVerbPhrase(superClauseVerb);

            eventPhrase.getVerb().setFeature(Feature.FORM,Form.GERUND);
            PPPhraseSpec superClauseEvent = this.factory.createPrepositionPhrase();
            superClauseEvent.setPreposition(this.preposition);
            superClauseEvent.setComplement(eventPhrase);
            superClause.addComplement(timeMemory);
            statements.add(this.realiser.realiseSentence(superClause));
        }
        else{
            if(time.isBefore(ZonedDateTime.now())){
                eventPhrase.getVerb().setFeature(Feature.TENSE,Tense.PAST);
            }
            else{
                eventPhrase.getVerb().setFeature(Feature.TENSE,Tense.FUTURE);
            }
            eventPhrase.addComplement(timeMemory);
            statements.add(this.realiser.realiseSentence(eventPhrase));
        }
        return statements.get(ThreadLocalRandom.current().nextInt(statements.size()));
    }


    /**
     * @param tense, the time, for past or future event     *
     * @return a String of a generic question.
     * TODO: Make this a generic function with language resources
     */
    private String getGenericQuestion(Tense tense){
        ArrayList<String> genericQuestions = new ArrayList<>();
        switch(tense){
            case PAST:
                genericQuestions.add("How did it go?");
                genericQuestions.add("How did you like it?");
                genericQuestions.add("How was it?");
                break;
            case FUTURE:
                genericQuestions.add("How much are you looking forward to it?");
                genericQuestions.add("How well are you prepared?");
                break;
            case PRESENT:
            default:
                genericQuestions.add("How much do you like it?");
                genericQuestions.add("How much do you do it?");
                genericQuestions.add("Why do you like it?");
        }
        return genericQuestions.get(ThreadLocalRandom.current().nextInt(genericQuestions.size()));
    }

    /**
     * Select appropriate interrogative type from the list based on the semantic role labels on events.
     * @param event, the event node containing the SRL labels
     * @return the interrogatives that are applicable
     */
    public EnumSet<InterrogativeType> selectCandidateInterrogatives(JsonNode event){
        EnumSet<InterrogativeType> interrogatives = EnumSet.allOf(InterrogativeType.class);
        if(event.has("args") && !event.get("args").has("V")){
            logger.error("SRL has found no VERB in this event!");
        }
        NLGElement verb = this.factory.createVerbPhrase(event.get("args").get("V").asText()).getVerb();
        if(event.has("args")){
            // Subject
            if(event.get("args").has("A0")){
                interrogatives.remove(InterrogativeType.WHO_SUBJECT);
                interrogatives.remove(InterrogativeType.WHAT_SUBJECT);
            }
            // Object
            if(event.get("args").has("A1") || verb.hasFeature("intransitive")){
                interrogatives.remove(InterrogativeType.WHAT_OBJECT);
                interrogatives.remove(InterrogativeType.WHO_OBJECT);
            }
            // Indirect Object
            if(event.get("args").has("A2") || verb.hasFeature("intransitive") || verb.hasFeature("transitive")){
                interrogatives.remove(InterrogativeType.WHO_INDIRECT_OBJECT);
            }
            // Location: 'at the museum', 'in San Francisco'
            // Directions: 'down', 'to Bangkok'
            if(event.get("args").has("AM-LOC") || event.get("args").has("AM-DIR")){
                interrogatives.remove(InterrogativeType.WHERE);
            }
            // Temporal: 'yesterday evening', 'now'
            if(event.get("args").has("AM-TMP")){
                interrogatives.remove(InterrogativeType.WHEN);
            }
            // Adjective modifiers, how an action is performed: 'clearly', 'with much enthusiasm'
            if(!event.get("args").has("AM-MNR")){
                interrogatives.remove(InterrogativeType.HOW_ADJECTIVE);
                interrogatives.remove(InterrogativeType.HOW_MANY);
            }
            // Indicate the amount of change occuring from an action ('prices raised by 15%', 'a lot', 'raised more than she did')
            if(event.get("args").has("AM-EXT")){

            }
            // Causal modifiers: 'because ..'
            if(event.get("args").has("AM-CAU")){
                interrogatives.remove(InterrogativeType.HOW);
            }
            // Purpose modifiers: 'in response to the ruling'
            if(event.get("args").has("AM-PNC")){
                interrogatives.remove(InterrogativeType.HOW_COME);
                interrogatives.remove(InterrogativeType.WHY);
            }
            // Secondary predicate inside a predicate: 'ate the meat raw'
            if(!event.get("args").has("AM-PRD")){
                interrogatives.remove(InterrogativeType.HOW_PREDICATE);
            }
            // Modals, so auxiliary verbs often: 'will', 'may', 'shall'
            if(event.get("args").has("AM-MOD")){

            }
            // Discourse markers: 'however', 'also', 'but'
            if(event.get("args").has("AM-DIS")){

            }
            // Reflective modifiers: 'themselves', 'each other'
            if(event.get("args").has("AM-REC")){

            }
            // Default modifiers for a verb, but modifies the entire sentence: 'probably', 'possibly', 'only', 'even', 'fortunately','really'
            if(event.get("args").has("AM-ADV")){

            }
            // Sometimes arguments are split up, so one part could be labeled as stranded
            if(event.get("args").has("AM-STR")){

            }
            // Negation found in a sentence
            if(event.get("args").has("AM-NEG")){

            }
            // Could be anything as part of the verb
            if(event.get("args").has("A3") || event.get("args").has("A4") || event.get("args").has("A5")){

            }
            //These are removed because they don't map well to the semantic role labels.
            // InterrogativeType.YES_NO always remains.
            // TODO: Make sure these types are added if possible.
            // Whose could be added when there is a possessive pronoun that cannot be resolved.
            // Which could be added when there is a disjunction in the sentence or multiple options are given.
            interrogatives.remove(InterrogativeType.WHOSE);
            interrogatives.remove(InterrogativeType.WHICH);
        }
        return interrogatives;
    }

    /**
     * Helper method to get the surface text for pronouns
     * For example, in English, for object function, first person singular is "me"
     * @param df, the discourse function
     * @param person, the desired person (first, second, third)
     * @return the correct surface text for a pronoun
     */
    public String getStringifiedPronoun(DiscourseFunction df, Person person, boolean plural){
        NPPhraseSpec dObject = this.factory.createNounPhrase(df.name());
        dObject.setFeature(Feature.PRONOMINAL, true);
        dObject.setFeature(Feature.PERSON, person);
        dObject.setFeature("discourse_function", df);
        dObject.setPlural(plural);
        String result = this.realiser.realise(dObject).getRealisation();
        return result.toLowerCase();
    }
}
