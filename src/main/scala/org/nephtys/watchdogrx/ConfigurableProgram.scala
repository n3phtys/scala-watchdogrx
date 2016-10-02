package org.nephtys.watchdogrx

/**
  * Created by nephtys on 10/1/16.
  */
trait ConfigurableProgram extends AutoCloseable {

  def initializeWithConfig(configs : Map[FilePath, FileContentStream]) : Unit



}
