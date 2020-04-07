/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.cache

/**
  * @author mcuthbert
  */
class ListCacheObject[T](value: List[T]) extends CacheObject[T] {
  override def name: String = value.toString

  override def id: T = value.head

  def list: List[T] = value
}
