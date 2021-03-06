package io.github.yuemenglong.orm.operate.execute.traits

import io.github.yuemenglong.orm.sql.{Assign, ExprT, UpdateStatement}

/**
  * Created by yml on 2017/7/15.
  */
//noinspection ScalaFileName
trait ExecutableUpdate extends UpdateStatement with Executable {
  def set(as: Assign*): ExecutableUpdate = {
    _sets = as.toArray
    this
  }

  def where(e: ExprT[_]): ExecutableUpdate = {
    _where = e.toExpr
    this
  }
}
