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
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.mvp.bean.TrackInfoBean;

public class Bottom_settings extends BottomSheetDialogFragment implements View.OnClickListener {

    private FragmentBottomSettingsBinding binding;
    private TrackInfoBean.Track currentTrack;
    private String artistName = "";
    private BottomSheetActionListener actionListener;
    private FavoriteDatabaseHelper favDbHelper;
    private LyricsDatabaseHelper lyricsDatabaseHelper;
    private FilesExtractor filesExtractor;


    public interface BottomSheetActionListener {
        void onFavoriteClicked(TrackInfoBean.Track track);

        void onDownloadClicked(TrackInfoBean.Track track);
    }

    public static Bottom_settings newInstance() {
        return new Bottom_settings();
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
    public void bindTrack(TrackInfoBean.Track track, String artistName) {
        this.currentTrack = track;
        this.artistName = artistName == null ? "" : artistName;
        if (binding != null) {
            refreshUi();
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
        filesExtractor = new FilesExtractor(requireContext());
        binding.llFavorites.setOnClickListener(this);
        binding.llDownload.setOnClickListener(this);
        binding.llRefreshLyrics.setOnClickListener(this);
        binding.ivDismiss.setOnClickListener(this);

        refreshUi();
    }

    private void refreshUi() {
        if (binding == null || currentTrack == null) return;

        binding.tvTitle.setText(currentTrack.getName());
        binding.tvArtist.setText(TextUtils.isEmpty(artistName) ? "Unknown Artist" : artistName);

        // Thumbnail
        String imageUrl = currentTrack.getImageUrl();
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
        if (isDownloaded) {
            binding.ivDownload.setImageResource(R.drawable.baseline_delete_24);
            binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
        } else {
            binding.ivDownload.setImageResource(android.R.drawable.stat_sys_download);
            binding.ivDownload.setColorFilter(Color.parseColor("#B3B3B3"));
        }
        binding.tvDownload.setText(isDownloaded ? "Downloaded" : "Not downloaded");
        binding.tvFavorites.setText(isFavorite ? "Added to favorites" : "Add to favorites");
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
            if (currentTrack == null) return;
            if (actionListener != null) {
                actionListener.onDownloadClicked(currentTrack);
            }
            refreshUi();
        } else if (v.getId() == R.id.ll_refreshLyrics) {
            showRefreshDialog(currentTrack.getName(),currentTrack.getArtist().getName());
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
            lyricsDatabaseHelper.deleteLyrics(artistName,trackName);
            Toast.makeText(getContext(),"Done refresh please check lyrics again!",Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }

}