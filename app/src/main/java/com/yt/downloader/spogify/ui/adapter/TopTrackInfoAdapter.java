package com.yt.downloader.spogify.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.yt.downloader.R;
import com.yt.downloader.spogify.Utils.Image.DeezerApiTrack;
import com.yt.downloader.spogify.Utils.lyrics.EqualizerView;
import com.yt.downloader.spogify.Utils.db.FavoriteDatabaseHelper;
import com.yt.downloader.spogify.Utils.FormatCountUtil;
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.base.BaseQuickAdapter;
import com.yt.downloader.spogify.base.BaseViewHolder;
import com.yt.downloader.spogify.mvp.bean.TrackInfoBean;
import com.yt.downloader.spogify.ui.fragment.Bottom_settings;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.AlertDialog;
import android.content.IntentSender;
import android.app.RecoverableSecurityException;
import android.os.Build;
import android.provider.MediaStore;
import android.net.Uri;
import android.app.Activity;

import java.io.File;

@SuppressLint("NotifyDataSetChanged")
public class TopTrackInfoAdapter extends BaseQuickAdapter<TrackInfoBean.Track, BaseViewHolder> {
    private static final int DELETE_REQUEST_CODE = 5012;

    private String pendingDeleteTrackName = "";

    public interface OnTrackClickListener {
        void onTrackClick(TrackInfoBean.Track track, int position, boolean isDownloaded);
    }

    public interface OnFavoriteRemovedListener {
        void onFavoriteRemoved();
    }

    public interface OnMoreClickListener {
        void onMoreClick(TrackInfoBean.Track track, int position);
    }

    private OnMoreClickListener moreClickListener;

    public void setOnMoreClickListener(OnMoreClickListener l) {
        this.moreClickListener = l;
    }

    private OnTrackClickListener listener;
    private final Map<String, String> imageCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> downloadProgressMap = new ConcurrentHashMap<>();
    private FavoriteDatabaseHelper favDbHelper;
    private FilesExtractor filesExtractor;
    private String currentPlayingTrackName = "";
    private boolean isPlaying = false;

    public TopTrackInfoAdapter() {
        super(R.layout.item_popular_track);
    }

    public void setOnTrackClickListener(OnTrackClickListener listener) {
        this.listener = listener;
    }

    public void updateDownloadProgress(String trackName, int progress) {
        Integer previous = downloadProgressMap.get(trackName);
        if (progress >= 100) {
            downloadProgressMap.remove(trackName);
        } else {
            if (previous != null && previous == progress) return;
            downloadProgressMap.put(trackName, progress);
        }

        List<TrackInfoBean.Track> data = getData();
        for (int i = 0; i < data.size(); i++) {
            if (trackName.equalsIgnoreCase(data.get(i).getName())) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    public void setPlayingTrackName(String trackName, boolean playing, int index) {
        String newName = trackName != null ? trackName : "";
        if (TextUtils.equals(currentPlayingTrackName, newName) && isPlaying == playing) {
            return;
        }
        int oldIndex = -1;
        if (!TextUtils.isEmpty(currentPlayingTrackName)) {
            List<TrackInfoBean.Track> data = getData();
            for (int i = 0; i < data.size(); i++) {
                if (currentPlayingTrackName.equalsIgnoreCase(data.get(i).getName())) {
                    oldIndex = i;
                    break;
                }
            }
        }

        currentPlayingTrackName = newName;
        isPlaying = playing;

        if (oldIndex >= 0) notifyItemChanged(oldIndex);
        if (index >= 0 && index != oldIndex) notifyItemChanged(index);
    }

    @SuppressLint("SetTextI18n")
    @Override
    protected void convert(BaseViewHolder helper, TrackInfoBean.Track item) {
        if (favDbHelper == null) {
            favDbHelper = new FavoriteDatabaseHelper(mContext);
        }
        if (filesExtractor == null) {
            filesExtractor = new FilesExtractor(mContext);
        }
        LinearLayout ll_action_buttons = helper.getView(R.id.ll_action_buttons);
        TextView tv_rank = helper.getView(R.id.tv_rank);
        TextView tv_track_title = helper.getView(R.id.tv_track_title);
        TextView tv_play_count = helper.getView(R.id.tv_play_count);
        ImageView iv_track_cover = helper.getView(R.id.iv_track_cover);
        ProgressBar pb_download_progress = helper.getView(R.id.pb_download_progress);
        EqualizerView equalizerView = helper.getView(R.id.equalizer_view);

        int position = helper.getAdapterPosition();

        boolean isDownloaded = filesExtractor.isFileDownloaded(item.getName());
        boolean isCurrentPlaying = item != null && !TextUtils.isEmpty(item.getName()) && item.getName().equalsIgnoreCase(currentPlayingTrackName);

        if (item.getAttributes() != null) {
            tv_rank.setText(item.getAttributes().getRank());
        }
        tv_track_title.setText(item.getName());
        String playCount = item.getPlaycount();

        if (isDownloaded) {
            tv_play_count.setText("Downloaded");
        } else {
            if (playCount != null) {
                try {
                    long count = Long.parseLong(playCount.trim());
                    tv_play_count.setText(new FormatCountUtil().formatListenerCount(count));
                } catch (NumberFormatException e) {
                    tv_play_count.setText("0 listeners");
                }
            } else {
                tv_play_count.setText("0 listeners");
            }

        }
        if (isCurrentPlaying && isPlaying) {
            tv_track_title.setTextColor(Color.parseColor("#1DB954"));
            tv_rank.setVisibility(View.GONE);
            equalizerView.setVisibility(View.VISIBLE);
            equalizerView.setPlaying(true);
        } else {
            tv_track_title.setTextColor(Color.WHITE);
            tv_rank.setVisibility(View.VISIBLE);
            equalizerView.setVisibility(View.GONE);
            equalizerView.setPlaying(false);
        }


        ll_action_buttons.setOnClickListener(v -> {
            if (moreClickListener != null) moreClickListener.onMoreClick(item, position);
        });


        if (downloadProgressMap.containsKey(item.getName())) {
            int progress = downloadProgressMap.get(item.getName());
            pb_download_progress.setVisibility(View.VISIBLE);
            pb_download_progress.setProgress(progress);
        } else {
            pb_download_progress.setVisibility(View.GONE);
        }

        String trackTitle = item.getName();
        iv_track_cover.setTag(trackTitle);

        helper.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onTrackClick(item, position, isDownloaded);
            }
        });

        if (item.getImageUrl() != null && item.getImageUrl().contains("i.ytimg.com")) {
            loadImage(iv_track_cover, item.getImageUrl());
        } else if (!TextUtils.isEmpty(item.getImageUrl())) {
            imageCache.put(trackTitle, item.getImageUrl());
            loadImage(iv_track_cover, item.getImageUrl());
        } else if (imageCache.containsKey(trackTitle)) {
            loadImage(iv_track_cover, imageCache.get(trackTitle));
        } else {
            fetchDeezerTrackCover(iv_track_cover, item);
        }
    }

    public void showDeleteDialog(String trackName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mContext);
        View dialogView = View.inflate(mContext, R.layout.delete_notice, null);

        builder.setView(dialogView);

        AlertDialog dialog = builder.create();

        TextView tvTitle = dialogView.findViewById(R.id.tvTitle);
        TextView tvMessage = dialogView.findViewById(R.id.tvMessage);
        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        TextView btnOk = dialogView.findViewById(R.id.btnOk);

        tvTitle.setText("Delete file?");
        tvMessage.setText("Are you sure you want to delete \"" + trackName + "\" from your downloaded music?");

        btnCancel.setOnClickListener(v -> {
            dialog.dismiss();
        });

        btnOk.setOnClickListener(v -> {
            dialog.dismiss();

            deleteDownloadedMusic(trackName);
        });

        dialog.show();
    }

    private void deleteDownloadedMusic(String trackName) {
        File file = filesExtractor.getDownloadedFile(trackName);

        if (file == null || !file.exists()) {
            Toast.makeText(mContext, "File not found", Toast.LENGTH_SHORT).show();
            notifyDataSetChanged();
            return;
        }

        Uri uri = filesExtractor.getDownloadedMusicUri(trackName);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && uri != null) {
            if (!(mContext instanceof Activity)) {
                Toast.makeText(mContext, "Cannot request delete permission", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                ArrayList<Uri> uris = new ArrayList<>();
                uris.add(uri);

                pendingDeleteTrackName = trackName;

                IntentSender intentSender = MediaStore.createDeleteRequest(mContext.getContentResolver(), uris).getIntentSender();

                ((Activity) mContext).startIntentSenderForResult(intentSender, DELETE_REQUEST_CODE, null, 0, 0, 0);

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(mContext, "Unable to request deletion", Toast.LENGTH_SHORT).show();
            }

            return;
        }

        if (uri != null) {
            try {
                int deleted = mContext.getContentResolver().delete(uri, null, null);

                if (deleted > 0) {
                    onMusicDeleted(trackName);
                    return;
                }

            } catch (SecurityException e) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q && e instanceof RecoverableSecurityException && mContext instanceof Activity) {

                    try {
                        pendingDeleteTrackName = trackName;

                        RecoverableSecurityException recoverable = (RecoverableSecurityException) e;

                        ((Activity) mContext).startIntentSenderForResult(recoverable.getUserAction().getActionIntent().getIntentSender(), DELETE_REQUEST_CODE, null, 0, 0, 0);

                    } catch (IntentSender.SendIntentException ex) {
                        ex.printStackTrace();
                    }

                    return;
                }

                Toast.makeText(mContext, "Permission denied", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (file.delete()) {
            onMusicDeleted(trackName);
        } else {
            Toast.makeText(mContext, "Failed to delete music", Toast.LENGTH_SHORT).show();
        }
    }

    private void onMusicDeleted(String trackName) {
        Toast.makeText(mContext, "Music deleted successfully", Toast.LENGTH_SHORT).show();

        downloadProgressMap.remove(trackName);
        dismissBottomSheet();
        notifyDataSetChanged();
    }

    private void dismissBottomSheet() {
        if (mContext instanceof androidx.fragment.app.FragmentActivity) {
            androidx.fragment.app.FragmentManager fm =
                    ((androidx.fragment.app.FragmentActivity) mContext).getSupportFragmentManager();
            androidx.fragment.app.Fragment f = fm.findFragmentByTag("bottom_settings");
            if (f instanceof Bottom_settings) {
                ((Bottom_settings) f).dismissAllowingStateLoss();
            }
        }
    }
    public void onDeleteMusicResult(int requestCode, int resultCode) {
        if (requestCode != DELETE_REQUEST_CODE) return;

        String trackName = pendingDeleteTrackName;
        pendingDeleteTrackName = "";

        if (resultCode == Activity.RESULT_OK) {
            if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                deleteDownloadedMusic(trackName);
            } else {
                onMusicDeleted(trackName);
            }
        } else {
            Toast.makeText(mContext, "Deletion cancelled", Toast.LENGTH_SHORT).show();
        }
    }

    private void fetchDeezerTrackCover(ImageView imageView, TrackInfoBean.Track item) {
        DeezerApiTrack.fetchArtistImageUrl(item.getName(), new DeezerApiTrack.Callback() {
            @Override
            public void onSuccess(String imageUrl) {
                if (imageUrl != null) {
                    imageCache.put(item.getName(), imageUrl);
                    item.setImageUrl(imageUrl);
                }
                if (item.getName().equals(imageView.getTag())) {
                    loadImage(imageView, imageUrl);
                }
            }

            @Override
            public void onFailure(Exception e) {
                e.printStackTrace();
            }
        });
    }

    private void loadImage(ImageView imageView, String imageUrl) {
        if (imageUrl == null) return;

        Object currentTag = imageView.getTag(R.id.iv_track_cover);
        if (imageUrl.equals(currentTag)) return;
        imageView.setTag(R.id.iv_track_cover, imageUrl);

        try {
            Glide.with(mContext).asBitmap().apply(new RequestOptions().placeholder(R.mipmap.app_logo).error(R.mipmap.app_logo).dontAnimate()).load(imageUrl).into(imageView);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}