package com.yt.downloader.spogify.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.yt.downloader.databinding.FragmentHomeBinding;
import com.yt.downloader.spogify.Retrofit.AppConstant;
import com.yt.downloader.spogify.mvp.bean.OpmBean;
import com.yt.downloader.spogify.mvp.bean.RAndBbean;
import com.yt.downloader.spogify.mvp.bean.TopArtistBean;
import com.yt.downloader.spogify.mvp.bean.TopTrackBean;
import com.yt.downloader.spogify.mvp.contract.HomeContract;
import com.yt.downloader.spogify.mvp.presenter.HomePresenter;
import com.yt.downloader.spogify.ui.activity.TrackInfoActivity;
import com.yt.downloader.spogify.ui.adapter.OpmAdapter;
import com.yt.downloader.spogify.ui.adapter.RnBAdapter;
import com.yt.downloader.spogify.ui.adapter.TopArtistAdapter;
import com.yt.downloader.spogify.ui.adapter.TopTrackAdapter;

import java.util.ArrayList;
import java.util.List;

public class HomeFragment extends Fragment implements HomeContract.View {

    private FragmentHomeBinding binding;
    private HomePresenter homePresenter;
    private TopArtistAdapter topArtistAdapter;
    private TopTrackAdapter topTrackAdapter;
    private OpmAdapter opmAdapter;
    private RnBAdapter rnBAdapter;
    private List<TopArtistBean.ArtistsBean.ArtistBean> topArtistList = new ArrayList<>();
    private List<TopTrackBean.Track> topTrackList = new ArrayList<>();
    private List<OpmBean.Artist> opmList = new ArrayList<>();
    private List<RAndBbean.Artist> RnbList = new ArrayList<>();

    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasConnected = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        homePresenter = new HomePresenter(this);

        binding.swipeRefresh.setOnRefreshListener(this::refreshData);

        registerNetworkCallback();

        if (isNetworkAvailable()) {
            wasConnected = true;
            initApi();
        } else {
            updateHomeUiConnected(false);
            binding.swipeRefresh.setEnabled(false);
            binding.tvContent.setText("You're in offline mode");
        }
    }

    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager)
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                new Handler(Looper.getMainLooper()).post(() -> handleNetworkAvailable());
            }

            @Override
            public void onLost(@NonNull Network network) {
                new Handler(Looper.getMainLooper()).post(() -> handleNetworkLost());
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    private void handleNetworkAvailable() {
        if (wasConnected) return;
        wasConnected = true;
        binding.swipeRefresh.setEnabled(true);
        Log.d("NetworkMonitor", "Internet AVAILABLE");
        updateHomeUiConnected(true);

        refreshData();
    }

    private void handleNetworkLost() {
        if (!wasConnected) return;
        wasConnected = false;
        binding.swipeRefresh.setEnabled(false);
        Log.d("NetworkMonitor", "Internet LOST");
        updateHomeUiConnected(false);
        binding.tvContent.setText("You're in offline mode");

        if (binding.swipeRefresh.isRefreshing()) {
            binding.swipeRefresh.setRefreshing(false);
        }
    }

    private void updateHomeUiConnected(boolean isConnected) {
        if (binding == null) return;

        binding.llEmpty.setVisibility(isConnected ? View.GONE : View.VISIBLE);
        binding.llContent.setVisibility(isConnected ? View.VISIBLE : View.GONE);

        if (!isConnected) {
            binding.llTopTracks.setVisibility(View.GONE);
            binding.llTopArtist.setVisibility(View.GONE);
            binding.llOpm.setVisibility(View.GONE);
            binding.llRnb.setVisibility(View.GONE);
        }
        binding.llEmpty.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getActivity().startActivity(new Intent(getContext(), TrackInfoActivity.class)
                        .putExtra("ArtistName", "Downloaded")
                        .putExtra("SelectedTrackName","")
                        .putExtra("isFromFavorite", false)
                        .putExtra("isFromDownload", true));
            }
        });
    }

    private void initApi() {
        updateHomeUiConnected(true);
        homePresenter.getTopTrackQuery(AppConstant.TopTracks, AppConstant.Api_Key, "json");
        homePresenter.getTopArtistQuery(AppConstant.TopArtist, AppConstant.Api_Key, "json");
        homePresenter.getOpmQuery(AppConstant.OPM, "opm", AppConstant.Api_Key, "json");
        homePresenter.getRnBQuery(AppConstant.RNB, "rnb", AppConstant.Api_Key, "json");
    }

    private void refreshData() {
        try {
            if (!isNetworkAvailable()) {
                if (binding.swipeRefresh.isRefreshing()) {
                    binding.swipeRefresh.setEnabled(false);
                    binding.swipeRefresh.setRefreshing(false);
                }
                return;
            }
            binding.swipeRefresh.setEnabled(true);
            topArtistList.clear();
            topTrackList.clear();
            opmList.clear();
            RnbList.clear();
            topArtistAdapter.setNewData(new ArrayList<>());
            topTrackAdapter.setNewData(new ArrayList<>());
            opmAdapter.setNewData(new ArrayList<>());
            rnBAdapter.setNewData(new ArrayList<>());

            binding.rvTopArtists.scrollToPosition(0);
            binding.rvTopTracks.scrollToPosition(0);
            binding.rvOpm.scrollToPosition(0);
            binding.rvRnb.scrollToPosition(0);

            binding.llTopTracks.setVisibility(View.GONE);
            binding.llTopArtist.setVisibility(View.GONE);
            binding.llOpm.setVisibility(View.GONE);
            binding.llRnb.setVisibility(View.GONE);
            binding.swipeRefresh.setRefreshing(true);
            initApi();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isNetworkAvailable() {
        if (connectivityManager == null) {
            connectivityManager = (ConnectivityManager)
                    requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        }
        Network active = connectivityManager.getActiveNetwork();
        if (active == null) return false;

        NetworkCapabilities caps = connectivityManager.getNetworkCapabilities(active);
        return caps != null &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void setupRecyclerView() {
        topArtistAdapter = new TopArtistAdapter();
        topTrackAdapter = new TopTrackAdapter();
        opmAdapter = new OpmAdapter();
        rnBAdapter = new RnBAdapter();

        binding.rvTopArtists.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvTopArtists.setAdapter(topArtistAdapter);
        binding.rvTopTracks.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvTopTracks.setAdapter(topTrackAdapter);
        binding.rvOpm.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvOpm.setAdapter(opmAdapter);
        binding.rvRnb.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));
        binding.rvRnb.setAdapter(rnBAdapter);
    }

    @Override public void showLoading() {
        if (binding != null) binding.swipeRefresh.setRefreshing(true);
    }
    @Override public void showError(String error) {
        hideLoading();
    }
    @Override public void hideLoading() {
        new Handler(Looper.getMainLooper()).postDelayed(
                () -> { if (binding != null) binding.swipeRefresh.setRefreshing(false); }, 500);
    }

    @Override
    public void getTopArtistSuccess(TopArtistBean bean) {
        if (bean != null && bean.getArtists() != null && bean.getArtists().getArtist() != null
                && !bean.getArtists().getArtist().isEmpty()) {
            topArtistList.clear();
            topArtistList.addAll(bean.getArtists().getArtist());
            topArtistAdapter.setNewData(topArtistList);
            binding.llTopArtist.setVisibility(View.VISIBLE);
        } else {
            binding.llTopArtist.setVisibility(View.GONE);
        }
    }

    @Override
    public void getTopTracksSuccess(TopTrackBean topTrackBean) {
        if (topTrackBean != null && topTrackBean.getTracks() != null
                && topTrackBean.getTracks().getTrack() != null
                && !topTrackBean.getTracks().getTrack().isEmpty()) {
            topTrackList.clear();
            topTrackList.addAll(topTrackBean.getTracks().getTrack());
            topTrackAdapter.setNewData(topTrackList);
            binding.llTopTracks.setVisibility(View.VISIBLE);
        } else {
            binding.llTopTracks.setVisibility(View.GONE);
        }
    }

    @Override
    public void getOpmSuccess(OpmBean opmBean) {
        if (opmBean != null && opmBean.getTopArtists() != null
                && opmBean.getTopArtists().getArtist() != null
                && !opmBean.getTopArtists().getArtist().isEmpty()) {
            opmList.clear();
            opmList.addAll(opmBean.getTopArtists().getArtist());
            opmAdapter.setNewData(opmList);
            binding.llOpm.setVisibility(View.VISIBLE);
        } else {
            binding.llOpm.setVisibility(View.GONE);
        }
    }

    @Override
    public void getRnBSuccess(RAndBbean rAndBbean) {
        if (rAndBbean != null && rAndBbean.getTopArtists() != null
                && rAndBbean.getTopArtists().getArtist() != null
                && !rAndBbean.getTopArtists().getArtist().isEmpty()) {
            RnbList.clear();
            RnbList.addAll(rAndBbean.getTopArtists().getArtist());
            rnBAdapter.setNewData(RnbList);
            binding.llRnb.setVisibility(View.VISIBLE);
        } else {
            binding.llRnb.setVisibility(View.GONE);
        }
    }

    @Override
    public void onDestroyView() {
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) { }
            networkCallback = null;
        }
        super.onDestroyView();
        binding = null;
    }
}