import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import nl.bliss.nvlg.ContentPlanner;
import nl.bliss.nvlg.MemoryComponent;
import nl.bliss.nvlg.SentencePlanner;
import nl.bliss.util.Converter;
import okhttp3.OkHttpClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.StringContains.containsString;


public class TestMemoryQuestions {

    @Rule
    public ErrorCollector collector = new ErrorCollector();

    public final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testMemoryQuestion(){
            String topicsJson = "{\"topics\": [\n" +
                    "  {\n" +
                    "    \"weekend\": {\n" +
                    "      \"firstTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                    "      \"lastTime\": \"2020-10-17-09-37-19-CEST\",\n" +
                    "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                    "      \"name\":\"weekend\",\n" +
                    "      \"type\": \"NP\",\n" +
                    "      \"frequency\": {\n" +
                    "        \"user\": 1,\n" +
                    "        \"agent\": 0\n" +
                    "      }\n" +
                    "    },\n" +
                    "    \"playing football\": {\n" +
                    "      \"firstTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                    "      \"lastTime\": \"2020-10-15-09-37-19-CEST\",\n" +
                    "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                    "      \"name\":\"playing\",\n" +
                    "      \"complement\":\"football\",\n" +
                    "      \"type\": \"VP\",\n" +
                    "      \"frequency\": {\n" +
                    "        \"user\": 6,\n" +
                    "        \"agent\": 1\n" +
                    "      }\n" +
                    "    }\n" +
                    "  }]}";

            try {
                SentencePlanner enPlanner = new SentencePlanner("en",SentencePlanner.class.getClassLoader().getResource("data/nlg").getPath());
                JsonNode topicNode = mapper.readTree(topicsJson);
                String question = enPlanner.generateTopicQuestion(topicNode.withArray("topics").get(0));
                collector.checkThat(question,containsString("weekend"));
            } catch (JsonProcessingException | NullPointerException e) {
                e.printStackTrace();
            }


    }

    @Test
    public void testEventQuestion() {
        String eventJson = "{\"events\": [\n" +
                "    {\n" +
                "        \"text\": \"i will spend some time with friends\",\n" +
                "        \"frequency\": {\n" +
                "          \"user\": 1,\n" +
                "          \"agent\": 0\n" +
                "        },\n" +
                "        \"firstTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"eventTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"lastTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                "        \"args\": {\n" +
                "          \"V\": \"spend\",\n" +
                "          \"A0\": \"i\",\n" +
                "          \"A1\": \"some time\",\n" +
                "          \"A2\": \"with friends\"\n" +
                "        },\n" +
                "        \"questionsAsked\": [\n" +
                "          {\n" +
                "            \"text\": \"where did you spend some time with friends?\",\n" +
                "            \"answered\": true,\n" +
                "            \"lastTime\": \"2020-08-03-09-37-19-CEST\"\n" +
                "          }\n" +
                "        ]\n" +
                "    },\n" +
                "    {\n" +
                "        \"text\": \"i like to play football in the garden while singing a song\",\n" +
                "        \"frequency\": {\n" +
                "          \"user\": 1,\n" +
                "          \"agent\": 0\n" +
                "        },\n" +
                "        \"firstTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"eventTime\": \"2020-11-03-09-37-19-CEST\",\n" +
                "        \"lastTime\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                "        \"args\": {\n" +
                "          \"V\": \"play\",\n" +
                "          \"A0\": \"i\",\n" +
                "          \"A1\": \"football\",\n" +
                "          \"AM-LOC\": \"in the garden\",\n" +
                "          \"AM-TMP\": \"while singing a song\"\n" +
                "        },\n" +
                "        \"questionsAsked\": [\n" +
                "          {\n" +
                "            \"text\": \"where did you spend some time with friends?\",\n" +
                "            \"answered\": true,\n" +
                "            \"lastTime\": \"2020-08-03-09-37-19-CEST\"\n" +
                "          }\n" +
                "        ]\n" +
                "      }\n" +
                "  ]}";        
        try {
            SentencePlanner enPlanner = new SentencePlanner("en",SentencePlanner.class.getClassLoader().getResource("data/nlg").getPath());
            JsonNode topicNode = mapper.readTree(eventJson);
            String question = enPlanner.generateEventQuestion(topicNode.withArray("events").get(1));
            collector.checkThat(question,containsString("football"));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTopic() {
        ObjectMapper mapper = new ObjectMapper();
        ContentPlanner cp = new ContentPlanner("en", "http://127.0.0.1:8190");
        String topics = "{\"topics\":[{\n" +
                "      \"weekend\":{\n" +
                "       \"name\":\"weekend\", \n" +
                "        \"firstTime\":\"2020-08-03-09-37-19-CEST\",\n" +
                "        \"lastTime\":\"2020-08-03-09-37-19-CEST\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                "        \"type\":\"NP\",\n" +
                "        \"frequency\":{\n" +
                "          \"user\": 1,\n" +
                "          \"agent\":0\n" +
                "        }\n" +                
                "      }},\n" +
                "      {\"friday\":{\n" +
                "       \"name\":\"friday\", \n" +
                "        \"firstTime\":\"2020-08-03-09-37-19-CEST\",\n" +
                "        \"lastTime\":\"2020-08-03-09-37-19-CEST\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                "        \"type\":\"NP\",\n" +
                "        \"frequency\":{\n" +
                "          \"user\": 6,\n" +
                "          \"agent\":1\n" +
                "        }\n" +                
                "      }\n" +
                "    }]}";
        try {
            ArrayNode topicNode = mapper.readTree(topics).withArray("topics");
            ArrayList<JsonNode> topicArray = new ArrayList<>();
            for(JsonNode topic : topicNode){
                topicArray.add(topic);
            }
            ArrayList<JsonNode> topic = cp.selectTopics((ArrayList<JsonNode>) topicArray,"weekend");
            if(!topic.isEmpty()){
                System.out.println(topic.get(0).toString());
            }
            else{
                System.out.println("No topics selected");
            }
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testTopics(){
        ContentPlanner cp = new ContentPlanner("en","http://127.0.0.1:8190");
        String topicJSON = "\"topics\": [\n" +
                "    {\n" +
                "      \"the weekend\": {\n" +
                "        \"frequency\": 2,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +
                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"in the weekend\": {\n" +
                "        \"frequency\": 1,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-19-CEST\",\n" +
                "        \"type\": \"PP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"The ones\": {\n" +
                "        \"frequency\": 2,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-27-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"my trust\": {\n" +
                "        \"frequency\": 2,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-27-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"information\": {\n" +
                "        \"frequency\": 1,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-43-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"If they\": {\n" +
                "        \"frequency\": 1,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-43-CEST\",\n" +
                "        \"type\": \"PP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"Personal information\": {\n" +
                "        \"frequency\": 2,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-48-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"the covid news\": {\n" +
                "        \"frequency\": 3,\n" +
                "        \"lastmention\": \"2020-08-03-09-37-59-CEST\",\n" +
                "        \"type\": \"NP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    },\n" +
                "    {\n" +
                "      \"play\": {\n" +
                "        \"frequency\": 2,\n" +
                "        \"lastmention\": \"2020-08-03-09-38-05-CEST\",\n" +
                "        \"type\": \"VP\",\n" +
                "        \"sentiment\":{ \"polarity\":0.5,\"intensity\":0.5},\n" +

                "      }\n" +
                "    }";
        try {
            List<JsonNode> topics = Converter.convertToList(mapper.readTree(topicJSON));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testMemoryGenerated(){
        OkHttpClient client = new OkHttpClient();
        MemoryComponent memoryComponent = new MemoryComponent("http://127.0.0.1:5000",client,"en");
        JsonNode result = memoryComponent.getAnswer("2","Where do you like to spend your holidays?");
        System.out.println(result.toString());

        JsonNode result2 = memoryComponent.getSelfDisclosure("2","What did you do yesterday?","color");
        System.out.println(result2.toString());

        JsonNode result3 = memoryComponent.getSelfDisclosureAndReflect("2","What is your favorite color?","color");
        System.out.println(result3.toString());

        JsonNode result4 = memoryComponent.getQuestion("2","I like the color blue.");
        System.out.println(result4.toPrettyString());
    }
}
