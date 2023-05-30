package nl.bliss.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import nl.bliss.environments.AuthenticationEnvironment;
import org.apache.commons.lang3.ArrayUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Class for using an Arduino for RFID authentication
 */
public class SerialIdentification extends SimpleIdentification {

    private AuthenticationEnvironment auth;
    private SerialPort comPort;
    private StringBuilder sb;

    //Timer
    private boolean timeOut;
    private long delay;
    private ScheduledFuture scheduledFuture;
    private TimeUnit timeUnit;

    public SerialIdentification(BaseFlipperEnvironment environment, JsonNode config){
        if(environment instanceof AuthenticationEnvironment){
            auth = (AuthenticationEnvironment) environment;
            init(config);
        }
        else{
            logger.error("No valid environment provided for SerialIdentification, needs to be of type AuthenticationEnvironment");
            System.exit(1);
        }
    }

    /**
     * Method for loading the configuration, basically
     * @param config, needs to contain the type of device for RFID (only Arduino supported)
     */
    public void init(JsonNode config){
        this.mapper = new ObjectMapper();
        this.sb = new StringBuilder();
        SerialPort[] commPorts = SerialPort.getCommPorts();
        if(commPorts.length <= 0){
            logger.error("No components connected, will exit now.");
            System.exit(1);
        }
        if(config.has("properties")){
            if(config.get("properties").has("delay")){
                this.delay = config.get("properties").get("delay").asLong();
            }
            else{
                this.delay = 3000;
                logger.warn("No delay configured, defaulting to {}",this.delay);
            }
            if(config.get("properties").has("timeunit")){
                this.timeUnit = TimeUnit.valueOf(config.get("properties").get("timeunit").asText());
            }
            else{
                this.timeUnit = TimeUnit.MILLISECONDS;
                logger.warn("No timeunit configured, defaulting to {}",this.timeUnit);
            }
            if(config.get("properties").has("port")){
                for(SerialPort cP : commPorts){
                    if(cP.getSystemPortName().contains(config.get("properties").get("port").asText())){
                        logger.info("Name: {}",cP.getDescriptivePortName());
                        logger.info("System port: {}",cP.getSystemPortName());
                        logger.info("Port description: {}",cP.getPortDescription());
                        this.comPort = cP;
                        break;
                    }
                }
                if(this.comPort == null){
                    logger.error("No match found for RFID configuration: {}",config.get("properties").get("port").asText());
                    List<SerialPort> ports = Arrays.asList(commPorts);
                    List<String> names = ports.stream().map(SerialPort::getSystemPortName).collect(Collectors.toList());
                    logger.info("Did you want to use any of these ports: {}", ArrayUtils.toString(names));
                    System.exit(1);
                }
            }
            else{
                this.comPort = SerialPort.getCommPorts()[0];
                logger.warn("No device defined, will take first occurence of serial port: {}",this.comPort);

            }
        }
        else{
            logger.error("No properties for the RFID port");
            System.exit(1);
        }
    }

    /**
     * Sets up a listener for RFID cards and sets new ones to the buffer.
     */
    public void start(){
        this.comPort.openPort();
        this.comPort.addDataListener(new SerialPortDataListener() {
            @Override
            public int getListeningEvents() { return SerialPort.LISTENING_EVENT_DATA_RECEIVED; }
            @Override
            public void serialEvent(SerialPortEvent event)
            {
                byte[] newData = event.getReceivedData();
                for (int i = 0; i < newData.length; ++i){
                    char data = (char) newData[i];
                    if(data == ('\n') ){
                        sendMessage(sb.toString());
                    }
                    else{
                        sb.append((char)newData[i]);
                    }
                }
            }
        });
        logger.info("RFID Listener started");
    }

    /**
     * Sends message through the authentication environment and resets the stringbuilder
     * @param toString, the new message
     */
    private void sendMessage(String toString) {
        try {
            JsonNode node = mapper.readTree(toString);
            if(node.has("info") && node.get("info").has("readBefore")){
                if(!node.get("info").get("readBefore").asBoolean()){
                    logger.debug("New card presented: {}",toString);
                    super.ID = toString;
                    this.auth.newCard(node);
                    resetTimeout();
                }
                else if(this.timeOut){
                    logger.debug("Resending previous card: {}",super.ID);
                    this.auth.newCard(mapper.readTree(super.ID));
                    resetTimeout();
                }
                else {
                    logger.debug("Known previous card presented too quick: {}",super.ID);
                }                
            }
            else{
                logger.error("Unknown message type: {}",toString);
            }
        } catch (IOException e) {
            logger.error("Incomplete message: {}",toString);
            e.printStackTrace();
        }
        sb.setLength(0);
    }

    /**
     * Method for resetting the scheduled timeout for reading SerialPort
     */
    private void resetTimeout() {
        this.timeOut = false;
        if(scheduledFuture != null){
            scheduledFuture.cancel(true);
        }
        this.timeOut();
    }

    /**
     * Schedules the timeout for the SerialPort read for RFID
     */
    private void timeOut() {
        ScheduledExecutorService service = Executors.newScheduledThreadPool(1);
        scheduledFuture = service.schedule((Callable<Boolean>) () -> {
            logger.debug("Timed out");
            timeOut = true;
            return true;
        },this.delay, this.timeUnit);
        service.shutdown();
    }

    /**
     * Close the port
     */
    public void stopListening(){
        logger.info("Closing authentication port now");
        this.comPort.closePort();
    }
}