package de.danoeh.antennapod.playback.service.internal;

import de.danoeh.antennapod.net.discovery.PodcastSearchResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

@RunWith(RobolectricTestRunner.class)
public class OnlineVoiceSearchTest {
    private final PodcastSearchResult bastards = new PodcastSearchResult("Behind the Bastards", null, "a", null);
    private final PodcastSearchResult other = new PodcastSearchResult("Bastards Weekly", null, "b", null);
    private final List<PodcastSearchResult> results = Arrays.asList(other, bastards);

    @Test
    public void picksTitleContainedInQuery() {
        assertEquals(bastards, OnlineVoiceSearch.pickResult(results,
                "the episode of behind the bastards about henry kissinger", false));
    }

    @Test
    public void rejectsUnrelatedResults() {
        assertNull(OnlineVoiceSearch.pickResult(results, "the latest episode of hard fork", false));
    }

    @Test
    public void trustsAssistantPodcastName() {
        assertEquals(other, OnlineVoiceSearch.pickResult(results, "bastards", true));
    }
}
