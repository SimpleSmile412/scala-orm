package io.github.yuemenglong.orm.test

import io.github.yuemenglong.orm.Orm
import io.github.yuemenglong.orm.db.Db
import io.github.yuemenglong.orm.kit.Kit
import io.github.yuemenglong.orm.meta.OrmMeta
import io.github.yuemenglong.orm.operate.core.traits.{Join, JoinInner}
import io.github.yuemenglong.orm.operate.join.traits.{Cascade, SelectFieldCascade}
import io.github.yuemenglong.orm.test.entity.Obj

/**
  * Created by <yuemenglong@126.com> on 2018/3/14.
  */
object PerformanceTest {
  def openDb(): Db = Orm.openDb("localhost", 3306, "root", "root", "test")

  def main(args: Array[String]): Unit = {
    Orm.init("io.github.yuemenglong.orm.test.entity")
    val meta = OrmMeta.entityMap(classOf[Obj])
    val root = new SelectFieldCascade {
      override val inner = new JoinInner(meta)
    }

    val mo = root.select("om").select("mo")
    mo.fields()
    println(root.getColumnWithAs)
  }
}

//  def main(args: Array[String]): Unit = {
//    Orm.init("io.github.yuemenglong.orm.test.entity")
//    val db = openDb()
//    db.rebuild()
//    db.beginTransaction(session => {
//      val obj = new Obj
//      obj.name = "name"
//      session.execute(Orm.insert(obj))
//
//      val start = System.currentTimeMillis()
//      val root = Orm.root(classOf[Obj])
//      //    root.select(_.ptr)
//      //    root.select(_.oo)
//      //    root.selects(_.om).select(_.mo)
//      val query = Orm.selectFrom(root).where(root.get(_.id).===(1).and(root.get(_.age) >= 10))
//      val res = (1 to 100000).map(_ => {
//        query.getSql
//        //        session.query(query)
//      })
//      val end = System.currentTimeMillis()
//      println(end - start, res.length)
//    })
//  }
//}
