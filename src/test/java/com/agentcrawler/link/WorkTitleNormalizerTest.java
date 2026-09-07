package com.agentcrawler.link;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTitleNormalizerTest {

    @Test
    void extractsQuotedWorkAndEpisode() {
        WorkTitleNormalizer.NormalizedTitle title = WorkTitleNormalizer.normalize(
                "《葬送的芙莉莲》第5话 - 哔哩哔哩"
        );

        assertThat(title.workTitle()).isEqualTo("葬送的芙莉莲");
        assertThat(title.episodeHint()).isEqualTo("第5集");
        assertThat(title.confidence()).isGreaterThanOrEqualTo(0.9);
    }

    @Test
    void ignoresChallengePages() {
        assertThat(WorkTitleNormalizer.normalize("Just a moment...").hasWorkTitle()).isFalse();
    }
}
