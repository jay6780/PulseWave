package com.yt.downloader.spogify.ui.adapter;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;
import com.yt.downloader.R;
import com.yt.downloader.spogify.Utils.lyrics.EqualizerView;
import com.yt.downloader.spogify.Utils.lyrics.PlayerState;
import com.yt.downloader.spogify.base.BaseQuickAdapter;
import com.yt.downloader.spogify.base.BaseViewHolder;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public class QueueAdapter extends BaseQuickAdapter<PlayerState.Item, BaseViewHolder> {

    public interface OnDragStartListener {
        void onStartDrag(BaseViewHolder holder);
    }

    public interface OnSelectionChangedListener {
        void onSelectionModeChanged(boolean active, int selectedCount, int totalCount);
    }

    public interface OnQueueItemClickListener {
        void onQueueItemClick(int position, PlayerState.Item item);
    }

    private boolean selectionMode = false;
    private final Set<PlayerState.Item> selectedItems = new HashSet<>();
    private OnDragStartListener dragStartListener;
    private OnSelectionChangedListener selectionChangedListener;
    private OnQueueItemClickListener queueItemClickListener;

    public QueueAdapter(List<PlayerState.Item> data) {
        super(R.layout.item_queue_track, data);
    }

    public void setOnDragStartListener(OnDragStartListener listener) {
        this.dragStartListener = listener;
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.selectionChangedListener = listener;
    }

    public void setOnQueueItemClickListener(OnQueueItemClickListener listener) {
        this.queueItemClickListener = listener;
    }

    public boolean isSelectionMode() {
        return selectionMode;
    }

    public void setSelectionMode(boolean active) {
        this.selectionMode = active;
        if (!active) {
            selectedItems.clear();
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void selectAll(boolean select) {
        selectedItems.clear();
        if (select) {
            selectedItems.addAll(getData());
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public boolean isAllSelected() {
        List<PlayerState.Item> data = getData();
        return !data.isEmpty() && selectedItems.size() == data.size();
    }

    public int getSelectedCount() {
        return selectedItems.size();
    }

    public void deleteSelected() {
        if (selectedItems.isEmpty()) return;
        PlayerState state = PlayerState.get();
        List<PlayerState.Item> queue = state.queue;
        PlayerState.Item currentItem = (state.queueIndex >= 0 && state.queueIndex < queue.size())
                ? queue.get(state.queueIndex)
                : null;

        Iterator<PlayerState.Item> iterator = queue.iterator();
        while (iterator.hasNext()) {
            PlayerState.Item item = iterator.next();
            if (selectedItems.contains(item)) {
                iterator.remove();
            }
        }

        if (currentItem != null) {
            state.queueIndex = queue.indexOf(currentItem);
        } else if (state.queueIndex >= queue.size()) {
            state.queueIndex = queue.isEmpty() ? -1 : 0;
        }

        selectedItems.clear();
        selectionMode = false;
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void toggleSelection(PlayerState.Item item) {
        if (selectedItems.contains(item)) {
            selectedItems.remove(item);
        } else {
            selectedItems.add(item);
        }
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionChangedListener != null) {
            selectionChangedListener.onSelectionModeChanged(selectionMode, selectedItems.size(), getData().size());
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void convert(BaseViewHolder helper, PlayerState.Item item) {
        CheckBox cbSelect = helper.getView(R.id.cb_select);
        ImageView ivThumb = helper.getView(R.id.iv_queue_thumb);
        TextView tvTitle = helper.getView(R.id.tv_queue_title);
        TextView tvArtist = helper.getView(R.id.tv_queue_artist);
        EqualizerView equalizerView = helper.getView(R.id.equalizer_view);
        ImageView ivDragHandle = helper.getView(R.id.iv_drag_handle);

        tvTitle.setText(!TextUtils.isEmpty(item.name) ? item.name : "Unknown Track");
        tvArtist.setText(!TextUtils.isEmpty(item.artist) ? item.artist : "Unknown Artist");

        PlayerState state = PlayerState.get();
        boolean isCurrentTrack = false;
        if (state.hasTrack) {
            if (state.isLocal && !TextUtils.isEmpty(state.localPath) && !TextUtils.isEmpty(item.localPath)) {
                isCurrentTrack = state.localPath.equals(item.localPath);
            } else if (!state.isLocal && !TextUtils.isEmpty(state.videoId) && !TextUtils.isEmpty(item.videoId)) {
                isCurrentTrack = state.videoId.equals(item.videoId);
            } else if (!TextUtils.isEmpty(state.title) && !TextUtils.isEmpty(item.name)) {
                isCurrentTrack = state.title.trim().equalsIgnoreCase(item.name.trim());
            }
        }

        if (isCurrentTrack && state.isPlaying) {
            tvTitle.setTextColor(Color.parseColor("#1DB954"));
            equalizerView.setVisibility(View.VISIBLE);
            equalizerView.setPlaying(true);
        } else {
            tvTitle.setTextColor(Color.WHITE);
            equalizerView.setVisibility(View.GONE);
            equalizerView.setPlaying(false);
        }

        if (!TextUtils.isEmpty(item.thumb)) {
            Glide.with(mContext)
                    .load(item.thumb)
                    .apply(new RequestOptions()
                            .transform(new CenterCrop(), new RoundedCorners(10))
                            .placeholder(R.mipmap.app_logo)
                            .error(R.mipmap.app_logo))
                    .into(ivThumb);
        } else {
            ivThumb.setImageResource(R.mipmap.app_logo);
        }

        cbSelect.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        cbSelect.setOnCheckedChangeListener(null);
        cbSelect.setChecked(selectedItems.contains(item));
        cbSelect.setOnClickListener(v -> toggleSelection(item));

        helper.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                toggleSelection(item);
            } else if (queueItemClickListener != null) {
                int clickPos = helper.getAdapterPosition();
                if (clickPos >= 0) {
                    queueItemClickListener.onQueueItemClick(clickPos, item);
                }
            }
        });

        int touchSlop = ViewConfiguration.get(mContext).getScaledTouchSlop();
        final float[] downPos = new float[2];
        final boolean[] dragStarted = new boolean[1];

        ivDragHandle.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downPos[0] = event.getX();
                    downPos[1] = event.getY();
                    dragStarted[0] = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!dragStarted[0]) {
                        float dx = Math.abs(event.getX() - downPos[0]);
                        float dy = Math.abs(event.getY() - downPos[1]);
                        if (dy > touchSlop || dx > touchSlop) {
                            dragStarted[0] = true;
                            if (dragStartListener != null) {
                                dragStartListener.onStartDrag(helper);
                            }
                        }
                    }
                    break;
            }
            return false;
        });

        ivDragHandle.setOnClickListener(v -> {
            if (!selectionMode) {
                selectionMode = true;
                selectedItems.add(item);
                notifyDataSetChanged();
                notifySelectionChanged();
            } else {
                setSelectionMode(false);
            }
        });
    }
}
