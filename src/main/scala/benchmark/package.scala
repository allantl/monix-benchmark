import monix.execution.ExecutionModel.SynchronousExecution
import monix.execution.Scheduler
import monix.execution.Scheduler.Implicits.global

package object benchmark {
  implicit val scheduler: Scheduler =
    global.withExecutionModel(SynchronousExecution)
}
