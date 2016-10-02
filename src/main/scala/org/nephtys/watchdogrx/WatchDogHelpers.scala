package org.nephtys.watchdogrx

import java.io.{BufferedReader, InputStream, InputStreamReader}
import java.nio.charset.Charset

import rx.lang.scala.{Observable, Subscription}

import scala.util.{Failure, Success, Try}

/**
  * Created by nephtys on 10/1/16.
  */
object WatchDogHelpers {

  implicit class ObservableFileMapExtension(filestreams: Observable[Map[FilePath, FileContentStream]]) {
    /**
      * closes streams of all open streams
      * @param program
      * @return
      */
    def forEachDistinct(program : () => ConfigurableProgram): Subscription = {
      var previous : Option[(Map[FilePath, FileContentStream], ConfigurableProgram)] = None
      filestreams.distinct.subscribe(config => {
        previous match {
          case Some((previousConfig, previousProgram)) => {
            previousProgram.close()
            previousConfig.foreach(_._2.closeIfOpen())
            val pr = program.apply()
            previous = Some((config, pr))
            pr.initializeWithConfig(config)
          }
          case None => {
              val pr = program.apply()
              previous = Some((config, pr))
              pr.initializeWithConfig(config)
          }
        }
      })
    }
  }

  implicit class ObservableAnyFileExtension(trigger: Observable[Any]) {
    def loadFileContentsOnEmission(filepaths : Seq[FilePath]): Observable[Map[FilePath, FileContentStream]] = {
      //println("Creating loadFile obs from " + filepaths)
      //cache previous map emision as option
      var previous : Option[Map[FilePath, FileContentStream]] = None
      //use map
      trigger.map(l => {
        //use FileContentStream object methods to read files (match with cached map)
        previous match {
          case Some(previousMap) => {
            val newmap = filepaths.map(p => (p, FileContentStream.from(p, previousMap.get(p)))).toMap
            //println("Newmap: "+ newmap)
            previous = Some(newmap)
            newmap
          }
          case None => {
            val newmap = filepaths.map(p => (p, FileContentStream.from(p, None))).toMap
            //println("Newmap: "+ newmap)
            previous = Some(newmap)
            newmap
          }
        }
      })
    }

    def programWithConfigsFilePaths(filepaths : Seq[FilePath])(program : () => ConfigurableProgram) : Unit = {
      trigger.loadFileContentsOnEmission(filepaths).forEachDistinct(program)
    }
    def programWithConfigs(filepaths : Seq[String])(program : () => ConfigurableProgram) : Unit =
      programWithConfigsFilePaths(filepaths.map(s => FilePath(s)))(program)
  }




  def inputstreamToString(inputStream : InputStream, cs: Charset) : String = {
    var str = ""
    cleanly(new BufferedReader(new InputStreamReader(inputStream, cs)))(_.close()) { br =>
      val sb = new StringBuilder()
      var line : String = ""
      while ({
        val l = br.readLine()
        line = l
        l != null
      }) {
        sb.append(line)
        sb.append('\n')
      }
      str = sb.toString()
    }
    str
  }

//copied from https://www.phdata.io/try-with-resources-in-scala/
  def cleanly[A, B](resource: A)(cleanup: A => Unit)(doWork: A => B): Try[B] = {
    try {
      Success(doWork(resource))
    } catch {
      case e: Exception => Failure(e)
    }
    finally {
      try {
        if (resource != null) {
          cleanup(resource)
        }
      } catch {
        case e: Exception => println(e) //TODO: should be logged
      }
    }
  }

}
