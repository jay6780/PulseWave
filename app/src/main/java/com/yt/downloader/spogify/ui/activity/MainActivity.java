package com.yt.downloader.spogify.ui.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.Lifecycle;

import com.yt.downloader.R;
import com.yt.downloader.databinding.ActivityMainBinding;
import com.yt.downloader.spogify.Utils.lyrics.LrcParser;
import com.yt.downloader.spogify.Utils.lyrics.PlayerState;
import com.yt.downloader.spogify.ui.fragment.HomeFragment;
import com.yt.downloader.spogify.ui.fragment.PlayListFragment;
import com.yt.downloader.spogify.ui.fragment.SearchFragment;

import java.util.ArrayList;
import java.util.List;

import com.yt.downloader.spogify.Utils.db.LyricsDatabaseHelper;
import com.yt.downloader.spogify.Utils.db.QueueDatabaseHelper;

public class MainActivity extends MiniPlayerActivity implements View.OnClickListener {

    private ActivityMainBinding binding;
    private Fragment homeFragment, searchFragment, playlistFragment;
    public static final int TAB_HOME = 1, TAB_SEARCH = 2, TAB_PLAYLIST = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        bindMiniPlayer(binding.layoutMiniPlayer);
        loadSavedQueueIntoMiniPlayer();
        askNotificationPermission();
        binding.llHome.setOnClickListener(this);
        binding.llPlaylist.setOnClickListener(this);
        binding.llSearch.setOnClickListener(this);
        initFragments(savedInstanceState);
    }

    private void loadSavedQueueIntoMiniPlayer() {
        QueueDatabaseHelper queueDbHelper = new QueueDatabaseHelper(this);
        PlayerState state = PlayerState.get();
        if (state.queue.isEmpty()) {
            List<PlayerState.Item> savedQueue = queueDbHelper.getAllQueueTracks();
            if (!savedQueue.isEmpty()) {
                state.queue.addAll(savedQueue);
            }
        }
        if (!state.hasTrack && !state.queue.isEmpty()) {
            PlayerState.Item first = state.queue.get(0);
            state.queueIndex = 0;
            state.hasTrack = true;
            state.isPlaying = false;
            state.title = first.name;
            state.artist = first.artist;
            state.thumb = first.thumb;
            state.videoId = first.videoId;
            state.localPath = first.localPath;
            state.isLocal = !TextUtils.isEmpty(first.localPath) && new java.io.File(first.localPath).exists();
            restoreFromState();
        }
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private final ActivityResultLauncher<String> notificationPermissionLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
        if (!granted) {
            Toast.makeText(this, "Enable notifications to control background playback", Toast.LENGTH_LONG).show();
        }
    });

    private void initFragments(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            homeFragment = getSupportFragmentManager().findFragmentByTag("homeFragment");
            searchFragment = getSupportFragmentManager().findFragmentByTag("searchFragment");
            playlistFragment = getSupportFragmentManager().findFragmentByTag("PlayListFragment");
        }
        switchTab(TAB_HOME);
    }

    private void hideAllFragments(FragmentTransaction transaction) {
        if (homeFragment != null && homeFragment.isAdded()) {
            transaction.hide(homeFragment);
            transaction.setMaxLifecycle(homeFragment, Lifecycle.State.STARTED);
        }
        if (searchFragment != null && searchFragment.isAdded()) {
            transaction.hide(searchFragment);
            transaction.setMaxLifecycle(searchFragment, Lifecycle.State.STARTED);
        }
        if (playlistFragment != null && playlistFragment.isAdded()) {
            transaction.hide(playlistFragment);
            transaction.setMaxLifecycle(playlistFragment, Lifecycle.State.STARTED);
        }
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.ll_home) {
            switchTab(TAB_HOME);
        } else if (id == R.id.ll_search) {
            switchTab(TAB_SEARCH);
        } else if (id == R.id.ll_playlist) {
            switchTab(TAB_PLAYLIST);
        }
    }

    private void switchTab(int tab) {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        hideAllFragments(transaction);

        switch (tab) {
            case TAB_HOME:
                if (homeFragment == null) {
                    homeFragment = new HomeFragment();
                    transaction.add(R.id.homeFragment, homeFragment, "homeFragment");
                } else {
                    transaction.show(homeFragment);
                }
                transaction.setMaxLifecycle(homeFragment, Lifecycle.State.RESUMED);
                break;

            case TAB_SEARCH:
                if (searchFragment == null) {
                    searchFragment = new SearchFragment();
                    transaction.add(R.id.homeFragment, searchFragment, "searchFragment");
                } else {
                    transaction.show(searchFragment);
                }
                transaction.setMaxLifecycle(searchFragment, Lifecycle.State.RESUMED);
                break;

            case TAB_PLAYLIST:
                if (playlistFragment == null) {
                    playlistFragment = new PlayListFragment();
                    transaction.add(R.id.homeFragment, playlistFragment, "PlayListFragment");
                } else {
                    transaction.show(playlistFragment);
                }
                transaction.setMaxLifecycle(playlistFragment, Lifecycle.State.RESUMED);
                break;

            default:
                return;
        }
        changeTabUi(tab);
        transaction.commitAllowingStateLoss();
    }

    private void changeTabUi(int tabNum) {
        switch (tabNum) {
            case TAB_HOME:
                binding.tvHome.setTextColor(Color.parseColor("#ffffff"));
                binding.tvSearch.setTextColor(Color.parseColor("#C0C4CC"));
                binding.tvPlaylist.setTextColor(Color.parseColor("#C0C4CC"));

                binding.ivHome.setImageResource(R.mipmap.home_white);
                binding.ivSearch.setImageResource(R.mipmap.search_grey);
                binding.ivPlaylist.setImageResource(R.mipmap.playlist_grey);
                break;

            case TAB_SEARCH:
                binding.tvHome.setTextColor(Color.parseColor("#C0C4CC"));
                binding.tvSearch.setTextColor(Color.parseColor("#ffffff"));
                binding.tvPlaylist.setTextColor(Color.parseColor("#C0C4CC"));

                binding.ivHome.setImageResource(R.mipmap.home_grey);
                binding.ivSearch.setImageResource(R.mipmap.search_white);
                binding.ivPlaylist.setImageResource(R.mipmap.playlist_grey);
                break;

            case TAB_PLAYLIST:
                binding.tvHome.setTextColor(Color.parseColor("#C0C4CC"));
                binding.tvSearch.setTextColor(Color.parseColor("#C0C4CC"));
                binding.tvPlaylist.setTextColor(Color.parseColor("#ffffff"));

                binding.ivHome.setImageResource(R.mipmap.home_grey);
                binding.ivSearch.setImageResource(R.mipmap.search_grey);
                binding.ivPlaylist.setImageResource(R.mipmap.playlist_white);
                break;
        }
    }



    @Override
    protected void onResume() {
        super.onResume();
        onTrackChanged();
        PlayerState s = PlayerState.get();
        List<String> name = new ArrayList<>();
        for (PlayerState.Item item : s.queue) {
            name.add(item.getName());
        }
        Log.d("Quelist", "val: " + name);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (searchFragment != null && searchFragment.isAdded()) {
            searchFragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}