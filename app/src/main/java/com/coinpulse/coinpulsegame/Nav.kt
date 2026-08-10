package com.coinpulse.coinpulsegame

import androidx.appcompat.app.AppCompatActivity

/** Navigation contract implemented by MainActivity and handed to screens. */
interface Nav {
    val activity: AppCompatActivity
    fun toMenu()
    fun toGame()
    fun toUpgrades()
    fun toCollection()
    fun toArenas()
    fun toSettings()
    fun toProfile()
    fun openWeb(fileName: String, onlineUrl: String)
    fun pickAvatarFromGallery()
    fun captureAvatarFromCamera()
}
