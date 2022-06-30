package com.clantracker.api;

import com.clantracker.ClanTrackerPlugin;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import javax.inject.Inject;

@Slf4j
public class APIClient {

    public static final Gson gson = new Gson();
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final String apiUrl = "http://139.162.135.91:3000/api/";
    // private static final String apiUrl = "http://osclan.art:3000/api/";
    private static final String ANALYZE = "analyze";
    private static final String ONLINE_COUNT = "onlinecount";
    private static final String GET_SEQUENCE = "getsequence";

    private static OkHttpClient okHttpClient;


    @Inject
    public APIClient(OkHttpClient rlClient)
    {
        okHttpClient = rlClient.newBuilder()
                .pingInterval(0, TimeUnit.SECONDS)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .addNetworkInterceptor(chain ->
                {
                    Request headerRequest = chain.request()
                            .newBuilder()
                            .header("User-Agent", "ClanTracker-Plugin-1.0.0")
                            .build();
                    return chain.proceed(headerRequest);
                })
                .build();
    }

    public void getSequence(Callback callback) throws IOException {
        Request request = new Request.Builder()
                .get()
                .url(apiUrl + GET_SEQUENCE)
                .build();

        OkHttpClient client = okHttpClient;
        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public static void sendOnlineCount(List<String> onlinePlayersList, String clanName, String pluginPassword, Callback callback) throws IOException
    {
        int onlineCount = onlinePlayersList.size();
        JsonObject apiRequestBody = new JsonObject();
        apiRequestBody.addProperty("clan", clanName);
        apiRequestBody.addProperty("cpw", pluginPassword);
        apiRequestBody.addProperty("onlineCount", onlineCount);
        RequestBody body = RequestBody.create(JSON, (gson.toJson(apiRequestBody)));
        Request request = new Request.Builder()
                .post(body)
                .url(apiUrl + ONLINE_COUNT)
                .build();
        OkHttpClient client = okHttpClient;
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(call, e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                callback.onResponse(call, response);
                response.close();
            }
        });
    }

    public static void message(String clanName, String pluginPassword, int sequenceNumber, int requestType, String author, String content, int retryAttempt, int maxAttempts, Callback callback) throws IOException
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
        OkHttpClient client = okHttpClient;
        Call call = client.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                if (retryAttempt < maxAttempts) {
                    try {
                        message(clanName, pluginPassword, sequenceNumber, requestType, author, content, retryAttempt + 1, maxAttempts, callback);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    callback.onFailure(call, e);
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                callback.onResponse(call, response);
            }
        });
    }
}
