package com.yt.downloader.spogify.ui.fragment;

import static android.widget.Toast.LENGTH_SHORT;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.gson.Gson;
import com.yt.downloader.R;
import com.yt.downloader.databinding.FragmentSearchBinding;
import com.yt.downloader.spogify.Retrofit.AppConstant;
import com.yt.downloader.spogify.SharedPreference.SPUtils;
import com.yt.downloader.spogify.Utils.db.FavoriteDatabaseHelper;
import com.yt.downloader.spogify.Utils.download.DownloadTrackModel;
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.base.BaseQuickAdapter;
import com.yt.downloader.spogify.mvp.bean.DeezerArtistBean;
import com.yt.downloader.spogify.mvp.bean.FavoriteTrack;
import com.yt.downloader.spogify.mvp.bean.Mp3downloadBean;
import com.yt.downloader.spogify.mvp.bean.SearchArtistBean;
import com.yt.downloader.spogify.mvp.bean.SearchTotalBean;
import com.yt.downloader.spogify.mvp.bean.TrackInfoBean;
import com.yt.downloader.spogify.mvp.bean.YoutubeSearchBean;
import com.yt.downloader.spogify.mvp.contract.MusicListContract;
import com.yt.downloader.spogify.mvp.contract.SearchArtistContract;
import com.yt.downloader.spogify.mvp.presenter.MusicListPresenter;
import com.yt.downloader.spogify.mvp.presenter.SearchArtistPresenter;
import com.yt.downloader.spogify.ui.activity.TrackInfoActivity;
import com.yt.downloader.spogify.ui.activity.VideoWatchActivity;
import com.yt.downloader.spogify.ui.adapter.MusicListAdapter;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public class SearchFragment extends Fragment implements View.OnClickListener, MusicListContract.View, SearchArtistContract.View {
    private FragmentSearchBinding binding;
    private MusicListAdapter musicListAdapter;
    private MusicListPresenter musicListPresenter;
    private List<SearchTotalBean> itemsBeans = new ArrayList<>();
    private Bottom_settings_search currentSheet;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable searchRunnable;
    private SearchArtistPresenter searchArtistPresenter;
    private String ArtistQuery = "";
    private String matchedArtistName = "";
    private final Map<String, String> imageCache = new ConcurrentHashMap<>();
    private FavoriteDatabaseHelper favDbHelper;
    private FilesExtractor filesExtractor;


    private SearchTotalBean currentDownloadTrack;

    private final ActivityResultLauncher<String> storagePermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
        if (isGranted) {
            if (currentDownloadTrack != null) {
                startDownloadFlow(currentDownloadTrack);
            }
        } else {
            Toast.makeText(getContext(), "Permission required to save audio to Music folder", LENGTH_SHORT).show();
        }
    });
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        binding.ivBtnSearch.setOnClickListener(this);
        favDbHelper = new FavoriteDatabaseHelper(getContext());
        filesExtractor = new FilesExtractor(getContext());
        initRecycler();
        initTextWatcher();
        initKeyboardSearch();

        musicListPresenter = new MusicListPresenter(this);
        searchArtistPresenter = new SearchArtistPresenter(this);
        binding.swipeRefresh.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                refreshData();
            }
        });

        return binding.getRoot();
    }

    private void initKeyboardSearch() {
        binding.etSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    if (searchRunnable != null) {
                        searchHandler.removeCallbacks(searchRunnable);
                    }

                    hideKeyboard(getActivity());
                    refreshData();
                    return true;
                }
                return false;
            }
        });
    }

    private void initTextWatcher() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0) {
                    binding.ivBtnSearch.setVisibility(View.VISIBLE);
                } else {
                    binding.ivBtnSearch.setVisibility(View.GONE);
                }

                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(final Editable s) {
                searchRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (!TextUtils.isEmpty(s.toString().trim())) {
                            refreshData();
                        }
                    }
                };
                searchHandler.postDelayed(searchRunnable, 1000);
            }
        });
    }

    private void initRecycler() {
        binding.rvMusicList.setLayoutManager(new LinearLayoutManager(getContext()));
        musicListAdapter = new MusicListAdapter();
        binding.rvMusicList.setAdapter(musicListAdapter);
        musicListAdapter.setEmptyView(R.layout.layout_empty_music, binding.rvMusicList);
        musicListAdapter.setOnMoreClickListener((track, position) -> showBottomSheet(track));
        musicListAdapter.setOnMusicDeletedListener(trackName -> {
            if (currentSheet != null && currentSheet.isAdded()) {
                currentSheet.dismissAllowingStateLoss();
            }
        });
        musicListAdapter.setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                SearchTotalBean selectedItem = musicListAdapter.getData().get(position);
                if (selectedItem.isArtist()) {
                    startActivity(new Intent(getContext(), TrackInfoActivity.class).putExtra("ArtistName", selectedItem.getName()));
                } else {
                    startActivity(new Intent(getContext(), VideoWatchActivity.class).putExtra("videoId", selectedItem.getVideoId()).putExtra("title", selectedItem.getName()).putExtra("tumbnail", selectedItem.getThumbnail()));
                }
            }
        });
    }

    private void showBottomSheet(SearchTotalBean track) {
        Bottom_settings_search sheet = Bottom_settings_search.newInstance();
        sheet.bindTrack(track);
        sheet.setActionListener(new Bottom_settings_search.BottomSheetActionListener() {
            @Override
            public void onFavoriteClicked(SearchTotalBean t) {
                boolean currentlyFav = favDbHelper.isTrackFavorite(t.getName());
                if (currentlyFav) {
                    favDbHelper.removeFavoriteTrack(t.getName());
                    Toast.makeText(getActivity(), "Removed from Favorites", LENGTH_SHORT).show();
                } else {
                    String imageThumb = !TextUtils.isEmpty(t.getThumbnail()) ? t.getThumbnail() : "";
                    String totalListeners = String.valueOf(new java.util.Random().nextInt(9_000_000) + 1_000);
                    String artistForDb = !TextUtils.isEmpty(t.getArtistName()) ? t.getArtistName() : "";
                    favDbHelper.addFavoriteTrack(t.getName(), artistForDb, imageThumb, totalListeners);
                    Toast.makeText(getActivity(), "Added to Favorites", LENGTH_SHORT).show();
                }
                musicListAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDownloadClicked(SearchTotalBean t) {
                if (filesExtractor.isFileDownloaded(t.getName())) {
                    musicListAdapter.showDeleteDialog(t.getName());
                } else {
                    checkPermissionAndDownload(t);
                }
            }
        });
        sheet.show(getParentFragmentManager(), "bottom_settings_search");
        currentSheet = sheet;
    }

    private void checkPermissionAndDownload(SearchTotalBean track) {
        currentDownloadTrack = track;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                return;
            }
        }
        startDownloadFlow(track);
    }

    private void startDownloadFlow(SearchTotalBean track) {
        if (track == null || TextUtils.isEmpty(track.getVideoId())) {
            Toast.makeText(getContext(), "Unable to download: missing video ID", LENGTH_SHORT).show();
            return;
        }
        currentDownloadTrack = track;
        if (currentSheet != null && currentSheet.isAdded()) {
            currentSheet.setDownloading(true, 0);
        }
        String ytUrl = "https://www.youtube.com/watch?v=" + track.getVideoId();
        searchArtistPresenter.getDownloadQuery(ytUrl, "mp3", 128);
    }

    private String buildSearchQuery(SearchTotalBean track) {
        if (track == null) return "";
        String trackName = track.getName();
        String musicQuery = binding.etSearch.getText().toString().trim();
        if (!TextUtils.isEmpty(musicQuery)) {
            return trackName + " " + musicQuery + " official audio";
        }
        return trackName + " official audio";
    }


    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.iv_btn_search) {
            binding.etSearch.setText("");
            binding.ivBtnSearch.setVisibility(View.GONE);
            itemsBeans.clear();
            if (musicListAdapter != null) {
                musicListAdapter.setNewData(itemsBeans);
            }
        }
    }

    private void refreshData() {
        String musicQuery = binding.etSearch.getText().toString().trim();
        if (TextUtils.isEmpty(musicQuery)) {
            binding.swipeRefresh.setRefreshing(false);
            return;
        }

        if (!isNetworkAvailable()) {
            if (binding.swipeRefresh.isRefreshing()) {
                binding.swipeRefresh.setRefreshing(false);
            }
            Toast.makeText(getContext(), "Please check network and try again", Toast.LENGTH_SHORT).show();
            return;
        }

        this.ArtistQuery = musicQuery;
        this.matchedArtistName = "";
        SPUtils.getInstance().put(AppConstant.recentSearch, musicQuery);
        if (!itemsBeans.isEmpty()) {
            itemsBeans.clear();
            musicListAdapter.setNewData(itemsBeans);
        }
        musicListPresenter.getSearchListQuery(musicQuery);
        searchArtistPresenter.getSearchArtistQuery(AppConstant.SearchArtist, musicQuery, AppConstant.Api_Key, "json");
    }

    private static void hideKeyboard(Activity activity) {
        if (activity == null) return;
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    @SuppressWarnings("deprecation")
    @SuppressLint("MissingPermission")
    private boolean isNetworkAvailable() {
        if (getActivity() == null) return false;
        ConnectivityManager connectivityManager = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager != null ? connectivityManager.getActiveNetworkInfo() : null;
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    @Override
    public void showLoading() {
        binding.swipeRefresh.setRefreshing(true);
    }

    @Override
    public void showError(String error) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentSheet != null && currentSheet.isAdded()) {
                    currentSheet.setDownloading(false, 0);
                }
                Toast.makeText(getContext(), "Error fetching data: " + error, Toast.LENGTH_SHORT).show();
                binding.swipeRefresh.setRefreshing(false);
            }
        }, 500);
    }

    @Override
    public void hideLoading() {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                binding.swipeRefresh.setRefreshing(false);
            }
        }, 500);
    }

    @Override
    public void getSearchAristSuccess(SearchArtistBean searchArtistBean) {
        if (searchArtistBean != null && searchArtistBean.getResults() != null && searchArtistBean.getResults().getArtistMatches() != null && searchArtistBean.getResults().getArtistMatches().getArtistList() != null && !searchArtistBean.getResults().getArtistMatches().getArtistList().isEmpty()) {

            List<SearchArtistBean.Artist> artistList = searchArtistBean.getResults().getArtistMatches().getArtistList();
            SearchArtistBean.Artist matchedArtist = null;

            for (SearchArtistBean.Artist artist : artistList) {
                if (artist.getName() != null && artist.getName().equalsIgnoreCase(ArtistQuery)) {
                    matchedArtist = artist;
                    break;
                }
            }

            if (matchedArtist == null) {
                matchedArtist = artistList.get(0);
            }

            final String artistName = matchedArtist.getName();
            if (artistName != null && artistName.equalsIgnoreCase(ArtistQuery)) {
                matchedArtistName = artistName;
            }

            if (imageCache.containsKey(artistName)) {
                addArtistToAdapter(artistName, imageCache.get(artistName));
            } else {
                fetchDeezerArtist(artistName);
            }
        }
    }

    @Override
    public void getDownloadSuccess(Mp3downloadBean mp3downloadBean) {
        if (mp3downloadBean != null && mp3downloadBean.getDownloadUrl() != null) {
            String downloadUrl = mp3downloadBean.getDownloadUrl();
            String trackName = currentDownloadTrack != null ? currentDownloadTrack.getName() : "downloaded_track";

            Toast.makeText(getContext(), "Starting download for: " + trackName, LENGTH_SHORT).show();

            DownloadTrackModel.downloadMp3File(getContext(), downloadUrl, trackName, new DownloadTrackModel.DownloadCallback() {
                @Override
                public void onProgress(int progress) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (musicListAdapter != null) {
                            musicListAdapter.updateDownloadProgress(trackName, progress);
                        }
                        if (currentSheet != null && currentSheet.isAdded()) {
                            currentSheet.setDownloading(true, progress);
                        }
                    });
                }

                @Override
                public void onSuccess(String filePath) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (currentSheet != null && currentSheet.isAdded()) {
                            currentSheet.bindTrack(currentDownloadTrack);
                        }
                        if (musicListAdapter != null) {
                            musicListAdapter.notifyDataSetChanged();
                        }
                        Toast.makeText(getContext(), "Saved to Music/downloaded_music: " + filePath, Toast.LENGTH_LONG).show();
                    });
                }

                @Override
                public void onError(String error) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (currentSheet != null && currentSheet.isAdded()) {
                            currentSheet.setDownloading(false, 0);
                        }
                        Toast.makeText(getContext(), "Download Error: " + error, LENGTH_SHORT).show();
                    });
                }
            });
        } else {
            if (currentSheet != null && currentSheet.isAdded()) {
                currentSheet.setDownloading(false, 0);
            }
            Toast.makeText(getContext(), "Failed to resolve download link", LENGTH_SHORT).show();
        }
    }

    private void fetchDeezerArtist(String artistName) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                String query = URLEncoder.encode(artistName, "UTF-8");
                String deezerUrl = "https://api.deezer.com/search/artist?q=" + query;

                URL url = new URL(deezerUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    InputStreamReader reader = new InputStreamReader(connection.getInputStream());
                    DeezerArtistBean response = new Gson().fromJson(reader, DeezerArtistBean.class);
                    reader.close();

                    if (response != null && response.getData() != null && !response.getData().isEmpty()) {
                        String imageUrl = response.getData().get(0).getPictureBig();

                        imageCache.put(artistName, imageUrl);

                        new Handler(Looper.getMainLooper()).post(() -> addArtistToAdapter(artistName, imageUrl));
                    } else {
                        new Handler(Looper.getMainLooper()).post(() -> addArtistToAdapter(artistName, ""));
                    }
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> addArtistToAdapter(artistName, ""));
                }
                connection.disconnect();
            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> addArtistToAdapter(artistName, ""));
            }
        });
    }

    private void addArtistToAdapter(String artistName, String imageUrl) {
        SearchTotalBean artistBean = new SearchTotalBean(imageUrl, artistName, "Artist", "", true);

        if (!itemsBeans.isEmpty() && itemsBeans.get(0).isArtist()) {
            itemsBeans.set(0, artistBean);
        } else {
            itemsBeans.add(0, artistBean);
        }

        if (!TextUtils.isEmpty(artistName) && artistName.equalsIgnoreCase(ArtistQuery)) {
            String lowerArtist = artistName.toLowerCase();
            for (SearchTotalBean item : itemsBeans) {
                if (!item.isArtist()) {
                    String sourceTitle = !TextUtils.isEmpty(item.getRawTitle()) ? item.getRawTitle() : item.getName();
                    if ((sourceTitle != null && sourceTitle.toLowerCase().contains(lowerArtist))
                            || TextUtils.isEmpty(item.getArtistName())) {
                        item.setArtistName(artistName);
                        if (sourceTitle != null) {
                            item.setName(YoutubeSearchBean.ItemsBean.extractPlainTitle(sourceTitle, artistName));
                        }
                    }
                }
            }
        }

        if (musicListAdapter != null) {
            musicListAdapter.setNewData(new ArrayList<>(itemsBeans));
        }
    }

    @Override
    public void getSearchList(YoutubeSearchBean youtubeSearchBean) {
        if (youtubeSearchBean != null && youtubeSearchBean.getItems() != null && !youtubeSearchBean.getItems().isEmpty()) {
            SearchTotalBean existingArtist = (!itemsBeans.isEmpty() && itemsBeans.get(0).isArtist()) ? itemsBeans.get(0) : null;

            itemsBeans.clear();
            if (existingArtist != null) {
                itemsBeans.add(existingArtist);
            }

            List<YoutubeSearchBean.ItemsBean> fetchedItems = youtubeSearchBean.getItems();
            String songQueryFilter = !TextUtils.isEmpty(matchedArtistName) ? null : ArtistQuery;
            for (YoutubeSearchBean.ItemsBean item : fetchedItems) {
                String extractedArtist = item.getArtistName(songQueryFilter);
                if (TextUtils.isEmpty(extractedArtist) && !TextUtils.isEmpty(matchedArtistName)) {
                    extractedArtist = matchedArtistName;
                }
                String plainTitle = item.getPlainTitle(extractedArtist);
                SearchTotalBean bean = new SearchTotalBean(
                        item.getThumbnail(),
                        plainTitle,
                        item.getDuration(),
                        item.getId(),
                        extractedArtist,
                        false
                );
                bean.setRawTitle(item.getTitle());
                itemsBeans.add(bean);
            }

            musicListAdapter.setNewData(new ArrayList<>(itemsBeans));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (searchRunnable != null) {
            searchHandler.removeCallbacks(searchRunnable);
        }
        binding = null;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (musicListAdapter != null) {
            musicListAdapter.onDeleteMusicResult(requestCode, resultCode);
        }
    }
}