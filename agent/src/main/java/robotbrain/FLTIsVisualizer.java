package robotbrain;

import hmi.flipper2.FlipperException;
import hmi.flipper2.launcher.FlipperLauncherThread;

import java.util.Map;
import java.util.Properties;

import com.google.gson.Gson;

public class FLTIsVisualizer extends FlipperLauncherThread {

//so, your flipper object (flt) will have an templatecpontroller (tc)
// which has an information state (is) object. flt.tc.is.xxxxxx
    String isJSONString;


    public Map createMapFromJson(String isJSONString){
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
//        super.updateGUI();
//        System.out.println("GUIUPDATE");
//        try {
//            isJSONString = super.tc.is.getIs("is");
//            System.out.println(isJSONString);
//            printMap(createMapFromJson(isJSONString));
//
//        } catch (FlipperException e) {
//            throw new RuntimeException(e);
//        }
//        see json path from pictures on ipad
//        extract json to java map
//        if after update obj already in map do nothing else replace
//        print object see how to use ASCII escape codes to clear the console (no need for GUI)

    }
    public void printMap(Map map){
        for (Object name: map.keySet()) {
            String key = name.toString();
            String value = map.get(name).toString();
            System.out.println(key + " " + value);
        }
    }
    public FLTIsVisualizer(Properties ps) throws FlipperException {
        super(ps);

        Map jsonJavaRootObject = new Gson().fromJson(isJSONString, Map.class);

//        System.out.println(isJSONString);
    }
}
