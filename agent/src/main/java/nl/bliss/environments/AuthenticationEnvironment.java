package nl.bliss.environments;

import com.fasterxml.jackson.databind.JsonNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import nl.bliss.auth.GUIIdentification;
import nl.bliss.auth.SerialIdentification;
import nl.bliss.auth.SimpleIdentification;


public class AuthenticationEnvironment extends BaseFlipperEnvironment {

    private SimpleIdentification identification;

    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) {
    
        return null;
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) {
        for(IFlipperEnvironment env : envs){
            logger.warn(this.getClass().getName() + "doesn't require additional environment {}!",env.getId());
        }
    }

    @Override
    public void init(JsonNode params) {
        if(params.has("identification")){
            if(!params.get("identification").has("type")){
                logger.error("No type for identification defined!");
                System.exit(1);
            }
            else{
                if(params.get("identification").get("type").asText().equals("rfid")){
                    this.identification = new SerialIdentification(this, params.get("identification"));
                }
                else if(params.get("identification").get("type").asText().equals("gui")){
                    this.identification = new GUIIdentification();
                }
            }
            this.identification.start();
        }
        else{
            logger.error("No identification params defined.");
            System.exit(1);
        }
    }

    /**
     * Sending a message of authentication to Flipper
     * @param node, the node containing the authentication:
     *              {
     *              "type":"auth",
     *              "info":{
     *                  "piccType":"MIFARE 4KB",
     *                  "readBefore":false,
     *                  "uid":[hexadecimalID]     *
     *                  }
     *              }
     */
    public void newCard(JsonNode node){
        enqueueMessage(node, "auth");
    }

    public SimpleIdentification getIdentification(){
        return this.identification;
    }

}
