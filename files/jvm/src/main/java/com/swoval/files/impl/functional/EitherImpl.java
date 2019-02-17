package com.swoval.files.impl.functional;

import com.swoval.functional.Either;
import com.swoval.functional.throwables.NotLeftException;
import com.swoval.functional.throwables.NotRightException;

/**
 * Represents a value that can be one of two types. Inspired by <a
 * href="https://www.scala-lang.org/api/current/scala/util/Either.html"
 * target="_blank">EitherImpl</a>, it is right biased, but does not define all of the combinators
 * that the scala version does.
 *
 * @param <L> The left value
 * @param <R> The right value
 */
public abstract class EitherImpl<L, R> implements Either<L, R> {
  private EitherImpl() {}

  public static <T> T getOrElse(final Either<?, ? extends T> eitherImpl, final T t) {
    try {
      return eitherImpl.isRight() ? eitherImpl.rightValue() : t;
    } catch (final NotRightException e) {
      return t;
    }
  }

  public static <L> L getLeft(final Either<L, ?> eitherImpl) {
    try {
      return eitherImpl.leftValue();
    } catch (final NotLeftException e) {
      throw new RuntimeException(e);
    }
  }

  public static <R> R getRight(final Either<?, R> eitherImpl) {
    try {
      return eitherImpl.rightValue();
    } catch (final NotRightException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public abstract int hashCode();

  @Override
  public abstract boolean equals(final Object other);

  /**
   * Returns a left projected either.
   *
   * @param value the value to wrap
   * @param <L> the type of the left parameter of the result
   * @param <R> the type of the right parameter of the result
   * @param <T> a refinement type that allows us to wrap subtypes of L
   * @return A left projected either
   */
  public static <L, R, T extends L> Either<L, R> left(final T value) {
    return new Left<>((L) value);
  }

  /**
   * Returns a right projected either.
   *
   * @param value the value to wrap
   * @param <L> the type of the left parameter of the result
   * @param <R> the type of the right parameter of the result
   * @param <T> a refinement type that allows us to wrap subtypes of R
   * @return a right projected either.
   */
  public static <L, R, T extends R> Either<L, R> right(final T value) {
    return new Right<>((R) value);
  }

  /**
   * A left projected {@link EitherImpl}.
   *
   * @param <L> the left type
   * @param <R> the right type
   */
  public static final class Left<L, R> extends EitherImpl<L, R> {
    private final L value;

    Left(final L value) {
      this.value = value;
    }

    /**
     * Returns the wrapped value
     *
     * @return the wrapped value
     */
    @Override
    public L leftValue() {
      return value;
    }

    @Override
    public boolean isRight() {
      return false;
    }

    @Override
    public R rightValue() throws NotRightException {
      throw new NotRightException(this + " is not an instance of Right");
    }

    @Override
    public String toString() {
      return "Left(" + value + ")";
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EitherImpl.Left<?, ?>
          && this.value.equals(((EitherImpl.Left<?, ?>) other).leftValue());
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }

  /**
   * A right projected {@link EitherImpl}.
   *
   * @param <L> the left type
   * @param <R> the right type
   */
  public static final class Right<L, R> extends EitherImpl<L, R> {
    private final R value;

    Right(final R value) {
      this.value = value;
    }

    /**
     * Returns the wrapped value.
     *
     * @return the wrapped value.
     */
    @Override
    public R rightValue() {
      return value;
    }

    @Override
    public boolean isRight() {
      return true;
    }

    @Override
    public L leftValue() throws NotLeftException {
      throw new NotLeftException(this + " is not an instance of Left");
    }

    @Override
    public String toString() {
      return "Right(" + value + ")";
    }

    @Override
    public boolean equals(Object other) {
      return other instanceof EitherImpl.Right<?, ?>
          && this.value.equals(((EitherImpl.Right<?, ?>) other).rightValue());
    }

    @Override
    public int hashCode() {
      return value.hashCode();
    }
  }
}
