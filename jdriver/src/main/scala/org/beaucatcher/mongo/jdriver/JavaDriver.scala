package org.beaucatcher.mongo.jdriver

import org.beaucatcher.bson._
import org.beaucatcher.mongo._
import org.beaucatcher.driver._
import com.mongodb._
import org.bson.types.{ ObjectId => JavaObjectId, _ }
import akka.actor.ActorSystem
import com.typesafe.config.Config

/**
 * [[org.beaucatcher.jdriver.JavaDriver]] is final with a private constructor - there's no way to create one
 * directly. If you're creating an [[org.beaucatcher.mongo.CollectionAccess]] object then mix in
 * [[org.beaucatcher.jdriver.JavaDriverProvider]]. Otherwise the [[org.beaucatcher.jdriver.JavaDriver]]
 * companion object has a field called `instance` with a driver instance.
 */
final class JavaDriver private[mongo] (val config: Config, val loader: ClassLoader)
    extends Driver {

    private[beaucatcher] override def newSyncCollection(name: String)(implicit context: DriverContext): SyncDriverCollection = {
        val jContext = context.asJavaContext
        val underlying = jContext.underlyingCollection(name)
        new JavaDriverSyncCollection(underlying, jContext)
    }

    private[beaucatcher] override def newAsyncCollection(name: String)(implicit context: DriverContext): AsyncDriverCollection = {
        implicit val executor = context.asJavaContext.actorSystem.dispatcher
        AsyncDriverCollection.fromSync(newSyncCollection(name))
    }

    private[beaucatcher] override def newContext(url: String, system: ActorSystem): DriverContext = {
        new JavaDriverContext(this, url, system)
    }
}
