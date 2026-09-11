package com.miku.gamingsidebar.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LauncherHideManager(private val context: Context) {

    suspend fun resetAllToDefaultState(games: List<GameModel>) = withContext(Dispatchers.IO) {
        val commands = mutableListOf<String>()

        // 1. Reset specific known multi-alias games to their exact default APK state
        val multiAliasPackages = listOf(
            "com.geode.launcher",
            "com.roblox.client",
            "com.raongames.growcastle",
            "com.robtopx.geometryjump",
            "com.dts.freefiremax",
            "com.dogbytegames.otrnext",
            "com.mmo.android",
            "com.nintendo.znca",
            "com.nintendo.znej",
            "com.dualcarbon.universaltrucksimulator",
            "com.BStudio.PCSimulator",
            "com.Yiming.PC",
            "com.prineside.tdi2",
            "io.anuke.mindustry",
            "com.supercell.brawlstars",
            "com.mojang.minecraftpe"
        )

        for (pkg in multiAliasPackages) {
            commands.add("pm default-state --user 0 $pkg")
            commands.add("pm default-state $pkg")
        }

        for (game in games) {
            commands.add("pm default-state --user 0 ${game.packageName}")
            commands.add("pm default-state ${game.packageName}")
        }

        // Disable specifically the extra Geode Pride/Trans/Sapphire theme aliases
        commands.add("pm disable --user 0 com.geode.launcher/com.geode.launcher.MainActivityPride")
        commands.add("pm disable --user 0 com.geode.launcher/com.geode.launcher.MainActivityTrans")
        commands.add("pm disable --user 0 com.geode.launcher/com.geode.launcher.MainActivitySapphire")
        commands.add("pm disable com.geode.launcher/com.geode.launcher.MainActivityPride")
        commands.add("pm disable com.geode.launcher/com.geode.launcher.MainActivityTrans")
        commands.add("pm disable com.geode.launcher/com.geode.launcher.MainActivitySapphire")

        // Force-stop launcher so it reloads clean defaults immediately
        commands.add("am force-stop com.google.android.apps.nexuslauncher")
        commands.add("am force-stop com.android.launcher3")

        RootHelper.runCommandsAsRoot(commands)
    }
}
