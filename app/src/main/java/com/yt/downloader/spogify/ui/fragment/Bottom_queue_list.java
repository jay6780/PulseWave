package com.yt.downloader.spogify.ui.fragment;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.net.NetworkRequest;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.yt.downloader.R;
import com.yt.downloader.databinding.FragmentBottomQueueBinding;
import com.yt.downloader.spogify.Service.PlaybackService;
import com.yt.downloader.spogify.Utils.db.QueueDatabaseHelper;
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.Utils.fileUtils.VideoFile;
import com.yt.downloader.spogify.Utils.lyrics.PlayerState;
import com.yt.downloader.spogify.ui.activity.MiniPlayerActivity;
import com.yt.downloader.spogify.ui.adapter.QueueAdapter;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Bottom_queue_list extends BottomSheetDialogFragment {

    private FragmentBottomQueueBinding binding;
    private QueueAdapter adapter;
    private ItemTouchHelper itemTouchHelper;
    private QueueDatabaseHelper queueDbHelper;
    private FilesExtractor filesExtractor;
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private boolean wasConnected = true;
    private String lastNowThumb = null;
    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final Handler syncHandler = new Handler(Looper.getMainLooper());
    private boolean lastSyncedPlaying = false;
    private String lastSyncedTitle = "";
    private final Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            if (binding != null) {
                PlayerState state = PlayerState.get();
                if (lastSyncedPlaying != state.isPlaying || !TextUtils.equals(lastSyncedTitle, state.title)) {
                    lastSyncedPlaying = state.isPlaying;
                    lastSyncedTitle = state.title == null ? "" : state.title;
                    refreshUi();
                }
            }
            syncHandler.postDelayed(this, 400);
        }
    };

    public static Bottom_queue_list newInstance() {
        return new Bottom_queue_list();
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBottomQueueBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        queueDbHelper = new QueueDatabaseHelper(requireContext());
        filesExtractor = new FilesExtractor(requireContext());
        wasConnected = isNetworkAvailable();
        if (wasConnected) {
            PlayerState state = PlayerState.get();
            if (state.queue.isEmpty()) {
                bgExecutor.execute(() -> {
                    List<PlayerState.Item> saved = queueDbHelper.getAllQueueTracks();
                    new Handler(Looper.getMainLooper()).post(() -> {
                        if (binding == null) return;
                        if (PlayerState.get().queue.isEmpty() && !saved.isEmpty()) {
                            PlayerState.get().queue.addAll(saved);
                            PlayerState.get().queueIndex = 0;
                            refreshUi();
                        }
                    });
                });
            } else {
                List<PlayerState.Item> snapshot = new ArrayList<>(state.queue);
                bgExecutor.execute(() -> queueDbHelper.replaceQueue(snapshot));
            }
        } else {
            applyQueueByNetworkState(false, false);
        }
        registerNetworkCallback();

        binding.ivDismiss.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.btnShuffle.setOnClickListener(v -> toggleShuffle());
        binding.ivNowPlayPause.setOnClickListener(v -> togglePlayPause());

        binding.cbSelectAll.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.selectAll(binding.cbSelectAll.isChecked());
            }
        });
        binding.tvCancelSelection.setOnClickListener(v -> {
            if (adapter != null) {
                adapter.setSelectionMode(false);
            }
        });
        binding.tvDeleteSelected.setOnClickListener(v -> {
            if (adapter != null) {
                if (adapter.getSelectedCount() == 0) {
                    Toast.makeText(getContext(), "Select at least 1 track to delete", Toast.LENGTH_SHORT).show();
                    return;
                }
                adapter.deleteSelected();
                queueDbHelper.replaceQueue(PlayerState.get().queue);
                refreshUi();
            }
        });

        setupRecycler();
        refreshUi();
    }

    @Override
    public void onStart() {
        super.onStart();
        lastSyncedPlaying = PlayerState.get().isPlaying;
        lastSyncedTitle = PlayerState.get().title == null ? "" : PlayerState.get().title;
        syncHandler.removeCallbacks(syncRunnable);
        syncHandler.postDelayed(syncRunnable, 400);
    }

    @Override
    public void onStop() {
        syncHandler.removeCallbacks(syncRunnable);
        super.onStop();
    }

    private void setupRecycler() {
        binding.rvQueue.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new QueueAdapter(PlayerState.get().queue);
        adapter.setOnDragStartListener(holder -> {
            if (itemTouchHelper != null) {
                itemTouchHelper.startDrag(holder);
            }
        });
        adapter.setOnSelectionChangedListener((active, selectedCount, totalCount) -> {
            if (binding == null) return;
            binding.llSelectionBar.setVisibility(active ? View.VISIBLE : View.GONE);
            binding.cbSelectAll.setChecked(active && totalCount > 0 && selectedCount == totalCount);
            binding.tvDeleteSelected.setText(selectedCount > 0 ? "Delete (" + selectedCount + ")" : "Delete");
        });
        adapter.setOnQueueItemClickListener((position, item) -> {
            if (getActivity() instanceof MiniPlayerActivity) {
                ((MiniPlayerActivity) getActivity()).playSharedQueueAt(position);
                refreshUi();
            }
        });
        binding.rvQueue.setAdapter(adapter);

        ItemTouchHelper.Callback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();
                if (fromPos == RecyclerView.NO_POSITION || toPos == RecyclerView.NO_POSITION) {
                    return false;
                }

                PlayerState state = PlayerState.get();
                if (fromPos < toPos) {
                    for (int i = fromPos; i < toPos; i++) {
                        Collections.swap(state.queue, i, i + 1);
                    }
                } else {
                    for (int i = fromPos; i > toPos; i--) {
                        Collections.swap(state.queue, i, i - 1);
                    }
                }

                if (state.queueIndex == fromPos) {
                    state.queueIndex = toPos;
                } else if (fromPos < state.queueIndex && toPos >= state.queueIndex) {
                    state.queueIndex--;
                } else if (fromPos > state.queueIndex && toPos <= state.queueIndex) {
                    state.queueIndex++;
                }

                adapter.notifyItemMoved(fromPos, toPos);
                return true;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (queueDbHelper != null) {
                    queueDbHelper.replaceQueue(PlayerState.get().queue);
                }
            }
        };

        itemTouchHelper = new ItemTouchHelper(callback);
        itemTouchHelper.attachToRecyclerView(binding.rvQueue);
    }

    private void refreshUi() {
        if (binding == null) return;
        PlayerState state = PlayerState.get();
        String nowTitle = state.title;
        String nowArtist = state.artist;
        String nowThumb = state.thumb;

        if (state.queueIndex >= 0 && state.queueIndex < state.queue.size()) {
            PlayerState.Item activeItem = state.queue.get(state.queueIndex);
            if (activeItem != null && !TextUtils.isEmpty(activeItem.name)) {
                boolean matchesActive = !state.hasTrack
                        || TextUtils.isEmpty(nowTitle)
                        || (state.isLocal && !TextUtils.isEmpty(state.localPath) && state.localPath.equals(activeItem.localPath))
                        || (!state.isLocal && !TextUtils.isEmpty(state.videoId) && state.videoId.equals(activeItem.videoId))
                        || nowTitle.toLowerCase().contains(activeItem.name.toLowerCase());
                if (matchesActive) {
                    nowTitle = activeItem.name;
                    if (!TextUtils.isEmpty(activeItem.artist)) {
                        nowArtist = activeItem.artist;
                    }
                    if (!TextUtils.isEmpty(activeItem.thumb)) {
                        nowThumb = activeItem.thumb;
                    }
                }
            }
        }

        if (!TextUtils.isEmpty(nowTitle)) {
            binding.llNowPlaying.setVisibility(View.VISIBLE);
            binding.tvNowTitle.setText(nowTitle);
            binding.tvNowArtist.setText(!TextUtils.isEmpty(nowArtist) ? nowArtist : "Unknown Artist");

            String thumbKey = nowThumb == null ? "" : nowThumb;
            if (!TextUtils.equals(lastNowThumb, thumbKey)) {
                lastNowThumb = thumbKey;
                if (!TextUtils.isEmpty(nowThumb)) {
                    Glide.with(this)
                            .load(nowThumb)
                            .apply(new RequestOptions()
                                    .transform(new CenterCrop(), new RoundedCorners(10))
                                    .placeholder(R.mipmap.app_logo)
                                    .error(R.mipmap.app_logo))
                            .into(binding.ivNowThumb);
                } else {
                    binding.ivNowThumb.setImageResource(R.mipmap.app_logo);
                }
            }

            binding.ivNowPlayPause.setImageResource(state.isPlaying
                    ? android.R.drawable.ic_media_pause
                    : android.R.drawable.ic_media_play);
        } else {
            lastNowThumb = null;
            binding.llNowPlaying.setVisibility(View.GONE);
        }

        boolean isEmpty = state.queue.isEmpty();
        binding.rvQueue.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.tvEmptyQueue.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        if (isEmpty) {
            binding.llSelectionBar.setVisibility(View.GONE);
        }

        updateShuffleUi();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }
    private void registerNetworkCallback() {
        connectivityManager = (ConnectivityManager) requireContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager == null) return;

        NetworkRequest request = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build();

        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override
            public void onAvailable(@NonNull Network network) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (wasConnected || binding == null) return;
                    wasConnected = true;
                    applyQueueByNetworkState(true, true);
                });
            }

            @Override
            public void onLost(@NonNull Network network) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (!wasConnected || binding == null) return;
                    wasConnected = false;
                    applyQueueByNetworkState(false, true);
                });
            }
        };

        connectivityManager.registerNetworkCallback(request, networkCallback);
    }

    @SuppressWarnings("deprecation")
    private boolean isNetworkAvailable() {
        if (getContext() == null) return false;
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo activeNetworkInfo = cm.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void applyQueueByNetworkState(boolean isConnected, boolean autoPlayFirst) {
        bgExecutor.execute(() -> {
            List<PlayerState.Item> nextList = new ArrayList<>();
            if (isConnected) {
                nextList.addAll(queueDbHelper.getAllQueueTracks());
            } else if (filesExtractor != null) {
                ArrayList<VideoFile> downloadedFiles = filesExtractor.listVideos();
                for (VideoFile vf : downloadedFiles) {
                    if (vf == null || TextUtils.isEmpty(vf.getName())) continue;
                    String rawName = vf.getName();
                    String cleanName = rawName.toLowerCase().endsWith(".mp3")
                            ? rawName.substring(0, rawName.length() - 4)
                            : rawName;
                    nextList.add(new PlayerState.Item(
                            cleanName,
                            "Downloaded Track",
                            "",
                            "",
                            vf.getPath()
                    ));
                }
            }

            new Handler(Looper.getMainLooper()).post(() -> {
                if (binding == null) return;
                PlayerState state = PlayerState.get();
                state.queue.clear();
                state.queue.addAll(nextList);

                if (isConnected && !TextUtils.isEmpty(state.title)) {
                    int matchedIdx = -1;
                    for (int i = 0; i < state.queue.size(); i++) {
                        if (state.title.equalsIgnoreCase(state.queue.get(i).name)) {
                            matchedIdx = i;
                            break;
                        }
                    }
                    state.queueIndex = matchedIdx >= 0 ? matchedIdx : (state.queue.isEmpty() ? -1 : 0);
                } else {
                    state.queueIndex = state.queue.isEmpty() ? -1 : 0;
                }

                if (autoPlayFirst && !state.queue.isEmpty() && getActivity() instanceof MiniPlayerActivity) {
                    ((MiniPlayerActivity) getActivity()).playSharedQueueAt(0);
                }
                refreshUi();
            });
        });
    }

    private void updateShuffleUi() {
        PlayerState.PlayMode mode = PlayerState.get().playMode;
        int iconRes;
        int color;
        switch (mode) {
            case SHUFFLE:
                iconRes = R.mipmap.shuffle;
                color = Color.parseColor("#1DB954");
                break;
            case PLAY_ONCE:
                iconRes = R.mipmap.play_once;
                color = Color.parseColor("#1DB954");
                break;
            case REPEAT_ALL:
            default:
                iconRes = R.mipmap.repeat;
                color = Color.parseColor("#FFFFFF");
                break;
        }
        binding.ivShuffle.setImageResource(iconRes);
        binding.ivShuffle.setColorFilter(color);
        binding.tvShuffle.setText(playModeLabel(mode));
        binding.tvShuffle.setTextColor(color);

        if (!wasConnected) {
            binding.tvQueueSection.setText("Offline — Downloaded music only");
        } else if (mode == PlayerState.PlayMode.SHUFFLE) {
            binding.tvQueueSection.setText("Shuffling from:");
        } else if (mode == PlayerState.PlayMode.PLAY_ONCE) {
            binding.tvQueueSection.setText("Playing once:");
        } else {
            binding.tvQueueSection.setText("Next in queue");
        }
    }

    private String playModeLabel(PlayerState.PlayMode mode) {
        switch (mode) {
            case SHUFFLE:
                return "Shuffle";
            case PLAY_ONCE:
                return "Play once";
            case REPEAT_ALL:
            default:
                return "Repeat all";
        }
    }

    private void toggleShuffle() {
        PlayerState state = PlayerState.get();
        switch (state.playMode) {
            case REPEAT_ALL:
                state.playMode = PlayerState.PlayMode.SHUFFLE;
                if (state.queue.size() > 1) {
                    PlayerState.Item current = (state.queueIndex >= 0 && state.queueIndex < state.queue.size())
                            ? state.queue.get(state.queueIndex)
                            : null;
                    Collections.shuffle(state.queue);
                    if (current != null) {
                        state.queueIndex = state.queue.indexOf(current);
                    }
                    if (queueDbHelper != null && wasConnected) {
                        queueDbHelper.replaceQueue(state.queue);
                    }
                }
                break;
            case SHUFFLE:
                state.playMode = PlayerState.PlayMode.PLAY_ONCE;
                break;
            case PLAY_ONCE:
            default:
                state.playMode = PlayerState.PlayMode.REPEAT_ALL;
                break;
        }
        updateShuffleUi();
        Toast.makeText(getContext(), playModeLabel(state.playMode), Toast.LENGTH_SHORT).show();
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    private void togglePlayPause() {
        PlayerState state = PlayerState.get();
        if (getActivity() instanceof MiniPlayerActivity) {
            MiniPlayerActivity miniActivity = (MiniPlayerActivity) getActivity();
            if (!state.hasTrack && !state.queue.isEmpty()) {
                int idx = (state.queueIndex >= 0 && state.queueIndex < state.queue.size()) ? state.queueIndex : 0;
                miniActivity.playSharedQueueAt(idx);
                state.isPlaying = true;
            } else {
                boolean nextPlaying = !state.isPlaying;
                miniActivity.togglePlayPause();
                state.isPlaying = nextPlaying;
            }
            lastSyncedPlaying = state.isPlaying;
            lastSyncedTitle = state.title == null ? "" : state.title;
            refreshUi();
            return;
        }

        String action = state.isPlaying ? PlaybackService.ACTION_PAUSE : PlaybackService.ACTION_PLAY;
        Intent intent = new Intent(action);
        intent.setPackage(requireContext().getPackageName());
        requireContext().sendBroadcast(intent);
        state.isPlaying = !state.isPlaying;
        lastSyncedPlaying = state.isPlaying;
        refreshUi();
    }

    @Override
    public void onDestroyView() {
        syncHandler.removeCallbacks(syncRunnable);
        if (connectivityManager != null && networkCallback != null) {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback);
            } catch (Exception ignored) {
            }
            networkCallback = null;
        }
        super.onDestroyView();
        binding = null;
        lastNowThumb = null;
    }
}
