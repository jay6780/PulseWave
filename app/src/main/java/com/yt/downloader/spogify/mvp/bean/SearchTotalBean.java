package com.yt.downloader.spogify.mvp.bean;

public class SearchTotalBean {
    private String thumbnail;
    private String name;
    private String duration;
    private String videoId;
    private String artistName;
    private String rawTitle;

    private boolean isArtist;


    public SearchTotalBean(String thumbnail,String name,String duration,String videoId,boolean isArtist){
        this(thumbnail, name, duration, videoId, "", isArtist);
    }

    public SearchTotalBean(String thumbnail,String name,String duration,String videoId,String artistName,boolean isArtist){
        this.thumbnail = thumbnail;
        this.name = name;
        this.duration = duration;
        this.isArtist = isArtist;
        this.videoId = videoId;
        this.artistName = artistName;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getVideoId() {
        return videoId;
    }

    public void setVideoId(String videoId) {
        this.videoId = videoId;
    }

    public String getArtistName() {
        return artistName;
    }

    public void setArtistName(String artistName) {
        this.artistName = artistName;
    }

    public String getRawTitle() {
        return rawTitle;
    }

    public void setRawTitle(String rawTitle) {
        this.rawTitle = rawTitle;
    }

    public boolean isArtist() {
        return isArtist;
    }

    public void setArtist(boolean artist) {
        isArtist = artist;
    }
}
