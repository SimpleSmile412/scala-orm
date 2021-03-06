package io.github.yuemenglong.orm.kit

import java.lang.reflect.{Field, Method}

import io.github.yuemenglong.orm.lang.interfaces.Entity

import scala.reflect.ClassTag

/**
  * Created by Administrator on 2017/5/24.
  */
object Kit {
  def lodashCase(str: String): String = {
    """[A-Z]""".r.replaceAllIn(lowerCaseFirst(str), m => "_" + m.group(0).toLowerCase())
  }

  def lowerCaseFirst(str: String): String = {
    str.substring(0, 1).toLowerCase() + str.substring(1)
  }

  def upperCaseFirst(str: String): String = {
    str.substring(0, 1).toUpperCase() + str.substring(1)
  }

  def getDeclaredFields(clazz: Class[_]): Array[Field] = {
    val parent = clazz.getSuperclass
    if (parent != null) {
      getDeclaredFields(parent) ++ clazz.getDeclaredFields
    } else {
      clazz.getDeclaredFields
    }
  }

  def getDeclaredMethods(clazz: Class[_]): Array[Method] = {
    val parent = clazz.getSuperclass
    if (parent != null) {
      getDeclaredMethods(parent) ++ clazz.getDeclaredMethods
    } else {
      clazz.getDeclaredMethods
    }
  }

  def newArray(clazz: Class[_], values: Entity*): Array[_] = {
    val ct = ClassTag[Entity](clazz)
    var builder = Array.newBuilder(ct)
    builder ++= values
    builder.result()
  }

  def getArrayType(clazz: Class[_]): Class[_] = {
    if (!clazz.isArray) {
      return clazz
    }
    val name = clazz.getName.replaceAll("(^\\[L)|(;$)", "")
    Class.forName(name)
  }
}
