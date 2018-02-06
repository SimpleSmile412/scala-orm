package io.github.yuemenglong.orm

import io.github.yuemenglong.orm.db.Db
import io.github.yuemenglong.orm.entity.EntityManager
import io.github.yuemenglong.orm.init.Scanner
import io.github.yuemenglong.orm.lang.interfaces.Entity
import io.github.yuemenglong.orm.logger.Logger
import io.github.yuemenglong.orm.meta.{EntityMeta, OrmMeta}
import io.github.yuemenglong.orm.operate.impl._
import io.github.yuemenglong.orm.operate.impl.core._
import io.github.yuemenglong.orm.operate.traits.core.JoinType.JoinType
import io.github.yuemenglong.orm.operate.traits.core._
import io.github.yuemenglong.orm.operate.traits._

import scala.reflect.ClassTag

object Orm {

  def init(paths: Array[String]): Unit = {
    Scanner.scan(paths)
  }

  private[orm] def init(path: String): Unit = {
    Scanner.scan(path)
  }

  private def init(clazzs: Array[Class[_]]): Unit = {
    Scanner.scan(clazzs)
  }

  def reset(): Unit = {
    OrmMeta.reset()
  }

  def openDb(host: String, port: Int, user: String, pwd: String, db: String,
             minConn: Int, maxConn: Int, partition: Int): Db = {
    if (OrmMeta.entityVec.isEmpty) throw new RuntimeException("Orm Not Init Yet")
    new Db(host, port, user, pwd, db, minConn, maxConn, partition)
  }

  def openDb(host: String, port: Int, user: String, pwd: String, db: String): Db = {
    if (OrmMeta.entityVec.isEmpty) throw new RuntimeException("Orm Not Init Yet")
    new Db(host, port, user, pwd, db, 5, 30, 3)
  }

  def create[T](clazz: Class[T]): T = {
    EntityManager.create(clazz)
  }

  def empty[T](clazz: Class[T]): T = {
    EntityManager.empty(clazz)
  }

  def convert[T](obj: T): T = {
    if (obj.getClass.isArray) {
      val arr = obj.asInstanceOf[Array[_]]
      if (arr.isEmpty) {
        obj
      } else {
        arr.map(convert).toArray(ClassTag(arr(0).getClass)).asInstanceOf[T]
      }
    } else {
      EntityManager.convert(obj.asInstanceOf[Object]).asInstanceOf[T]
    }
  }

  @Deprecated
  def converts[T](arr: Array[T]): Array[T] = {
    if (arr.isEmpty) {
      throw new RuntimeException("Converts Nothing")
    }
    arr.map(convert).toArray(ClassTag(arr(0).getClass))
  }

  def setLogger(b: Boolean): Unit = {
    Logger.setEnable(b)
  }

  def insert[T <: Object](obj: T): TypedExecuteRoot[T] = ExecuteRootImpl.insert(convert(obj))

  def update[T <: Object](obj: T): TypedExecuteRoot[T] = ExecuteRootImpl.update(convert(obj))

  def delete[T <: Object](obj: T): TypedExecuteRoot[T] = ExecuteRootImpl.delete(convert(obj))

  def root[T](clazz: Class[T]): Root[T] = {
    OrmMeta.entityMap.get(clazz) match {
      case None => throw new RuntimeException("Not Entity Class")
      case Some(m) =>
        val rootInner = new JoinInner {
          override val meta: EntityMeta = m
          override val parent: Join = null
          override val joinName: String = null
          override val right: String = null
          override val left: String = null
          override val joinType: JoinType = null
        }
        val root = new RootImpl[T] with TypedRootImpl[T] with TypedSelectJoinImpl[T] with TypedJoinImpl[T]
          with SelectableImpl[T] with SelectFieldJoinImpl with JoinImpl {
          override val inner: JoinInner = rootInner
        }
        root
    }
  }

  def cond(): Cond = new CondHolder

  def select[T](s: Selectable[T]): QueryBuilder[T] = {
    new QueryBuilderImpl[T] {
      override val st = new SelectableTupleImpl[T](s.getType, s)
    }
  }

  def select[T1, T2](s1: Selectable[T1], s2: Selectable[T2]): QueryBuilder[(T1, T2)] = {
    new QueryBuilderImpl[(T1, T2)] {
      override val st = new SelectableTupleImpl[(T1, T2)](classOf[(T1, T2)], s1, s2)
    }
  }

  def select[T1, T2, T3](s1: Selectable[T1], s2: Selectable[T2], s3: Selectable[T3]): QueryBuilder[(T1, T2, T3)] = {
    new QueryBuilderImpl[(T1, T2, T3)] {
      override val st = new SelectableTupleImpl[(T1, T2, T3)](classOf[(T1, T2, T3)], s1, s2, s3)
    }
  }

  def selectFrom[T](root: Root[T]): Query[T, T] = {
    val pRoot = root
    new QueryImpl[T, T] with TypedQueryImpl[T, T] with QueryBuilderImpl[T] {
      override val root = pRoot
      override val st = new SelectableTupleImpl[T](root.getType, root)
    }
  }

  @Deprecated
  def insert[T](clazz: Class[T]): ExecutableInsert[T] = new InsertImpl(clazz)

  def inserts[T](arr: Array[T]): ExecutableInsert[T] = {
    arr.isEmpty match {
      case true => throw new RuntimeException("Batch Insert But Array Is Empty")
      case false => {
        val entityArr = Orm.convert(arr)
        val clazz = entityArr(0).asInstanceOf[Entity].$$core()
          .meta.clazz.asInstanceOf[Class[T]]
        new InsertImpl[T](clazz).values(entityArr)
      }
    }
  }

  def update(root: Root[_]): ExecutableUpdate = new UpdateImpl(root)

  def delete(joins: Join*): ExecutableDelete = new DeleteImpl(joins: _*)

  def deleteFrom(root: Root[_]): ExecutableDelete = new DeleteImpl(root).from(root)

  def clear(obj: Object, field: String): Unit = EntityManager.clear(obj, field)

  def clear[T <: Object](obj: T)(fn: T => Any): Unit = EntityManager.clear(obj)(fn)
}
