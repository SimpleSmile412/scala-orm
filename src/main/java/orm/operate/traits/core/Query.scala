package orm.operate.traits.core

import java.sql.{Connection, ResultSet}


/**
  * Created by yml on 2017/7/14.
  */
trait Queryable {
  def query(conn: Connection): Array[Array[Object]]
}

trait AsSelectable {
  def as[T](clazz: Class[T]): Selectable[T]
}

trait Selectable[T] extends Node {
  def pick(rs: ResultSet): T

  def getColumnWithAs: String

  def getType: Class[T]

  def getKey(value: Object): String
}

trait SelectJoin extends Join {
  def select(field: String): SelectJoin
}

trait SelectRoot[T] extends Root[T] with Selectable[T] with SelectJoin

trait SelectBuilder {
  def from(selectRoot: SelectRoot[_]): Query
}

trait Query extends Queryable {
  def limit(l: Long): Query

  def offset(l: Long): Query

  def asc(field: Field): Query

  def desc(field: Field): Query

  def where(cond: Cond): Query
}

trait SelectBuilder1[T] {
  def from(selectRoot: SelectRoot[_]): Query1[T]
}

trait Query1[T] extends Query {
  def transform(res: Array[Array[Object]]): Array[T]
}