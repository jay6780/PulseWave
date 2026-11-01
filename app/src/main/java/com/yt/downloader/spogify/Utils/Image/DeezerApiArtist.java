package com.yt.downloader.spogify.Utils.Image;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.yt.downloader.spogify.mvp.bean.DeezerArtistBean;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeezerApiArtist {

    public interface Callback {
        void onSuccess(String imageUrl);
        void onFailure(Exception e);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    private DeezerApiArtist() {}

    public static void fetchArtistImageUrl(String track, Callback callback) {
        if (TextUtils.isEmpty(track)) {
            MAIN.post(() -> callback.onFailure(new IllegalArgumentException("Empty name")));
            return;
        }

        String cached = CACHE.get(track);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached));
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String query = URLEncoder.encode(track, "UTF-8");
                URL url = new URL("https://api.deezer.com/search/artist?q=" + query);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStreamReader reader =
                                 new InputStreamReader(connection.getInputStream(), "UTF-8")) {
                        DeezerArtistBean response = GSON.fromJson(reader, DeezerArtistBean.class);

                        if (response != null && response.getData() != null
                                && !response.getData().isEmpty()) {
                            String imageUrl = response.getData().get(0).getPictureBig();
                            CACHE.put(track, imageUrl);
                            MAIN.post(() -> callback.onSuccess(imageUrl));
                            return;
                        }
                    }
                }
                MAIN.post(() -> callback.onFailure(new Exception("No result for: " + track)));
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure(e));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}