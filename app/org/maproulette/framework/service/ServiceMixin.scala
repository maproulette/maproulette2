/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.service

import org.maproulette.framework.psql.Query

/**
  * @author mcuthbert
  */
trait ServiceMixin[T] {

  /**
    * Retrieves all the objects based on the search criteria
    *
    * @param query The query to match against to retrieve the objects
    * @return The list of objects
    */
  def query(query: Query): List[T]

  /**
    * Retrieves an object of that type
    *
    * @param id The identifier for the object
    * @return An optional object, None if not found
    */
  def retrieve(id: Long): Option[T]
}
