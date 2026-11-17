package com.yt.downloader.spogify.ui.adapter;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.RecoverableSecurityException;
import android.content.IntentSender;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.yt.downloader.R;
import com.yt.downloader.spogify.Utils.db.FavoriteDatabaseHelper;
import com.yt.downloader.spogify.Utils.fileUtils.FilesExtractor;
import com.yt.downloader.spogify.base.BaseQuickAdapter;
import com.yt.downloader.spogify.base.BaseViewHolder;
import com.yt.downloader.spogify.mvp.bean.SearchTotalBean;
import com.yt.downloader.spogify.ui.fragment.Bottom_settings;
import com.yt.downloader.spogify.ui.fragment.Bottom_settings_search;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class MusicListAdapter extends BaseQuickAdapter<SearchTotalBean, BaseViewHolder> {
    private static final int DELETE_REQUEST_CODE = 5012;

    private String pendingDeleteTrackName = "";
    private FavoriteDatabaseHelper favDbHelper;
    private final Map<String, Integer> downloadProgressMap = new ConcurrentHashMap<>();
    private FilesExtractor filesExtractor;
    public interface OnMoreClickListener {
        void onMoreClick(SearchTotalBean item, int position);
    }

    public interface OnMusicDeletedListener {
        void onMusicDeleted(String trackName);
    }

    private OnMoreClickListener moreClickListener;
    private OnMusicDeletedListener musicDeletedListener;

    public void setOnMoreClickListener(MusicListAdapter.OnMoreClickListener l) {
        this.moreClickListener = l;
    }

    public void setOnMusicDeletedListener(OnMusicDeletedListener listener) {
        this.musicDeletedListener = listener;
    }

    public MusicListAdapter() {
        super(R.layout.music_item);
    }

    @Override
    protected void convert(BaseViewHolder helper, SearchTotalBean item) {
        if (favDbHelper == null) {
            favDbHelper = new FavoriteDatabaseHelper(mContext);
        }
        if (filesExtractor == null) {
            filesExtractor = new FilesExtractor(mContext);
        }
        int position = helper.getAdapterPosition();
        TextView tv_title = helper.getView(R.id.tv_title);
        TextView tv_duration = helper.getView(R.id.tv_duration);
        ImageView iv_icon = helper.getView(R.id.iv_icon);
        ImageView more = helper.getView(R.id.more);
        more.setVisibility(item.isArtist()? View.GONE : View.VISIBLE);

        try {
            Glide.with(mContext)
                    .load(item.getThumbnail())
                    .apply(new RequestOptions()
                            .transform(new CenterCrop(), new RoundedCorners(16))
                            .placeholder(R.drawable.noimage)
                            .error(R.drawable.noimage))
                    .into(iv_icon);

        } catch (Exception e) {
            e.printStackTrace();
        }


        tv_title.setText(item.getName());
        tv_duration.setText(item.getDuration());


        more.setOnClickListener(v -> {
            if (moreClickListener != null) moreClickListener.onMoreClick(item, position);
        });


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
        if (musicDeletedListener != null) {
            musicDeletedListener.onMusicDeleted(trackName);
        }
        dismissBottomSheet();
        notifyDataSetChanged();
    }

    private void dismissBottomSheet() {
        if (mContext instanceof androidx.fragment.app.FragmentActivity) {
            androidx.fragment.app.FragmentManager fm =
                    ((androidx.fragment.app.FragmentActivity) mContext).getSupportFragmentManager();
            if (dismissSheetInManager(fm)) {
                return;
            }
            for (androidx.fragment.app.Fragment parent : fm.getFragments()) {
                if (parent != null && parent.isAdded() && dismissSheetInManager(parent.getChildFragmentManager())) {
                    return;
                }
            }
        }
    }

    private boolean dismissSheetInManager(androidx.fragment.app.FragmentManager fm) {
        androidx.fragment.app.Fragment f = fm.findFragmentByTag("bottom_settings_search");
        if (f instanceof Bottom_settings_search) {
            ((Bottom_settings_search) f).dismissAllowingStateLoss();
            return true;
        }
        return false;
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

    public void updateDownloadProgress(String trackName, int progress) {
        Integer previous = downloadProgressMap.get(trackName);
        if (progress >= 100) {
            downloadProgressMap.remove(trackName);
        } else {
            if (previous != null && previous == progress) return;
            downloadProgressMap.put(trackName, progress);
        }

        List<SearchTotalBean> data = getData();
        for (int i = 0; i < data.size(); i++) {
            if (trackName.equalsIgnoreCase(data.get(i).getName())) {
                notifyItemChanged(i);
                break;
            }
        }
    }
}