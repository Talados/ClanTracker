package com.clantracker.api;

import com.clantracker.ClanTrackerConfig;
import com.clantracker.ClanTrackerPlugin;
import com.google.gson.Gson;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.http.api.RuneLiteAPI;
import okhttp3.*;

import javax.inject.Inject;

@Slf4j
public class APIClient {

    // Injects our config
    @Inject
    private ClanTrackerConfig config;
    // Provides our config
    @Provides
    ClanTrackerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(ClanTrackerConfig.class);
    }

    @Inject
    private Gson gson;

    private static final MediaType JSON = MediaType.parse("application/json");
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

    public void getSequence(String clanName, Callback callback) throws IOException {
        if(config.apiUrl() == null) return;
        JsonObject apiRequestBody = new JsonObject();
        apiRequestBody.addProperty("clan", clanName);
        RequestBody body = RequestBody.create(JSON, (gson.toJson(apiRequestBody)));
        Request request = new Request.Builder()
                .post(body)
                .url(config.apiUrl() + GET_SEQUENCE)
                .build();

        OkHttpClient client = okHttpClient;
        Call call = client.newCall(request);
        call.enqueue(callback);
    }

    public void sendOnlineCount(List<String> onlinePlayersList, String clanName, String pluginPassword, Callback callback) throws IOException
    {
        if(config.apiUrl() == null) return;
        int onlineCount = onlinePlayersList.size();
        JsonObject apiRequestBody = new JsonObject();
        apiRequestBody.addProperty("clan", clanName);
        apiRequestBody.addProperty("cpw", pluginPassword);
        apiRequestBody.addProperty("onlineCount", onlineCount);
        RequestBody body = RequestBody.create(JSON, (gson.toJson(apiRequestBody)));
        Request request = new Request.Builder()
                .post(body)
                .url(config.apiUrl() + ONLINE_COUNT)
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

    public void message(String clanName, String pluginPassword, int sequenceNumber, int requestType, String author, String content, int retryAttempt, int maxAttempts, Callback callback) throws IOException
    {
        if(config.apiUrl() == null) return;
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
                .url(config.apiUrl() + ANALYZE)
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
