package com.clantracker.api;

import com.google.gson.Gson;
import java.io.IOException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

@Slf4j
public class APIClient {

    public static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String apiUrl = "http://127.0.0.1:3000/api/";
    // private static final String apiUrl = "http://osclan.art:3000/api/";
    private static final String ANALYZE = "analyze";
    private static final String GET_SEQUENCE = "getsequence";


    public static int getSequence() throws IOException {
        Request request = new Request.Builder()
                .get()
                .url(apiUrl + GET_SEQUENCE)
                .build();

        OkHttpClient client = RuneLiteAPI.CLIENT;
        log.info("Sending request");
        Call call = client.newCall(request);
        Response response = call.execute();
        log.info("Send request");

        if (response.body() == null)
        {
            log.debug("API Call - Reponse was null.");
            response.close();
            return -1;
        }
        else
        {
            log.info("parsing response");
            String responseString = response.body().string();
            log.info(responseString);

            JsonObject jsonResponse = new JsonParser().parse(responseString).getAsJsonObject();
            log.info(jsonResponse.get("sequence_number").getAsString());
            return jsonResponse.get("sequence_number").getAsInt();
        }
    }

    public static int message(String clanName, String pluginPassword, int sequenceNumber, int requestType, String author, String content, int retryAttempt, int maxAttempts) throws IOException
    {
        JsonObject apiRequestBody = new JsonObject();
        apiRequestBody.addProperty("clan", clanName);
        apiRequestBody.addProperty("cpw", pluginPassword);
        apiRequestBody.addProperty("sequence_number", sequenceNumber);
        apiRequestBody.addProperty("req_type", requestType);
        apiRequestBody.addProperty("author", author);
        apiRequestBody.addProperty("content", content);

        RequestBody body = RequestBody.create(JSON, (gson.toJson(apiRequestBody)));
        Request request = new Request.Builder()
                .post(body)
                .url(apiUrl + ANALYZE)
                .build();
        OkHttpClient client = RuneLiteAPI.CLIENT;
        log.info("Sending request");
        Call call = client.newCall(request);
        Response response = call.execute();
        log.info("Send request");


        if (response.body() == null)
        {
            if (retryAttempt < maxAttempts)
            {
                return message(clanName, pluginPassword, sequenceNumber, requestType, author, content,retryAttempt + 1, maxAttempts);
            }else{
                return -1;
            }
        } else {
            log.info("parsing response");
            String responseString = response.body().string();
            log.info(responseString);

            JsonObject jsonResponse = new JsonParser().parse(responseString).getAsJsonObject();
            log.info(jsonResponse.get("sequence_number").getAsString());
            return jsonResponse.get("sequence_number").getAsInt();
        }
    }
}
