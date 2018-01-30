package io.github.yuemenglong.orm.operate.impl.core

import java.lang
import java.sql.ResultSet

import io.github.yuemenglong.orm.entity.{EntityCore, EntityManager}
import io.github.yuemenglong.orm.kit.Kit
import io.github.yuemenglong.orm.lang.interfaces.Entity
import io.github.yuemenglong.orm.lang.types.Types.String
import io.github.yuemenglong.orm.meta._
import io.github.yuemenglong.orm.operate.impl._
import io.github.yuemenglong.orm.operate.traits.core.JoinType.JoinType
import io.github.yuemenglong.orm.operate.traits.core._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

/**
  * Created by <yuemenglong@126.com> on 2017/7/15.
  */
class FieldImpl(val meta: FieldMeta, val parent: JoinImpl) extends Field {
  override def getField: String = meta.name

  override def getColumn: String = s"${parent.getAlias}.${meta.column}"

  override def getAlias: String = s"${parent.getAlias}$$${Kit.lodashCase(meta.name)}"

  override def getRoot: Node = parent.getRoot

  override def as[T](clazz: Class[T]): SelectableField[T] = new SelectableFieldImpl[T](clazz, this)
}

class SelectableFieldImpl[T](clazz: Class[T], val impl: Field) extends SelectableField[T] {
  private var distinctVar: String = ""

  override def getColumn: String = s"$distinctVar${impl.getColumn}"

  override def getField: String = impl.getField

  override def getAlias: String = impl.getAlias

  override def getType: Class[T] = clazz

  override def getRoot: Node = impl.getRoot

  override def as[R](clazz: Class[R]): SelectableField[R] = throw new RuntimeException("Already Selectable")

  override def distinct(): SelectableField[T] = {
    distinctVar = "DISTINCT "
    this
  }
}

trait JoinInner {
  val meta: EntityMeta
  val parent: Join
  val joinName: String
  val left: String
  val right: String
  val joinType: JoinType

  protected[orm] var cond: Cond = new CondHolder
  protected[orm] val joins = new ArrayBuffer[JoinImpl]()
}

trait JoinImpl extends Join {
  val inner: JoinInner

  override def getMeta: EntityMeta = inner.meta

  override def join(field: String, joinType: JoinType): Join = {
    if (!getMeta.fieldMap.contains(field) || !getMeta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Field $field On ${getMeta.entity}")
    }
    if (getMeta.fieldMap(field).asInstanceOf[FieldMetaRefer].refer.db != getMeta.db) {
      throw new RuntimeException(s"Field $field Not the Same DB")
    }
    inner.joins.find(_.inner.joinName == field) match {
      case Some(p) => p.inner.joinType == joinType match {
        case true => p
        case false => throw new RuntimeException(s"JoinType Not Match, ${p.inner.joinType} Exists")
      }
      case None =>
        val referField = getMeta.fieldMap(field).asInstanceOf[FieldMetaRefer]
        //        val join = new JoinImpl(referField.refer, this, field, referField.left, referField.right, joinType)
        val that = this
        val jt = joinType
        val joinInner = new JoinInner {
          override val meta: EntityMeta = referField.refer
          override val parent: Join = that
          override val joinName: String = field
          override val left: String = referField.left
          override val right: String = referField.right
          override val joinType: JoinType = jt
        }
        val join = new JoinImpl {
          override val inner: JoinInner = joinInner
        }
        inner.joins += join
        join
    }
  }

  override def joinAs[T](left: String, right: String, clazz: Class[T], joinType: JoinType): SelectableJoin[T] = {
    if (!OrmMeta.entityMap.contains(clazz)) {
      throw new RuntimeException(s"$clazz Is Not Entity")
    }
    val referMeta = OrmMeta.entityMap(clazz)
    if (!getMeta.fieldMap.contains(left)) {
      throw new RuntimeException(s"Unknown Field $left On ${getMeta.entity}")
    }
    if (!referMeta.fieldMap.contains(right)) {
      throw new RuntimeException(s"Unknown Field $right On ${referMeta.entity}")
    }
    if (getMeta.db != referMeta.db) {
      throw new RuntimeException(s"$left And $right Not The Same DB")
    }
    val that = this
    val join: JoinImpl = inner.joins.find(_.inner.joinName == referMeta.entity) match {
      case Some(p) => p
      case None =>
        //        val join = new JoinImpl(referMeta, this, referMeta.entity, left, right, joinType)
        val (l, r, jt) = (left, right, joinType)
        val joinInner = new JoinInner {
          override val meta: EntityMeta = referMeta
          override val parent: Join = that
          override val joinName: String = referMeta.entity
          override val left: String = l
          override val right: String = r
          override val joinType: JoinType = jt
        }
        val join = new JoinImpl {
          override val inner: JoinInner = joinInner
        }
        inner.joins += join
        join
    }
    val joinInner = join.inner
    val joinClazz = clazz
    new SelectableImpl[T] with SelectFieldJoinImpl with JoinImpl {
      override val clazz: Class[T] = joinClazz
      override val inner: JoinInner = joinInner
    }
  }

  override def get(field: String): Field = {
    if (!getMeta.fieldMap.contains(field) || getMeta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Field $field On ${getMeta.entity}")
    }
    val fieldMeta = getMeta.fieldMap(field)
    new FieldImpl(fieldMeta, this)
  }

  override def getRoot: Node = {
    inner.parent match {
      case null => this
      case _ => inner.parent.getRoot
    }
  }

  override def as[T](clazz: Class[T]): SelectableJoin[T] = {
    val joinInner = inner
    val joinClazz = clazz
    new SelectableImpl[T] with SelectFieldJoinImpl with JoinImpl {
      override val clazz: Class[T] = joinClazz
      override val inner: JoinInner = joinInner
    }
  }

  override def getAlias: String = {
    inner.parent match {
      case null => Kit.lowerCaseFirst(getMeta.entity)
      case _ => s"${inner.parent.getAlias}_${Kit.lowerCaseFirst(inner.joinName)}"
    }
  }

  override def getTableWithJoinCond: String = {
    if (inner.parent == null) {
      s"`${getMeta.table}` AS `$getAlias`"
    } else {
      val leftColumn = inner.parent.getMeta.fieldMap(inner.left).column
      val rightColumn = getMeta.fieldMap(inner.right).column
      val leftTable = inner.parent.getAlias
      val rightTable = getAlias
      val condSql = new JoinCond(leftTable, leftColumn, rightTable, rightColumn).and(inner.cond).getSql
      s"${inner.joinType.toString} JOIN `${getMeta.table}` AS `$getAlias` ON $condSql"
    }
  }

  override def getParams: Array[Object] = inner.cond.getParams ++ inner.joins.flatMap(_.getParams).toArray[Object]

  override def on(c: Cond): Join = {
    inner.cond = c
    this
  }
}

trait SelectFieldJoinImpl extends SelectFieldJoin {
  self: JoinImpl =>
  protected[impl] var selects = new ArrayBuffer[(String, Self)]()
  //  protected[impl] var fields: Array[FieldImpl] = getMeta.fields().filter(_.isNormalOrPkey).map(f => new FieldImpl(f, this)).toArray
  protected[impl] var fields: Array[FieldImpl] = _
  protected[impl] var ignores: Set[String] = Set()

  override def select(field: String): Self = {
    if (!getMeta.fieldMap.contains(field) || !getMeta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Object Field $field")
    }
    selects.find(_._1 == field) match {
      case Some(p) => p._2
      case None =>
        val j = leftJoin(field).asInstanceOf[JoinImpl]
        val s = new SelectFieldJoinImpl with JoinImpl {
          override val inner: JoinInner = j.inner
        }
        selects += ((field, s))
        s
    }
  }

  def validFields(): Array[FieldImpl] = {
    (fields match {
      case null =>
        getMeta.fields().filter(_.isNormalOrPkey)
          .map(f => new FieldImpl(f, this)).toArray
      case _ => fields
    }).filter(f => !ignores.contains(f.getField))
  }

  override def fields(fields: String*): Self = {
    val ret = fields.map(f => {
      if (!this.getMeta.fieldMap.contains(f)) {
        throw new RuntimeException(s"Invalid Field $f In ${this.getMeta.entity}")
      }
      if (!this.getMeta.fieldMap(f).isNormal) {
        throw new RuntimeException(s"Not Normal Field $f In ${this.getMeta.entity}")
      }
      new FieldImpl(this.getMeta.fieldMap(f), this)
    })
    val pkey = new FieldImpl(this.getMeta.pkey, this)
    //    this.fields = Array(pkey) ++ fields.map(this.getMeta.fieldMap(_)).map(f => new FieldImpl(f, this))
    this.fields = Array(pkey) ++ ret
    this
  }

  override def ignore(fields: String*): Self = {
    fields.foreach(f => {
      if (!getMeta.fieldMap.contains(f)) {
        throw new RuntimeException(s"Not Exists Field, $f")
      }
      val fieldMeta = getMeta.fieldMap(f)
      if (!fieldMeta.isNormal) {
        throw new RuntimeException(s"Only Normal Field Can Ignore, $f")
      }
    })
    this.ignores = fields.toSet
    this
  }

  protected def getFilterKey(core: EntityCore): String = {
    s"$getAlias@${core.getPkey.toString}"
  }

  override def pickSelf(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): Entity = {
    val map: Map[String, Object] = validFields().map(field => {
      val alias = field.getAlias
      val value = resultSet.getObject(alias)
      (field.meta.name, value)
    })(collection.breakOut)
    val core = new EntityCore(getMeta, map)
    if (core.getPkey == null) {
      return null
    }
    val key = getFilterKey(core)
    if (filterMap.contains(key)) {
      return filterMap(key)
    }
    val a = EntityManager.wrap(core)
    filterMap += (key -> a)
    a
  }
}

trait SelectableImpl[T] extends Selectable[T] {
  self: SelectFieldJoinImpl with JoinImpl =>
  val clazz: Class[T]
  //  if (clazz != meta.clazz) {
  //    throw new RuntimeException("Class Not Match")
  //  }

  private def getOneManyFilterKey(field: String, core: EntityCore): String = {
    s"$getAlias@$field@${core.getPkey.toString}"
  }

  private def pickSelfAndRefer(select: SelectFieldJoinImpl, resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): Entity = {
    val a = select.pickSelf(resultSet, filterMap)
    if (a == null) {
      return null
    }
    pickRefer(select, a, resultSet, filterMap)
    a
  }

  protected def pickRefer(selfSelect: SelectFieldJoinImpl, a: Object, resultSet: ResultSet, filterMap: mutable.Map[String, Entity]) {
    val aCore = EntityManager.core(a)
    selfSelect.selects.foreach { case (field, select) =>
      val fieldMeta = getMeta.fieldMap(field)
      val b = pickSelfAndRefer(select.asInstanceOf[SelectFieldJoinImpl], resultSet, filterMap)
      fieldMeta match {
        case _: FieldMetaPointer => aCore.fieldMap += (field -> b)
        case _: FieldMetaOneOne => aCore.fieldMap += (field -> b)
        case f: FieldMetaOneMany =>
          val hasArr = aCore.fieldMap.contains(field)
          if (!hasArr) {
            aCore.fieldMap += (field -> Kit.newArray(f.refer.clazz))
          }
          if (b != null) {
            val key = getOneManyFilterKey(field, b.$$core())
            if (!filterMap.contains(key)) {
              // 该对象还未被加入过一对多数组
              val arr = aCore.fieldMap(field).asInstanceOf[Array[_]]
              val brr = Kit.newArray(f.refer.clazz, b)
              aCore.fieldMap += (field -> (arr ++ brr))
            }
            filterMap += (key -> b)
          }
      }
    }
  }

  override def getColumnWithAs: String = {
    def go(select: SelectFieldJoinImpl): Array[String] = {
      val selfColumn = select.validFields().map(field => s"${field.getColumn} AS ${field.getAlias}")
      // 1. 属于自己的字段 2. 级联的部分
      selfColumn ++ select.selects.map(_._2.asInstanceOf[SelectFieldJoinImpl]).flatMap(go)
    }

    go(this).mkString(",\n")
  }

  override def getType: Class[T] = clazz

  override def getKey(value: Object): String = {
    value match {
      case null => ""
      case _ => value.asInstanceOf[Entity].$$core().getPkey.toString
    }
  }

  override def pick(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): T = {
    pickSelfAndRefer(this, resultSet, filterMap).asInstanceOf[T]
  }
}

trait RootImpl[T] extends Root[T] {
  self: SelectableImpl[T] with SelectFieldJoinImpl with JoinImpl =>

  override def getTable: String = {
    def go(join: JoinImpl): Array[String] = {
      Array(join.getTableWithJoinCond) ++ join.inner.joins.flatMap(go)
    }

    go(this).mkString("\n")
  }

  override def count(): Selectable[java.lang.Long] = new Count_(this)

  override def count(field: Field): SelectableField[lang.Long] = new Count(field)

  override def sum(field: Field): SelectableField[lang.Double] = new Sum(field)

  override def max[R](field: Field, clazz: Class[R]): SelectableField[R] = new Max(field, clazz)

  override def min[R](field: Field, clazz: Class[R]): SelectableField[R] = new Min(field, clazz)
}

//
//class TypedJoinImpl[T](meta: EntityMeta, parent: Join,
//                       joinName: String, left: String, right: String,
//                       joinType: JoinType)
//  extends JoinImpl(meta, parent, joinName, left, right, joinType) with TypedJoin[T] {
//
//  def this(meta: EntityMeta) {
//    // for create root
//    this(meta, null, null, null, null, null)
//  }
//
//  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = {
//    val marker = EntityManager.createMarker[T](meta)
//    fn(marker)
//    val field = marker.toString
//    if (!meta.fieldMap.contains(field) || !meta.fieldMap(field).isRefer) {
//      throw new RuntimeException(s"Unknown Field $field On ${meta.entity}")
//    }
//    if (meta.fieldMap(field).asInstanceOf[FieldMetaRefer].refer.db != meta.db) {
//      throw new RuntimeException(s"Field $field Not the Same DB")
//    }
//    joins.find(_.joinName == field) match {
//      case Some(p) => p.joinType == joinType match {
//        case true => p.asInstanceOf[TypedJoin[R]]
//        case false => throw new RuntimeException(s"JoinType Not Match, ${p.joinType} Exists")
//      }
//      case None =>
//        val referField = meta.fieldMap(field).asInstanceOf[FieldMetaRefer]
//        val join = new TypedJoinImpl[R](referField.refer, this, field, referField.left, referField.right, joinType)
//        joins += join
//        join
//    }
//  }
//
//  override def get(fn: (T) => Object): Field = {
//    val marker = EntityManager.createMarker[T](meta)
//    fn(marker)
//    val field = marker.toString
//    require(field.nonEmpty)
//    val fields = field.split("\\.")
//    val join: Join = fields.take(fields.length - 1).foldLeft(this.asInstanceOf[Join]) { case (j, f) =>
//      j.leftJoin(f)
//    }
//    join.get(fields.last)
//  }
//
//  override def joinAs[R](clazz: Class[R], joinType: JoinType)
//                        (leftFn: T => Object)
//                        (rightFn: R => Object)
//
//  : SelectableJoinImpl[R] = {
//    if (!OrmMeta.entityMap.contains(clazz)) {
//      throw new RuntimeException(s"$clazz Is Not Entity")
//    }
//    val referMeta = OrmMeta.entityMap(clazz)
//    val lm = EntityManager.createMarker[T](meta)
//    val rm = EntityManager.createMarker[R](referMeta)
//    leftFn(lm)
//    rightFn(rm)
//    val left = lm.toString
//    val right = rm.toString
//    if (!meta.fieldMap.contains(left)) {
//      throw new RuntimeException(s"Unknown Field $left On ${meta.entity}")
//    }
//    if (!referMeta.fieldMap.contains(right)) {
//      throw new RuntimeException(s"Unknown Field $right On ${referMeta.entity}")
//    }
//    if (meta.db != referMeta.db) {
//      throw new RuntimeException(s"$left And $right Not The Same DB")
//    }
//    val join = joins.find(_.joinName == referMeta.entity) match {
//      case Some(p) => p
//      case None =>
//        val join = new TypedJoinImpl[R](referMeta, this, referMeta.entity, left, right, joinType)
//        joins += join
//        join
//    }
//    new SelectableJoinImpl[R](clazz, join)
//  }
//}
//
//class TypedSelectableJoinImpl[T](clazz: Class[T], impl: TypedJoinImpl[T])
//  extends SelectableJoinImpl[T](clazz, impl) with TypedSelectableJoin[T] {
//  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = impl.join(fn, joinType)
//
//  override def fields(fields: Array[String]): TypedSelectableJoin[T] = {
//    super.fields(fields)
//    this
//  }
//
//  override def ignore(fields: Array[String]): TypedSelectableJoin[T] = {
//    super.ignore(fields)
//    this
//  }
//
//  override def get(fn: (T) => Object): Field = impl.get(fn)
//
//  override def joinAs[R](clazz: Class[R], joinType: JoinType)
//                        (leftFn: (T) => Object)
//                        (rightFn: (R) => Object): SelectableJoinImpl[R]
//  = impl.joinAs(clazz, joinType)(leftFn)(rightFn)
//
//  override def fields(fns: (T => Object)*): TypedSelectableJoin[T] = {
//    val fields = fns.map(fn => {
//      val marker = EntityManager.createMarker[T](impl.meta)
//      fn(marker)
//      marker.toString
//    })
//    super.fields(fields: _*)
//    this
//  }
//
//  override def ignore(fns: (T => Object)*): TypedSelectableJoin[T] = {
//    val fields = fns.map(fn => {
//      val marker = EntityManager.createMarker[T](impl.meta)
//      fn(marker)
//      marker.toString
//    })
//    super.ignore(fields: _*)
//    this
//  }
//}
//
//class TypedRootImpl[T](clazz: Class[T], impl: TypedSelectableJoinImpl[T])
//  extends RootImpl[T](clazz, impl.impl) with TypedRoot[T] {
//  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = impl.join(fn, joinType)
//
//  override def fields(fields: Array[String]): TypedRoot[T] = {
//    super.fields(fields)
//    this
//  }
//
//  override def ignore(fields: Array[String]): TypedRoot[T] = {
//    super.ignore(fields)
//    this
//  }
//
//  override def get(fn: (T) => Object): Field = impl.get(fn)
//
//  override def joinAs[R](clazz: Class[R], joinType: JoinType)
//                        (leftFn: (T) => Object)
//                        (rightFn: (R) => Object): SelectableJoinImpl[R]
//  = impl.joinAs(clazz, joinType)(leftFn)(rightFn)
//
//  override def fields(fns: (T => Object)*): TypedRoot[T] = {
//    impl.fields(fns: _*)
//    this
//  }
//
//  override def ignore(fns: (T => Object)*): TypedRoot[T] = {
//    impl.ignore(fns: _*)
//    this
//  }
//}
