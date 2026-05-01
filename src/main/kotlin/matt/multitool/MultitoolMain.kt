package matt.multitool

import androidx.compose.runtime.remember
import matt.compose.app.myApplication
import matt.compose.controls.window.main.MyMainWindow
import matt.compose.controls.window.state.MyWindowState
import matt.compose.state.win.HardWindowState
import matt.model.k.kstruct.mod.uniqueCamelCaseName
import matt.multitool.redis.RedisToolPane
import matt.rstruct.desktop.modId

fun main() {
    val appInstanceNumber = 0 /*still stupid*/
    val hardWindow =
        HardWindowState(
            key = "main-window-multitool",
            appInstanceNumber = appInstanceNumber
        )
    myApplication(
        appName = "Multitool",
        appId = modId.uniqueCamelCaseName,
        logFile = null
    ) { scope ->
        val windowState =
            remember(scope) {
                MyWindowState(hardWindow.createReal(scope))
            }
        MyMainWindow(windowState) {
            RedisToolPane()
        }
    }
}
