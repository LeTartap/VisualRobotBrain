package nl.bliss.environments;

import java.util.Properties;

import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.environment.FlipperEnvironmentMessageJSON;
import hmi.flipper2.environment.IFlipperEnvironment;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;

public class GenericMiddlewareEnvironment extends BaseFlipperEnvironment implements MiddlewareListener {

	private static final org.slf4j.Logger logger = LoggerFactory.getLogger(GenericMiddlewareEnvironment.class.getName());

	private Middleware middleware;

	public static GenericMiddlewareEnvironment loadMiddlewareEnvironment(GenericMiddlewareEnvironment mEnv, IFlipperEnvironment[] envs) {
		for(IFlipperEnvironment env : envs){
			if(env instanceof GenericMiddlewareEnvironment){
				if(mEnv != null){
					logger.warn("Middleware environment already set to: " + mEnv.toString());
				}
				mEnv = (GenericMiddlewareEnvironment) env;
			}
			else{
				logger.warn(mEnv.getClass().getName() + "does not need " + env.toString());
			}
		}
		if(mEnv == null){
			try {
				throw new Exception("Required loader of "+mEnv.getClass().getName()+" is a GenericMiddlewareEnvironment");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return mEnv;
	}

	@Override
	public void setRequiredEnvironments(IFlipperEnvironment[] envs) throws Exception {}

	@Override
	public void init(JsonNode params) throws Exception {
		if (!params.has("middleware")) throw new Exception("middleware object required in params");
		Properties mwProperties = getGMLProperties(params.get("middleware"));
		String loaderClass = getGMLClass(params.get("middleware"));
		if (loaderClass == null || mwProperties == null) throw new Exception("Invalid middleware spec in params");
		GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwProperties);
		this.middleware = gml.load();
		this.middleware.addListener(this);
	}

	@Override
	public void receiveData(JsonNode jn) {
		logger.debug("Receiving data on {}: {}", this.getId(),jn.toString());
		enqueueMessage(jn, this.getId()+"_data");
	}

	@Override
	public FlipperEnvironmentMessageJSON onMessage(FlipperEnvironmentMessageJSON fenvmsg) throws Exception {
		switch (fenvmsg.cmd) {
			case "send":
				logger.debug("Sending data: {}", fenvmsg.params.toString());
				middleware.sendData(fenvmsg.params);
				break;
			default:
				logger.warn("{} - unhandled message: {}", this.getId(), fenvmsg.cmd);
				break;
		}
		return null;
	}

	/**
	 * Send data via the GenericMiddlewareEnvironment
	 * @param jn, the JsonNode containing the information for sending over the middleware
	 */
	public void sendData(JsonNode jn){
		this.middleware.sendData(jn);
	}

}
