package org.beaucatcher.jdriver

import org.beaucatcher.mongo.SyncCollection
import org.beaucatcher.bson._
import org.beaucatcher.bson.Implicits._
import org.beaucatcher.mongo._
import org.beaucatcher.jdriver._
import akka.actor.ActorSystem

trait JavaDriverTestContextProvider
    extends ContextProvider {
    def mongoContext = JavaTestContexts.testContext
}

trait JavaDriverTestDatabaseContextProvider
    extends ContextProvider {
    def mongoContext = JavaTestContexts.testDatabaseContext
}

object JavaTestContexts
    extends JavaDriverProvider {
    private val actorSystem = ActorSystem("beaucatcherjdrivertest")

    lazy val testContext = Context(mongoDriver, new SimpleMongoConfig("beaucatcherjdriver", "localhost", 27017),
        actorSystem)
    lazy val testDatabaseContext = Context(mongoDriver, new SimpleMongoConfig("beaucatcherjdriverdb", "localhost", 27017),
        actorSystem)
}
