package nl.bliss.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.IOException;

public class APIHelper {

    private static OkHttpClient apiClient;
    private static ObjectMapper apiMapper;
    private static String apiURL;

    public static boolean initialized(){
        return apiURL != null;
    }

    public static void init(String url){
        init(url,new OkHttpClient());
    }

    public static String getURL(){
        return apiURL;
    }

    public static void init(String url, OkHttpClient client){
        apiMapper = new ObjectMapper();
        apiClient = client;
        apiURL = url;
    }

    public static double getSimilarity(String a, String b, String language){
        HttpUrl.Builder urlBuilder = HttpUrl.parse(apiURL).newBuilder();
        urlBuilder.addPathSegment("/similarity");
        urlBuilder.addQueryParameter("text_a",a);
        urlBuilder.addQueryParameter("text_b",b);
        urlBuilder.addQueryParameter("lang",language.substring(0,2));
        Request request = new Request.Builder()
                .url(urlBuilder.build().toString())
                .build();
        try (Response response = apiClient.newCall(request).execute()) {
            if(response.isSuccessful()){
                JsonNode node = apiMapper.readTree(response.body().string());
                if(node.has("similarity")){
                    return node.get("similarity").asDouble();
                }
                return 0.0;
            }
        } catch (IOException ex) {

        }
        return 0.0;
    }
}
