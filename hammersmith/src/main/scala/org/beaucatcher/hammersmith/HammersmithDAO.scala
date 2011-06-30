package org.beaucatcher.hammersmith

import akka.actor.Channel
import akka.dispatch.Future
import akka.dispatch.DefaultCompletableFuture
import org.beaucatcher.bson._
import org.beaucatcher.async._
import org.beaucatcher.mongo._
import com.mongodb.WriteResult
import com.mongodb.CommandResult
import com.mongodb.async.Collection
import com.mongodb.async.futures._
import org.bson.collection.BSONDocument
import org.bson._
import com.mongodb.async.Cursor
import akka.actor.Actor
import akka.actor.ActorRef
import org.bson.DefaultBSONSerializer
import org.bson.collection.Document

private object CursorActor {
    // requests
    sealed trait Request
    case object Next extends Request

    // replies
    sealed trait Result
    case class Entry[T : SerializableBSONObject](doc : T) extends Result
    case object EOF extends Result
}

// actor used to convert a cursor into futures
private class CursorActor[T : SerializableBSONObject](val cursor : Cursor[T]) extends Actor {
    private def handleNext(sender : Channel[CursorActor.Result]) : Unit = {
        cursor.next() match {
            case Cursor.Entry(doc) =>
                sender ! CursorActor.Entry(doc.asInstanceOf[T])
            case Cursor.Empty =>
                cursor.nextBatch(() => { handleNext(sender) })
            case Cursor.EOF =>
                sender ! CursorActor.EOF
        }
    }

    def receive = {
        case CursorActor.Next =>
            // FIXME cast is no longer required when Akka upgrades (Channel now contravariant)
            handleNext(self.channel.asInstanceOf[Channel[CursorActor.Result]])
    }
}

// wrap a Cursor[T] in an Iterator[Future[T]]
private class CursorIterator[T : SerializableBSONObject](val cursor : Cursor[T]) extends Iterator[Future[T]] {
    private val actor = Actor.actorOf(new CursorActor(cursor)).start
    private var result : Option[Future[CursorActor.Result]] = None

    // ensure that result (current item) is not None.
    // when not None, it can be an Entry or EOF.
    private def check = {
        result match {
            case None =>
                result = Some(actor !!! CursorActor.Next)
            case Some(_) =>
        }
        if (!result.isDefined) {
            throw new IllegalStateException("result was None after check")
        }
    }

    override def hasNext = {
        check
        // time to block
        result.get.await.resultOrException.get match {
            case CursorActor.EOF =>
                actor.stop // clean up
                false
            case CursorActor.Entry(_) =>
                true
        }
    }

    override def next = {
        check
        val f = new DefaultCompletableFuture[T]
        result.get.onComplete({ completed : Future[CursorActor.Result] =>
            completed.result match {
                case None =>
                    f.completeWithException(new Exception("Timeout waiting on cursor"))
                case Some(CursorActor.EOF) =>
                    f.completeWithException(new NoSuchElementException("No more items in cursor"))
                case Some(CursorActor.Entry(doc)) =>
                    f.completeWithResult(doc.asInstanceOf[T])
            }
        })
        // need to get another item
        result = None
        // return the future for the previous item
        f
    }
}

private[hammersmith] class HammersmithAsyncDAO[EntityType : SerializableBSONObject, IdType <: AnyRef](protected val collection : Collection)
    extends AsyncDAO[BSONDocument, EntityType, IdType, Any] {
    private implicit def optionalfields2bsondocument(maybeFields : Option[Fields]) : BSONDocument = {
        maybeFields match {
            case Some(fields) =>
                val builder = Document.newBuilder
                for (i <- fields.included) {
                    builder += (i -> 1)
                }
                for (e <- fields.excluded) {
                    builder += (e -> 0)
                }
                builder.result
            case None =>
                Document.empty
        }
    }

    private def completeFromEither[T](f : DefaultCompletableFuture[T])(result : Either[Throwable, T]) : Unit = {
        result match {
            case Left(e) => f.completeWithException(e)
            case Right(o) => f.completeWithResult(o)
        }
    }

    // this is a workaround for a problem where we get a non-option but are wanting an option
    private def completeOptionalFromEither[T](f : DefaultCompletableFuture[Option[T]])(result : Either[Throwable, T]) : Unit = {
        result match {
            case Left(e) => f.completeWithException(e)
            case Right(o) => f.completeWithResult(Some(o))
        }
    }

    override def emptyQuery : BSONDocument =
        Document.empty

    override def count(query : BSONDocument, options : CountOptions) : Future[Long] =
        throw new UnsupportedOperationException

    override def distinct(key : String, options : DistinctOptions[BSONDocument]) : Future[Seq[Any]] =
        throw new UnsupportedOperationException

    override def find(query : BSONDocument, options : FindOptions) : Future[Iterator[Future[EntityType]]] = {
        val f = new DefaultCompletableFuture[Iterator[Future[EntityType]]]
        val handler = RequestFutures.query[EntityType]({ result : Either[Throwable, Cursor[EntityType]] =>
            result match {
                case Left(e) => f.completeWithException(e)
                case Right(c) => f.completeWithResult(new CursorIterator(c))
            }
        })
        collection.find(query, options.fields : BSONDocument, options.skip.getOrElse(0L).intValue,
            options.batchSize.getOrElse(0))(handler)
        // FIXME handle options.limit and options.overrideQueryFlags
        f
    }

    // FIXME shouldn't Hammersmith let us return None if query doesn't match ? or is that an exception ?
    override def findOne(query : BSONDocument, options : FindOneOptions) : Future[Option[EntityType]] = {
        val f = new DefaultCompletableFuture[Option[EntityType]]
        val handler = RequestFutures.findOne[EntityType](completeOptionalFromEither(f)(_))
        collection.findOne(query)(handler)
        f
    }

    // FIXME shouldn't Hammersmith let us return None if query doesn't match ? or is that an exception ?
    override def findOneById(id : IdType, options : FindOneByIdOptions) : Future[Option[EntityType]] = {
        val f = new DefaultCompletableFuture[Option[EntityType]]
        val handler = RequestFutures.findOne[EntityType](completeOptionalFromEither(f)(_))
        collection.findOneByID(id)(handler)
        f
    }

    override def entityToUpsertableObject(entity : EntityType) : BSONDocument =
        throw new UnsupportedOperationException
    override def entityToModifierObject(entity : EntityType) : BSONDocument =
        throw new UnsupportedOperationException
    override def entityToUpdateQuery(entity : EntityType) : BSONDocument =
        throw new UnsupportedOperationException
    override def findAndModify(query : BSONDocument, update : Option[BSONDocument], options : FindAndModifyOptions[BSONDocument]) : Future[Option[EntityType]] =
        throw new UnsupportedOperationException
    override def insert(o : EntityType) : Future[WriteResult] =
        throw new UnsupportedOperationException
    override def update(query : BSONDocument, modifier : BSONDocument, options : UpdateOptions) : Future[WriteResult] =
        throw new UnsupportedOperationException
    override def remove(query : BSONDocument) : Future[WriteResult] =
        throw new UnsupportedOperationException
    override def removeById(id : IdType) : Future[WriteResult] =
        throw new UnsupportedOperationException
}

private[hammersmith] abstract class HammersmithSyncDAO[EntityType : SerializableBSONObject, IdType <: AnyRef]
    extends SyncDAO[BSONDocument, EntityType, IdType, Any]

private[hammersmith] class BObjectBSONDocument(bobject : BObject) extends BSONDocument {
    // we have to make a mutable map to make hammersmith happy, which pretty much blows
    override val self = scala.collection.mutable.HashMap.empty ++ bobject.unwrapped
    override def asMap = self
}

private[hammersmith] class BObjectHammersmithQueryComposer extends QueryComposer[BObject, BSONDocument] {
    override def queryIn(q : BObject) : BSONDocument = new BObjectBSONDocument(q)
    override def queryOut(q : BSONDocument) : BObject = BObject(q.toList map { kv => (kv._1, BValue.wrap(kv._2)) })
}

private[hammersmith] class BObjectHammersmithEntityComposer extends EntityComposer[BObject, BSONDocument] {
    override def entityIn(o : BObject) : BSONDocument = new BObjectBSONDocument(o)
    override def entityOut(o : BSONDocument) : BObject = BObject(o.toList map { kv => (kv._1, BValue.wrap(kv._2)) })
}

/**
 * A BObject DAO that specifically backends to a Hammersmith DAO.
 * Subclass would provide the backend and could override the in/out type converters.
 */
private[hammersmith] abstract trait BObjectHammersmithSyncDAO[OuterIdType, InnerIdType <: AnyRef]
    extends BObjectComposedSyncDAO[OuterIdType, BSONDocument, BSONDocument, InnerIdType, Any] {
    override protected val backend : HammersmithSyncDAO[BSONDocument, InnerIdType]

    override protected val queryComposer : QueryComposer[BObject, BSONDocument]
    override protected val entityComposer : EntityComposer[BObject, BSONDocument]
    override protected val idComposer : IdComposer[OuterIdType, InnerIdType]
}

