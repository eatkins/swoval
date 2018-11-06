// Do not edit this file manually. It is autogenerated.

package com.swoval.files

import com.swoval.functional.Either.leftProjection
import com.swoval.functional.Filters.AllPass
import com.swoval.files.FileTreeDataViews.Converter
import com.swoval.files.FileTreeDataViews.Entry
import com.swoval.files.FileTreeViews.Updates
import com.swoval.functional.Either
import com.swoval.functional.Filter
import java.io.IOException
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.ArrayList
import java.util.Collection
import java.util.Collections
import java.util.HashMap
import java.util.Iterator
import java.util.List
import java.util.Map
import java.util.concurrent.atomic.AtomicReference
import CachedDirectoryImpl._
import scala.beans.{ BeanProperty, BooleanBeanProperty }

object CachedDirectoryImpl {

  private trait ListTransformer[T, R] {

    def apply(entry: Entry[T]): R

  }

  /**
   * Returns the name components of a path in an array.
   *
   * @param path The path from which we extract the parts.
   * @return Empty array if the path is an empty relative path, otherwise return the name parts.
   */
  private def parts(path: Path): List[Path] = {
    val it: Iterator[Path] = path.iterator()
    val result: List[Path] = new ArrayList[Path]()
    while (it.hasNext) result.add(it.next())
    result
  }

}

/**
 * Provides a mutable in-memory cache of files and subdirectories with basic CRUD functionality. The
 * CachedDirectory can be fully recursive as the subdirectories are themselves stored as recursive
 * (when the CachedDirectory is initialized without the recursive toggle, the subdirectories are
 * stored as [[Entry]] instances. The primary use case is the implementation of [[FileTreeRepository]] and [[NioPathWatcher]]. Directly handling CachedDirectory instances is
 * discouraged because it is inherently mutable so it's better to let the FileTreeRepository manage
 * it and query the cache rather than CachedDirectory directly.
 *
 * <p>The CachedDirectory should cache all of the files and subdirectories up the maximum depth. A
 * maximum depth of zero means that the CachedDirectory should cache the subdirectories, but not
 * traverse them. A depth {@code < 0} means that it should not cache any files or subdirectories
 * within the directory. In the event that a loop is created by symlinks, the CachedDirectory will
 * include the symlink that completes the loop, but will not descend further (inducing a loop).
 *
 * @tparam T the cache value type.
 */
class CachedDirectoryImpl[T <: AnyRef](@BeanProperty val typedPath: TypedPath,
                                       private val converter: Converter[T],
                                       private val depth: Int,
                                       filter: Filter[_ >: TypedPath],
                                       private val fileTreeView: FileTreeView)
    extends CachedDirectory[T] {

  private val _cacheEntry: AtomicReference[Entry[T]] = new AtomicReference(null)

  private val pathFilter: Filter[_ >: TypedPath] = filter

  private val subdirectories: LockableMap[Path, CachedDirectoryImpl[T]] =
    new LockableMap()

  private val files: Map[Path, Entry[T]] = new HashMap()

  this._cacheEntry.set(Entries.get(this.typedPath, converter, this.typedPath))

  def getMaxDepth(): Int = depth

  override def getPath(): Path = typedPath.getPath

  override def list(maxDepth: Int, filter: Filter[_ >: TypedPath]): List[TypedPath] =
    list(getPath, maxDepth, filter)

  override def list(path: Path, maxDepth: Int, filter: Filter[_ >: TypedPath]): List[TypedPath] =
    if (this.subdirectories.lock()) {
      try {
        val findResult: Either[Entry[T], CachedDirectoryImpl[T]] = find(path)
        if (findResult != null) {
          if (findResult.isRight) {
            val result: List[TypedPath] = new ArrayList[TypedPath]()
            findResult.get.listImpl[TypedPath](
              maxDepth,
              filter,
              result,
              new ListTransformer[T, TypedPath]() {
                override def apply(entry: Entry[T]): TypedPath =
                  TypedPaths.getDelegate(entry.getTypedPath.getPath, entry.getTypedPath)
              }
            )
            result
          } else {
            val entry: Entry[T] = leftProjection(findResult).getValue
            val result: List[TypedPath] = new ArrayList[TypedPath]()
            if (entry != null && filter.accept(entry.getTypedPath))
              result.add(TypedPaths.getDelegate(entry.getTypedPath.getPath, entry.getTypedPath))
            result
          }
        } else {
          Collections.emptyList()
        }
      } finally this.subdirectories.unlock()
    } else {
      Collections.emptyList()
    }

  override def listEntries(path: Path,
                           maxDepth: Int,
                           filter: Filter[_ >: Entry[T]]): List[Entry[T]] =
    if (this.subdirectories.lock()) {
      try {
        val findResult: Either[Entry[T], CachedDirectoryImpl[T]] = find(path)
        if (findResult != null) {
          if (findResult.isRight) {
            val result: List[Entry[T]] = new ArrayList[Entry[T]]()
            findResult.get
              .listImpl[Entry[T]](maxDepth, filter, result, new ListTransformer[T, Entry[T]]() {
                override def apply(entry: Entry[T]): Entry[T] = entry
              })
            result
          } else {
            val entry: Entry[T] = leftProjection(findResult).getValue
            val result: List[Entry[T]] = new ArrayList[Entry[T]]()
            if (entry != null && filter.accept(entry)) result.add(entry)
            result
          }
        } else {
          Collections.emptyList()
        }
      } finally this.subdirectories.unlock()
    } else {
      Collections.emptyList()
    }

  override def listEntries(maxDepth: Int, filter: Filter[_ >: Entry[T]]): List[Entry[T]] =
    listEntries(getPath, maxDepth, filter)

  override def getEntry(): Entry[T] = _cacheEntry.get

  override def close(): Unit = {
    subdirectories.clear()
    files.clear()
  }

  /**
   * Updates the CachedDirectory entry for a particular typed typedPath.
   *
   * @param typedPath the typedPath to update
   * @return a list of updates for the typedPath. When the typedPath is new, the updates have the
   *     oldCachedPath field set to null and will contain all of the children of the new typedPath
   *     when it is a directory. For an existing typedPath, the List contains a single Updates that
   *     contains the previous and new [[Entry]].
   *     traversing the directory.
   */
  override def update(typedPath: TypedPath): Updates[T] =
    if (pathFilter.accept(typedPath))
      updateImpl(if (typedPath.getPath == this.getPath) new ArrayList[Path]()
                 else parts(this.getPath.relativize(typedPath.getPath)),
                 typedPath)
    else new Updates[T]()

  /**
   * Remove a path from the directory.
   *
   * @param path the path to remove
   * @return a List containing the Entry instances for the removed path. The result also contains
   *     the cache entries for any children of the path when the path is a non-empty directory.
   */
  def remove(path: Path): List[Entry[T]] =
    if (path.isAbsolute && path.startsWith(this.getPath)) {
      removeImpl(parts(this.getPath.relativize(path)))
    } else {
      Collections.emptyList()
    }

  override def toString(): String =
    "CachedDirectory(" + getPath + ", maxDepth = " + depth +
      ")"

  private def subdirectoryDepth(): Int =
    if (depth == java.lang.Integer.MAX_VALUE) depth
    else if (depth > 0) depth - 1
    else 0

  private def addDirectory(currentDir: CachedDirectoryImpl[T],
                           typedPath: TypedPath,
                           updates: Updates[T]): Unit = {
    val path: Path = typedPath.getPath
    val dir: CachedDirectoryImpl[T] = new CachedDirectoryImpl[T](typedPath,
                                                                 converter,
                                                                 currentDir.subdirectoryDepth(),
                                                                 pathFilter,
                                                                 fileTreeView)
    var exists: Boolean = true
    try dir.init()
    catch {
      case nsfe: NoSuchFileException => exists = false

      case e: IOException => {}

    }
    if (exists) {
      val oldEntries: Map[Path, Entry[T]] = new HashMap[Path, Entry[T]]()
      val newEntries: Map[Path, Entry[T]] = new HashMap[Path, Entry[T]]()
      val previous: CachedDirectoryImpl[T] =
        currentDir.subdirectories.put(path.getFileName, dir)
      if (previous != null) {
        oldEntries.put(previous.getPath, previous.getEntry)
        val entryIterator: Iterator[Entry[T]] =
          previous.listEntries(java.lang.Integer.MAX_VALUE, AllPass).iterator()
        while (entryIterator.hasNext) {
          val entry: Entry[T] = entryIterator.next()
          oldEntries.put(entry.getTypedPath.getPath, entry)
        }
        previous.close()
      }
      newEntries.put(dir.getPath, dir.getEntry)
      val it: Iterator[Entry[T]] =
        dir.listEntries(java.lang.Integer.MAX_VALUE, AllPass).iterator()
      while (it.hasNext) {
        val entry: Entry[T] = it.next()
        newEntries.put(entry.getTypedPath.getPath, entry)
      }
      MapOps.diffDirectoryEntries(oldEntries, newEntries, updates)
    } else {
      val it: Iterator[Entry[T]] = remove(dir.getPath).iterator()
      while (it.hasNext) updates.onDelete(it.next())
    }
  }

  private def isLoop(path: Path, realPath: Path): Boolean =
    path.startsWith(realPath) && path != realPath

  private def updateImpl(parts: List[Path], typedPath: TypedPath): Updates[T] = {
    val result: Updates[T] = new Updates[T]()
    if (this.subdirectories.lock()) {
      try if (!parts.isEmpty) {
        val it: Iterator[Path] = parts.iterator()
        var currentDir: CachedDirectoryImpl[T] = this
        while (it.hasNext && currentDir != null && currentDir.depth >= 0) {
          val p: Path = it.next()
          if (p.toString.isEmpty) result
          val resolved: Path = currentDir.getPath.resolve(p)
          val realPath: Path = typedPath.expanded()
          if (!it.hasNext) {
// We will always return from this block
            val isDirectory: Boolean = typedPath.isDirectory
            if (!isDirectory || currentDir.depth <= 0 || isLoop(resolved, realPath)) {
              val previousCachedDirectoryImpl: CachedDirectoryImpl[T] =
                if (isDirectory) currentDir.subdirectories.get(p) else null
              val oldEntry: Entry[T] =
                if (previousCachedDirectoryImpl != null)
                  previousCachedDirectoryImpl.getEntry
                else currentDir.files.get(p)
              val newEntry: Entry[T] = Entries.get(TypedPaths.getDelegate(p, typedPath),
                                                   converter,
                                                   TypedPaths.getDelegate(resolved, typedPath))
              if (isDirectory) {
                val previous: CachedDirectoryImpl[T] =
                  currentDir.subdirectories.put(
                    p,
                    new CachedDirectoryImpl(TypedPaths.getDelegate(resolved, typedPath),
                                            converter,
                                            -1,
                                            pathFilter,
                                            fileTreeView))
                if (previous != null) previous.close()
              } else {
                currentDir.files.put(p, newEntry)
              }
              val oldResolvedEntry: Entry[T] =
                if (oldEntry == null) null
                else Entries.resolve(currentDir.getPath, oldEntry)
              if (oldResolvedEntry == null) {
                result.onCreate(Entries.resolve(currentDir.getPath, newEntry))
              } else {
                result.onUpdate(oldResolvedEntry, Entries.resolve(currentDir.getPath, newEntry))
              }
              result
            } else {
              addDirectory(currentDir, typedPath, result)
              result
            }
          } else {
            val dir: CachedDirectoryImpl[T] = currentDir.subdirectories.get(p)
            if (dir == null && currentDir.depth > 0) {
              addDirectory(currentDir,
                           TypedPaths.getDelegate(currentDir.getPath.resolve(p), typedPath),
                           result)
            }
            currentDir = dir
          }
        }
      } else if (typedPath.isDirectory) {
        val oldEntries: List[Entry[T]] = listEntries(getMaxDepth, AllPass)
        init()
        val newEntries: List[Entry[T]] = listEntries(getMaxDepth, AllPass)
        MapOps.diffDirectoryEntries(oldEntries, newEntries, result)
      } else {
        val oldEntry: Entry[T] = getEntry
        val tp: TypedPath =
          TypedPaths.getDelegate(getTypedPath.expanded(), typedPath)
        val newEntry: Entry[T] = Entries.get(tp, converter, tp)
        _cacheEntry.set(newEntry)
        result.onUpdate(oldEntry, getEntry)
      } finally this.subdirectories.unlock()
    }
    result
  }

  private def findImpl(parts: List[Path]): Either[Entry[T], CachedDirectoryImpl[T]] = {
    val it: Iterator[Path] = parts.iterator()
    var currentDir: CachedDirectoryImpl[T] = this
    var result: Either[Entry[T], CachedDirectoryImpl[T]] = null
    while (it.hasNext && currentDir != null && result == null) {
      val p: Path = it.next()
      if (!it.hasNext) {
        val subdir: CachedDirectoryImpl[T] = currentDir.subdirectories.get(p)
        if (subdir != null) {
          result = Either.right(subdir)
        } else {
          val entry: Entry[T] = currentDir.files.get(p)
          if (entry != null)
            result = Either.left(Entries.resolve(currentDir.getPath, entry))
        }
      } else {
        currentDir = currentDir.subdirectories.get(p)
      }
    }
    result
  }

  private def find(path: Path): Either[Entry[T], CachedDirectoryImpl[T]] =
    if (path == this.getPath) {
      Either.right(this)
    } else if (!path.isAbsolute) {
      findImpl(parts(path))
    } else if (path.startsWith(this.getPath)) {
      findImpl(parts(this.getPath.relativize(path)))
    } else {
      null
    }

  private def listImpl[R](maxDepth: Int,
                          filter: Filter[_ >: R],
                          result: List[_ >: R],
                          function: ListTransformer[T, R]): Unit = {
    if (this.depth < 0 || maxDepth < 0) {
      result.add(function.apply(this.getEntry))
    } else {
      if (subdirectories.lock()) {
        try {
          val files: Collection[Entry[T]] =
            new ArrayList[Entry[T]](this.files.values)
          val subdirectories: Collection[CachedDirectoryImpl[T]] =
            new ArrayList[CachedDirectoryImpl[T]](this.subdirectories.values)
          val filesIterator: Iterator[Entry[T]] = files.iterator()
          while (filesIterator.hasNext) {
            val entry: Entry[T] = filesIterator.next()
            val resolved: R = function.apply(Entries.resolve(getPath, entry))
            if (filter.accept(resolved)) result.add(resolved)
          }
          val subdirIterator: Iterator[CachedDirectoryImpl[T]] =
            subdirectories.iterator()
          while (subdirIterator.hasNext) {
            val subdir: CachedDirectoryImpl[T] = subdirIterator.next()
            val entry: Entry[T] = subdir.getEntry
            val resolved: R = function.apply(Entries.resolve(getPath, entry))
            if (filter.accept(resolved)) result.add(resolved)
            if (maxDepth > 0)
              subdir.listImpl[R](maxDepth - 1, filter, result, function)
          }
        } finally subdirectories.unlock()
      }
    }
  }

  private def removeImpl(parts: List[Path]): List[Entry[T]] = {
    val result: List[Entry[T]] = new ArrayList[Entry[T]]()
    if (this.subdirectories.lock()) {
      try {
        val it: Iterator[Path] = parts.iterator()
        var currentDir: CachedDirectoryImpl[T] = this
        while (it.hasNext && currentDir != null) {
          val p: Path = it.next()
          if (!it.hasNext) {
            val entry: Entry[T] = currentDir.files.remove(p)
            if (entry != null) {
              result.add(Entries.resolve(currentDir.getPath, entry))
            } else {
              val dir: CachedDirectoryImpl[T] =
                currentDir.subdirectories.remove(p)
              if (dir != null) {
                result.addAll(dir.listEntries(java.lang.Integer.MAX_VALUE, AllPass))
                result.add(dir.getEntry)
              }
            }
          } else {
            currentDir = currentDir.subdirectories.get(p)
          }
        }
      } finally this.subdirectories.unlock()
    }
    result
  }

  def init(): CachedDirectoryImpl[T] = init(typedPath.getPath)

  private def init(realPath: Path): CachedDirectoryImpl[T] = {
    if (subdirectories.lock()) {
      try {
        subdirectories.clear()
        files.clear()
        if (depth >= 0 &&
            (!this.getPath.startsWith(realPath) || this.getPath == realPath)) {
          val it: Iterator[TypedPath] =
            fileTreeView.list(this.getPath, 0, pathFilter).iterator()
          while (it.hasNext) {
            val file: TypedPath = it.next()
            if (pathFilter.accept(file)) {
              val path: Path = file.getPath
              val expandedPath: Path = file.expanded()
              val key: Path =
                this.typedPath.getPath.relativize(path).getFileName
              if (file.isDirectory) {
                if (depth > 0) {
                  if (!file.isSymbolicLink || !isLoop(path, expandedPath)) {
                    val dir: CachedDirectoryImpl[T] =
                      new CachedDirectoryImpl[T](file,
                                                 converter,
                                                 subdirectoryDepth(),
                                                 pathFilter,
                                                 fileTreeView)
                    try {
                      dir.init()
                      subdirectories.put(key, dir)
                    } catch {
                      case e: IOException =>
                        if (Files.exists(dir.getPath)) {
                          subdirectories.put(key, dir)
                        }

                    }
                  } else {
                    subdirectories.put(
                      key,
                      new CachedDirectoryImpl(file, converter, -1, pathFilter, fileTreeView))
                  }
                } else {
                  files.put(key, Entries.get(TypedPaths.getDelegate(key, file), converter, file))
                }
              } else {
                files.put(key, Entries.get(TypedPaths.getDelegate(key, file), converter, file))
              }
            }
          }
        }
      } finally subdirectories.unlock()
    }
    this
  }

}
