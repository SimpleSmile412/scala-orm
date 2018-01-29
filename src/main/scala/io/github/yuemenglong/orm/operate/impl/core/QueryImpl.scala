package io.github.yuemenglong.orm.operate.impl.core

import java.lang
import java.sql.ResultSet

import io.github.yuemenglong.orm.entity.{EntityCore, EntityManager}
import io.github.yuemenglong.orm.kit.Kit
import io.github.yuemenglong.orm.lang.interfaces.Entity
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

class JoinImpl(val meta: EntityMeta, val parent: Join,
               val joinName: String, val left: String, val right: String,
               val joinType: JoinType) extends Join {
  private[orm] var cond: Cond = new CondHolder
  private[orm] val joins = new ArrayBuffer[JoinImpl]()
  private val fields = new ArrayBuffer[FieldImpl]()

  def this(meta: EntityMeta) {
    // for create root
    this(meta, null, null, null, null, null)
  }

  override def getMeta: EntityMeta = meta

  override def join(field: String, joinType: JoinType): Join = {
    if (!meta.fieldMap.contains(field) || !meta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Field $field On ${meta.entity}")
    }
    if (meta.fieldMap(field).asInstanceOf[FieldMetaRefer].refer.db != meta.db) {
      throw new RuntimeException(s"Field $field Not the Same DB")
    }
    joins.find(_.joinName == field) match {
      case Some(p) => p.joinType == joinType match {
        case true => p
        case false => throw new RuntimeException(s"JoinType Not Match, ${p.joinType} Exists")
      }
      case None =>
        val referField = meta.fieldMap(field).asInstanceOf[FieldMetaRefer]
        val join = new JoinImpl(referField.refer, this, field, referField.left, referField.right, joinType)
        joins += join
        join
    }
  }

  override def joinAs[T](left: String, right: String, clazz: Class[T], joinType: JoinType): SelectableJoin[T] = {
    if (!OrmMeta.entityMap.contains(clazz)) {
      throw new RuntimeException(s"$clazz Is Not Entity")
    }
    val referMeta = OrmMeta.entityMap(clazz)
    if (!meta.fieldMap.contains(left)) {
      throw new RuntimeException(s"Unknown Field $left On ${meta.entity}")
    }
    if (!referMeta.fieldMap.contains(right)) {
      throw new RuntimeException(s"Unknown Field $right On ${referMeta.entity}")
    }
    if (meta.db != referMeta.db) {
      throw new RuntimeException(s"$left And $right Not The Same DB")
    }
    val join = joins.find(_.joinName == referMeta.entity) match {
      case Some(p) => p
      case None =>
        val join = new JoinImpl(referMeta, this, referMeta.entity, left, right, joinType)
        joins += join
        join
    }
    new SelectableJoinImpl[T](clazz, join)
  }

  override def get(field: String): Field = {
    if (!meta.fieldMap.contains(field) || meta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Field $field On ${meta.entity}")
    }
    fields.find(_.getField == field) match {
      case Some(f) => f
      case None =>
        val fieldMeta = meta.fieldMap(field)
        val f = new FieldImpl(fieldMeta, this)
        fields += f
        f
    }
  }

  override def getRoot: Node = {
    if (parent != null) {
      parent.getRoot
    }
    else {
      this
    }
  }

  override def as[T](clazz: Class[T]): SelectableJoin[T] = new SelectableJoinImpl[T](clazz, this)

  override def getAlias: String = {
    if (parent == null) {
      Kit.lowerCaseFirst(meta.entity)
    } else {
      s"${parent.getAlias}_${Kit.lowerCaseFirst(joinName)}"
    }
  }

  override def getTableWithJoinCond: String = {
    if (parent == null) {
      s"`${meta.table}` AS `$getAlias`"
    } else {
      val leftColumn = parent.getMeta.fieldMap(left).column
      val rightColumn = meta.fieldMap(right).column
      val leftTable = parent.getAlias
      val rightTable = getAlias
      val condSql = new JoinCond(leftTable, leftColumn, rightTable, rightColumn).and(cond).getSql
      s"${joinType.toString} JOIN `${meta.table}` AS `$getAlias` ON $condSql"
    }
  }

  override def getParams: Array[Object] = cond.getParams ++ joins.flatMap(_.getParams).toArray[Object]

  override def on(c: Cond): Join = {
    cond = c
    this
  }
}

class SelectJoinImpl(val impl: JoinImpl) extends SelectJoin {
  protected[impl] var selects = new ArrayBuffer[(String, SelectJoinImpl)]()
  protected[impl] var fields: Array[FieldImpl] = impl.meta.fields().filter(_.isNormalOrPkey).map(f => new FieldImpl(f, impl)).toArray

  override def getAlias: String = impl.getAlias

  override def getTableWithJoinCond: String = impl.getTableWithJoinCond

  override def join(field: String, joinType: JoinType): Join = impl.join(field, joinType)

  override def joinAs[T](left: String, right: String, clazz: Class[T], joinType: JoinType): SelectableJoin[T] = impl.joinAs(left, right, clazz, joinType)

  override def get(field: String): Field = impl.get(field)

  override def getRoot: Node = impl.getRoot

  override def as[R](clazz: Class[R]): SelectableJoin[R] = impl.as(clazz)

  /*---------------------------------------------------------------------*/

  override def select(field: String): SelectJoin = {
    if (!impl.meta.fieldMap.contains(field) || !impl.meta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Object Field $field")
    }
    selects.find(_._1 == field) match {
      case Some(p) => p._2
      case None =>
        val j = impl.leftJoin(field).asInstanceOf[JoinImpl]
        val s = new SelectJoinImpl(j)
        selects += ((field, s))
        s
    }
  }

  override def fields(fields: Array[String]): SelectJoin = {
    fields.foreach(f => {
      if (!this.getMeta.fieldMap.contains(f)) {
        throw new RuntimeException(s"Invalid Field $f In ${this.getMeta.entity}")
      }
      if (!this.getMeta.fieldMap(f).isNormal) {
        throw new RuntimeException(s"Not Normal Field $f In ${this.getMeta.entity}")
      }
    })
    val pkey = new FieldImpl(this.getMeta.pkey, impl)
    this.fields = Array(pkey) ++ fields.map(this.getMeta.fieldMap(_)).map(f => new FieldImpl(f, impl))
    this
  }

  protected def getFilterKey(core: EntityCore): String = {
    s"$getAlias@${core.getPkey.toString}"
  }

  protected def getOneManyFilterKey(field: String, core: EntityCore): String = {
    s"$getAlias@$field@${core.getPkey.toString}"
  }

  protected def pickResult(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): Entity = {
    val a = pickSelf(resultSet, filterMap)
    if (a == null) {
      return null
    }
    pickRefer(a, resultSet, filterMap)
    a
  }

  protected def pickSelf(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): Entity = {
    val map: Map[String, Object] = fields.map(field => {
      val alias = field.getAlias
      val value = resultSet.getObject(alias)
      (field.meta.name, value)
    })(collection.breakOut)
    val core = new EntityCore(impl.meta, map)
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

  protected def pickRefer(a: Object, resultSet: ResultSet, filterMap: mutable.Map[String, Entity]) {
    val aCore = EntityManager.core(a)
    selects.foreach { case (field, select) =>
      val fieldMeta = impl.meta.fieldMap(field)
      val b = select.pickResult(resultSet, filterMap)
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

  override def on(c: Cond): Join = impl.on(c)

  override def getParams: Array[Object] = impl.getParams

  override def getMeta: EntityMeta = impl.getMeta

  override def ignore(fields: Array[String]): SelectJoin = {
    fields.foreach(f => {
      if (!getMeta.fieldMap.contains(f)) {
        throw new RuntimeException(s"Not Exists Field, $f")
      }
      val fieldMeta = getMeta.fieldMap(f)
      if (!fieldMeta.isNormal) {
        throw new RuntimeException(s"Only Normal Field Can Ignore, $f")
      }
      this.fields = this.fields.filter(_.meta.name != f)
    })
    this
  }
}

class SelectableJoinImpl[T](val clazz: Class[T], impl: JoinImpl)
  extends SelectJoinImpl(impl) with SelectableJoin[T] {
  if (clazz != impl.meta.clazz) {
    throw new RuntimeException("Class Not Match")
  }

  override def getColumnWithAs: String = {
    def go(select: SelectJoinImpl): Array[String] = {
      val selfColumn = select.fields.map(field => s"${field.getColumn} AS ${field.getAlias}")
      // 1. 属于自己的字段 2. 级联的部分
      selfColumn ++ select.selects.map(_._2).flatMap(go)
    }

    go(this).mkString(",\n")
  }

  override def getType: Class[T] = clazz

  override def getKey(value: Object): String = {
    if (value == null) {
      ""
    } else {
      value.asInstanceOf[Entity].$$core().getPkey.toString
    }
  }

  override def pick(resultSet: ResultSet, filterMap: mutable.Map[String, Entity]): T = pickResult(resultSet, filterMap).asInstanceOf[T]

  override def fields(fields: Array[String]): SelectableJoin[T] = super.fields(fields).asInstanceOf[SelectableJoin[T]]

  override def ignore(fields: Array[String]): SelectableJoin[T] = super.ignore(fields).asInstanceOf[SelectableJoin[T]]
}

class RootImpl[T](clazz: Class[T], impl: JoinImpl)
  extends SelectableJoinImpl[T](clazz, impl)
    with Root[T] {

  override def getTableWithJoinCond: String = {
    def go(join: JoinImpl): Array[String] = {
      Array(join.getTableWithJoinCond) ++ join.joins.flatMap(go)
    }

    go(impl).mkString("\n")
  }

  override def count(): Selectable[java.lang.Long] = new Count_(this)

  override def count(field: Field): SelectableField[lang.Long] = new Count(field)

  override def sum(field: Field): SelectableField[lang.Double] = new Sum(field)

  override def max[R](field: Field, clazz: Class[R]): SelectableField[R] = new Max(field, clazz)

  override def min[R](field: Field, clazz: Class[R]): SelectableField[R] = new Min(field, clazz)

  override def fields(fields: Array[String]): Root[T] = super.fields(fields).asInstanceOf[Root[T]]

  override def ignore(fields: Array[String]): Root[T] = super.ignore(fields).asInstanceOf[Root[T]]
}

class TypedJoinImpl[T](meta: EntityMeta, parent: Join,
                       joinName: String, left: String, right: String,
                       joinType: JoinType)
  extends JoinImpl(meta, parent, joinName, left, right, joinType) with TypedJoin[T] {

  def this(meta: EntityMeta) {
    // for create root
    this(meta, null, null, null, null, null)
  }

  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = {
    val marker = EntityManager.createMarker[T](meta)
    fn(marker)
    val field = marker.toString
    if (!meta.fieldMap.contains(field) || !meta.fieldMap(field).isRefer) {
      throw new RuntimeException(s"Unknown Field $field On ${meta.entity}")
    }
    if (meta.fieldMap(field).asInstanceOf[FieldMetaRefer].refer.db != meta.db) {
      throw new RuntimeException(s"Field $field Not the Same DB")
    }
    joins.find(_.joinName == field) match {
      case Some(p) => p.joinType == joinType match {
        case true => p.asInstanceOf[TypedJoin[R]]
        case false => throw new RuntimeException(s"JoinType Not Match, ${p.joinType} Exists")
      }
      case None =>
        val referField = meta.fieldMap(field).asInstanceOf[FieldMetaRefer]
        val join = new TypedJoinImpl[R](referField.refer, this, field, referField.left, referField.right, joinType)
        joins += join
        join
    }
  }

  override def get(fn: (T) => Object): Field = {
    val marker = EntityManager.createMarker[T](meta)
    fn(marker)
    val field = marker.toString
    require(field.nonEmpty)
    val fields = field.split("\\.")
    val join: Join = fields.take(fields.length - 1).foldLeft(this.asInstanceOf[Join]) { case (j, f) =>
      j.leftJoin(f)
    }
    join.get(fields.last)
  }

  override def joinAs[R](clazz: Class[R], joinType: JoinType)
                        (leftFn: T => Object)
                        (rightFn: R => Object)

  : SelectableJoinImpl[R] = {
    if (!OrmMeta.entityMap.contains(clazz)) {
      throw new RuntimeException(s"$clazz Is Not Entity")
    }
    val referMeta = OrmMeta.entityMap(clazz)
    val lm = EntityManager.createMarker[T](meta)
    val rm = EntityManager.createMarker[R](referMeta)
    leftFn(lm)
    rightFn(rm)
    val left = lm.toString
    val right = rm.toString
    if (!meta.fieldMap.contains(left)) {
      throw new RuntimeException(s"Unknown Field $left On ${meta.entity}")
    }
    if (!referMeta.fieldMap.contains(right)) {
      throw new RuntimeException(s"Unknown Field $right On ${referMeta.entity}")
    }
    if (meta.db != referMeta.db) {
      throw new RuntimeException(s"$left And $right Not The Same DB")
    }
    val join = joins.find(_.joinName == referMeta.entity) match {
      case Some(p) => p
      case None =>
        val join = new TypedJoinImpl[R](referMeta, this, referMeta.entity, left, right, joinType)
        joins += join
        join
    }
    new SelectableJoinImpl[R](clazz, join)
  }
}

class TypedSelectableJoinImpl[T](clazz: Class[T], impl: TypedJoinImpl[T])
  extends SelectableJoinImpl[T](clazz, impl) with TypedSelectableJoin[T] {
  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = impl.join(fn, joinType)

  override def fields(fields: Array[String]): TypedSelectableJoin[T] = {
    super.fields(fields)
    this
  }

  override def ignore(fields: Array[String]): TypedSelectableJoin[T] = {
    super.ignore(fields)
    this
  }

  override def get(fn: (T) => Object): Field = impl.get(fn)

  override def joinAs[R](clazz: Class[R], joinType: JoinType)
                        (leftFn: (T) => Object)
                        (rightFn: (R) => Object): SelectableJoinImpl[R]
  = impl.joinAs(clazz, joinType)(leftFn)(rightFn)

  override def fields(fns: (T => Object)*): TypedSelectableJoin[T] = {
    val fields = fns.map(fn => {
      val marker = EntityManager.createMarker[T](impl.meta)
      fn(marker)
      marker.toString
    })
    super.fields(fields: _*)
    this
  }

  override def ignore(fns: (T => Object)*): TypedSelectableJoin[T] = {
    val fields = fns.map(fn => {
      val marker = EntityManager.createMarker[T](impl.meta)
      fn(marker)
      marker.toString
    })
    super.ignore(fields: _*)
    this
  }
}

class TypedRootImpl[T](clazz: Class[T], impl: TypedSelectableJoinImpl[T])
  extends RootImpl[T](clazz, impl.impl) with TypedRoot[T] {
  override def join[R](fn: (T) => R, joinType: JoinType): TypedJoin[R] = impl.join(fn, joinType)

  override def fields(fields: Array[String]): TypedRoot[T] = {
    super.fields(fields)
    this
  }

  override def ignore(fields: Array[String]): TypedRoot[T] = {
    super.ignore(fields)
    this
  }

  override def get(fn: (T) => Object): Field = impl.get(fn)

  override def joinAs[R](clazz: Class[R], joinType: JoinType)
                        (leftFn: (T) => Object)
                        (rightFn: (R) => Object): SelectableJoinImpl[R]
  = impl.joinAs(clazz, joinType)(leftFn)(rightFn)

  override def fields(fns: (T => Object)*): TypedRoot[T] = {
    impl.fields(fns: _*)
    this
  }

  override def ignore(fns: (T => Object)*): TypedRoot[T] = {
    impl.ignore(fns: _*)
    this
  }
}
