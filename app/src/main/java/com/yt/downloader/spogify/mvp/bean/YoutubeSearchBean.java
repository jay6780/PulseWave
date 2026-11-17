package com.yt.downloader.spogify.mvp.bean;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class YoutubeSearchBean {

    private List<ItemsBean> items;

    public List<ItemsBean> getItems() {
        return items;
    }

    public void setItems(List<ItemsBean> items) {
        this.items = items;
    }

    public static class ItemsBean {
        /**
         * duration : 3:25
         * id : mhoSmDvePV4
         * thumbnail : https://i.ytimg.com/vi/mhoSmDvePV4/hq720.jpg?sqp=-oaymwEgCOgCEMoBSFXyq4qpAxIIARUAAIhCGAFwAcABBrgC8xg=&rs=AOn4CLCCtVpbpcsqNwdHJ-BUAVKoxXROow
         * title : Rockstar - Parting Time (Lyrics)
         * url : https://www.youtube.com/watch?v=mhoSmDvePV4
         */

        private String duration;
        private String id;
        private String thumbnail;
        private String title;
        private String url;

        public String getDuration() {
            return duration;
        }

        public void setDuration(String duration) {
            this.duration = duration;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getThumbnail() {
            return thumbnail;
        }

        public void setThumbnail(String thumbnail) {
            this.thumbnail = thumbnail;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getArtistName() {
            return getArtistName(null);
        }

        public String getArtistName(String searchQuery) {
            if (title == null || title.trim().isEmpty()) {
                return "";
            }

            // Strip bracketed/parenthesized tags, hashtags, and non-ASCII symbols/emojis
            String cleaned = title
                    .replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "")
                    .replaceAll("\\s*#\\S+", "")
                    .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "")
                    .trim();

            // Pattern 1: "<Artist> performs \"<Song>\" ..."
            Matcher performsMatcher = Pattern.compile("^(.+?)\\s+performs\\s+[\"“].+", Pattern.CASE_INSENSITIVE).matcher(cleaned);
            if (performsMatcher.find()) {
                return performsMatcher.group(1).trim();
            }

            // Pattern 2: Split on common title separators (-, –, |)
            String[] parts = cleaned.split("\\s*[-–|]\\s*");
            if (parts.length >= 2) {
                String first = parts[0].trim();
                String second = parts[1].trim();

                if (searchQuery != null && !searchQuery.trim().isEmpty()) {
                    String normalizedQuery = searchQuery.trim().toLowerCase();
                    if (first.toLowerCase().contains(normalizedQuery) && !second.isEmpty()) {
                        return second;
                    }
                }
                if (!first.isEmpty()) {
                    return first;
                }
            }

            return "";
        }
        public String getPlainTitle(String artistName) {
            return extractPlainTitle(title, artistName);
        }

        public static String extractPlainTitle(String rawTitle, String artistName) {
            if (rawTitle == null || rawTitle.trim().isEmpty()) {
                return "";
            }

            String cleaned = rawTitle
                    .replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]", "")
                    .replaceAll("\\s*#\\S+", "")
                    .replaceAll("[^\\p{L}\\p{N}\\p{P}\\p{Z}]", "")
                    .trim();

            Matcher performsMatcher = Pattern.compile("^.+?\\s+performs\\s+[\"“]([^\"”]+)[\"”].*", Pattern.CASE_INSENSITIVE).matcher(cleaned);
            if (performsMatcher.find()) {
                return performsMatcher.group(1).trim();
            }

            String[] parts = cleaned.split("\\s*[-–|]\\s*");
            if (parts.length >= 2) {
                String first = parts[0].trim();
                String second = parts[1].trim();
                if (artistName != null && !artistName.trim().isEmpty()) {
                    String normalizedArtist = artistName.trim().toLowerCase();
                    if (first.toLowerCase().equals(normalizedArtist) && !second.isEmpty()) {
                        return second;
                    }
                    if (second.toLowerCase().equals(normalizedArtist) && !first.isEmpty()) {
                        return first;
                    }
                }
                if (!first.isEmpty()) {
                    return first;
                }
            }

            return cleaned;
        }
    }
}
