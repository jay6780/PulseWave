package com.yt.downloader.spogify.Utils.Image;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.yt.downloader.spogify.mvp.bean.DeezerArtistBean;
import com.yt.downloader.spogify.mvp.bean.DeezerTrackBean;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DeezerApiTrack {

    public interface Callback {
        void onSuccess(String imageUrl);
        void onFailure(Exception e);
    }

    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(2);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new Gson();

    private DeezerApiTrack() {}

    public static void fetchArtistImageUrl(String name, Callback callback) {
        if (TextUtils.isEmpty(name)) {
            MAIN.post(() -> callback.onFailure(new IllegalArgumentException("Empty name")));
            return;
        }

        // Return cached result immediately
        String cached = CACHE.get(name);
        if (cached != null) {
            MAIN.post(() -> callback.onSuccess(cached));
            return;
        }

        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String query = URLEncoder.encode(name, "UTF-8");
                URL url = new URL("https://api.deezer.com/search/track?q=" + query);

                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    try (InputStreamReader reader =
                                 new InputStreamReader(connection.getInputStream(), "UTF-8")) {
                        DeezerTrackBean response = GSON.fromJson(reader,  DeezerTrackBean .class);

                        if (response != null && response.getData() != null
                                && !response.getData().isEmpty()) {
                            DeezerTrackBean.TrackData firstTrack = response.getData().get(0);
                            if (firstTrack.getAlbum() != null) {
                                String imageUrl = firstTrack.getAlbum().getCoverBig();
                                if (imageUrl != null) {
                                    CACHE.put(name, imageUrl);
                                    MAIN.post(() -> callback.onSuccess(imageUrl));
                                }
                            }
                            return;
                        }
                    }
                }
                MAIN.post(() -> callback.onFailure(new Exception("No result for: " + name)));
            } catch (Exception e) {
                MAIN.post(() -> callback.onFailure(e));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}