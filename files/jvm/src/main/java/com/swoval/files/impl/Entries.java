package com.swoval.files.impl;

import static com.swoval.files.impl.LinkOption.NOFOLLOW_LINKS;

import com.swoval.files.CacheEntry;
import com.swoval.functional.IOFunction;
import com.swoval.files.TypedPath;
import com.swoval.files.impl.functional.EitherImpl;
import com.swoval.functional.Either;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;

/** Provides static constants and methods related to {@link CacheEntry}. */
final class Entries {
  static final int DIRECTORY = 1;
  static final int FILE = 2;
  static final int LINK = 4;
  static final int UNKNOWN = 8;
  static final int NONEXISTENT = 16;

  private Entries() {}

  static <T> CacheEntry<T> get(
      final TypedPath typedPath,
      final IOFunction<TypedPath, T> converter,
      final TypedPath converterPath) {
    try {
      return new ValidCacheEntry<>(typedPath, converter.apply(converterPath));
    } catch (final IOException e) {
      return new InvalidCacheEntry<>(typedPath, e);
    }
  }

  static <T> CacheEntry<T> setExists(final CacheEntry<T> cacheEntry, final boolean exists) {
    final TypedPath typedPath = cacheEntry.getTypedPath();
    final int kind =
        (exists ? 0 : NONEXISTENT)
            | (typedPath.isFile() ? FILE : 0)
            | (typedPath.isDirectory() ? DIRECTORY : 0)
            | (typedPath.isSymbolicLink() ? LINK : 0);
    final TypedPath nonExistent = TypedPaths.get(typedPath.getPath(), kind);
    if (!cacheEntry.getValue().isRight()) {
      return new InvalidCacheEntry<>(nonExistent, EitherImpl.getLeft(cacheEntry.getValue()));
    } else {
      return new ValidCacheEntry<>(nonExistent, EitherImpl.getRight(cacheEntry.getValue()));
    }
  }

  static <T> CacheEntry<T> resolve(final Path path, final CacheEntry<T> cacheEntry) {
    final Either<IOException, T> value = cacheEntry.getValue();
    final int kind = getKind(cacheEntry);
    final TypedPath typedPath =
        TypedPaths.get(path.resolve(cacheEntry.getTypedPath().getPath()), kind);
    return value.isRight()
        ? new ValidCacheEntry<>(typedPath, EitherImpl.getRight(value))
        : new InvalidCacheEntry<T>(typedPath, EitherImpl.getLeft(value));
  }

  private static int getKindFromAttrs(final Path path, final BasicFileAttributes attrs) {
    return attrs.isSymbolicLink()
        ? LINK | (Files.isDirectory(path) ? DIRECTORY : FILE)
        : attrs.isDirectory() ? DIRECTORY : FILE;
  }
  /**
   * Compute the underlying file type for the path.
   *
   * @param path The path whose type is to be determined.
   * @throws IOException if the path can't be opened
   * @return The file type of the path
   */
  static int getKind(final Path path) throws IOException {
    final BasicFileAttributes attrs = NioWrappers.readAttributes(path, NOFOLLOW_LINKS);
    return getKindFromAttrs(path, attrs);
  }

  private static int getKind(final CacheEntry<?> cacheEntry) {
    final TypedPath typedPath = cacheEntry.getTypedPath();
    return (typedPath.isSymbolicLink() ? LINK : 0)
        | (typedPath.isDirectory() ? DIRECTORY : 0)
        | (typedPath.isFile() ? FILE : 0);
  }

  private abstract static class CacheEntryImpl<T> implements CacheEntry<T> {
    private final TypedPath typedPath;

    CacheEntryImpl(final TypedPath typedPath) {
      this.typedPath = typedPath;
    }

    @Override
    public int hashCode() {
      final int value = EitherImpl.getOrElse(getValue(), 0).hashCode();
      return typedPath.hashCode() ^ value;
    }

    @Override
    public boolean equals(final Object other) {
      return other instanceof CacheEntry<?>
          && ((CacheEntry<?>) other).getTypedPath().getPath().equals(getTypedPath().getPath())
          && getValue().equals(((CacheEntry<?>) other).getValue());
    }

    @Override
    public int compareTo(final CacheEntry<T> that) {
      return this.getTypedPath().getPath().compareTo(that.getTypedPath().getPath());
    }

    @Override
    public TypedPath getTypedPath() {
      return typedPath;
    }
  }

  private static final class ValidCacheEntry<T> extends CacheEntryImpl<T> {
    private final T value;

    @Override
    public Either<IOException, T> getValue() {
      return EitherImpl.right(value);
    }
    /**
     * Create a new CacheEntry
     *
     * @param typedPath The path to which this entry corresponds
     * @param value The {@code path} derived value for this entry
     */
    ValidCacheEntry(final TypedPath typedPath, final T value) {
      super(typedPath);
      this.value = value;
    }

    @Override
    public String toString() {
      return "ValidCacheEntry(" + getTypedPath().getPath() + ", " + value + ")";
    }
  }

  private static class InvalidCacheEntry<T> extends CacheEntryImpl<T> {
    private final IOException exception;

    InvalidCacheEntry(final TypedPath typedPath, final IOException exception) {
      super(typedPath);
      this.exception = exception;
    }

    @Override
    public Either<IOException, T> getValue() {
      return EitherImpl.left(exception);
    }

    @Override
    public String toString() {
      return "InvalidCacheEntry(" + getTypedPath().getPath() + ", " + exception + ")";
    }
  }
}
