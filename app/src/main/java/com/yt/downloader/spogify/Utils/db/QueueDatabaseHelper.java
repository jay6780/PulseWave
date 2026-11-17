package com.yt.downloader.spogify.Utils.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import com.yt.downloader.spogify.Utils.lyrics.PlayerState;

import java.util.ArrayList;
import java.util.List;

public class QueueDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "spogify_queue.db";
    private static final int DATABASE_VERSION = 1;

    public static final String TABLE_QUEUE = "queue_tracks";
    public static final String COLUMN_POSITION = "position";
    public static final String COLUMN_TRACK_NAME = "musicName";
    public static final String COLUMN_ARTIST_NAME = "artistName";
    public static final String COLUMN_VIDEO_ID = "videoId";
    public static final String COLUMN_IMAGE_THUMB = "imageThumb";
    public static final String COLUMN_LOCAL_PATH = "localPath";

    private static final String TABLE_CREATE =
            "CREATE TABLE " + TABLE_QUEUE + " (" +
                    COLUMN_TRACK_NAME + " TEXT PRIMARY KEY, " +
                    COLUMN_ARTIST_NAME + " TEXT, " +
                    COLUMN_VIDEO_ID + " TEXT, " +
                    COLUMN_IMAGE_THUMB + " TEXT, " +
                    COLUMN_LOCAL_PATH + " TEXT, " +
                    COLUMN_POSITION + " INTEGER);";

    public QueueDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(TABLE_CREATE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_QUEUE);
        onCreate(db);
    }

    public boolean isTrackInQueue(String musicName) {
        if (TextUtils.isEmpty(musicName)) return false;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_QUEUE,
                new String[]{COLUMN_TRACK_NAME},
                "LOWER(" + COLUMN_TRACK_NAME + ") = LOWER(?)",
                new String[]{musicName.trim()},
                null, null, null
        );
        boolean exists = (cursor != null && cursor.getCount() > 0);
        if (cursor != null) cursor.close();
        db.close();
        return exists;
    }

    public boolean addQueueTrack(PlayerState.Item item) {
        if (item == null || TextUtils.isEmpty(item.name)) return false;
        if (isTrackInQueue(item.name)) {
            return false;
        }

        SQLiteDatabase db = this.getWritableDatabase();
        int nextPos = 0;
        Cursor cursor = db.rawQuery("SELECT MAX(" + COLUMN_POSITION + ") FROM " + TABLE_QUEUE, null);
        if (cursor != null) {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                nextPos = cursor.getInt(0) + 1;
            }
            cursor.close();
        }

        ContentValues values = new ContentValues();
        values.put(COLUMN_TRACK_NAME, item.name.trim());
        values.put(COLUMN_ARTIST_NAME, item.artist);
        values.put(COLUMN_VIDEO_ID, item.videoId);
        values.put(COLUMN_IMAGE_THUMB, item.thumb);
        values.put(COLUMN_LOCAL_PATH, item.localPath);
        values.put(COLUMN_POSITION, nextPos);

        long result = db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
        return result != -1;
    }

    public List<PlayerState.Item> getAllQueueTracks() {
        List<PlayerState.Item> list = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT * FROM " + TABLE_QUEUE + " ORDER BY " + COLUMN_POSITION + " ASC", null);

        if (cursor != null && cursor.moveToFirst()) {
            do {
                String musicName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TRACK_NAME));
                String artistName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_ARTIST_NAME));
                String videoId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VIDEO_ID));
                String imageThumb = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_IMAGE_THUMB));
                String localPath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LOCAL_PATH));
                list.add(new PlayerState.Item(musicName, artistName, videoId, imageThumb, localPath));
            } while (cursor.moveToNext());
            cursor.close();
        }
        db.close();
        return list;
    }

    public void replaceQueue(List<PlayerState.Item> items) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(TABLE_QUEUE, null, null);
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    PlayerState.Item item = items.get(i);
                    if (item == null || TextUtils.isEmpty(item.name)) continue;
                    ContentValues values = new ContentValues();
                    values.put(COLUMN_TRACK_NAME, item.name.trim());
                    values.put(COLUMN_ARTIST_NAME, item.artist);
                    values.put(COLUMN_VIDEO_ID, item.videoId);
                    values.put(COLUMN_IMAGE_THUMB, item.thumb);
                    values.put(COLUMN_LOCAL_PATH, item.localPath);
                    values.put(COLUMN_POSITION, i);
                    db.insertWithOnConflict(TABLE_QUEUE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
    }
}
