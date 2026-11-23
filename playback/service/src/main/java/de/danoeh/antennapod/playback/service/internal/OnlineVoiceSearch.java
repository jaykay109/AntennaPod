package de.danoeh.antennapod.playback.service.internal;

import android.content.Context;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import de.danoeh.antennapod.model.download.DownloadRequest;
import de.danoeh.antennapod.model.feed.Feed;
import de.danoeh.antennapod.net.common.NetworkUtils;
import de.danoeh.antennapod.net.discovery.CombinedSearcher;
import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import de.danoeh.antennapod.net.discovery.PodcastSearcherRegistry;
import de.danoeh.antennapod.net.download.service.feed.remote.Downloader;
import de.danoeh.antennapod.net.download.service.feed.remote.FeedParserTask;
import de.danoeh.antennapod.net.download.service.feed.remote.HttpDownloader;
import de.danoeh.antennapod.net.download.serviceinterface.DownloadRequestCreator;
import de.danoeh.antennapod.parser.feed.FeedHandlerResult;
import de.danoeh.antennapod.storage.database.DBReader;
import de.danoeh.antennapod.storage.database.DBWriter;
import de.danoeh.antennapod.storage.database.FeedDatabaseWriter;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class OnlineVoiceSearch {
    private static final String TAG = "OnlineVoiceSearch";
    private static final int TIMEOUT_SECONDS = 10;

    private OnlineVoiceSearch() {
    }

    @WorkerThread
    @Nullable
    public static Feed findFeed(Context context, String term, String normalizedQuery, boolean trusted) {
        try {
            if (term.isEmpty() || !NetworkUtils.networkAvailable() || !NetworkUtils.isFeedRefreshAllowed()) {
                return null;
            }
            List<PodcastSearchResult> results = new CombinedSearcher().search(term)
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet();
            PodcastSearchResult result = pickResult(results, normalizedQuery, trusted);
            if (result == null || result.feedUrl == null) {
                Log.d(TAG, "No confident online match for " + term);
                return null;
            }
            String url = PodcastSearcherRegistry.lookupUrl(result.feedUrl)
                    .timeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).blockingGet();
            for (Feed feed : DBReader.getFeedList()) {
                if (url.equals(feed.getDownloadUrl())) {
                    return feed;
                }
            }
            DownloadRequest request = DownloadRequestCreator.create(new Feed(url, null))
                    .withInitiatedByUser(true).build();
            Downloader downloader = new HttpDownloader(request);
            downloader.call();
            if (!downloader.getResult().isSuccessful()) {
                Log.d(TAG, "Feed download failed for " + url);
                return null;
            }
            FeedHandlerResult parsed = new FeedParserTask(request).call();
            if (parsed == null) {
                return null;
            }
            Feed feed = parsed.feed;
            feed.setState(Feed.STATE_NOT_SUBSCRIBED);
            feed.setLastRefreshAttempt(System.currentTimeMillis());
            Feed saved = FeedDatabaseWriter.updateFeed(context, feed, false);
            saved.getPreferences().setKeepUpdated(false);
            DBWriter.setFeedPreferences(saved.getPreferences()).get();
            return saved;
        } catch (Exception e) {
            Log.e(TAG, "Online voice search failed", e);
            return null;
        }
    }

    @Nullable
    static PodcastSearchResult pickResult(List<PodcastSearchResult> results, String normalizedQuery,
                                          boolean trusted) {
        String paddedQuery = " " + normalizedQuery + " ";
        for (PodcastSearchResult result : results) {
            String title = MediaLibrarySessionCallback.normalize(result.title);
            if (!title.isEmpty() && paddedQuery.contains(" " + title + " ")) {
                return result;
            }
        }
        if (trusted && !results.isEmpty()
                && MediaLibrarySessionCallback.normalize(results.get(0).title).contains(normalizedQuery)) {
            return results.get(0);
        }
        return null;
    }
}
