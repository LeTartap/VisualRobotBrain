package nl.bliss.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class RandomHelper {

    public static <E> E getRandomSetElement(Set<E> set) {
        return set.stream().skip(ThreadLocalRandom.current().nextInt(0, set.size())).findFirst().orElse(null);
    }

    public static JsonNode getRandomArrayNodeElement(ArrayNode arrayNode){
        return arrayNode.get(ThreadLocalRandom.current().nextInt(0,arrayNode.size()));
    }
}
