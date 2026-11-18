package dev.guardrail
package sbt

import _root_.sbt._

object GuardrailHelpers {

  private def dropExtension(filePath: String): String = {
    filePath.split('.').toList match {
      case l@List() => l
      case l@List(_) => l
      case l => l.dropRight(1)
    }
  }.mkString(".")

  private def recursiveListFiles(f: File): List[File] = {
    val these = Option(f.listFiles).toList.flatten
    these.filter(f => f.isFile) ++ these.filter(_.isDirectory).flatMap(recursiveListFiles)
  }

  def isOpenApiSpec(file: File): Boolean = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{Map => JMap}
    import scala.io.Source
    import scala.jdk.CollectionConverters._
    import scala.util.{Try, Using}

    Using(Source.fromFile(file)) { source =>
      val reader = source.bufferedReader()
      val docs = new Load(LoadSettings.builder().setAllowDuplicateKeys(true).setAllowRecursiveKeys(true).build()).loadAllFromReader(reader).asScala
      val yamls = docs.flatMap(d => Try(d.asInstanceOf[JMap[String, Any]].asScala).toOption).toList
      yamls.exists(m => m.contains("openapi") || m.contains("swagger"))
    }.toOption.getOrElse(false)
  }

  def referencedFiles(spec: File): Set[File] = {
    @scala.annotation.tailrec
    def loop(pending: List[File], seen: Set[File]): Set[File] = pending match {
      case Nil => seen
      case file :: rest if seen.contains(file) => loop(rest, seen)
      case file :: rest => loop(externalRefs(file) ++ rest, seen + file)
    }
    val root = spec.getCanonicalFile
    loop(externalRefs(root), Set(root)) - root
  }

  private def externalRefs(file: File): List[File] = {
    import org.snakeyaml.engine.v2.api.{Load, LoadSettings}

    import java.util.{IdentityHashMap, Collections, Map => JMap, List => JList}
    import scala.jdk.CollectionConverters._

    val visited = Collections.newSetFromMap(new IdentityHashMap[AnyRef, java.lang.Boolean]())
    def refs(node: Any): List[String] = node match {
      case m: JMap[_, _] if visited.add(m) =>
        m.asScala.toList.flatMap {
          case ("$ref", ref: String) => List(ref)
          case (_, value) => refs(value)
        }
      case l: JList[_] if visited.add(l) => l.asScala.toList.flatMap(refs)
      case _ => Nil
    }

    val docs =
      try IO.reader(file)(reader => new Load(LoadSettings.builder().setAllowDuplicateKeys(true).build()).loadAllFromReader(reader).asScala.toList)
      catch { case _: Exception => Nil }

    docs.flatMap(refs).flatMap { ref =>
      val path = ref.takeWhile(_ != '#')
      if (path.isEmpty || path.contains("://")) None
      else Some(new File(file.getParentFile, path).getCanonicalFile).filter(_.isFile)
    }
  }

  case class DiscoveredFile(base: File, file: File, fileRelative: File) {
    val fileRelativePath: String = fileRelative.getPath
    val fileRelativePathWithoutExtension: String = dropExtension(fileRelative.getPath)
    val pkg: String = fileRelativePathWithoutExtension.replace(Path.sep, '.')
  }

  def discoverFiles(base: File): List[DiscoveredFile] = recursiveListFiles(base).flatMap { file =>
    file.relativeTo(base).map { fileRelative =>
      DiscoveredFile(base, file, fileRelative)
    }
  }

  def discoverOpenApiFiles(base: File): List[DiscoveredFile] =
     discoverFiles(base).filter(f => isOpenApiSpec(f.file))

  def createGuardrailTasks(
                            sourceDirectory: File,
                          )(discoveredFileToTasks: DiscoveredFile => List[dev.guardrail.sbt.Types.Args]): List[dev.guardrail.sbt.Types.Args] =
    discoverOpenApiFiles(sourceDirectory).flatMap(discoveredFileToTasks)

}