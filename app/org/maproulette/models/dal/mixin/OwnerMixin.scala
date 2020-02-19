package org.maproulette.models.dal.mixin

import java.sql.Connection

import anorm._
import org.maproulette.models.BaseObject
import org.maproulette.models.dal.BaseDAL
import org.maproulette.session.User

/**
  * @author mcuthbert
  */
trait OwnerMixin[T <: BaseObject[_]] {
  this: BaseDAL[_, T] =>

  /**
    * Changes the owner of the object
    *
    * @param objectId  The id of the object to change the owner
    * @param newUserId The new users id
    * @param user      The user making the request
    */
  def changeOwner(objectId: Long, newUserId: Long, user: User)(
      implicit c: Option[Connection] = None
  ): Unit = {
    // for now only super users can change the owners
    this.permission.hasSuperAccess(user)
    this.withMRConnection { implicit c =>
      SQL"""UPDATE $tableName SET owner_id = $newUserId WHERE id = $objectId""".executeUpdate()
    }
  }
}
