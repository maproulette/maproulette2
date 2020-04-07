/*
 * Copyright (C) 2020 MapRoulette contributors (see CONTRIBUTORS.md).
 * Licensed under the Apache License, Version 2.0 (see LICENSE).
 */

package org.maproulette.framework.repository

import org.maproulette.framework.psql.TransactionManager
import org.maproulette.utils.{Readers, Writers}

/**
  * Handles some very basic requirements for any repository
  *
  * @author mcuthbert
  */
trait RepositoryMixin extends TransactionManager with Readers with Writers {}
