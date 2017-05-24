package orm.entity

import java.lang.reflect.Method

import net.sf.cglib.proxy.Enhancer
import orm.java.interfaces.Entity
import orm.meta.OrmMeta
import net.sf.cglib.proxy.MethodInterceptor
import net.sf.cglib.proxy.MethodProxy

/**
  * Created by Administrator on 2017/5/18.
  */
object EntityManager {
  def empty[T](clazz:Class[T]): T = {
    require(OrmMeta.entityMap.size > 0)
    val meta = OrmMeta.entityMap(clazz.getSimpleName())
    val core = EntityCore.empty(meta)
    wrap(core).asInstanceOf[T]
  }

  def create[T](clazz:Class[T]): T = {
    require(OrmMeta.entityMap.size > 0)
    val meta = OrmMeta.entityMap(clazz.getSimpleName())
    val core = EntityCore.create(meta)
    wrap(core).asInstanceOf[T]
  }

  def wrap(core: EntityCore): Object = {
    val enhancer: Enhancer = new Enhancer
    enhancer.setSuperclass(core.meta.clazz)
    enhancer.setInterfaces(Array(classOf[Entity]))

    enhancer.setCallback(new MethodInterceptor() {
      @throws[Throwable]
      def intercept(obj: Object, method: Method, args: Array[Object], proxy: MethodProxy): Object = {
        core.intercept(obj, method, args, proxy)
      }
    })
    enhancer.create()
  }

  def core(obj: Object): EntityCore = {
    require(obj != null)
    require(obj.isInstanceOf[Entity])
    val entity = obj.asInstanceOf[Entity]
    return entity.$$core()
  }
}
