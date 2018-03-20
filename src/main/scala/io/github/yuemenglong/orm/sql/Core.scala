package io.github.yuemenglong.orm.sql

import io.github.yuemenglong.orm.kit.UnreachableException

/**
  * Created by <yuemenglong@126.com> on 2018/3/19.
  */

trait Select extends SelectStmt {
  def from(table: Table)

  def where(expr: Expression)
}

class Table(c: (
  (String, String), // tableName, uid
    (SelectStmt, String), // (Select xx) AS
    JoinPart, // JoinPart
  )) extends TableSource {
  this.children = c

  def this(table: String, uid: String) = this(((table, uid), null, null))

  def this(stmt: SelectStmt, uid: String) = this((null, (stmt, uid), null))

  def join(t: Table, joinType: String, leftColunm: String, rightColumn: String): Table = {
    val c = Expr(get(leftColunm), "=", t.get(rightColumn))

    val jp: JoinPart = children match {
      case ((name, uid), null, null) => new JoinPart {
        override val table = new Table(name, uid)
        override val joins = List((joinType, t, c))
      }
      case (null, (stmt, uid), null) => new JoinPart {
        override val table = new Table(stmt, uid)
        override val joins = List((joinType, t, c))
      }
      case (null, null, j) => new JoinPart {
        override private[orm] val table = j.table
        override private[orm] val joins = j.joins ::: List((joinType, t, c))
      }
      case _ => throw new UnreachableException
    }
    children = (null, null, jp)
    this
  }

  def get(column: String): Expression = {
    Expr.column(getAlias, column, s"${getAlias}$$${column}")
  }

  def getUid(children: (
    (String, String), // tableName, uid
      (SelectStmt, String), // (Select xx) AS
      JoinPart, // JoinPart
    )): String = children match {
    case ((_, uid), null, null) => uid
    case (null, (_, uid), null) => uid
    case (null, null, j) => getUid(j.table.children)
    case _ => throw new UnreachableException
  }

  def getAlias: String = getUid(children)
}

trait Expr extends Expression {

}

object Expr {
  def const(v: Object): Expression = new Expression() {
    val c = new Constant {
      override val value = v
    }
    override val children = (c, null, null, null, null, null, null, null, null)
  }

  def column(t: String, c: String, u: String): Expression = new Expression() {
    val tc = new TableColumn {
      override val table = t
      override val column = c
      override val uid = u
    }
    override val children = (null, tc, null, null, null, null, null, null, null)
  }

  def func(f: String, d: Boolean, p: List[Expression]): Expression = new Expression {
    val fc = new FunctionCall {
      override val fn = f
      override val distinct = d
      override val params = p
    }
    override val children = (null, null, fc, null, null, null, null, null, null)
  }

  def apply(op: String, e: Expression): Expression = new Expression {
    override val children = (null, null, null, (op, e), null, null, null, null, null)
  }

  def apply(e: Expression, op: String): Expression = new Expression {
    override val children = (null, null, null, null, (e, op), null, null, null, null)
  }

  def apply(l: Expression, op: String, r: Expression): Expression = new Expression {
    override val children = (null, null, null, null, null, (l, op, r), null, null, null)
  }

  def apply(e: Expression, l: Expression, r: Expression): Expression = new Expression {
    override val children = (null, null, null, null, null, null, (e, l, r), null, null)
  }

  def apply(e: Expression, op: String, stmt: SelectStmt): Expression = new Expression {
    override val children = (null, null, null, null, null, null, null, (e, op, stmt), null)
  }

  def apply(list: List[Expression]): Expression = new Expression {
    override val children = (null, null, null, null, null, null, null, null, list)
  }
}

object Core {
  def main(args: Array[String]): Unit = {
    val obj = new Table("obj", "obj")
    val ptr = new Table("ptr", "obj_ptr")

    obj.join(ptr, "LEFT", "ptr_id", "id")

    val sb = new StringBuffer()
    obj.genSql(sb)
    println(sb.toString)
  }
}

