package robotbrain;

import hmi.flipper2.FlipperException;
import hmi.flipper2.launcher.FlipperLauncherThread;

import java.util.Map;
import java.util.Properties;

public class FLTIsVisualizer extends FlipperLauncherThread {

    //so, your flipper object (flt) will have an templatecpontroller (tc)
// which has an information state (is) object. flt.tc.is.xxxxxx
    String isJSONString;


    public Map createMapFromJson(String isJSONString) {
        Gson gson = new Gson();
        Map map = gson.fromJson(isJSONString, Map.class);

        String jsonPath1 = "is.env.environmentSpec.environments.params.middleware.properties.amqBrokerURI";
        String jsonPath2 = "";
        String feature1 = (String) map.get(jsonPath1);
        System.out.println(feature1);
        return map;
    }

    @Override
    public void updateGUI() {
        super.updateGUI();
        System.out.println("GUIUPDATE");
        try {
            isJSONString = super.tc.is.getIs("is");

            Gson gson = new GsonBuilder().create();
            JsonObject job = gson.fromJson(isJSONString, JsonObject.class);
            JsonElement amqBrokerURI = job.getAsJsonObject("env")
                    .getAsJsonObject("environmentSpec")
                    .getAsJsonArray("environments").asList().get(0).getAsJsonObject()
                    .getAsJsonObject("params")
                    .getAsJsonObject("middleware")
                    .getAsJsonObject("properties")
                    .getAsJsonPrimitive("amqBrokerURI");


//            ----------------------------
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
            }
//            ----------------------------
//----------------------------
//
//            JsonObject umObject = componentsObject.getAsJsonObject("um");
//
//            int polarity = 0;
//            int intensity = 0;
//            if (umObject != null) {
//                JsonElement requestQueueElement = umObject.getAsJsonArray("requestQueue").get(0);
//
//                if (requestQueueElement != null) {
//                    JsonObject requestQueueObj = requestQueueElement.getAsJsonObject();
//                    JsonObject emotionObj = requestQueueObj.getAsJsonObject("emotion");
//
//                    if (emotionObj != null) {
//                        JsonObject sentimentObj = emotionObj.getAsJsonObject("sentiment");
//
//                        if (sentimentObj != null) {
//                            polarity = sentimentObj.get("polarity").getAsInt();
//                            intensity = sentimentObj.get("intensity").getAsInt();
//
//
//                        }
//                    }
//                }
//            }


// ----------------------------

            clearScreenANSI();
            System.out.println("-----------------------------------------------");
            System.out.println();
            System.out.println();
            System.out.println("Emotion: " + emotionString);
            System.out.println("Intent: " + intentString);

//            System.out.println(amqBrokerURI.toString());
//            System.out.println("Polarity: " + polarity);
//            System.out.println("Intensity: " + intensity);

//            System.out.println(isJSONString);

            System.out.println();
            System.out.println();
            System.out.println("-----------------------------------------------");

//            System.out.println(isJSONString);

        } catch (FlipperException e) {
            throw new RuntimeException(e);
        }
//        see json path from pictures on ipad
//        extract json to java map
//        if after update obj already in map do nothing else replace
//        print object see how to use ASCII escape codes to clear the console (no need for GUI)

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

    public FLTIsVisualizer(Properties ps) throws FlipperException {
        super(ps);

        Map jsonJavaRootObject = new Gson().fromJson(isJSONString, Map.class);

//        System.out.println(isJSONString);
    }
}
