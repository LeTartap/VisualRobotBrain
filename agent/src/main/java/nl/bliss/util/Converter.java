package nl.bliss.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.ArrayList;
import java.util.List;

public class Converter {

    public final static ObjectMapper mapper = new ObjectMapper();

    public static List<String> convertToList(ArrayNode values) {
        final ArrayList<String> result = new ArrayList<>();
        values.forEach(jsonNode -> result.add(jsonNode.asText()));
        return result;
    }

    public static List<String> takeFirstElements(ArrayNode values){
        final ArrayList<String> result = new ArrayList<>();
        values.forEach(jsonNode -> result.add(jsonNode.get(0).asText()));
        return result;
    }

    public static List<JsonNode>  convertToList(JsonNode values){
        final ArrayList<JsonNode> result = new ArrayList<>();
        values.forEach(jsonNode -> result.add(jsonNode));
        return result;
    }

    public static ArrayNode convertToArrayNode(List<? extends Object> values) {
        ArrayNode result = mapper.valueToTree(values);
        return result;
    }

    public static JsonNode convertToJsonNode(List<? extends JsonNode> values){
        JsonNode result = mapper.valueToTree(values);
        return result;
    }

}
