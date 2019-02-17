package com.swoval.files
package impl
import com.swoval.files.FileTreeDataViews.Entry
import com.swoval.functional.IOFunction

object TestEntries {
  def get[T](typedPath: TypedPath,
             converter: IOFunction[TypedPath, T],
             converterPath: TypedPath): Entry[T] =
    Entries.get(typedPath, converter, converterPath)
  val FILE = Entries.FILE
  val DIRECTORY = Entries.DIRECTORY
  val LINK = Entries.LINK
  val NONEXISTENT = Entries.NONEXISTENT
  val UNKNOWN = Entries.UNKNOWN
}
