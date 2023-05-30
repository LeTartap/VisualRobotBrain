/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.bliss.environments;

import com.fasterxml.jackson.databind.JsonNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;

/**
 * This class takes care for sending Flipper messages for the GUI.
 * @author WaterschootJB
 */
public class GUIEnvironment extends BaseFlipperEnvironment{

    private GenericMiddlewareEnvironment mEnv;

    @Override
    public void init(JsonNode params) throws Exception {

    }


    /**
     * Message for the GUI should have the format as:
     * {
     *  "gui":
     *      [{"type": [id/gender/region/age],
     *        "value": [value] }
     *      ]
     *
     * }
     * @param fenvmsg
     * @return
     * @throws Exception
     */
    @Override
    public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
        switch(fenvmsg.cmd){
            case "updateGUI":
                logger.debug("Sending update GUI message: {}",fenvmsg.params.toString());
                if(fenvmsg.params.has("content")){
                    this.mEnv.sendData(fenvmsg.params.get("content"));
                }
                break;
            default:
                logger.error("No known command {}",fenvmsg.cmd);
        }
        return null;
    }

    @Override
    public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {
        this.mEnv = GenericMiddlewareEnvironment.loadMiddlewareEnvironment(this.mEnv, envs);
    }

}
