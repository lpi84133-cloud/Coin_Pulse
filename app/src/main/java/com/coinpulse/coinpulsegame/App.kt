package com.coinpulse.coinpulsegame

import com.coinpulse.coinpulsegame.audio.SfxBank
import com.coinpulse.coinpulsegame.data.GameStore
import com.coinpulse.coinpulsegame.game.Assets

/** Simple process-wide holder for the shared singletons. */
object App {
    lateinit var store: GameStore
    lateinit var sound: SfxBank
    lateinit var assets: Assets
    var assetsReady = false
}
