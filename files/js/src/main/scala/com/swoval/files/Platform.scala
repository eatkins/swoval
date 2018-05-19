package com.swoval.files

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport

object Platform {
  def isMac(): Boolean = os.platform() == "darwin"
  def isWin(): Boolean = os.platform() == "win32"
}
@js.native
@JSImport("os", JSImport.Default)
private object os extends js.Object {
  def platform(): String = js.native
}
