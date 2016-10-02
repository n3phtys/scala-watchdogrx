package org.nephtys.watchdogrx

import java.io.{File, FileInputStream, IOException}
import java.nio.charset.{Charset, StandardCharsets}

/**
  * Created by nephtys on 10/1/16.
  */
case class FileContentStream(lastModifiedDate : Long, inputstream : FileInputStream, encoding : Charset = StandardCharsets.UTF_8) {
  def closeIfOpen(): Unit = {
    //TODO: makes this smarter / more defensive
    inputstream.close()
  }

  lazy val contentAsString : String = {
      WatchDogHelpers.inputstreamToString(inputstream, encoding)
    }

  override def equals(obj: scala.Any): Boolean = {
    obj.isInstanceOf[FileContentStream] && {
      val other = obj.asInstanceOf[FileContentStream]
      other.lastModifiedDate == this.lastModifiedDate && this.encoding == other.encoding
    }
  }
}

object FileContentStream {

  def from(filepath : FilePath, ifNewerThan : Option[FileContentStream]) : FileContentStream = from(filepath
    .filepath, ifNewerThan)

  def from(filepath : String, ifNewerThan : Option[FileContentStream]) : FileContentStream = {
    val file = new File(filepath)
    if (!file.exists() || !file.isFile || !file.canRead) {
      throw new IOException(s"org.nephtys.watchdogrx.FileContentStream.from() could not find a readable file at $filepath")
    }
    val modifiedDate = file.lastModified()
    ifNewerThan.filter(_.lastModifiedDate >= modifiedDate).getOrElse(FileContentStream(modifiedDate, new FileInputStream(file)))
  }
}