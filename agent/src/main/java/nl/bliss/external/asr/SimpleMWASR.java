package nl.bliss.external.asr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Properties;

import static hmi.flipper2.environment.BaseFlipperEnvironment.getGMLClass;
import static hmi.flipper2.environment.BaseFlipperEnvironment.getGMLProperties;

public abstract class SimpleMWASR implements MiddlewareListener {

    protected ObjectMapper mapper;
    protected Middleware middleware;
    protected String languageCode;

    protected static Logger logger = LoggerFactory.getLogger(SimpleMWASR.class.getName());
    protected volatile boolean pauseCapture;

    public SimpleMWASR(JsonNode props){
        this.mapper = new ObjectMapper();
        this.init(props);
    }

    protected void init(JsonNode params) {
        if (!params.has("middleware")) try {
            throw new Exception("middleware object required in params");
        } catch (Exception e) {
            e.printStackTrace();
        }
        Properties mwProperties = getGMLProperties(params.get("middleware"));
        String loaderClass = getGMLClass(params.get("middleware"));
        if (loaderClass == null || mwProperties == null) try {
            throw new Exception("Invalid middleware spec in params");
        } catch (Exception e) {
            e.printStackTrace();
        }
        GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwProperties);
        init(gml.load());
        // this.pauseCapture = true;
        this.pauseCapture = false;
    }

    /**
     * Initializing the middleware and adding a listener for feedback
     * @param m, the middleware to initialize and add a listener to
     */
    protected void init(Middleware m) {
        this.middleware = m;
        this.middleware.addListener(this);
    }

    protected abstract void start();

    @Override
    public void receiveData(JsonNode jsonNode) {
        if(jsonNode.has("type") && jsonNode.get("type").asText().equals("sense")){
            logger.info("Received ASR feedback: {}",jsonNode.toString());
            if(jsonNode.has("sense")){
                this.setPause(jsonNode.get("sense").asBoolean());
            }
        }
    }
    
    public synchronized void setPause(boolean sense) {
        this.pauseCapture = !sense;
    }   
    
    public synchronized boolean isPaused(){
        return this.pauseCapture;
    }

    public static void main(String[] args){
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: -file";
        String propertiesFile = "external/ASR.json";
        boolean record = false;
        SimpleMWASR sense = null;
        if (args.length == 0 || args.length % 2 != 0) {
            logger.error(help);
            System.exit(0);
        }
        for (int i = 0; i < args.length; i = i + 2) {
            if (args[i].equals("-file")) {
                propertiesFile = args[i+1];
                ObjectMapper mapper = new ObjectMapper();
                JsonNode props;
                try {
                    props = mapper.readTree(SimpleMWASR.class.getClassLoader().getResourceAsStream(propertiesFile));
                    if(props.has("interpreter") && props.get("interpreter").has("type")){
                        String type = props.get("interpreter").get("type").asText();
                        switch (type){
                            case "Spraak":
                                logger.info("Initializing ASR with Spraak");
                                sense = new SpraakMWASR(props);
                                break;
                            case "Google":
                                logger.info("Intializing ASR with Google Speech");
                                sense = new GoogleMWASR(props);
                                break;
                            default:
                                logger.error("No correct interpreter for ASR provided: {}, exit now.",type);
                                System.exit(1);
                        }
                    }
                    else{
                        logger.error("No interpreter for Act provided in {}, exit now.",propertiesFile);
                        System.exit(1);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            else {
                logger.error("Unknown commandline argument: \"{}\" {} \".\n {}", args[i], args[i + 1], help);
                System.exit(1);
            }
        }

    }
}
