package robotbrain;


//import com.google.gson.*;

import com.jayway.jsonpath.JsonPath;
import hmi.flipper2.FlipperException;
import hmi.flipper2.launcher.FlipperLauncherThread;
import net.minidev.json.JSONArray;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FLTIsVisualizer extends FlipperLauncherThread {
    private ValueUpdateListener listener;
    private GUI gui;


    private Map<String, String> updatedValues;

    public FLTIsVisualizer(Properties ps) throws FlipperException {
        super(ps);
    }




    //      so, your flipper object (flt) will have an templatecpontroller (tc)
//      which has an information state (is) object. flt.tc.is.xxxxxx
    String isJSONString;

// initial idea, not in use
//    public Map createMapFromJson(String isJSONString) {
//        Gson gson = new Gson();
//        Map map = gson.fromJson(isJSONString, Map.class);
////        String jsonPath1 = "is.env.environmentSpec.environments.params.middleware.properties.amqBrokerURI";
////        String feature1 = map.get(jsonPath1).toString();
////        System.out.println(feature1);
////        return (Map) map.get(jsonPath1);
//        return null;
//    }


    public void setListener(ValueUpdateListener listener) {
        this.listener = listener;
    }
    @Override
    public void updateGUI() {
        super.updateGUI();
        System.out.println("GUIUPDATE");
        try {
            isJSONString = super.tc.is.getIs("is");

/*            -------------GSON way of extracting - this is old code.
                    using json objects was replaced by JSONPath queries
                    GSON isn't needed anymore--------------

            example for amqBrokerURI
            Gson gson = new GsonBuilder().create();
            JsonObject job = gson.fromJson(isJSONString, JsonObject.class);
            JsonElement amqBrokerURI = job.getAsJsonObject("env")
                    .getAsJsonObject("environmentSpec")
                    .getAsJsonArray("environments").asList().get(0).getAsJsonObject()
                    .getAsJsonObject("params")
                    .getAsJsonObject("middleware")
                    .getAsJsonObject("properties")
                    .getAsJsonPrimitive("amqBrokerURI");


            String emotionString = "initialEmotion";
            String intentString = "initialIntent";
            JsonObject componentsObject = job.getAsJsonObject("components");

            if (componentsObject != null) {
                JsonObject ttsObject = componentsObject.getAsJsonObject("tts");

                if (ttsObject != null) {
                    JsonObject responseObject = ttsObject.getAsJsonObject("response");

                    if (responseObject != null) {
                        JsonObject parametersObject = responseObject.getAsJsonObject("parameters");

                        if (parametersObject != null) {
                            JsonPrimitive emotionObject = parametersObject.getAsJsonPrimitive("emotion");
                            JsonPrimitive intentObject = parametersObject.getAsJsonPrimitive("intent");

                            if (emotionObject != null) {
                                emotionString = emotionObject.getAsJsonPrimitive().toString();
                                intentString = intentObject.getAsJsonPrimitive().toString();
                            }
                        }
                    }
                }
            }*/

            String jsonPathQueryTTSEmotion = "$.components.tts..emotion";
            net.minidev.json.JSONArray jsaEmotion = JsonPath.read(isJSONString, jsonPathQueryTTSEmotion);

            String jsonPathQueryTTSIntent = "$.components.tts..intent";
            net.minidev.json.JSONArray jsaIntent = JsonPath.read(isJSONString, jsonPathQueryTTSIntent);

//            old String jsonPathQuerypolarity = "$.components.um.requestQueue[0]..emotion.sentiment.polarity";
            String jsonPathQuerypolarity = "$.components.um.requestQueue..emotion.sentiment.polarity";
            net.minidev.json.JSONArray jsaPolarity = JsonPath.read(isJSONString, jsonPathQuerypolarity);

            String jsonPathQueryIntensity = "$.components.um.requestQueue..emotion.sentiment.intensity";
            net.minidev.json.JSONArray jsaIntensity = JsonPath.read(isJSONString, jsonPathQueryIntensity);

            String jsonPathQueryAnswersAsSentences = "$.components.um.requestQueue..phrases..VPS";
            net.minidev.json.JSONArray jsaQueryClauses = JsonPath.read(isJSONString, jsonPathQueryAnswersAsSentences);

            String jsonPathQueryA0AMV = "$.components.um.requestQueue..events";
            net.minidev.json.JSONArray jsaA0AMV = JsonPath.read(isJSONString, jsonPathQueryA0AMV);

            String jsonPathQueryUnderstoodByTheRobot = "$..um.requestQueue..text";
            net.minidev.json.JSONArray understoodByTheRobot = JsonPath.read(isJSONString, jsonPathQueryUnderstoodByTheRobot);

// ----------------------------

            clearScreenANSI();
            System.out.println("---------------------------------------------------------------------------------------");
            System.out.println();
            System.out.println();





            String emotionString;
            String intentString;
            String jsonQueryClausesString;
            String rawPolarity;
            String rawIntensity;
            String polarityString = "";
            String intensityString = "";


            emotionString = returnLastIfNonEmpty(jsaEmotion);
            intentString = returnLastIfNonEmpty(jsaIntent);

            System.out.println("Emotion: " + emotionString);
            System.out.println("Intent: " + intentString);

            if (jsaPolarity != null && jsaPolarity.size() > 0) {
                rawPolarity = (returnLastIfNonEmpty(jsaPolarity)).toString();
                polarityString = polarityToString(Double.valueOf(returnLastIfNonEmpty(jsaPolarity))).toString();
                System.out.println("polarity raw:  "+ rawPolarity + "polarity meaning :"+ polarityString);


            }
            if (jsaIntensity != null && jsaIntensity.size() > 0) {
                rawIntensity = (returnLastIfNonEmpty(jsaIntensity)).toString();
                intensityString = intensityToString(Double.valueOf(returnLastIfNonEmpty(jsaIntensity))).toString();
                System.out.println("raw intensity:  " + rawIntensity+ "intensity meaning :"+ intensityString);
            }

            System.out.println("---------------------------------------------------------------------------------------");


            if (jsaQueryClauses.size() > 0 && jsaQueryClauses != null) {
                jsonQueryClausesString = jsaQueryClauses.get(jsaQueryClauses.size() - 1).toString();
                System.out.println(jsonQueryClausesString);
            } else {
                jsonQueryClausesString = jsaQueryClauses.toString();
                System.out.println(jsonQueryClausesString);
            }

//            this will output and array like this:
//            [[{"verb":"be","complement":"David"}],[],[{"verb":"be","complement":"a rapper"}],[],[],[{"verb":"bring","complement":"the smell of Christmas candles"}],[],[],[{"verb":"talk"},{"verb":"talk","complement":"have fun"}],[{"verb":"aviation","complement":"airplanes"}],[{"verb":"work"},{"verb":"fly"}],[{"verb":"be","complement":"am allergic to cats"}],[]]
//            use index -1 to get the most recent one

            String jsaA0AMVString = returnLastIfNonEmpty(jsaA0AMV).toString();

            System.out.println(jsaA0AMVString);

            System.out.println("The robot understood:");

            String understoodByTheRobotString = returnLastIfNonEmpty(understoodByTheRobot).toString();

            System.out.println(understoodByTheRobotString);
            System.out.println();
            System.out.println();
            System.out.println("---------------------------------------------------------------------------------------");


            updatedValues = new HashMap<>();
            updatedValues.put("variable0", understoodByTheRobotString);
            updatedValues.put("variable1", emotionString);
            updatedValues.put("variable2", intentString);
            updatedValues.put("variable3", polarityString);
            updatedValues.put("variable4", intensityString);

            updatedValues.put("variable5", jsonQueryClausesString);
            updatedValues.put("variable6", jsaA0AMVString);


//            System.out.println(isJSONString);
            // Notify the listener with the updated values
            if (listener != null) {
                listener.onValuesUpdated(updatedValues);
            }

        } catch (FlipperException e) {
            throw new RuntimeException(e);
        }

//        see json path from pictures on ipad
//        extract json to java map
//        if after update obj already in map do nothing else replace
//        print object see how to use ASCII escape codes to clear the console (no need for GUI)

    }

    /*
     * @param jsa net.minidev.JsonArray
     * @return the last element of that JSONArray
     * */
    private static String returnLastIfNonEmpty(JSONArray jsa) {
        if (jsa.size() > 0) {
            return String.valueOf(jsa.get(jsa.size() - 1));
        } else {
            return String.valueOf((jsa));
        }
    }


    public String polarityToString(Double polarity) {
        if (polarity > 0) {
            return "positive";
        } else if (polarity < 0) {
            return "negative";
        } else {
            return "neutral";
        }
    }


    public String intensityToString(Double intensity) {
        //    |intensity| ≥ 2.5 Medium,
        //    if 1.5 ≤ |intensity| < 2.5 Weak,
        //    if 0.5 ≤ |intensity| < 1.5 Neutral,
        //    if |intensity| < 0.5
        if (intensity >= 2.5) {
            return "medium";
        } else if (intensity >= 1.5 && intensity < 2.5) {
            return "weak";
        } else if (intensity >= 0.5 && intensity < 1.5) {
            return "neutral";
        } else if (intensity < 0.5) {
            return "neutral";
        } else {
            return "neutral";
        }
    }

    public void printMap(Map map) {
        for (Object name : map.keySet()) {
            String key = name.toString();
            String value = map.get(name).toString();
            System.out.println(key + " " + value);
        }
    }

    public static void clearScreenANSI() {
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }


}
