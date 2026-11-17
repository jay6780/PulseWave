package com.yt.downloader.spogify.ui.fragment;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.yt.downloader.R;
import com.yt.downloader.databinding.FragmentBottomSettingsBinding;
import com.yt.downloader.spogify.Utils.db.FavoriteDatabaseHelper;
import com.yt.downloader.spogify.Utils.db.LyricsDatabaseHelper;
import com.yt.downloader.spogify.Utils.db.QueueDatabaseHelper;
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.Utils.lyrics.PlayerState;
import com.yt.downloader.spogify.mvp.bean.SearchTotalBean;
import com.yt.downloader.spogify.mvp.bean.TrackInfoBean;

import java.io.File;

public class Bottom_settings_search extends BottomSheetDialogFragment implements View.OnClickListener {

    private FragmentBottomSettingsBinding binding;
    private SearchTotalBean currentTrack;
    private BottomSheetActionListener actionListener;
    private FavoriteDatabaseHelper favDbHelper;
    private LyricsDatabaseHelper lyricsDatabaseHelper;
    private QueueDatabaseHelper queueDbHelper;
    private FilesExtractor filesExtractor;
    private boolean isDownloading = false;
    private int downloadProgress = 0;


    public interface BottomSheetActionListener {
        void onFavoriteClicked(SearchTotalBean track);

        void onDownloadClicked(SearchTotalBean track);
    }

    public static Bottom_settings_search newInstance() {
        return new Bottom_settings_search();
    }

    @Override
    public int getTheme() {
        return R.style.AppBottomSheetDialogTheme;
    }

    public void setActionListener(BottomSheetActionListener listener) {
        this.actionListener = listener;
    }

    /**
     * Bind the track to this sheet before showing it.
     */
    public void bindTrack(SearchTotalBean track) {
        this.currentTrack = track;
        this.isDownloading = false;
        this.downloadProgress = 0;
        if (binding != null) {
            refreshUi();
        }
    }

    public void setDownloading(boolean downloading, int progress) {
        if (this.isDownloading == downloading && this.downloadProgress == progress) {
            return;
        }
        this.isDownloading = downloading;
        this.downloadProgress = progress;
        if (binding != null) {
            if (downloading) {
                binding.ivDownload.setImageResource(android.R.drawable.stat_sys_download);
                binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
                binding.tvDownload.setText(progress > 0
                        ? "Downloading... " + progress + "%"
                        : "Preparing download...");
            } else {
                refreshUi();
            }
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentBottomSettingsBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        favDbHelper = new FavoriteDatabaseHelper(requireContext());
        lyricsDatabaseHelper = new LyricsDatabaseHelper(requireContext());
        queueDbHelper = new QueueDatabaseHelper(requireContext());
        filesExtractor = new FilesExtractor(requireContext());
        binding.llFavorites.setOnClickListener(this);
        binding.llDownload.setOnClickListener(this);
        binding.llRefreshLyrics.setOnClickListener(this);
        binding.llAddToQueue.setOnClickListener(this);
        binding.ivDismiss.setOnClickListener(this);

        refreshUi();
    }

    private void refreshUi() {
        if (binding == null || currentTrack == null) return;

        binding.tvTitle.setText(currentTrack.getName());
        String artistText = !TextUtils.isEmpty(currentTrack.getArtistName())
                ? currentTrack.getArtistName()
                : "Unknown Artist";
        binding.tvArtist.setText(artistText);

        // Thumbnail
        String imageUrl = currentTrack.getThumbnail();
        if (!TextUtils.isEmpty(imageUrl)) {
            Glide.with(this).asBitmap().apply(new RequestOptions().placeholder(R.mipmap.app_logo).error(R.mipmap.app_logo)).load(imageUrl).into(binding.ivThumb);
        } else {
            binding.ivThumb.setImageResource(R.mipmap.app_logo);
        }

        // Favorite state
        boolean isFavorite = favDbHelper.isTrackFavorite(currentTrack.getName());
        updateAddButtonIcon(binding.ivFavorite, isFavorite);

        // Download state
        boolean isDownloaded = filesExtractor.isFileDownloaded(currentTrack.getName());
        if (isDownloading) {
            binding.ivDownload.setImageResource(android.R.drawable.stat_sys_download);
            binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
            binding.tvDownload.setText(downloadProgress > 0
                    ? "Downloading... " + downloadProgress + "%"
                    : "Preparing download...");
        } else if (isDownloaded) {
            binding.ivDownload.setImageResource(R.drawable.baseline_delete_24);
            binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
            binding.tvDownload.setText("Downloaded");
        } else {
            binding.ivDownload.setImageResource(android.R.drawable.stat_sys_download);
            binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
            binding.tvDownload.setText("Not downloaded");
        }
        binding.tvFavorites.setText(isFavorite ? "Added to favorites" : "Add to favorites");


        binding.llRefreshLyrics.setAlpha(!artistText.equals("Unknown Artist")?  1.0f : 0.5f);


    }

    private void updateAddButtonIcon(ImageView imageView, boolean isFavorite) {
        if (isFavorite) {
            imageView.setImageResource(android.R.drawable.checkbox_on_background);
            imageView.setImageResource(android.R.drawable.ic_menu_add);
        } else {
            imageView.setImageResource(android.R.drawable.ic_input_add);
            imageView.setColorFilter(Color.parseColor("#B3B3B3"));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.ll_favorites) {
            if (currentTrack == null) return;
            if (actionListener != null) {
                actionListener.onFavoriteClicked(currentTrack);
            }
            refreshUi();
        } else if (v.getId() == R.id.ll_download) {
            if (currentTrack == null || isDownloading) return;
            if (actionListener != null) {
                actionListener.onDownloadClicked(currentTrack);
            }
            refreshUi();
        } else if (v.getId() == R.id.ll_refreshLyrics) {
            if (currentTrack == null) return;
            String artist = !TextUtils.isEmpty(currentTrack.getArtistName())
                    ? currentTrack.getArtistName() : "";

            if(TextUtils.isEmpty(artist)){
                Toast.makeText(getContext(),"Artist name not found can't refresh lyrics",Toast.LENGTH_SHORT).show();
                return;
            }
            showRefreshDialog(currentTrack.getName(), artist);
        } else if (v.getId() == R.id.ll_add_to_queue) {
            if (currentTrack == null) return;
            String trackName = currentTrack.getName() == null ? "" : currentTrack.getName().trim();
            boolean alreadyInQueue = queueDbHelper.isTrackInQueue(trackName);
            if (!alreadyInQueue) {
                for (PlayerState.Item qItem : PlayerState.get().queue) {
                    if (qItem != null && qItem.name != null && qItem.name.trim().equalsIgnoreCase(trackName)) {
                        alreadyInQueue = true;
                        break;
                    }
                }
            }

            if (alreadyInQueue) {
                Toast.makeText(getContext(), "Already in quelist", Toast.LENGTH_SHORT).show();
            } else {
                String artist = !TextUtils.isEmpty(currentTrack.getArtistName())
                        ? currentTrack.getArtistName() : "";
                String localPath = "";
                if (filesExtractor != null && filesExtractor.isFileDownloaded(trackName)) {
                    File f = filesExtractor.getDownloadedFile(trackName);
                    if (f != null) {
                        localPath = f.getAbsolutePath();
                    }
                }
                PlayerState.Item newItem = new PlayerState.Item(
                        trackName,
                        artist,
                        currentTrack.getVideoId(),
                        currentTrack.getThumbnail(),
                        localPath
                );
                queueDbHelper.addQueueTrack(newItem);
                PlayerState.get().queue.add(newItem);
                Toast.makeText(getContext(), "Added to quelist", Toast.LENGTH_SHORT).show();
            }
            androidx.fragment.app.FragmentManager fm = getParentFragmentManager();
            dismissAllowingStateLoss();
            Bottom_queue_list.newInstance().show(fm, "bottom_queue_list");
        } else if (v.getId() == R.id.iv_dismiss) {
            dismissAllowingStateLoss();
        }
    }

    public void showRefreshDialog(String trackName,String artistName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        View dialogView = View.inflate(getContext(), R.layout.delete_notice, null);

        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnOk = dialogView.findViewById(R.id.btnOk);

        tvTitle.setText("Refresh lyrics??");
        tvMessage.setText("Are you sure you want to refresh: " + trackName);

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();
            dismissAllowingStateLoss();
            lyricsDatabaseHelper.deleteLyrics(artistName, trackName);
            PlayerState state = PlayerState.get();
            if (state.hasTrack && !TextUtils.isEmpty(state.title) && state.title.equalsIgnoreCase(trackName)) {
                state.parsedLyrics = null;
                state.lyrics = "";
                state.lyricsDurationSec = 0f;
            }
            Toast.makeText(getContext(),"Done refresh please check lyrics again!",Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

}