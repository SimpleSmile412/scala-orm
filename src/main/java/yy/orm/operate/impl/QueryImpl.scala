package yy.orm.operate.impl

import java.sql.{Connection, ResultSet}

import yy.orm.entity.EntityManager
import yy.orm.lang.interfaces.Entity
import yy.orm.meta.OrmMeta
import yy.orm.operate.impl.core.CondRoot
import yy.orm.operate.traits.core._
import yy.orm.operate.traits.{Query, SelectableTuple}

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag

/**
  * Created by <yuemenglong@126.com> on 2017/7/16.
  */

class QueryImpl[T](private var st: SelectableTuple[T],
                   private var root: SelectRoot[_] = null,
                   private var cond: Cond = new CondRoot(),
                   private var orders: ArrayBuffer[(Field, String)] = new ArrayBuffer[(Field, String)](),
                   private var limitOffset: (Long, Long) = (-1, -1))
  extends Query[T] {

  def getParams: Array[Object] = {
    val loParams = limitOffset match {
      case (-1, -1) => Array()
      case (l, -1) => Array[Object](new java.lang.Long(l))
      case (-1, o) => Array[Object](new java.lang.Long(o))
      case (l, o) => Array[Object](new java.lang.Long(l), new java.lang.Long(o))
    }
    root.getParams ++ cond.getParams ++ loParams
  }

  def getSql: String = {
    val columnsSql = st.getColumnWithAs
    val tableSql = root.getTableWithJoinCond
    val condSql = cond.getSql match {
      case "" => "1 = 1"
      case s => s
    }
    val orderBySql = orders.length match {
      case 0 => ""
      case _ =>
        val content = orders.map { case (f, s) => s"${f.getColumn} $s" }.mkString(", ")
        s"\nORDER BY $content"
    }
    val loSql = limitOffset match {
      case (-1, -1) => ""
      case (_, -1) => "\nLIMIT ?"
      case (-1, _) => "\nOFFSET ?"
      case (_, _) => "\nLIMIT ? OFFSET ?"
    }
    s"SELECT\n$columnsSql\nFROM\n$tableSql\nWHERE\n$condSql$orderBySql$loSql"
  }

  override def query(conn: Connection): Array[T] = {
    var filterSet = Set[String]()
    val sql = getSql
    println(sql)
    val params = getParams
    println(s"""[Params] => [${params.map(_.toString).mkString(", ")}]""")
    val stmt = conn.prepareStatement(sql)
    params.zipWithIndex.foreach { case (param, i) =>
      stmt.setObject(i + 1, param)
    }
    val rs = stmt.executeQuery()
    var ab = ArrayBuffer[T]()
    val filterMap = mutable.Map[String, Entity]()
    while (rs.next()) {
      val value = st.pick(rs, filterMap)
      val key = st.getKey(value.asInstanceOf[Object])
      if (!filterSet.contains(key)) {
        ab += value
      }
      filterSet += key
    }
    rs.close()
    stmt.close()
    ab.map(st.walk(_, bufferToArray)).toArray(ClassTag(st.getType))
  }

  private def bufferToArray(entity: Entity): Entity = {
    val core = entity.$$core()
    core.fieldMap.toArray.map(pair => {
      val (name, value) = pair
      value match {
        case ab: ArrayBuffer[_] =>
          val entityName = core.meta.fieldMap(name).typeName
          val entityClass = OrmMeta.entityMap(entityName).clazz
          val ct = ClassTag(entityClass).asInstanceOf[ClassTag[Object]]
          val array = ab.map(_.asInstanceOf[Entity]).map(bufferToArray).toArray(ct)
          (name, array)
        case _ =>
          null
      }
    }).filter(_ != null).foreach(p => core.fieldMap += p)
    entity
  }

  //////

  override def select[T1](t: Selectable[T1]): Query[T1] = {
    val st = new SelectableTupleImpl[T1](t.getType, t)
    new QueryImpl[T1](st, root, cond, orders, limitOffset)
  }

  override def select[T1, T2](t1: Selectable[T1], t2: Selectable[T2]): Query[(T1, T2)] = {
    val st = new SelectableTupleImpl[(T1, T2)](classOf[(T1, T2)], t1, t2)
    new QueryImpl[(T1, T2)](st, root, cond, orders, limitOffset)
  }

  override def from(selectRoot: SelectRoot[_]): Query[T] = {
    root = selectRoot
    this
  }

  override def limit(l: Long): Query[T] = {
    limitOffset = (l, limitOffset._2)
    this
  }

  override def offset(o: Long): Query[T] = {
    limitOffset = (limitOffset._1, o)
    this
  }

  override def asc(field: Field): Query[T] = {
    orders += ((field, "ASC"))
    this
  }

  override def desc(field: Field): Query[T] = {
    orders += ((field, "DESC"))
    this
  }

  override def where(c: Cond): Query[T] = {
    cond = c
    this
  }

  override def getRoot: SelectRoot[_] = root

  override def walk(t: T, fn: (Entity) => Entity): T = st.walk(t, fn)

  override def getType: Class[T] = st.getType
}

class SelectableTupleImpl[T](clazz: Class[T], ss: Selectable[_]*) extends SelectableTuple[T] {
  val selects: Array[Selectable[_]] = ss.toArray
  val tuple2Class: Class[(_, _)] = classOf[(_, _)]

  override def pick(rs: ResultSet, filterMap: mutable.Map[String, Entity]): T = {
    val row = selects.map(_.pick(rs, filterMap).asInstanceOf[Object])
    arrayToTuple(row)
  }

  override def getColumnWithAs: String = selects.map(_.getColumnWithAs).mkString(",\n")

  override def getType: Class[T] = clazz

  override def getKey(value: Object): String = {
    val values = tupleToArray(value.asInstanceOf[T])
    (selects, values).zipped.map(_.getKey(_)).mkString("$")
  }

  def tupleToArray(tuple: T): Array[Object] = {
    val ret: Array[Any] = tuple match {
      case (t1, t2) => Array(t1, t2)
      case t => Array(t)
    }
    ret.map(_.asInstanceOf[Object])
  }

  def arrayToTuple(arr: Array[Object]): T = {
    clazz match {
      case `tuple2Class` => (arr(0), arr(1)).asInstanceOf[T]
      case _ => arr(0).asInstanceOf[T]
    }
  }

  override def getParent: Node = selects(0).getParent

  override def walk(tuple: T, fn: (Entity) => Entity): T = {
    val arr = tupleToArray(tuple).map {
      case e: Entity => EntityManager.walk(e, fn)
      case obj => obj
    }
    arrayToTuple(arr)
  }
}

class Count_(root: SelectRoot[_]) extends Selectable[java.lang.Long] {
  def getAlias: String = "$count$"

  override def pick(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): java.lang.Long = resultSet.getObject(getAlias, classOf[java.lang.Long])

  override def getColumnWithAs: String = s"COUNT(*) AS $getAlias"

  override def getType: Class[java.lang.Long] = classOf[java.lang.Long]

  override def getKey(value: Object): String = value.toString

  override def getParent: Node = root.getRoot
}