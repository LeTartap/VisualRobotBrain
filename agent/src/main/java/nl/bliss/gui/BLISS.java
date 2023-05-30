package nl.bliss.gui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import hmi.flipper2.environment.BaseFlipperEnvironment;
import hmi.flipper2.launcher.FlipperLauncher;
import nl.bliss.util.JavaSoundRecorder;
import nl.utwente.hmi.middleware.Middleware;
import nl.utwente.hmi.middleware.MiddlewareListener;
import nl.utwente.hmi.middleware.loader.GenericMiddlewareLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.*;

import nl.bliss.util.TimeHelper;


/**
 * @author WaterschootJB
 */
public class BLISS extends javax.swing.JFrame implements MiddlewareListener {

    //Middleware
    private Middleware reverseMiddleware;
    private Middleware middleware;
    private final ObjectMapper mapper;
    private static final Logger LOGGER = LoggerFactory.getLogger(BLISS.class.getName());


    //Threads and processes
    private Thread recordThread;
    private JavaSoundRecorder recorder;
    private volatile boolean dmRunning;
    private volatile boolean asrRunning;
    private volatile boolean ttsRunning;
    private Process flipperProcess;
    private String dmConfigName;
    private Process ttsProcess;
    private String ttsConfigName;
    private Process asrProcess;
    private String asrConfigName;
    private final ReentrantLock lock = new ReentrantLock();
    private final boolean headless;

    //Configuration
    private String filename;
    private JsonNode guiProps;
    private Preferences prefs;
    private int choiceDM;
    private int choiceTTS;
    private int choiceASR;

    //Data
    private String startDate;
    private String genderValue;
    private String ageValue;
    private String regionValue;
    private String idValue;
    private String tempIdValue;
    private ZonedDateTime date;


    /**
     * Creates new form ChatWindow
     *
     * @param configuration, a String in JSON format that has the middleware properties to send the text messages to
     * @param headless,      launch the system without a gui, start recording immediately and start all components
     * @throws java.lang.Exception
     */
    public BLISS(String configuration, boolean headless) throws Exception {
        this.headless = headless;
        this.date = ZonedDateTime.now();
        this.prefs = Preferences.userRoot().node(this.getClass().getName());
        this.mapper = new ObjectMapper();
        this.choiceDM = prefs.getInt("dmDefault", 0);
        this.choiceASR = prefs.getInt("asrDefault", 0);
        this.choiceTTS = prefs.getInt("ttsDefault", 0);
        this.prefs.sync();
        this.guiProps = this.mapper.readTree(configuration);
        date = ZonedDateTime.now();
        initializeGUI(this.guiProps);
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Thread stopperThread = stopAll();
                try {
                    stopperThread.join();
                } catch (InterruptedException e) {
                }
            }
        });
        if (headless) {
            this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + TimeHelper.formatter.format(date) + ".wav";
            startHeadless();
        } else {
            initComponents();
            this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + this.getFullID() + ".wav";
            this.userInput.requestFocusInWindow();
            this.addCloseHook();
        }
        LOGGER.debug("filename:          " + this.filename);
        this.recorder = new JavaSoundRecorder(filename);
    }

    /**
     * Send a message to the ASR to start sensing (or not)
     */
    private void sendSenseMessage(boolean sense) {
        if (!this.muteUserMicrophone.isSelected()) {
            this.muteUserMicrophone.setText("Listening...");
        } else {
            this.muteUserMicrophone.setText("Talking!");
        }
        ObjectNode muteMicrophone = mapper.createObjectNode();
        muteMicrophone.put("type", "sense");
        muteMicrophone.put("sense", sense);
        this.reverseMiddleware.sendData(muteMicrophone);
    }

    /**
     * Initialize the Dialogue Manager (Flipper) with its properties.
     *
     * @param props, the properties file to initialize with
     * @return, the properties from the file.
     */
    private Properties initDM(String props) {
        String flipperPropFile;
        if (props.isEmpty()) {
            LOGGER.info("No flipper properties file found");
            flipperPropFile = "blissflipper.properties";
        } else {
            flipperPropFile = props;
        }
        Properties ps = new Properties();
        InputStream flipperPropStream = FlipperLauncher.class.getClassLoader().getResourceAsStream(flipperPropFile);
        try {
            ps.load(flipperPropStream);
        } catch (IOException ex) {
            LOGGER.error("Could not load flipper settings from {} ", flipperPropFile);
        }
        return ps;

    }

    /**
     * Expects data in the format of:
     * {
     * "interlocutor":"NAME",
     * "text":"TEXT"
     * }
     *
     * @param jsonNode, the node containg speech of interlocutors
     */
    @Override
    public void receiveData(JsonNode jsonNode) {
        LOGGER.debug("Receiving data on main GUI: {}", jsonNode.toString());
        if (jsonNode.has("interlocutor") && !this.headless) {
            this.appendToWindow(jsonNode.get("interlocutor").asText(), jsonNode.get("text").asText());
        }
        if (jsonNode.has("gui") && !this.headless) {
            JsonNode updates = jsonNode.get("gui");
            for (JsonNode update : updates) {
                switch (update.get("type").asText()) {
                    case "id":
                        this.userID.setText(update.get("value").asText());
                        this.idValue = this.userID.getText();
                        break;
                    case "age":
                        switch (update.get("value").asText()) {
                            case "18+":
                                this.age.setSelectedIndex(1);
                                break;
                            case "31+":
                                this.age.setSelectedIndex(2);
                                break;
                            case "46+":
                                this.age.setSelectedIndex(3);
                                break;
                            case "61+":
                                this.age.setSelectedIndex(4);
                                break;
                            default:
                                this.age.setSelectedIndex(0);
                                LOGGER.error("No correct age bin entered! Will leave unknown.");
                        }
                        break;
                    case "region":
                        this.region.setSelectedItem(update.get("value").asText());
                        break;
                    case "gender":
                        this.gender.setSelectedItem(update.get("value").asText());
                        break;
                    case "recording":
                        if (update.has("value")) {
                            try {
                                boolean record = update.get("value").asBoolean();
                                if (record) {
                                    if (!this.asrRunning && this.autoStartASR.isSelected()) {
                                        this.launchASR.doClick();
                                    }
                                    if (this.recording.isSelected() && !this.recorder.isRecording()) {
                                        this.date = ZonedDateTime.now();
                                        this.startDate = TimeHelper.formatter.format(this.date);
                                        startRecording();
                                    }
                                } else {
                                    if (this.asrRunning && this.autoStartASR.isSelected()) {
                                        this.launchASR.doClick();
                                    }
                                    this.idValue = null;
                                    this.userID.setText("");
                                    this.age.setSelectedIndex(0);
                                    this.region.setSelectedIndex(0);
                                    this.gender.setSelectedIndex(0);
                                    if (this.recorder.isRecording()) {
                                        stopRecording();
                                        this.muteUserMicrophone.setText("Asleep");
                                    }
                                }
                            } catch (InterruptedException ex) {
                                java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
                            }
                        }
                        break;
                    default:
                        LOGGER.error("No valid update for GUI: {}", update.get("type").asText());
                }
            }
            this.userID.setEnabled(true);
        }
        if (jsonNode.has("tts") && !this.headless) {
            if (jsonNode.get("tts").has("text")) {
                this.appendToWindow("Agent", jsonNode.get("tts").get("text").asText());
            }
            if (jsonNode.get("tts").has("type") && jsonNode.get("tts").get("type").asText().equals("feedback")) {
                if (jsonNode.get("tts").has("isTalking")) {
                    if (jsonNode.get("tts").get("isTalking").asBoolean() && !this.muteUserMicrophone.isSelected() ||
                            !jsonNode.get("tts").get("isTalking").asBoolean() && this.muteUserMicrophone.isSelected()) {
                        this.muteUserMicrophone.doClick();
                    }
                }
            }
        }
        if (jsonNode.has("type") && !this.headless) {
            if (jsonNode.get("type").asText().equals("final") && !jsonNode.get("text").asText().equals("<unk>.")) {
                this.appendToWindow("User", jsonNode.get("text").asText());
            }
        }

    }

    /**
     * Method for sending the fake speech over the Middleware
     *
     * @param text, the text to send
     */
    private void sendTypedText(String text, String type) {
        ObjectNode fakeSpeech = mapper.createObjectNode();
        fakeSpeech.put("text", text);
        fakeSpeech.put("type", type);
        fakeSpeech.put("typed", true);
        fakeSpeech.put("confidence", 1);
        fakeSpeech.put("timestamp", ZonedDateTime.now().toString());
        this.middleware.sendData(fakeSpeech);
        LOGGER.debug("Sending text: " + text);
    }

    /**
     * General method for adding text of interlocutors to the dialogue
     *
     * @param actor, the one who is speaking
     * @param text,  the text of what the interlocutor is saying.
     */
    public void appendToWindow(String actor, String text) {
        if (!this.headless) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
            ZonedDateTime currentDate = ZonedDateTime.now();
            this.conversation.append("[" + formatter.format(currentDate) + "] " + actor + ": " + text + "\n");
            this.conversation.setCaretPosition(conversation.getDocument().getLength());
        }
    }

    /**
     * Method for sending the TTS recording service a message
     *
     * @param cmd, which could be 'start' (start the recording) or 'stop' (stop the recording and save it to wav)
     */
    private void sendAgentRecordingMessage(String cmd) {
        try {
            ObjectNode params = mapper.createObjectNode();
            byte[] b = this.getFullID().getBytes("UTF-8");
            String id = new String(b, "UTF-8");
            params.put("id", id + "-a");
            ObjectNode node = mapper.createObjectNode();
            switch (cmd) {
                case "start":
                    params.put("type", "start");
                    break;
                case "stop":
                    params.put("type", "stop");
                    break;
                default:
                    LOGGER.error("Unknown command: {}", cmd);
                    break;
            }
            node.set("recording", params);
            this.middleware.sendData(node);
        } catch (UnsupportedEncodingException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Start a recorder for capturing the user input and agent output.
     */
    private void startRecording() {
        if (!this.recorder.isRecording()) {
            //this.sendAgentRecordingMessage("start");
            this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + this.getFullID() + ".wav";
            this.recorder = new JavaSoundRecorder(filename);
            recordThread = new Thread(this.recorder);
            recordThread.start();
            this.recordIndicator.setVisible(true);
            LOGGER.info("Starting recording now.");
        }
    }

    /**
     * Get the full ID of the user
     * Sets: Timestamp, ID, gender, age and region
     *
     * @return: the combined ID
     */
    private String getFullID() {
        String genderCode = this.getGenderCode(this.gender.getSelectedItem().toString());
        this.genderValue = genderCode;
        String ageCode = this.getAgeCode(this.age.getSelectedItem().toString());
        this.ageValue = ageCode;
        String regionCode = this.getRegionCode(this.region.getSelectedItem().toString());
        this.regionValue = regionCode;
        if (this.userID.getText().isEmpty()) {
            return this.tempIdValue + "_" + this.startDate + "_" + this.genderValue + "_" + this.ageValue + "_" + this.regionValue;
        } else {
            return this.userID.getText() + "_" + this.startDate + "_" + this.genderValue + "_" + this.ageValue + "_" + this.regionValue;
        }

    }


    /**
     * Updates the ID of the .wav and IS with the current user input values. Will always have a unique ID based on the timestamp.
     */
    private void updateUserInformation() {
        //updateUserID(this.idValue,"id",this.idValue);
        this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + this.getFullID() + ".wav";
    }

    /**
     * Start the DM. Updates the user ID and starts Flipper
     */
    private void startDM() {
        this.tempIdValue = TimeHelper.formatter.format(ZonedDateTime.now());
        if (this.userID.getText().isEmpty()) {
            //this.idValue = this.tempIdValue;
            this.userID.setText(this.tempIdValue);
        }
        try {
            this.dmRunning = true;
            this.startFlipper();
            LOGGER.info("Starting DM thread.");
            if (!headless) {
                this.launchDM.setText("Stop DM");
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    /**
     * Stop the DM
     * Stops Flipper and also changes the button and updates the user ID.
     */
    private void stopDM() {
        try {
            this.userID.setText("");
            if (this.recorder.isRecording()) {
                try {
                    this.stopRecording();
                } catch (InterruptedException ex) {
                    java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            this.dmRunning = false;
            this.updateUserInformation();
            LOGGER.info("Updating user before closing Flipper");
            Thread.sleep(200);
            this.stopFlipper();
            LOGGER.info("Stopping DM now.");
            if (!headless) {
                this.launchDM.setText("Start DM");
            }
        } catch (InterruptedException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
            if (this.dmRunning) {
                this.dmRunning = false;
                if (!headless) {
                    this.launchDM.setText("Start DM");
                }
            }
        }
    }

    /**
     * @param cmd,   the cmd to use
     * @param type,  the column of the field to update
     * @param value, the value for that field
     */
    private void updateUserID(String cmd, String type, String value) {
        if (!this.dmRunning || idValue == null) {
            if (!this.dmRunning) {
                LOGGER.debug("The DM is not running yet. No update performed yet.");
            }
            if (idValue == null) {
                LOGGER.debug("No valid ID has been set. Will not update");
            }
        } else if (value.equals("null")) {
            LOGGER.debug("No valid value for {}", type);
        } else {
            LOGGER.debug("Command {} for {} with {} = {}", cmd, this.idValue, type, value);

            JsonNode message = mapper.createObjectNode()
                    .put("id", this.idValue)
                    .put("cmd", cmd)
                    .put("type", type)
                    .put("value", value);
            middleware.sendData(message);
        }
    }

    /**
     * Check if a user exists in the database
     *
     * @param id, the id to check against.
     */
    private void hasUserID(String id) {
        LOGGER.info("Checking for existing user {}", id);
        JsonNode message = mapper.createObjectNode().put("id", id)
                .put("cmd", "has");
        this.middleware.sendData(message);
    }

    /**
     * Retrieve user ID from the database
     *
     * @param id, the user ID to retrieve
     */
    private void getUserID(String id) {
        LOGGER.info("Checking for existing user {}", id);
        JsonNode message = mapper.createObjectNode().put("id", id)
                .put("cmd", "get");
        //this.umMiddleware.sendData(message);
        this.middleware.sendData(message);
    }

    /**
     * Retrieve the age code from a file to put in the ID
     *
     * @param ageString, the age bin of the user
     * @return, the key representing that bin.
     */
    private String getAgeCode(String ageString) {
        try {
            InputStream ageFile = BLISS.class.getClassLoader().getResourceAsStream("external/age.json");
            JsonNode regionArray = this.mapper.readTree(ageFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (value.equals(ageString)) {
                    return key;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    /**
     * Transforming the code back to the string
     *
     * @param code, the code to transform
     * @return the string representation
     */
    private String fromAgeCode(String code) {
        try {
            InputStream ageFile = BLISS.class.getClassLoader().getResourceAsStream("external/age.json");
            JsonNode regionArray = this.mapper.readTree(ageFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (key.equals(code)) {
                    return value;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    private String getGenderCode(String genderString) {
        try {
            InputStream genderFile = BLISS.class.getClassLoader().getResourceAsStream("external/gender.json");
            JsonNode regionArray = this.mapper.readTree(genderFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (value.equals(genderString)) {
                    return key;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    private String fromGenderCode(String code) {
        try {
            InputStream genderFile = BLISS.class.getClassLoader().getResourceAsStream("external/gender.json");
            JsonNode regionArray = this.mapper.readTree(genderFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (key.equals(code)) {
                    return value;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    private void startFlipper() throws IOException {
        LOGGER.info("Starting Flipper....");
        this.addUserID.setEnabled(true);
        this.userID.setEnabled(true);
        if ((flipperProcess == null || !flipperProcess.isAlive())) {
            flipperProcess = startFlipperProcess("nl.bliss.Main");
        }
    }

    private void startTTS() throws IOException {
        LOGGER.info("Starting TTS....");
        if ((ttsProcess == null || !ttsProcess.isAlive())) {
            ttsProcess = startTTSProcess("nl.bliss.external.tts.SimpleMWTTS");
        }
    }

    private void startASR() throws IOException {
        LOGGER.info("Starting ASR....");
        if ((asrProcess == null || !asrProcess.isAlive())) {
            asrProcess = startASRProcess("nl.bliss.external.asr.SimpleMWASR");
        }
        if (!this.ttsRunning) {
            try {
                Thread.sleep(2000);
                this.sendSenseMessage(true);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    /**
     * Stops any currently running UT processes
     *
     * @return the Thread to stop all processes.
     */
    public Thread stopAll() {
        Thread stopperThread;
        stopperThread = new Thread(() -> {
            lock.lock();
            System.out.println("Stopping all modules....");
            try {
                stopTTS();
                stopASR();
                stopFlipper();
                LOGGER.info("Stopped");
            } catch (InterruptedException e) {
            } finally {
                lock.unlock();
            }
        });

        stopperThread.start();
        return stopperThread;
    }

    private Process startFlipperProcess(String main) throws IOException {
        // java -cp "build/classes/:lib/*:resource" -Djava.library.path=lib
        List<String> args = new ArrayList<>();
        args.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        args.add("-cp");
        args.add("\"build/classes" + File.pathSeparator + System.getProperty("java.class.path") + "\"");
        args.add("-Dlogback.configurationFile=logback.xml");
        args.add("-Djava.library.path=lib");
        args.add(main);
        args.add("-config");
        if (this.dmConfigName == null) {
            String defaultDM = "data" + File.separator + "dm" + File.separator + "blissflipper.properties";
            this.dmConfigName = defaultDM;
            LOGGER.warn("Defaulting DM now to {}", defaultDM);
        }
        args.add("data" + File.separator + "dm" + File.separator + this.dmConfigName);
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
        return pb.start();
    }

    private Process startASRProcess(String main) throws IOException {
        // java -cp "build/classes/:lib/*:resource" -Djava.library.path=lib
        List<String> args = new ArrayList<>();
        args.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        args.add("-cp");
        args.add("\"build/classes" + File.pathSeparator + System.getProperty("java.class.path") + "\"");
        args.add("-Dlogback.configurationFile=logback.xml");
        args.add("-Djava.library.path=lib");
        args.add(main);
        args.add("-file");
        if (this.asrConfigName == null) {
            String defaultASR = "ASR.json";
            this.asrConfigName = defaultASR;
            LOGGER.warn("Defaulting ASR n ow to {}", defaultASR);
        }
        args.add("external" + File.separator + this.asrConfigName);
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
        return pb.start();
    }

    private Process startTTSProcess(String main) throws IOException {
        // java -cp "build/classes/:lib/*:resource" -Djava.library.path=lib
        List<String> args = new ArrayList<>();
        args.add(System.getProperty("java.home") + File.separator + "bin" + File.separator + "java");
        args.add("-cp");
        args.add("\"build/classes" + File.pathSeparator + System.getProperty("java.class.path") + "\"");
        args.add("-Dlogback.configurationFile=logback.xml");
        args.add("-Djava.library.path=lib");
        args.add(main);
        args.add("-file");
        if (this.ttsConfigName == null) {
            String defaultTTS = "TTS.json";
            this.ttsConfigName = defaultTTS;
            LOGGER.warn("Defaulting TTS now to {}", defaultTTS);
        }
        args.add("external" + File.separator + this.ttsConfigName);
        args.add("-record");
        args.add(String.valueOf(this.recording.isSelected()));
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.inheritIO();
        return pb.start();
    }

    private void stopRecording() throws InterruptedException {
        if (this.recorder.isRecording()) {
            //this.sendAgentRecordingMessage("stop");
            LOGGER.warn("Attempting to stop recording gracefully....");
            this.recorder.finish();
            if (this.recordThread.isAlive()) {
                LOGGER.warn("Attempting to stop recording forcefully....");
                this.recorder.finish();
                this.recordIndicator.setVisible(false);
            }
            this.recordIndicator.setVisible(false);
        }
    }


    private void stopFlipper() throws InterruptedException {
        if (flipperProcess != null && flipperProcess.isAlive()) {
            this.addUserID.setEnabled(true);
            this.userID.setEnabled(true);
            LOGGER.info("Attempting to stop Flipper gracefully....");
            flipperProcess.destroy();
            Thread.sleep(500);
            if (flipperProcess.isAlive()) {
                LOGGER.warn("Attempting to stop Flipper forcefully....");
                flipperProcess.destroyForcibly();
                flipperProcess = null;
                Thread.sleep(1000);
            }
        }
    }

    private void stopASR() throws InterruptedException {
        if (asrProcess != null && asrProcess.isAlive()) {
            LOGGER.info("Attempting to stop ASR gracefully....");
            asrProcess.destroy();
            Thread.sleep(500);
            if (asrProcess.isAlive()) {
                LOGGER.warn("Attempting to stop ASR forcefully....");
                asrProcess.destroyForcibly();
                asrProcess = null;
                Thread.sleep(1000);
            }
        }
        this.muteUserMicrophone.setText("Asleep");
    }

    private void stopTTS() throws InterruptedException {
        //stopRecording();
        Thread.sleep(100);
        if (ttsProcess != null && ttsProcess.isAlive()) {
            LOGGER.info("Attempting to stop TTS gracefully....");
            ttsProcess.destroy();
            Thread.sleep(500);
            if (ttsProcess.isAlive()) {
                LOGGER.warn("Attempting to stop TTS forcefully....");
                ttsProcess.destroyForcibly();
                ttsProcess = null;
                Thread.sleep(1000);
            }
        }
        if (this.asrRunning) {
            this.sendSenseMessage(true);
        }
    }

    public Thread startAll() {
        Thread starterThread = new Thread(() -> {
            lock.lock();
            System.out.println("Starting all modules...");
            try {
                startASR();
                Thread.sleep(500);
                startTTS();
                Thread.sleep(500);
                startFlipper();
                Thread.sleep(500);
                LOGGER.info("");

            } catch (IOException | InterruptedException e) {
            } finally {
                lock.unlock();
            }
        });
        starterThread.start();
        return starterThread;
    }

    /**
     * @param args the command line arguments
     * @throws java.io.IOException
     */
    public static void main(String args[]) throws IOException, Exception {

        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html
         */


        //</editor-fold>

        //System.out.println("Main args: " + Arrays.toString(args));
        final String mw = determineMW(args);
        if (determineHead(args)) {
            Runnable r = () -> {
                try {
                    new BLISS(mw, true);
                } catch (Exception e) {
                }
            };
            Thread t = new Thread(r);
            t.start();
        } else {
            /* Create and display the form */
            java.awt.EventQueue.invokeLater(() -> {
                try {
                    new BLISS(mw, false);
                } catch (Exception e) {
                }
            });
            try {
                for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                    if ("Nimbus".equals(info.getName())) {
                        javax.swing.UIManager.setLookAndFeel(info.getClassName());
                        break;
                    } else {
                        javax.swing.UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                        break;
                    }
                }
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException |
                     javax.swing.UnsupportedLookAndFeelException ex) {
                java.util.logging.Logger.getLogger(BLISS.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
            }
        }


    }

    private static boolean determineHead(String[] args) {
        for (int i = 0; i < args.length; i = i + 2) {
            if (args[0].equals("-headless") || args[0].equals("-H")) {
                return Boolean.parseBoolean(args[1]);
            }
        }
        return false;
    }

    private static String determineMW(String[] args) throws IOException {
        String properties;
        String help = "Expecting commandline arguments in the form of \"-<argname> <arg>\".\nAccepting the following argnames: -file or -mw";
        String mw = "{\n" +
                "    \"Flipper\":{\n" +
                "        \"middleware\": {\n" +
                "            \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                "            \"properties\": {\n" +
                "                \"iTopic\": \"BLISS/in\",\n" +
                "                \"oTopic\": \"BLISS/out\",\n" +
                "                \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                "            }\n" +
                "        }\n" +
                "    },\n" +
                "    \"GUI\":{\n" +
                "        \"middleware\":{\n" +
                "            \"loaderClass\": \"nl.utwente.hmi.middleware.activemq.ActiveMQMiddlewareLoader\",\n" +
                "            \"properties\": {\n" +
                "                \"iTopic\": \"BLISS/out\",\n" +
                "                \"oTopic\": \"BLISS/in\",\n" +
                "                \"amqBrokerURI\": \"tcp://localhost:61616\"\n" +
                "            }\n" +
                "        }    \n" +
                "    }\n" +
                "    \n" +
                "}";
        for (int i = 0; i < args.length; i = i + 2) {
            if (args[i].equals("-file")) {
                properties = args[i + 1];
                ObjectMapper mapper = new ObjectMapper();
                JsonNode props = mapper.readTree(BLISS.class.getClassLoader().getResourceAsStream(properties));
                mw = props.toString();
                return mw;
            }
            if (args[i].equals("-mw")) {
                mw = args[i + 1];
                return mw;
            } else {
                LOGGER.error(help);
            }
        }
        LOGGER.warn("No middleware argument found, will use default values.");
        return mw;
    }

//    private void updateUserID(String text) {
//        LOGGER.info("Sending new userID");
//        this.umMiddleware.sendData(mapper.createObjectNode().put("id",text));
//    }

    /**
     * Initialize all the middleware components
     *
     * @param mwProps
     * @throws Exception
     */
    private void initializeGUI(JsonNode mwProps) throws Exception {
        if (mwProps.has("Flipper")) {
            JsonNode component = mwProps.get("Flipper");
            Properties mwProperties = BaseFlipperEnvironment.getGMLProperties(component.get("middleware"));
            String loaderClass = BaseFlipperEnvironment.getGMLClass(component.get("middleware"));
            if (loaderClass != null && mwProperties != null) {
                GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwProperties);
                this.initFlipperMW(gml.load());
            } else {
                throw new Exception("Invalid middleware spec in params");
            }
        }
        if (mwProps.has("GUI")) {
            JsonNode component = mwProps.get("GUI");
            Properties mwProperties = BaseFlipperEnvironment.getGMLProperties(component.get("middleware"));
            String loaderClass = BaseFlipperEnvironment.getGMLClass(component.get("middleware"));
            if (loaderClass != null && mwProperties != null) {
                GenericMiddlewareLoader gml = new GenericMiddlewareLoader(loaderClass, mwProperties);
                this.initGUIMW(gml.load());
            } else {
                throw new Exception("Invalid middleware spec in params");
            }
        }
    }

    private String getRegionCode(String regionString) {
        try {
            InputStream regionFile = BLISS.class.getClassLoader().getResourceAsStream("external/regions.json");
            JsonNode regionArray = this.mapper.readTree(regionFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (value.equals(regionString)) {
                    return key;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }

    private String fromRegionCode(String code) {
        try {
            InputStream regionFile = BLISS.class.getClassLoader().getResourceAsStream("external/regions.json");
            JsonNode regionArray = this.mapper.readTree(regionFile);
            Iterator<JsonNode> values = regionArray.iterator();
            while (values.hasNext()) {
                JsonNode node = values.next();
                String key = node.fieldNames().next();
                String value = node.get(key).asText();
                if (key.equals(code)) {
                    return value;
                }
            }
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
        return "";
    }


    private void startHeadless() {
        LOGGER.info("Starting headless mode.");
        this.setVisible(false);
//        this.recorder.start();
        this.startAll();
    }

    private void initFlipperMW(Middleware load) {
        this.reverseMiddleware = load;
        this.reverseMiddleware.addListener(this);
    }

    private void addCloseHook() {
        this.setVisible(true);
        this.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                new Thread(() -> {
                    Thread stopperThread = stopAll();
                    try {
                        stopperThread.join();
                    } catch (InterruptedException e) {
                    }
                    System.exit(0);
                }).start();
            }
        });
    }

    private void initGUIMW(Middleware load) {
        this.middleware = load;
        this.middleware.addListener(this);
    }


    /**
     * Reset the TTS/TTS with this command if stuck.
     */
    public void resetTTS() {
        ObjectNode root;
        root = mapper.createObjectNode();
        ObjectNode feedback = mapper.createObjectNode()
                .put("isTalking", false)
                .put("type", "feedback")
                .put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
        root.set("tts", feedback);
        this.middleware.sendData(root);
    }


    /**
     * Method for sending an authentication message with the ID value as the hex/UID value.
     */
    private void sendAuthMessage() {
        ObjectNode info = mapper.createObjectNode()
                .put("piccType", "GUI")
                .put("readBefore", this.userID.getText().equals(this.idValue))
                .put("uid", this.userID.getText());
        JsonNode id = mapper.createObjectNode()
                .put("type", "auth")
                .set("info", info);
        this.middleware.sendData(id);
        this.sendUserDemographics();
    }

    private void sendUserDemographics() {
        if (!this.ageValue.equals("Unknown")) {
            this.updateUserID("update", "age", this.ageValue);
        }
        if (!this.genderValue.equals("Unknown")) {
            this.updateUserID("update", "gender", this.genderValue);
        }
        if (!this.regionValue.equals("Regio onbekend")) {
            this.updateUserID("update", "region", this.regionValue);
        }
    }

    public DefaultComboBoxModel initConfig(String type, int comboIndex) {
        DefaultComboBoxModel configBox;
        try {
            String[] configs;
            FilenameFilter filter;
            File f;
            URL configPath;
            switch (type) {
                case "DM":
                    configPath = this.getClass().getClassLoader().getResource("data/dm");
                    filter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.endsWith(".properties");
                        }
                    };
                    f = new File(configPath.toURI());
                    configs = f.list(filter);
                    if (this.choiceDM >= configs.length) {
                        this.choiceDM = 0;
                        comboIndex = 0;
                    }
                    configBox = new DefaultComboBoxModel<>(configs);
                    configBox.setSelectedItem(configBox.getElementAt(comboIndex));
                    this.dmConfigName = configBox.getSelectedItem().toString();
                    break;
                case "ASR":
                    configPath = BLISS.class.getClassLoader().getResource("external");
                    filter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.startsWith(type) && name.endsWith(".json");
                        }
                    };
                    f = new File(configPath.toURI());
                    configs = f.list(filter);
                    if (this.choiceASR >= configs.length) {
                        this.choiceASR = 0;
                        comboIndex = 0;
                    }
                    configBox = new DefaultComboBoxModel<>(configs);
                    configBox.setSelectedItem(configBox.getElementAt(comboIndex));
                    this.asrConfigName = configBox.getSelectedItem().toString();
                    break;
                case "TTS":
                    configPath = BLISS.class.getClassLoader().getResource("external");
                    filter = new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            return name.startsWith(type) && name.endsWith(".json");
                        }
                    };
                    f = new File(configPath.toURI());
                    configs = f.list(filter);
                    if (this.choiceTTS >= configs.length) {
                        this.choiceTTS = 0;
                        comboIndex = 0;
                    }
                    configBox = new DefaultComboBoxModel<>(configs);
                    configBox.setSelectedItem(configBox.getElementAt(comboIndex));
                    this.ttsConfigName = configBox.getSelectedItem().toString();
                    break;
                default:
                    LOGGER.error("Cannot set config for {}", type);
                    return null;
            }
            return configBox;
        } catch (URISyntaxException ex) {
            LOGGER.error(ex.toString());
        }
        return new DefaultComboBoxModel<>();
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        fileChooser = new javax.swing.JFileChooser();
        jSeparator3 = new javax.swing.JSeparator();
        jPanel1 = new javax.swing.JPanel();
        sendTypedText = new javax.swing.JButton();
        userInput = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        conversation = new javax.swing.JTextArea();
        restart = new javax.swing.JButton();
        launchASR = new javax.swing.JToggleButton();
        launchTTS = new javax.swing.JToggleButton();
        launchDM = new javax.swing.JToggleButton();
        userID = new javax.swing.JTextField();
        userIDLabel = new javax.swing.JLabel();
        ageLabel = new javax.swing.JLabel();
        regionLabel = new javax.swing.JLabel();
        genderLabel = new javax.swing.JLabel();
        region = new javax.swing.JComboBox<>();
        gender = new javax.swing.JComboBox<>();
        age = new javax.swing.JComboBox<>();
        startAll = new javax.swing.JToggleButton();
        recording = new javax.swing.JCheckBox();
        userInformation = new javax.swing.JLabel();
        controlInfo = new javax.swing.JLabel();
        jSeparator1 = new javax.swing.JSeparator();
        recordIndicator = new javax.swing.JLabel();
        this.recordIndicator.setVisible(false);
        recordActiveLabel = new javax.swing.JLabel();
        clear = new javax.swing.JButton();
        addUserID = new javax.swing.JButton();
        configLabel = new javax.swing.JLabel();
        asrConfig = new javax.swing.JComboBox<>();
        ttsConfig = new javax.swing.JComboBox<>();
        dmConfig = new javax.swing.JComboBox<>();
        asrLabel = new javax.swing.JLabel();
        ttsLabel = new javax.swing.JLabel();
        dmLabel = new javax.swing.JLabel();
        jSeparator2 = new javax.swing.JSeparator();
        agentLabel = new javax.swing.JLabel();
        agentTextField = new javax.swing.JTextField();
        sendAgentText = new javax.swing.JButton();
        agentUtterance = new javax.swing.JLabel();
        muteUserMicrophone = new javax.swing.JToggleButton();
        statusAgent = new javax.swing.JLabel();
        autoStartASRLabel = new javax.swing.JLabel();
        autoStartASR = new javax.swing.JCheckBox();
        jMenuBar1 = new javax.swing.JMenuBar();
        jMenu1 = new javax.swing.JMenu();
        Save = new javax.swing.JMenuItem();
        Exit = new javax.swing.JMenuItem();

        fileChooser.setDialogType(javax.swing.JFileChooser.SAVE_DIALOG);
        fileChooser.setApproveButtonText("Save");
        fileChooser.setApproveButtonToolTipText("");
        fileChooser.setDialogTitle("Specify a file to save");

        setDefaultCloseOperation(javax.swing.WindowConstants.DO_NOTHING_ON_CLOSE);
        setTitle("Dialogue");
        setIconImage(Toolkit.getDefaultToolkit().getImage(BLISS.class.getResource("/data/blissLogo.png")));

        sendTypedText.setText("Send");
        sendTypedText.setToolTipText("Send the message!");
        sendTypedText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendTypedTextActionPerformed(evt);
            }
        });

        userInput.setToolTipText("Type here text to send to the ASR");
        userInput.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                userInputActionPerformed(evt);
            }
        });
        userInput.addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                userInputKeyPressed(evt);
            }
        });

        this.startDate = TimeHelper.formatter.format(date);
        this.conversation.append(this.startDate + "\n");
        conversation.setEditable(false);
        conversation.setColumns(20);
        conversation.setLineWrap(true);
        conversation.setRows(5);
        conversation.setWrapStyleWord(true);
        jScrollPane1.setViewportView(conversation);

        restart.setText("Restart");
        restart.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                restartActionPerformed(evt);
            }
        });

        launchASR.setText("Start ASR");
        launchASR.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                launchASRItemStateChanged(evt);
            }
        });

        launchTTS.setText("Start TTS");
        launchTTS.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                launchTTSItemStateChanged(evt);
            }
        });

        launchDM.setText("Start DM");
        launchDM.setToolTipText("");
        launchDM.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                launchDMItemStateChanged(evt);
            }
        });

        userIDLabel.setText("ID");

        ageLabel.setText("Age");

        regionLabel.setText("Region");

        genderLabel.setText("Gender");

        region.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"Regio onbekend", "Achterhoek", "Overijssel", "Drenthe", "Groningen", "Friesland", "Noord-Brabant", "Limburg (Nederland)", "Zuid-Holland, excl. Goeree Overflakkee", "Noord-Holland, excl. West Friesland", "West Utrecht, incl. de stad Utrecht", "Oost Utrecht, excl. de stad Utrecht", "Zeeland, incl. Goeree Overflakkee en Zeeuws-Vlaanderen", "Gelders rivierengebied, incl. Arnhem en Nijmegen", "Veluwe tot aan de IJssel", "West Friesland", "Polders", "Nederland -overig", "Antwerpen en Vlaams-Brabant", "Oost-Vlaanderen", "West-Vlaanderen", "Limburg (Vlaanderen)", "Wallonië", "Vlaanderen -overig", "Regio buiten Nederland en Vlaanderen"}));
        region.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                regionActionPerformed(evt);
            }
        });

        gender.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"Unknown", "Rather not say", "Man", "Vrouw"}));
        gender.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                genderActionPerformed(evt);
            }
        });

        age.setModel(new javax.swing.DefaultComboBoxModel<>(new String[]{"Unknown", "Rather not say", "18 -<= 30", "31 -<= 45", "46 -<= 60", "61 -<= 110"}));
        age.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ageActionPerformed(evt);
            }
        });

        startAll.setText("Start All");
        startAll.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                startAllItemStateChanged(evt);
            }
        });

        recording.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        recording.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        userInformation.setText("User Information");

        controlInfo.setText("Control");

        recordIndicator.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        recordIndicator.setIcon(new javax.swing.ImageIcon(getClass().getResource("/data/record.png"))); // NOI18N
        recordIndicator.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);

        recordActiveLabel.setText("Record");

        clear.setText("Clear");
        clear.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                clearActionPerformed(evt);
            }
        });

        addUserID.setText("Set");
        addUserID.setToolTipText("");
        addUserID.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addUserIDActionPerformed(evt);
            }
        });

        configLabel.setText("Configuration");

        asrConfig.setModel(initConfig("ASR", this.choiceASR));
        asrConfig.setSelectedIndex(this.choiceASR);
        asrConfig.setToolTipText("");
        asrConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                asrConfigActionPerformed(evt);
            }
        });

        ttsConfig.setModel(initConfig("TTS", this.choiceTTS));
        ttsConfig.setSelectedIndex(this.choiceTTS);
        ttsConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ttsConfigActionPerformed(evt);
            }
        });

        dmConfig.setModel(initConfig("DM", this.choiceDM));
        dmConfig.setSelectedIndex(this.choiceDM
        );
        dmConfig.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                dmConfigActionPerformed(evt);
            }
        });

        asrLabel.setText("ASR");

        ttsLabel.setText("TTS");

        dmLabel.setText("DM");

        agentLabel.setText("Agent");

        agentTextField.setText("Enter agent text to say");
        agentTextField.setToolTipText("Enter text you want to agent to say.");
        agentTextField.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                agentTextFieldActionPerformed(evt);
            }
        });

        sendAgentText.setText("Send");
        sendAgentText.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                sendAgentTextActionPerformed(evt);
            }
        });

        agentUtterance.setText("Text");

        muteUserMicrophone.setText("Asleep");
        muteUserMicrophone.setFocusable(false);
        muteUserMicrophone.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                muteUserMicrophoneActionPerformed(evt);
            }
        });

        statusAgent.setText("Status");

        autoStartASRLabel.setText("Autostart ASR?");

        autoStartASR.setSelected(true);
        autoStartASR.setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
        autoStartASR.setHorizontalTextPosition(javax.swing.SwingConstants.LEFT);

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(userInput)
                                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 564, javax.swing.GroupLayout.PREFERRED_SIZE))
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGap(116, 116, 116)
                                                .addComponent(controlInfo)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                .addComponent(configLabel)
                                                .addGap(54, 54, 54))
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                .addGap(155, 155, 155)
                                                                .addComponent(userInformation))
                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(sendTypedText, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addGap(24, 24, 24)
                                                                .addComponent(recordActiveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 42, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                .addComponent(recording)
                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(agentLabel)
                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                .addComponent(recordIndicator, javax.swing.GroupLayout.PREFERRED_SIZE, 28, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                .addComponent(autoStartASRLabel)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                                .addComponent(autoStartASR))))
                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                .addGap(18, 18, 18)
                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                        .addComponent(jSeparator2)
                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addComponent(userIDLabel)
                                                                                        .addComponent(ageLabel)
                                                                                        .addComponent(regionLabel)
                                                                                        .addComponent(genderLabel)
                                                                                        .addComponent(asrLabel)
                                                                                        .addComponent(ttsLabel)
                                                                                        .addComponent(dmLabel))
                                                                                .addGap(18, 18, 18)
                                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                                        .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                                                .addComponent(age, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                .addComponent(region, javax.swing.GroupLayout.PREFERRED_SIZE, 241, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                                .addComponent(gender, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                                                                        .addComponent(userID)
                                                                                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                                                                        .addComponent(addUserID, javax.swing.GroupLayout.PREFERRED_SIZE, 84, javax.swing.GroupLayout.PREFERRED_SIZE)))
                                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING, false)
                                                                                                        .addComponent(restart, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                        .addComponent(clear, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                        .addComponent(startAll, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                        .addComponent(launchDM, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                        .addComponent(launchTTS, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                                                                                        .addComponent(launchASR, javax.swing.GroupLayout.Alignment.LEADING, javax.swing.GroupLayout.DEFAULT_SIZE, 111, Short.MAX_VALUE))
                                                                                                .addGap(37, 37, 37)
                                                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                                                                                                        .addComponent(ttsConfig, 0, 104, Short.MAX_VALUE)
                                                                                                        .addComponent(dmConfig, 0, 104, Short.MAX_VALUE)
                                                                                                        .addComponent(asrConfig, 0, 104, Short.MAX_VALUE)))))
                                                                        .addComponent(jSeparator1, javax.swing.GroupLayout.DEFAULT_SIZE, 305, Short.MAX_VALUE)))
                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                .addGap(16, 16, 16)
                                                                                .addComponent(agentUtterance))
                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                .addGap(10, 10, 10)
                                                                                .addComponent(statusAgent)))
                                                                .addGap(44, 44, 44)
                                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                                                        .addComponent(muteUserMicrophone, javax.swing.GroupLayout.PREFERRED_SIZE, 104, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                                                .addComponent(agentTextField, javax.swing.GroupLayout.PREFERRED_SIZE, 159, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                                                .addComponent(sendAgentText, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE)))))
                                                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel1Layout.setVerticalGroup(
                jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                .addContainerGap()
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(jScrollPane1)
                                        .addGroup(jPanel1Layout.createSequentialGroup()
                                                .addComponent(userInformation)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(userIDLabel)
                                                        .addComponent(userID, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(addUserID))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(age, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(ageLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 14, javax.swing.GroupLayout.PREFERRED_SIZE))
                                                .addGap(12, 12, 12)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(region, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(regionLabel))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(gender, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(genderLabel))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(jSeparator1, javax.swing.GroupLayout.PREFERRED_SIZE, 9, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(controlInfo)
                                                        .addComponent(configLabel))
                                                .addGap(11, 11, 11)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(launchASR)
                                                        .addComponent(asrConfig, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(asrLabel))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(launchTTS)
                                                        .addComponent(ttsConfig, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(ttsLabel))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(launchDM)
                                                        .addComponent(dmConfig, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(dmLabel))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(startAll)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(clear)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(restart)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addComponent(jSeparator2, javax.swing.GroupLayout.PREFERRED_SIZE, 5, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                                .addComponent(agentLabel)
                                                .addGap(3, 3, 3)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(agentTextField, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(sendAgentText)
                                                        .addComponent(agentUtterance))
                                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(muteUserMicrophone)
                                                        .addComponent(statusAgent))
                                                .addGap(0, 35, Short.MAX_VALUE)))
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                        .addComponent(recording, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addComponent(recordIndicator, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                        .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel1Layout.createSequentialGroup()
                                                .addGap(0, 0, Short.MAX_VALUE)
                                                .addGroup(jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                                                        .addComponent(userInput, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(sendTypedText, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(recordActiveLabel, javax.swing.GroupLayout.PREFERRED_SIZE, 32, javax.swing.GroupLayout.PREFERRED_SIZE)
                                                        .addComponent(autoStartASRLabel)))
                                        .addComponent(autoStartASR, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                                .addGap(15, 15, 15))
        );

        jMenu1.setText("File");

        Save.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_S, java.awt.event.InputEvent.CTRL_DOWN_MASK));
        Save.setMnemonic('S');
        Save.setText("Save as...");
        Save.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                SaveActionPerformed(evt);
            }
        });
        jMenu1.add(Save);

        Exit.setAccelerator(javax.swing.KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_F4, java.awt.event.InputEvent.ALT_DOWN_MASK));
        Exit.setText(" Exit");
        Exit.setToolTipText("Exit the program");
        Exit.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                ExitActionPerformed(evt);
            }
        });
        jMenu1.add(Exit);

        jMenuBar1.add(jMenu1);

        setJMenuBar(jMenuBar1);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );
        layout.setVerticalGroup(
                layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                        .addComponent(jPanel1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void SaveActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_SaveActionPerformed
        int returnVal = this.fileChooser.showSaveDialog(this);
        if (returnVal == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            try {
                try (FileWriter fileWriter = new FileWriter(file.getAbsolutePath())) {
                    fileWriter.append(this.conversation.getText());
                    System.out.println("Wrote text!");
                }
            } catch (IOException ex) {
                System.out.println("Did not save file.");
            }
        } else {
            System.out.println("File access cancelled by user");
        }
    }//GEN-LAST:event_SaveActionPerformed

    /**
     * When the exit button is pressed, stop all processes and then exit the program
     *
     * @param evt
     */
    private void ExitActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ExitActionPerformed
        this.stopAll().start();
        System.exit(0);
    }//GEN-LAST:event_ExitActionPerformed

    private void changeIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_changeIDActionPerformed
        if (this.userID.getText().isEmpty()) {
            LOGGER.info("ID empty! Will not change.");
        } else {
            LOGGER.info("ID is not empty: {}", this.userID.getText());
            this.updateUserID("update", "id", this.userID.getText());
            this.sendAuthMessage();
            this.idValue = this.userID.getText();
        }
    }//GEN-LAST:event_changeIDActionPerformed

    private void addUserIDActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addUserIDActionPerformed
        if (this.userID.getText().isEmpty()) {
            LOGGER.info("ID empty! Will not add.");
        } else {
            LOGGER.info("ID is not empty: {}", this.userID.getText());
            //this.addUserID(this.userID.getText());
            this.idValue = this.userID.getText();
            this.sendAuthMessage();
        }
    }//GEN-LAST:event_addUserIDActionPerformed

    private void clearActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_clearActionPerformed
        this.conversation.setText("");
        date = ZonedDateTime.now();
        this.startDate = TimeHelper.formatter.format(date);
        String fullID = this.getFullID();
        this.tempIdValue = this.startDate;
        this.idValue = null;
        //        this.updateUserID(this.idValue, "id", this.idValue);
        this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + fullID + ".wav";
        this.conversation.append(this.startDate + "\n");
    }//GEN-LAST:event_clearActionPerformed

    /**
     * When the 'StartAll' button is pressed, all other buttons also receive a click.
     * It starts ASR, DM and TTS.
     * When recording is selected, it will also start the recording.
     *
     * @param evt
     */
    private void startAllItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_startAllItemStateChanged
        if (this.startAll.isSelected()) {
            if (!this.ttsRunning) {
                this.launchTTS.doClick();
            }
            if (!this.asrRunning && !this.autoStartASR.isSelected()) {
                this.launchASR.doClick();
            }
            if (!this.dmRunning) {
                this.launchDM.doClick();
            }
            this.startAll.setText("Stop all");
        } else {
            if (this.ttsRunning) {
                this.launchTTS.doClick();
            }
            if (this.asrRunning && !this.autoStartASR.isSelected()) {
                this.launchASR.doClick();
            }
            if (this.dmRunning) {
                this.launchDM.doClick();
            }
            this.startAll.setText("Start all");
        }
    }//GEN-LAST:event_startAllItemStateChanged

    private void ageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ageActionPerformed
        String ageCode = this.getAgeCode(this.age.getSelectedItem().toString());
        this.ageValue = ageCode;
        if (!this.ageValue.equals("Unknown")) {
            this.updateUserID("update", "age", this.ageValue);
        }
    }//GEN-LAST:event_ageActionPerformed

    private void genderActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_genderActionPerformed
        String genderCode = this.getGenderCode(this.gender.getSelectedItem().toString());
        this.genderValue = genderCode;
        if (!this.genderValue.equals("Unknown")) {
            this.updateUserID("update", "gender", this.genderValue);
        }
    }//GEN-LAST:event_genderActionPerformed

    private void regionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_regionActionPerformed
        String regCode = this.getRegionCode(this.region.getSelectedItem().toString());
        this.regionValue = regCode;
        if (!this.regionValue.equals("Unknown")) {
            this.updateUserID("update", "region", this.regionValue);
        }
    }//GEN-LAST:event_regionActionPerformed

    private void launchDMItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_launchDMItemStateChanged
        if (this.dmRunning) {
            stopDM();
        } else {
            startDM();
        }
    }//GEN-LAST:event_launchDMItemStateChanged

    /**
     * Button event for starting the TTS. It resets the TTS listener and the TTS
     *
     * @param evt
     */
    private void launchTTSItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_launchTTSItemStateChanged
        try {
            if (this.ttsRunning) {
                this.ttsRunning = false;
                this.stopTTS();
                LOGGER.info("Stopping TTS now.");
                this.launchTTS.setText("Start TTS");
            } else {
                this.ttsRunning = true;
//                this.resetTTS();
                this.startTTS();
                LOGGER.info("Starting TTS thread.");
                this.launchTTS.setText("Stop TTS");
            }

        } catch (IOException | InterruptedException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_launchTTSItemStateChanged

    private void launchASRItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_launchASRItemStateChanged
        try {
            if (this.asrRunning) {
                this.stopASR();
                this.asrRunning = false;
                LOGGER.info("Stopping ASR now.");
                this.launchASR.setText("Start ASR");
            } else {
                this.asrRunning = true;
                this.startASR();
                LOGGER.info("Starting ASR thread.");
                this.launchASR.setText("Stop ASR");
            }

        } catch (IOException | InterruptedException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_launchASRItemStateChanged

    private void restartActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_restartActionPerformed
        this.conversation.setText("");
        date = ZonedDateTime.now();
        this.startDate = TimeHelper.formatter.format(date);
        String fullID = this.getFullID();
        //        this.updateUserID("update", "id", this.idValue);
        this.filename = System.getProperty("user.dir") + File.separator + "log" + File.separator + fullID + ".wav";
        this.conversation.append(this.startDate + "\n");
        if (this.startAll.isSelected()) {
            this.startAll.doClick();
        }
        if (this.asrRunning && !this.autoStartASR.isSelected()) {
            this.launchASR.doClick();
        }
        if (this.ttsRunning) {
            this.launchTTS.doClick();
        }
        if (this.dmRunning) {
            this.launchDM.doClick();
        }
        if (this.recorder.isRecording()) {
            try {
                stopRecording();
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        this.clear.doClick();
        while (!this.autoStartASR.isSelected() && this.asrRunning || this.dmRunning || this.ttsRunning) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        this.startAll.doClick();
    }//GEN-LAST:event_restartActionPerformed

    private void userInputKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_userInputKeyPressed
        if (evt.getKeyCode() == (KeyEvent.VK_SPACE) || evt.getKeyCode() == KeyEvent.VK_BACK_SPACE) {
            String temp = this.userInput.getText();
            this.sendTypedText(temp, "inc");
            LOGGER.debug("Sending message, incremental: {}", temp);
        }
    }//GEN-LAST:event_userInputKeyPressed

    private void userInputActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_userInputActionPerformed
        if (!this.userInput.getText().equals("")) {
            this.sendTypedText(this.userInput.getText(), "final");
            this.userInput.setText("");
            this.userInput.requestFocusInWindow();
        }
    }//GEN-LAST:event_userInputActionPerformed

    private void sendTypedTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendTypedTextActionPerformed
        if (!this.userInput.getText().equals("")) {
            this.sendTypedText(this.userInput.getText(), "final");
            this.userInput.setText("");
            this.userInput.requestFocusInWindow();
        }
    }//GEN-LAST:event_sendTypedTextActionPerformed

    private void asrConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_asrConfigActionPerformed
        this.asrConfigName = this.asrConfig.getSelectedItem().toString();
        this.prefs.putInt("asrDefault", this.asrConfig.getSelectedIndex());
        try {
            this.prefs.sync();
        } catch (BackingStoreException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_asrConfigActionPerformed

    private void ttsConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_ttsConfigActionPerformed
        this.ttsConfigName = this.ttsConfig.getSelectedItem().toString();
        this.prefs.putInt("ttsDefault", this.ttsConfig.getSelectedIndex());
        try {
            this.prefs.sync();
        } catch (BackingStoreException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_ttsConfigActionPerformed

    private void dmConfigActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_dmConfigActionPerformed
        this.dmConfigName = this.dmConfig.getSelectedItem().toString();
        this.prefs.putInt("dmDefault", this.dmConfig.getSelectedIndex());
        try {
            this.prefs.sync();
        } catch (BackingStoreException ex) {
            java.util.logging.Logger.getLogger(BLISS.class.getName()).log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_dmConfigActionPerformed

    private void sendAgentTextActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_sendAgentTextActionPerformed
        ObjectNode tts = mapper.createObjectNode();
        ObjectNode fakeAgentSpeech = mapper.createObjectNode();
        fakeAgentSpeech.put("text", this.agentTextField.getText());
        fakeAgentSpeech.put("timestamp", TimeHelper.formatter.format(ZonedDateTime.now()));
        fakeAgentSpeech.put("moveID", -1);
        tts.set("tts", fakeAgentSpeech);
        this.reverseMiddleware.sendData(tts);
        LOGGER.debug("Sending text: {} ", tts.toString());
        this.agentTextField.setText("");
    }//GEN-LAST:event_sendAgentTextActionPerformed

    private void agentTextFieldActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_agentTextFieldActionPerformed
        this.sendAgentText.doClick();
    }//GEN-LAST:event_agentTextFieldActionPerformed

    private void muteUserMicrophoneActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_muteUserMicrophoneActionPerformed
        if (!this.muteUserMicrophone.isSelected()) {
            this.sendSenseMessage(true);
        } else {
            this.muteUserMicrophone.setText("Talking!");
            this.sendSenseMessage(false);
        }
    }//GEN-LAST:event_muteUserMicrophoneActionPerformed


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem Exit;
    private javax.swing.JMenuItem Save;
    private javax.swing.JButton addUserID;
    private javax.swing.JComboBox<String> age;
    private javax.swing.JLabel ageLabel;
    private javax.swing.JLabel agentLabel;
    private javax.swing.JTextField agentTextField;
    private javax.swing.JLabel agentUtterance;
    private javax.swing.JComboBox<String> asrConfig;
    private javax.swing.JLabel asrLabel;
    private javax.swing.JCheckBox autoStartASR;
    private javax.swing.JLabel autoStartASRLabel;
    private javax.swing.JButton clear;
    private javax.swing.JLabel configLabel;
    private javax.swing.JLabel controlInfo;
    private javax.swing.JTextArea conversation;
    private javax.swing.JComboBox<String> dmConfig;
    private javax.swing.JLabel dmLabel;
    private javax.swing.JFileChooser fileChooser;
    private javax.swing.JComboBox<String> gender;
    private javax.swing.JLabel genderLabel;
    private javax.swing.JMenu jMenu1;
    private javax.swing.JMenuBar jMenuBar1;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JSeparator jSeparator1;
    private javax.swing.JSeparator jSeparator2;
    private javax.swing.JSeparator jSeparator3;
    private javax.swing.JToggleButton launchASR;
    private javax.swing.JToggleButton launchDM;
    private javax.swing.JToggleButton launchTTS;
    private javax.swing.JToggleButton muteUserMicrophone;
    private javax.swing.JLabel recordActiveLabel;
    private javax.swing.JLabel recordIndicator;
    private javax.swing.JCheckBox recording;
    private javax.swing.JComboBox<String> region;
    private javax.swing.JLabel regionLabel;
    private javax.swing.JButton restart;
    private javax.swing.JButton sendAgentText;
    private javax.swing.JButton sendTypedText;
    private javax.swing.JToggleButton startAll;
    private javax.swing.JLabel statusAgent;
    private javax.swing.JComboBox<String> ttsConfig;
    private javax.swing.JLabel ttsLabel;
    private javax.swing.JTextField userID;
    private javax.swing.JLabel userIDLabel;
    private javax.swing.JLabel userInformation;
    private javax.swing.JTextField userInput;
    // End of variables declaration//GEN-END:variables
}
