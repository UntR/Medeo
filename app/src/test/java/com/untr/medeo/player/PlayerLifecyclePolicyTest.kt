package com.untr.medeo.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerLifecyclePolicyTest {
    @Test
    fun transientAudioFocusSuppression_requiresExplicitPause() {
        assertTrue(
            shouldPauseForPlaybackSuppression(
                Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS
            )
        )
        assertFalse(
            shouldPauseForPlaybackSuppression(Player.PLAYBACK_SUPPRESSION_REASON_NONE)
        )
    }

    @Test
    fun focusLossAndNoisyOutput_flushOnlyWhenPlaybackStops() {
        assertTrue(
            shouldFlushForPlayWhenReadyChange(
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            )
        )
        assertTrue(
            shouldFlushForPlayWhenReadyChange(
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY
            )
        )
        assertFalse(
            shouldFlushForPlayWhenReadyChange(
                playWhenReady = true,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS
            )
        )
        assertFalse(
            shouldFlushForPlayWhenReadyChange(
                playWhenReady = false,
                reason = Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST
            )
        )
    }
}
