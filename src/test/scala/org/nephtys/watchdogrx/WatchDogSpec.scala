package org.nephtys.watchdogrx

import java.io.{File, PrintWriter}
import java.util.concurrent.atomic.AtomicInteger

import collection.mutable.Stack
import org.scalatest._
import org.nephtys.watchdogrx.WatchDogHelpers._
import rx.lang.scala.observers.TestSubscriber
import rx.lang.scala.{Observable, Subscriber}

import scala.collection.mutable

/**
  * Created by nephtys on 10/2/16.
  */
class WatchDogSpec extends FlatSpec with Matchers {
  def writeFile(filepath : String, content : String, modifiedDate : Long = System.currentTimeMillis()) : Unit = {
    Some(new PrintWriter(filepath)).foreach{p => p.write(content); p.close()}
    //println(s"Writing file at moment $modifiedDate")
    new File(filepath).setLastModified(modifiedDate)
  }




  def getProgram(constructor : AtomicInteger, initer : AtomicInteger, closer : AtomicInteger) = new
      ConfigurableProgram {

    constructor.incrementAndGet()

    var closed : Boolean = false
    var initialized : Boolean = false

    override def initializeWithConfig(configs: Map[FilePath, FileContentStream]): Unit = {
      initer.incrementAndGet()
      initialized = true
    }

    override def close(): Unit = {
      closer.getAndIncrement()
      closed = true
    }
  }


  class Triggerable {
    private val subscribers : scala.collection.mutable.Buffer[Subscriber[Long]] = mutable.Buffer.empty
    private var completed = false
    private var errored = false
    private val observable = Observable.apply[Long](subscriber => {
        if(!(completed || errored)) {
          subscribers.+=(subscriber)
        }
    })

    def complete() : Unit  = {
      completed = true
      subscribers.foreach(_.onCompleted())
      subscribers.clear()
    }
    def endWithError(error : Throwable) : Unit = {
      errored = true
      subscribers.foreach(_.onError(error))
      subscribers.clear()
    }

    def obs : Observable[Long] = observable
    def trigger(e : Long) : Unit = subscribers.foreach(_.onNext(e))
  }



  "WatchdogsRx" should "restart the program every time a file changes" in {
    val source = new Triggerable()

    val filep1 = "target/test1-file1.txt"
    val filec1 = "content_file_1\n"
    val filep2 = "target/test1-file2.txt"
    val filec2 = "content_file_2\n"
    val filec2new = "changed_content_file2\n"
    writeFile(filep1, filec1)
    writeFile(filep2, filec2)

    val constructorcounter = new AtomicInteger(0)
    val initcounter = new AtomicInteger(0)
    val closecounter = new AtomicInteger(0)

    val getterProgram = () => getProgram(constructorcounter, initcounter, closecounter)

    val filepaths : Seq[FilePath] = Seq(FilePath(filep1), FilePath(filep2))


    constructorcounter.get() should be (0)
    initcounter.get() should be (0)
    closecounter.get() should be (0)

    source.obs.programWithConfigsFilePaths(filepaths)(getterProgram)

    //first trigger, should mean an element
    source.trigger(System.currentTimeMillis() - 100)
    //second trigger, should mean nothing
    source.trigger(System.currentTimeMillis())

    constructorcounter.get() should be (1)
    initcounter.get() should be (1)
    closecounter.get() should be (0)

    Thread.sleep(1000)
    writeFile(filep2, filec2new)
    Thread.sleep(500)
    source.trigger(System.currentTimeMillis())


    Thread.sleep(100)
    constructorcounter.get() should be (2)
    initcounter.get() should be (2)
    closecounter.get() should be (1)

    source.complete()

  }

  it should "give the correct Map on startup" in {
    val filep1 = "target/test2-file1.txt"
      val filec1 = "content_file_1\n"
        val filep2 = "target/test2-file2.txt"
          val filec2 = "content_file_2\n"
            writeFile(filep1, filec1)
    writeFile(filep2, filec2)

    val filepaths : Seq[FilePath] = Seq(FilePath(filep1), FilePath(filep2))

    filepaths.size should be (2)
    val first = Observable.just(1).loadFileContentsOnEmission(filepaths).toBlocking.first

    println(first)
    first.size should be (2)
    first(FilePath(filep1)).contentAsString should be (filec1)
    first(FilePath(filep2)).contentAsString should be (filec2)

  }

  it should "give the correct Map if a file changes" in {
    val source = new Triggerable()

    val filep1 = "target/test3-file1.txt"
    val filec1 = "content_file_1\n"
    val filep2 = "target/test3-file2.txt"
    val filec2 = "content_file_2\n"
    val filec2new = "changed_content_file2\n"
    writeFile(filep1, filec1)
    writeFile(filep2, filec2)

    val filepaths : Seq[FilePath] = Seq(FilePath(filep1), FilePath(filep2))

    val testsubscriber = TestSubscriber.apply[Map[FilePath, FileContentStream]]()
    source.obs.loadFileContentsOnEmission(filepaths).distinct.subscribe(testsubscriber)
    //first trigger, should mean an element
    source.trigger(System.currentTimeMillis() - 100)
    //second trigger, should mean nothing
    source.trigger(System.currentTimeMillis())

    val eventsBefore = testsubscriber.getOnNextEvents
    eventsBefore.size should be (1)
    eventsBefore(0)(FilePath(filep2)).contentAsString should be (filec2) //force lazy evaluation now
    //third trigger, should mean new element
    Thread.sleep(1000)
    writeFile(filep2, filec2new)
    Thread.sleep(1000)
    source.trigger(System.currentTimeMillis())

    source.complete()
    testsubscriber.assertCompleted()
    val events = testsubscriber.getOnNextEvents

    println(events)
    events.size should be (2)
    events(0)(FilePath(filep1)).contentAsString should be (filec1)
    events(0)(FilePath(filep2)).contentAsString should be (filec2) // this is only possible because of previous call
    events(1)(FilePath(filep2)).contentAsString should be (filec2new)

  }

}