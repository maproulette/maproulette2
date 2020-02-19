// Copyright (C) 2019 MapRoulette contributors (see CONTRIBUTORS.md).
// Licensed under the Apache License, Version 2.0 (see LICENSE).
package org.maproulette.cache

/**
  * @author mcuthbert
  */
trait CacheObject[Key] extends Serializable {
  def name: String

  def id: Key
}
