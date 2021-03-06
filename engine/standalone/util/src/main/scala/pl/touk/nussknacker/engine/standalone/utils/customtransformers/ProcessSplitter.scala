package pl.touk.nussknacker.engine.standalone.utils.customtransformers

import cats.data.NonEmptyList
import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, Unknown}
import pl.touk.nussknacker.engine.api.typed.{ClazzRef, ReturningType, typing}
import pl.touk.nussknacker.engine.standalone.api.StandaloneCustomTransformer
import pl.touk.nussknacker.engine.standalone.api.types.InterpreterType

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}

object ProcessSplitter extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("parts") parts: LazyParameter[java.util.Collection[Any]]): StandaloneCustomTransformer = {
    new ProcessSplitter(parts)
  }

}

class ProcessSplitter(parts: LazyParameter[java.util.Collection[Any]])
  extends StandaloneCustomTransformer with ReturningType {

  override def createTransformation(outputVariable: String): StandaloneCustomTransformation =
    (continuation: InterpreterType, lpi: LazyParameterInterpreter) => (ctx, ec) => {
      implicit val ecc: ExecutionContext = ec
      lpi.createInterpreter(parts)(ec, ctx).flatMap { partsToInterpret =>
        Future.sequence(partsToInterpret.asScala.toList.map { partToInterpret =>
          val newCtx = ctx.withVariable(outputVariable, partToInterpret)
          continuation(newCtx, ec)
        })
      }.map { results: List[Either[NonEmptyList[EspExceptionInfo[_ <: Throwable]], List[InterpretationResult]]] =>
        results.sequence.right.map(_.flatten)
      }
    }

  override def returnType: typing.TypingResult = {
    parts.returnType match {
      case Typed(classes) if classes.size == 1
        && classes.head.canBeSubclassOf(ClazzRef[java.util.Collection[_]]) && classes.head.params.nonEmpty =>
        classes.head.params.head
      case _ => Unknown
    }
  }
}
