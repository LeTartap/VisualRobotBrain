package nl.bliss.nvlg;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

import javax.xml.stream.*;
import java.io.IOException;
import java.io.StringWriter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;

public class ReadSpeakerSSML {

    private XMLOutputFactory xmlOutputFactory;
    private ObjectMapper mapper;
    private XmlMapper xmlMapper;
    private Pattern ssmlEmotion;

    public ReadSpeakerSSML() {
        this.mapper = new ObjectMapper();
        this.xmlOutputFactory = XMLOutputFactory.newInstance();
        this.ssmlEmotion = Pattern.compile("\\{\\{(emotion)=(\\S+)}}");
        this.xmlMapper = new XmlMapper();
    }

    public String displayParalinguistic(String language, String voice, String pl){
        String ssml = "";
        return ssml;
    }

    public String getPositiveBackchannel(String language, String voice) {
        JsonNode speak;
        //UTF-8 encoding for 'oké'
        String myString = "oké";
        byte[] ptext = myString.getBytes(ISO_8859_1);
        String value = new String(ptext, UTF_8);
        //Randomize which backchannel to pick
        String[] channels = {"file:consent", "file:consent2","file:enlightened", value};
        int randomNum = ThreadLocalRandom.current().nextInt(0, 3 + 1);
        String element = channels[randomNum];

        //Randomize variation of pitch and rate if text is selected
        double randomPitch = ThreadLocalRandom.current().nextGaussian() * Math.sqrt(3) + 0;
        String pitch;
        if(randomPitch < 0){
            pitch = randomPitch + "%";
        }
        else{
            pitch = "+" + randomPitch + "%";
        }
//        String[] rates = {"slow","normal","fast"};
//        int randomRate = ThreadLocalRandom.current().nextInt(0,3);
//        String rate = rates[randomRate];
        String rate = "normal";
        if(element.equals(value)){
            speak = mapper.createObjectNode()
                    .put("xml:lang",language)
                    .put("name",voice)
                    .put("text", element)
                    .put("pitch",pitch)
                    .put("rate",rate);
        }
        else{
            //Randomize with speed for audio
            double randomSpeed = ThreadLocalRandom.current().nextGaussian() * Math.sqrt(3) + 100;
            String speed = randomSpeed + "%";
            speak = mapper.createObjectNode()
                    .put("xml:lang",language)
                    .put("speed",speed)
                    .put("name",voice)
                    .put("src", element)
                    .put("pitch",pitch)
                    .put("rate",rate);
        }
        JsonNode node = mapper.createObjectNode()
                .set("speak",speak);
        try {
            return this.parseSSML(node.toString());
        } catch (XMLStreamException e) {
            return "{}";
        }
    }


    //Backchannels: consent (oké), consent2 (uhu), enlightened (aha!), 'oke'

    /**
     * Method for parsing random backchannel ssml, default:
     * {
     *     "speak":{
     *         "version":"1.1",
     *         "xml:lang":"nl_nl",
     *         "name":"Guus",
     *         "rate":"normal",
     *         "pitch":"+0%",
     *         "src":"file:consent2",
     *         "speed":"100%"
     *     }
     * }
     * @param jsonSSML, json containing the parameters for SSML
     *                  Required: language, voice
     * @return an SSML String representation to send to the TTS
     * @throws XMLStreamException
     */
    public String parseSSML(String jsonSSML) throws XMLStreamException {
        try {
            String language = "nl_nl";
            String voice = "Guus";
            String rate = "normal";
            JsonNode node = mapper.readTree(jsonSSML).get("speak");
            StringWriter out = new StringWriter();
            XMLStreamWriter sw = xmlOutputFactory.createXMLStreamWriter(out);
            sw.writeStartDocument();
            sw.writeStartElement("speak");
                sw.writeAttribute("version","1.1");
                if(node.has("xml:lang")){
                    language = node.get("xml:lang").asText();
                }
                sw.writeAttribute("xml:lang",language);
                sw.writeStartElement("voice");
                    if(node.has("name")){
                        voice = node.get("name").asText();
                    }
                    sw.writeAttribute("name",voice);
                    sw.writeStartElement("prosody");
                        if(node.has("rate")){
                            rate = node.get("rate").asText();
                        }
                        sw.writeAttribute("rate",rate);
                        if(node.has("pitch")){
                            String pitch = node.get("pitch").asText();
                            sw.writeAttribute("pitch",pitch);
                        }
                        if(node.has("text")){
                            String text = node.get("text").asText();
                            sw.writeCharacters(text);
                        }
                        if(node.has("src")) {
                            sw.writeStartElement("audio");
                            String file = node.get("src").asText();
                            sw.writeAttribute("src", file);

                            if (node.has("speed")) {
                                String speed = node.get("speed").asText();
                                sw.writeAttribute("speed", speed);
                            }
                            sw.writeCharacters("x");
                            sw.writeEndElement();
                        }
                    sw.writeEndElement();
                sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndDocument();
            return out.toString();
        } catch (IOException e) {
        }
        return "";
    }

    public String stringToSSML(JsonNode node) {

        String language = node.get("xml:lang").asText();
        String voice = node.get("name").asText();
        String rate = node.get("rate").asText();
        String text = node.get("text").asText();
        //JsonNode node = mapper.readTree(jsonSSML).get("speak");
        StringWriter out = new StringWriter();
        XMLStreamWriter sw;
        try {
            sw = xmlOutputFactory.createXMLStreamWriter(out);
            sw.writeStartDocument();
            sw.writeStartElement("speak");
            sw.writeAttribute("version", "1.1");
            sw.writeAttribute("xml:lang", language);

            //Now check for markers to add blocks.
            Matcher mEmotion = this.ssmlEmotion.matcher(text);
            while (mEmotion.find()) {
                String value = mEmotion.group(2);
                sw.writeStartElement("audio");
                String file = "file:" + value;
                sw.writeAttribute("src", file);
                if (node.has("speed")) {
                    String speed = node.get("speed").asText();
                    sw.writeAttribute("speed", speed);
                }
                sw.writeCharacters("x");
                sw.writeEndElement();
//
//                if(key.equals("emotion")){
//                    speak.put("src","file:"+value);
//                    content = mEmotion.replaceAll("");
//                }
//
//                speak.put("text",content);
//                ssmlNode.set("speak",speak);
//                String ssml = null;
//                try {
//                    ssml = parser.parseSSML(ssmlNode.toString());
//                } catch (XMLStreamException e) {
//                    e.printStackTrace();
            }
//                return ssml;

            sw.writeStartElement("voice");
            if (node.has("name")) {
                voice = node.get("name").asText();
            }
            sw.writeAttribute("name", voice);
            sw.writeStartElement("prosody");
            if (node.has("rate")) {
                rate = node.get("rate").asText();
            }
            sw.writeAttribute("rate", rate);
            if (node.has("pitch")) {
                String pitch = node.get("pitch").asText();
                sw.writeAttribute("pitch", pitch);
            }
//            if (node.has("text")) {
//                String text = node.get("text").asText();
//                sw.writeCharacters(text);
//            }
            if (node.has("src")) {
                sw.writeStartElement("audio");
                String file = node.get("src").asText();
                sw.writeAttribute("src", file);

                if (node.has("speed")) {
                    String speed = node.get("speed").asText();
                    sw.writeAttribute("speed", speed);
                }
                sw.writeCharacters("x");
                sw.writeEndElement();
            }
            sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndElement();
            sw.writeEndDocument();
            return out.toString();

        } catch (XMLStreamException ex) {
            ex.printStackTrace();
        }
        return "";
    }




    public static void main(String[] args) throws IOException, XMLStreamException {
        ReadSpeakerSSML parser = new ReadSpeakerSSML();

        String json = " {\n" +
                "  \"speak\": {\n" +
                "    \"version\": \"1.1\",\n" +
                "    \"xml:lang\": \"nl_nl\",\n" +
                "    \"name\": \"Guus\",\n" +
                "    \"rate\": \"normal\",\n" +
                "    \"pitch\": \"+0%\",\n" +
                "    \"src\": \"file:consent2\",\n" +
                "    \"speed\": \"100%\",\n" +
                "    \"text\": \"hallo\"\n" +
                "  }\n" +
                "}";
        String text = "{\n" +
                "\t\"speak\": {\n" +
                "\t\t\"version\": \"1.1\",\n" +
                "\t\t\"xml:lang\": \"nl_nl\",\n" +
                "\t\t\"voice\": {\n" +
                "\t\t\t\"name\": \"Guus\",\n" +
                "\t\t\t\"prosody\": {\n" +
                "\t\t\t\t\"rate\": \"normal\",\n" +
                "\t\t\t\t\"text\": \"hallo\",\n" +
                "\t\t\t\t\"pitch\": \"+5%\"\t\t\t\t\n" +
                "\t\t\t}\n" +
                "\t\t}\n" +
                "\t}\n" +
                "}";

        String result = parser.parseSSML(json);
        //String result = parser.parseSSML(text);
        int i = 0;
//        while(i < 10){
//            //String result = parser.sendBackchannel("nl_nl","Guus");
//            System.out.println(result);
//            i++;
//        }
        System.out.println(result);



    }
}
