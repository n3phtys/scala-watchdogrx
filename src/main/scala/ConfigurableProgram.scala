import java.io.Closeable

/**
  * Created by nephtys on 10/1/16.
  */
trait ConfigurableProgram extends AutoCloseable {

  def initializeWithConfig(configs : Map[FilePath, FileContentStream]) : Unit
}
