package com.yt.downloader.spogify.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.yt.downloader.R;
import com.yt.downloader.spogify.Utils.Image.DeezerApiArtist;
import com.yt.downloader.spogify.base.BaseQuickAdapter;
import com.yt.downloader.spogify.base.BaseViewHolder;
import com.yt.downloader.spogify.mvp.bean.FavoriteTrack;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FavoriteTrackAdapter extends BaseQuickAdapter<FavoriteTrack, BaseViewHolder> {

    public interface OnFavoriteClickListener {
        void onFavoriteClick(FavoriteTrack track);
    }

    private OnFavoriteClickListener listener;
    private final Map<String, String> imageCache = new ConcurrentHashMap<>();

    public FavoriteTrackAdapter(int layoutResId) {
        super(layoutResId);
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener listener) {
        this.listener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, FavoriteTrack item) {
        TextView tvArtistName = helper.getView(R.id.tvArtistName);
        TextView tvPlayCount = helper.getView(R.id.tvPlayCount);
        TextView tvCategory = helper.getView(R.id.tvCategory);
        ImageView ivArtistImage = helper.getView(R.id.ivArtistImage);

        tvCategory.setText("Song");
        tvArtistName.setText(item.getMusicName());
        tvPlayCount.setText(item.getArtistName());

        String trackTitle = item.getMusicName();
        ivArtistImage.setTag(trackTitle);

        String thumbUrl = item.getImageThumb();

        if (!TextUtils.isEmpty(thumbUrl)) {
            loadImage(ivArtistImage, thumbUrl);
        } else if (imageCache.containsKey(trackTitle)) {
            loadImage(ivArtistImage, imageCache.get(trackTitle));
        } else {
            loadImage(ivArtistImage, null);
            fetchDeezerTrackCover(trackTitle, ivArtistImage);
        }

        helper.getView(R.id.ll_artist).setOnClickListener(v -> {
            if (listener != null) {
                listener.onFavoriteClick(item);
            }
        });
    }

    private void fetchDeezerTrackCover(String trackTitle, ImageView imageView) {
        DeezerApiArtist.fetchArtistImageUrl(trackTitle, new DeezerApiArtist.Callback() {
            @Override
            public void onSuccess(String imageUrl) {
                if (trackTitle.equals(imageView.getTag())) {
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
        try {
            Glide.with(mContext)
                    .load(imageUrl)
                    .apply(new RequestOptions()
                            .placeholder(R.drawable.noimage)
                            .error(R.mipmap.app_logo))
                    .into(imageView);
        }catch (Exception e){
            e.printStackTrace();
        }

    }
}